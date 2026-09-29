package io.github.kvmy666.duostatusbar.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import io.github.kvmy666.duostatusbar.settings.UpdateChecker
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
 * One exception is the size: resizing the Rive view while System UI is running is what used to take it
 * down, so a size change is only applied on the next start. That is why the size row carries a Restart
 * button and says so.
 */
@Composable
fun DuoSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(DuoPrefs.read(context)) }
    var status by remember { mutableStateOf(DuoPrefs.status(context)) }
    var history by remember { mutableStateOf(DuoPrefs.statusHistory(context)) }
    var dump by remember { mutableStateOf(DuoPrefs.dump(context)) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    var logSent by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    var moduleLoadAt by remember { mutableStateOf(DuoPrefs.moduleLoadTime(context)) }
    var fallback by remember { mutableStateOf(DuoPrefs.fallback(context)) }
    var checkUpdates by remember { mutableStateOf(DuoPrefs.checkUpdates(context)) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
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
        }
    }

    fun update(new: DuoSettings) {
        val wasEnabled = settings.enabled
        settings = new
        DuoPrefs.write(context, new)
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
        // Issue #4: the Shizuku icon hiding only makes sense while the element is drawing. Turning the
        // master switch off puts the stock icons back rather than leaving a bare status bar.
        if (wasEnabled && !new.enabled && DuoPrefs.hideStockIcons(context)) {
            StockIconHider.apply(context, false) { }
        }
    }

    val onUpdate: (DuoSettings) -> Unit = { update(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Issue #3: edge-to-edge draws under the system bars, so the content is inset away from the
            // status bar and navigation bar instead of being clipped by them.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(BRAND_WINE),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.settings_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Fallback alert (see DuoPrefs.fallback): the module could not draw the animated element and is
        // using the simple drawing. Ask for the log where the developer watches — Telegram or GitHub.
        if (fallback.isNotEmpty() && query.isBlank()) {
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

        CustomizeSection(
            settings = settings,
            onUpdate = onUpdate,
            matches = search,
            onRestart = { restartSystemUi(context) }
        )

        AnimationsSection(
            settings = settings,
            onUpdate = onUpdate,
            matches = search
        )

        AppearanceSection(
            settings = settings,
            onUpdate = onUpdate,
            matches = search
        )

        IconsSection(
            settings = settings,
            onUpdate = onUpdate,
            matches = search
        )

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
            onCollect = {
                collecting = true
                logSent = false
                scope.launch {
                    val logs = withContext(Dispatchers.IO) { RootLogs.collect() }
                    collecting = false
                    val message = problem.trim()
                    val report = buildFullReport(
                        problem, settings, status, history, dump, moduleLoadAt, logs
                    )
                    val file = writeDiagnostics(context, report)
                    if (file == null) {
                        shareText(context, report)
                    } else {
                        val caption = message.take(200).ifBlank { "Duo Status Bar log" }
                        val result = withContext(Dispatchers.IO) {
                            TelegramLog.send(context, file, caption)
                        }
                        when (result) {
                            TelegramLog.Result.SENT -> {
                                logSent = true
                                Toast.makeText(
                                    context, R.string.settings_log_sent, Toast.LENGTH_LONG
                                ).show()
                            }
                            TelegramLog.Result.RATE_LIMITED -> Toast.makeText(
                                context, R.string.settings_log_wait, Toast.LENGTH_LONG
                            ).show()
                            TelegramLog.Result.FAILED ->
                                fileUri(context, file)?.let { shareLog(context, it) }
                                    ?: shareText(context, report)
                        }
                    }
                }
            },
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
                updateUrl = null
                scope.launch {
                    val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
                    checkingUpdate = false
                    when {
                        info == null ->
                            updateMessage = context.getString(R.string.settings_update_failed)
                        UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME) -> {
                            updateUrl = info.url
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
            updateUrl = updateUrl
        )
    }
}

/** Red Wine (FR-11): the app icon's background, reused for the header badge. */
private val BRAND_WINE = Color(0xFF7B1E3A)

/**
 * Asks the module to restart System UI so a size change takes effect.
 *
 * The module lives inside System UI, so the broadcast is the clean path when it is loaded. When it is
 * not (it has never reported), nothing receives the broadcast and the button looks dead, so System UI
 * is restarted directly — root first, then Shizuku — which is what makes the button work on a ROM
 * without `su` as long as Shizuku is running.
 */
private fun restartSystemUi(context: Context) {
    context.sendBroadcast(Intent(DuoPrefs.ACTION_RESTART_SYSTEMUI))
    if (DuoPrefs.moduleLoadTime(context) <= 0L) {
        Thread {
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
}
