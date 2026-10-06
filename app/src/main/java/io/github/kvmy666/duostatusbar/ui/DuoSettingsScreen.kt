package io.github.kvmy666.duostatusbar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealthCheck
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.StageOverride
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import io.github.kvmy666.duostatusbar.settings.UpdateChecker
import io.github.kvmy666.duostatusbar.settings.UpdateDownloadWorker
import io.github.kvmy666.duostatusbar.settings.UpdateInfo
import io.github.kvmy666.duostatusbar.settings.UpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The settings screen (FR-03/09/10/11/16/17).
 *
 * Every change is written to the app's own storage and then broadcast, which is what makes it reach the
 * status bar immediately: the module listens for that broadcast and re-reads (see
 * `hook/DuoHook.hookSettingsChanges`). The app cannot write to the module directly, so this handshake is
 * the mechanism — the app owns the values, the module applies them.
 *
 * Portrait and landscape each have their own copy. The phone's current orientation is the copy on
 * screen and the copy the status bar uses; rotating swaps both.
 *
 * One exception is the size: resizing the Rive view while System UI is running is what used to take it
 * down, so a size change is only applied on the next start. That is why the size row carries a Restart
 * button and says so.
 */
@Composable
fun DuoSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val orientation = DuoOrientation.of(LocalConfiguration.current.orientation)
    var settings by remember(orientation) { mutableStateOf(DuoPrefs.read(context, orientation)) }
    var status by remember { mutableStateOf(DuoPrefs.status(context)) }
    var history by remember { mutableStateOf(DuoPrefs.statusHistory(context)) }
    var dump by remember { mutableStateOf(DuoPrefs.dump(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(StudioPage.HOME) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    BackHandler(enabled = page != StudioPage.HOME || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else page = StudioPage.HOME
    }
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var logSent by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    var moduleLoadAt by remember { mutableStateOf(DuoPrefs.moduleLoadTime(context)) }
    var fallback by remember { mutableStateOf(DuoPrefs.fallback(context)) }
    // A leftover adb stage override pins the module to the simple drawing; the app can read it.
    var stageOverride by remember { mutableStateOf(StageOverride.read(context)) }
    var clearingOverride by remember { mutableStateOf(false) }
    var overrideFailed by remember { mutableStateOf(false) }
    // Whether the module is working, running old code, or unable to report (see ModuleHealthCheck).
    var moduleHealth by remember { mutableStateOf(ModuleHealthCheck.of(context)) }
    var checkUpdates by remember { mutableStateOf(DuoPrefs.checkUpdates(context)) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Export: the system file picker, so the log can be saved anywhere (Downloads, Drive, …) and shown
    // to anyone — independent of Telegram or a live network.
    val exportLog = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = pendingExport
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) R.string.settings_log_saved else R.string.settings_log_save_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Settings search: a row is shown when the query appears in its label or its detail. */
    fun matches(vararg text: String): Boolean =
        query.isBlank() || text.any { it.contains(query.trim(), ignoreCase = true) }

    val search = SearchGate { matches(*it) }

    // The module answers an instant after the broadcast; re-reading is simpler than a callback.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            status = DuoPrefs.status(context)
            history = DuoPrefs.statusHistory(context)
            dump = DuoPrefs.dump(context)
            moduleLoadAt = DuoPrefs.moduleLoadTime(context)
            fallback = DuoPrefs.fallback(context)
            stageOverride = StageOverride.read(context)
            moduleHealth = ModuleHealthCheck.of(context)
        }
    }

    fun update(new: DuoSettings) {
        val wasEnabled = settings.enabled
        settings = new
        DuoPrefs.write(context, new, orientation)
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
        // Also publish on the provider-independent channel, so a ROM where System UI cannot see the
        // provider (One UI 8) still receives the change.
        SettingsBridge.push(context)
        // Issue #4: the Shizuku icon hiding only makes sense while the element is drawing. Turning the
        // master switch off puts the stock icons back rather than leaving a bare status bar. The other
        // orientation can still be on, and that blacklist is one value for the whole phone, so it stays
        // until both orientations are off.
        val other = if (orientation == DuoOrientation.LANDSCAPE) {
            DuoOrientation.PORTRAIT
        } else {
            DuoOrientation.LANDSCAPE
        }
        if (wasEnabled && !new.enabled && DuoPrefs.hideStockIcons(context) &&
            !DuoPrefs.read(context, other).enabled
        ) {
            StockIconHider.apply(context, false) { }
        }
    }

    val onUpdate: (DuoSettings) -> Unit = { update(it) }

    // One-tap bug report, shared by the About button and the module-health card: packs the description,
    // the module status and the logs, uploads it, and falls back to the share sheet.
    fun collectAndSend() {
        if (collecting) return
        collecting = true
        logSent = false
        scope.launch {
            val logs = withContext(Dispatchers.IO) { RootLogs.collect() }
            collecting = false
            val message = problem.trim()
            val report = buildFullReport(problem, settings, status, history, dump, moduleLoadAt, logs)
            val file = writeDiagnostics(context, report)
            if (file == null) {
                shareText(context, report)
            } else {
                val caption = message.take(200).ifBlank { "Duo Status Bar log" }
                val result = withContext(Dispatchers.IO) { TelegramLog.send(context, file, caption) }
                when (result) {
                    TelegramLog.Result.SENT -> {
                        logSent = true
                        Toast.makeText(context, R.string.settings_log_sent, Toast.LENGTH_LONG).show()
                    }
                    TelegramLog.Result.RATE_LIMITED -> Toast.makeText(
                        context, R.string.settings_log_wait, Toast.LENGTH_LONG
                    ).show()
                    TelegramLog.Result.FAILED ->
                        fileUri(context, file)?.let { shareLog(context, it) } ?: shareText(context, report)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentWindowInsets = WindowInsets(0,0,0,0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { StudioNavigation(page) { page = it; query = "" } }
    ) { insets ->
    Column(
        modifier = Modifier.fillMaxSize().padding(insets)
            .verticalScroll(scroll).padding(horizontal=20.dp,vertical=20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StudioHeader(page,orientation==DuoOrientation.LANDSCAPE,query.isNotBlank())
        TextField(
            value=query,onValueChange={query=it},singleLine=true,
            placeholder={Text("Buscar um ajuste…",style=MaterialTheme.typography.bodyMedium)},
            leadingIcon={StudioGlyph(StudioSymbol.SEARCH,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(20.dp))},
            trailingIcon=if(query.isNotEmpty()){{TextButton(onClick={query=""}){Text("Limpar")}}}else null,
            shape=RoundedCornerShape(18.dp),
            colors=TextFieldDefaults.colors(
                focusedContainerColor=MaterialTheme.colorScheme.surface,
                unfocusedContainerColor=MaterialTheme.colorScheme.surface,
                focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent
            ),modifier=Modifier.fillMaxWidth().testTag("studio-search")
        )
        if(page==StudioPage.HOME && query.isBlank()) {
            StudioHome(settings,moduleHealth.state,onUpdate) { page=it }
        } else if(query.isNotBlank() && search("Personalizar a barra","Ativar","Usar o Duo Status Bar")) {
            StudioCard { SettingSwitch("Personalizar a barra","Ative o Duo na orientação atual.",settings.enabled,onChange={onUpdate(settings.copy(enabled=it))}) }
        }

        // Red health card: not loaded, running old code after an update, or loaded but unable to report
        // (the One UI 8 "Unknown authority" case). Recovery buttons plus a one-tap report.
        if (moduleHealth.state != ModuleState.OK && query.isBlank() && page == StudioPage.MORE) {
            ModuleHealthCard(
                state = moduleHealth.state,
                busy = collecting,
                onRestart = { restartSystemUi(context) },
                onReboot = { scope.launch { withContext(Dispatchers.IO) { RootLogs.reboot() } } },
                onReport = { collectAndSend() }
            )
        }

        // Fallback alert (see DuoPrefs.fallback): the module could not draw the animated element and is
        // using the simple drawing. Ask for the log where the developer watches — Telegram or GitHub.
        if (fallback.isNotEmpty() && query.isBlank() && page == StudioPage.MORE) {
            FallbackAlert(
                detail = fallback,
                busy = collecting,
                onTelegram = {
                    if (!collecting) {
                        collecting = true
                        scope.launch {
                            // Same full log as the About button: the module dump plus the root logcat.
                            val logs = withContext(Dispatchers.IO) { RootLogs.collect() }
                            collecting = false
                            sendLogOnTelegram(
                                context,
                                buildDiagnostics(settings, status, history, dump, moduleLoadAt) +
                                    "\n\n===== root log capture =====\n" + logs
                            )
                        }
                    }
                },
                onGitHub = { openGitHubIssue(context, fallback, status) },
                onDismiss = {
                    DuoPrefs.writeFallback(context, "")
                    fallback = ""
                }
            )
        }

        // A leftover adb override is the other reason the simple drawing shows with no warning: the app's
        // own settings ask for Rive, but `Settings.Global` outranks them. Explain it and offer the clear.
        val override = stageOverride
        if (override != null && query.isBlank() && page == StudioPage.MORE) {
            StageOverrideAlert(
                value = override,
                busy = clearingOverride,
                command = StageOverride.clearCommand,
                failed = overrideFailed,
                onClear = {
                    if (!clearingOverride) {
                        clearingOverride = true
                        overrideFailed = false
                        scope.launch {
                            withContext(Dispatchers.IO) { RootLogs.clearStageOverride() }
                            clearingOverride = false
                            stageOverride = StageOverride.read(context)
                            if (stageOverride == null) {
                                // The module re-reads on a settings broadcast, but the override lived in a
                                // store it cached; a restart makes the change deterministic.
                                restartSystemUi(context)
                            } else {
                                overrideFailed = true
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.settings_override_failed, StageOverride.clearCommand
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        if(StudioPage.VISUAL.visible(page,query)) {
            GeometrySection(settings,onUpdate,search)
            CustomizeSection(settings,onUpdate,search,onRestart={restartSystemUi(context)})
            AppearanceSection(settings,onUpdate,search)
        }
        if(StudioPage.EFFECTS.visible(page,query)) {
            if(search("Experimente o movimento","Demonstração","Ícones","Efeitos")) StudioCard {
                SectionTitle("Experimente o movimento")
                Text("Veja os ícones e os efeitos com estados simulados.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                EffectsPreview(settings,search)
            }
            AnimationsSection(settings,onUpdate,search)
            EffectsSection(settings,onUpdate,search)
        }
        if(StudioPage.MORE.visible(page,query)) {
        IconsSection(settings,onUpdate,search)

        val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
        ActionsSection(
            settings = settings,
            onUpdate = onUpdate,
            matches = search,
            autoExpand = autoExpand
        )

        AboutSection(
            matches = search,
            status = status,
            history = history,
            moduleLoadAt = moduleLoadAt,
            problem = problem,
            onProblemChange = { problem = it; logSent = false },
            collecting = collecting,
            onCollect = { collectAndSend() },
            logSent = logSent,
            exporting = exporting,
            onExport = {
                exporting = true
                logSent = false
                scope.launch {
                    val logs = withContext(Dispatchers.IO) { RootLogs.collect() }
                    exporting = false
                    pendingExport = buildFullReport(
                        problem, settings, status, history, dump, moduleLoadAt, logs
                    )
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportLog.launch("duo-log-$stamp.txt")
                }
            },
            checkUpdates = checkUpdates,
            onToggleUpdates = { on ->
                checkUpdates = on
                DuoPrefs.writeCheckUpdates(context, on)
                UpdateWorker.apply(context)
                if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            checkingUpdate = checkingUpdate,
            onCheckNow = {
                checkingUpdate = true
                updateMessage = ""
                updateInfo = null
                scope.launch {
                    val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
                    checkingUpdate = false
                    when {
                        info == null ->
                            updateMessage = context.getString(R.string.settings_update_failed)
                        UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME) -> {
                            updateInfo = info
                            updateMessage = context.getString(
                                R.string.settings_update_available, info.version
                            )
                        }
                        else -> updateMessage = context.getString(
                            R.string.settings_update_uptodate, BuildConfig.VERSION_NAME
                        )
                    }
                }
            },
            updateMessage = updateMessage,
            updateInfo = updateInfo,
            onDownload = {
                updateInfo?.let { info ->
                    UpdateDownloadWorker.enqueue(context, info.version, info.apkUrl, info.sha256)
                }
            }
        )
        }
    }
    }
}

/** Red Wine (FR-11): the app icon's background, reused for the header badge. */
private val BRAND_WINE = Color(0xFF7B1E3A)

/**
 * Asks the module to restart System UI so a size change takes effect.
 *
 * The module lives inside System UI, so killing its own process is the clean path — no root, no prompt.
 * The broadcast only reaches it when it is actually listening, though, and the old code assumed "the
 * module has loaded at least once" meant exactly that. It did not: a module that is injected but gated off
 * (the ColorOS/realme report: loaded, `stage=0`, no report) never registered the receiver, so the button
 * was silently dead. Now a fresh module load is the success signal: if System UI does not come back with a
 * newer load stamp, the app falls back to root, then to Shizuku — which is what makes the button work on a
 * ROM without `su` as long as Shizuku is running.
 */
private fun restartSystemUi(context: Context) {
    val before = DuoPrefs.moduleLoadTime(context)
    context.sendBroadcast(Intent(DuoPrefs.ACTION_RESTART_SYSTEMUI))
    Thread {
        if (before > 0L && moduleReloadedSince(context, before)) return@Thread
        val ok = runCatching { RootLogs.restartSystemUi() }.getOrDefault(false)
        if (!ok && StockIconHider.isShizukuRunning() && StockIconHider.isPermissionGranted()) {
            StockIconHider.exec(
                context,
                "pkill -f com.android.systemui || killall com.android.systemui || " +
                    "kill -9 ${'$'}(pidof com.android.systemui)",
                {}
            )
        }
    }.start()
}

/**
 * Whether System UI came back with a newer module-load stamp within [timeoutMs] of the restart request.
 * The module stamps `Settings.Global` before it does anything else, so a newer value is direct proof that
 * the restart happened and the module (or at least LSPosed) came back.
 */
private fun moduleReloadedSince(context: Context, before: Long, timeoutMs: Long = 2_500L): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        if (DuoPrefs.moduleLoadTime(context) > before) return true
        if (System.currentTimeMillis() >= deadline) return false
        try {
            Thread.sleep(250)
        } catch (_: InterruptedException) {
            return DuoPrefs.moduleLoadTime(context) > before
        }
    }
}
