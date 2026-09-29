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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.DuoRivePreview
import io.github.kvmy666.duostatusbar.DuoRiveStill
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.hook.DuoMapping
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

        // ------------------------------------------------------------------ Battery icon
        if (matches(
                stringResource(R.string.section_customize), stringResource(R.string.section_element),
                stringResource(R.string.settings_master),
                stringResource(R.string.settings_master_detail), stringResource(R.string.settings_percent),
                stringResource(R.string.settings_percent_detail), stringResource(R.string.settings_size),
                stringResource(R.string.settings_position), stringResource(R.string.settings_live_apply)
            )
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.section_customize))
                Text(
                    text = stringResource(R.string.settings_customize_detail),
                    style = MaterialTheme.typography.bodySmall
                )
                SettingSwitch(
                    label = stringResource(R.string.settings_master),
                    detail = stringResource(R.string.settings_master_detail),
                    checked = settings.enabled,
                    preview = {
                        // Off is the element *absent*: an empty chip, not a ring reading "0".
                        DuoSettingPreview(
                            off = demo(level = 0, showPercent = false),
                            on = demo()
                        )
                    }
                ) { update(settings.copy(enabled = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_percent),
                    detail = stringResource(R.string.settings_percent_detail),
                    checked = settings.showPercent,
                    enabled = settings.enabled,
                    preview = {
                        DuoSettingPreview(off = demo(showPercent = false), on = demo(showPercent = true))
                    }
                ) { update(settings.copy(showPercent = it)) }

                LabelledSlider(
                    label = "${stringResource(R.string.settings_size)} ${settings.sizePercent}%",
                    value = settings.sizePercent.toFloat(),
                    range = DuoPrefs.MIN_SIZE.toFloat()..DuoPrefs.MAX_SIZE.toFloat(),
                    enabled = settings.enabled,
                    preview = {
                        DuoSettingPreview(
                            off = demo(),
                            on = demo(),
                            scaleFrom = DuoPrefs.MIN_SIZE.toFloat() / DuoPrefs.MAX_SIZE
                        )
                    }
                ) { update(settings.copy(sizePercent = it.toInt())) }
                Text(
                    text = stringResource(R.string.settings_size_restart),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { restartSystemUi(context) },
                    enabled = settings.enabled,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.settings_restart)) }

                SettingSwitch(
                    label = stringResource(R.string.settings_live_apply),
                    detail = stringResource(R.string.settings_live_apply_detail),
                    checked = settings.liveApply,
                    enabled = settings.enabled
                ) { update(settings.copy(liveApply = it)) }

                PositionEditor(
                    offsetDp = settings.offsetX,
                    enabled = settings.enabled
                ) { update(settings.copy(offsetX = it)) }
            }
        }

        // -------------------------------------------------------------------- Animations
        if (matches(
                stringResource(R.string.section_animations), stringResource(R.string.settings_animations),
                stringResource(R.string.settings_anim_speed), stringResource(R.string.settings_anim_arrival),
                stringResource(R.string.settings_anim_departure), stringResource(R.string.settings_anim_charging)
            )
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.section_animations))
                SettingSwitch(
                    label = stringResource(R.string.settings_animations),
                    detail = stringResource(R.string.settings_animations_detail),
                    checked = settings.animationsEnabled,
                    enabled = settings.enabled
                ) { update(settings.copy(animationsEnabled = it)) }

                // The arrival is one Rive timeline at five speeds, so this is a choice, not a number.
                val speedIndex = (DuoPrefs.REVEAL_CHOICES.size - 1 -
                        DuoPrefs.REVEAL_CHOICES.indexOf(settings.revealMs).coerceAtLeast(0))
                LabelledSlider(
                    label = "${stringResource(R.string.settings_anim_speed)}: ${SPEED_LABELS[speedIndex]}",
                    value = speedIndex.toFloat(),
                    range = 0f..(DuoPrefs.REVEAL_CHOICES.size - 1).toFloat(),
                    steps = DuoPrefs.REVEAL_CHOICES.size - 2,
                    enabled = settings.enabled && settings.animationsEnabled
                ) { value ->
                    val index = (DuoPrefs.REVEAL_CHOICES.size - 1 - value.toInt())
                        .coerceIn(0, DuoPrefs.REVEAL_CHOICES.size - 1)
                    update(settings.copy(revealMs = DuoPrefs.REVEAL_CHOICES[index]))
                }

                SettingSwitch(
                    label = stringResource(R.string.settings_anim_arrival),
                    detail = stringResource(R.string.settings_anim_arrival_detail),
                    checked = settings.arrivalEnabled,
                    enabled = settings.enabled && settings.animationsEnabled,
                    preview = {
                        DuoRivePreview(fireReveal = true) { demo() }
                    }
                ) { update(settings.copy(arrivalEnabled = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_anim_departure),
                    detail = stringResource(R.string.settings_anim_departure_detail),
                    checked = settings.departureEnabled,
                    enabled = settings.enabled && settings.animationsEnabled,
                    preview = {
                        DuoRivePreview(fireReveal = true) { phase ->
                            if (phase < 0.5f) demo() else demo().copy(visible = false)
                        }
                    }
                ) { update(settings.copy(departureEnabled = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_anim_charging),
                    detail = stringResource(R.string.settings_anim_charging_detail),
                    checked = settings.chargingEnabled,
                    enabled = settings.enabled && settings.animationsEnabled,
                    preview = {
                        DuoRivePreview { phase -> demo(charging = phase >= 0.5f) }
                    }
                ) { update(settings.copy(chargingEnabled = it)) }
            }
        }

        // ------------------------------------------------------------------- Appearance
        if (matches(stringResource(R.string.section_look), stringResource(R.string.settings_renderer),
                stringResource(R.string.settings_renderer_detail), stringResource(R.string.settings_clock_font),
                stringResource(R.string.settings_icon_color), stringResource(R.string.settings_network_only),
                stringResource(R.string.settings_sim))
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.section_look))
                SettingSwitch(
                    label = stringResource(R.string.settings_renderer),
                    detail = stringResource(R.string.settings_renderer_detail),
                    checked = settings.useRive,
                    enabled = settings.enabled
                ) { update(settings.copy(useRive = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_clock_font),
                    detail = stringResource(R.string.settings_clock_font_detail),
                    checked = settings.systemClockFont,
                    enabled = settings.enabled
                ) { update(settings.copy(systemClockFont = it)) }
                OptionPicker(
                    label = stringResource(R.string.settings_icon_color),
                    options = listOf(
                        "auto" to stringResource(R.string.settings_icon_color_auto),
                        "black" to stringResource(R.string.settings_icon_color_black),
                        "white" to stringResource(R.string.settings_icon_color_white)
                    ),
                    selectedKey = settings.iconColor,
                    enabled = settings.enabled
                ) { update(settings.copy(iconColor = it)) }
                SettingSwitch(
                    label = stringResource(R.string.settings_network_only),
                    detail = stringResource(R.string.settings_network_only_detail),
                    checked = settings.networkOnly,
                    enabled = settings.enabled
                ) { update(settings.copy(networkOnly = it)) }
                OptionPicker(
                    label = stringResource(R.string.settings_sim),
                    options = listOf(
                        "auto" to stringResource(R.string.settings_sim_auto),
                        "sim1" to stringResource(R.string.settings_sim_1),
                        "sim2" to stringResource(R.string.settings_sim_2)
                    ),
                    selectedKey = settings.simChoice,
                    enabled = settings.enabled
                ) { update(settings.copy(simChoice = it)) }
                Text(
                    text = stringResource(R.string.settings_sim_detail),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // -------------------------------------------------------------- Status bar icons
        // Issue #4: an optional Shizuku path for hiding the stock icons when the module's own
        // view-hiding leaves them behind. Independent of the LSPosed module, so it is safe when unused.
        if (matches(stringResource(R.string.section_icons), stringResource(R.string.settings_hide_icons),
                stringResource(R.string.settings_hide_icons_detail),
                stringResource(R.string.settings_hide_other_icons))
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.section_icons))
                SettingSwitch(
                    label = stringResource(R.string.settings_hide_other_icons),
                    detail = stringResource(R.string.settings_hide_other_icons_detail),
                    checked = settings.hideOtherIcons,
                    enabled = settings.enabled
                ) { update(settings.copy(hideOtherIcons = it)) }
                IconHidingSetting(enabled = settings.enabled)
            }
        }

        // ------------------------------------------------------------------ Tap actions
        val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
        if (matches(stringResource(R.string.section_actions), stringResource(R.string.settings_tap),
                stringResource(R.string.settings_double_tap), stringResource(R.string.settings_long_press))
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.section_actions))
                Text(
                    text = if (autoExpand) {
                        stringResource(R.string.settings_gestures_note)
                    } else {
                        stringResource(R.string.settings_gestures_missing)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                ActionPicker(
                    label = stringResource(R.string.settings_tap),
                    selectedKey = settings.tapAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(tapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_double_tap),
                    selectedKey = settings.doubleTapAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(doubleTapAction = it)) }
                ActionPicker(
                    label = stringResource(R.string.settings_long_press),
                    selectedKey = settings.longPressAction,
                    enabled = autoExpand && settings.enabled
                ) { update(settings.copy(longPressAction = it)) }
            }
        }

        // ------------------------------------------------------------------------ About
        val emptyStatus = stringResource(R.string.settings_no_status)
        if (matches(stringResource(R.string.section_about), stringResource(R.string.settings_status),
                stringResource(R.string.settings_collect_log),
                stringResource(R.string.settings_check_updates), stringResource(R.string.settings_donate))
        ) Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.section_about))
                Text(
                    text = status.ifEmpty { emptyStatus },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        moduleLoadAt > 0L ->
                            stringResource(R.string.settings_module_loaded, formatTimestamp(moduleLoadAt))
                        status.isNotEmpty() -> stringResource(R.string.settings_module_loaded_recent)
                        else -> stringResource(R.string.settings_module_never)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (history.isNotEmpty()) {
                    Text(stringResource(R.string.settings_history), style = MaterialTheme.typography.labelLarge)
                    history.takeLast(3).forEach { entry ->
                        Text(
                            text = entry.substringAfter(' '),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // Let the user say what went wrong; it travels with the log so a fix can start from the
                // description rather than from a guess. Then one button packs status + logs and sends them.
                OutlinedTextField(
                    value = problem,
                    onValueChange = { problem = it; logSent = false },
                    label = { Text(stringResource(R.string.settings_problem)) },
                    placeholder = { Text(stringResource(R.string.settings_problem_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
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
                    enabled = !collecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (collecting) R.string.settings_collecting else R.string.settings_collect_log
                        )
                    )
                }
                if (logSent) {
                    Text(
                        text = stringResource(R.string.settings_log_sent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.settings_collect_log_detail),
                    style = MaterialTheme.typography.bodySmall
                )
                // Export: save the same report anywhere with the system file picker, independent of
                // Telegram or a network. The escape hatch when the upload path is not available.
                OutlinedButton(
                    onClick = {
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
                    enabled = !exporting && !collecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (exporting) R.string.settings_collecting else R.string.settings_export_log
                        )
                    )
                }
                // ----- Updates -----
                SettingSwitch(
                    label = stringResource(R.string.settings_check_updates),
                    detail = stringResource(R.string.settings_check_updates_detail),
                    checked = checkUpdates
                ) { on ->
                    checkUpdates = on
                    DuoPrefs.writeCheckUpdates(context, on)
                    UpdateWorker.apply(context)
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                OutlinedButton(
                    onClick = {
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
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (checkingUpdate) R.string.settings_checking else R.string.settings_check_now
                        )
                    )
                }
                if (updateMessage.isNotEmpty()) {
                    Text(updateMessage, style = MaterialTheme.typography.bodySmall)
                }
                updateUrl?.let { url ->
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.settings_update_open)) }
                }
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paypal),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_donate))
                }
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Red Wine (FR-11): the app icon's background, reused for the header badge. */
private val BRAND_WINE = Color(0xFF7B1E3A)

/** FR-25: the five arrival speeds, slowest first, matching [DuoPrefs.REVEAL_CHOICES] reversed. */
private val SPEED_LABELS = listOf("Slow", "Relaxed", "Normal", "Brisk", "Fast")

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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/**
 * The fallback alert: the module could not draw the animated element and is using the simple drawing.
 * It offers the two routes the developer actually reads — Telegram or GitHub — and can be dismissed once
 * the log has been sent.
 */
@Composable
private fun FallbackAlert(
    detail: String,
    busy: Boolean,
    onTelegram: () -> Unit,
    onGitHub: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.fallback_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.fallback_message), style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onTelegram, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.settings_collecting else R.string.fallback_telegram))
            }
            OutlinedButton(onClick = onGitHub, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fallback_github))
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fallback_dismiss))
            }
        }
    }
}

/**
 * FR-17: set the element's horizontal position on a status-bar-shaped preview.
 *
 * It is drawn to look like the real bar — a dark pill with the clock on the left and the element where
 * the battery sits — and the element is the **same Canvas element** the status bar falls back to, driven
 * by the same [DuoMapping]. Dragging anywhere on the bar moves it, and the value is shown so it can be
 * set exactly.
 */
@Composable
private fun PositionEditor(offsetDp: Int, enabled: Boolean, onOffset: (Int) -> Unit) {
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(offsetDp.toFloat()) }
    LaunchedEffect(offsetDp) { drag = offsetDp.toFloat() }
    val limit = DuoPrefs.MAX_OFFSET.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.settings_position), style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101014))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { _, amount ->
                        val next = (drag + amount.x / density.density).coerceIn(-limit, limit)
                        drag = next
                        onOffset(next.toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "9:41",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 14.dp)
            )
            DuoRiveStill(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(32.dp),
                visual = DuoMapping.visual(
                    level = 78,
                    charging = false,
                    saver = false,
                    showPercent = true,
                    wifiLevel = 3,
                    cellLevel = 4,
                    airplane = false
                ),
                translationXDp = drag
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_position_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text("${drag.toInt()} dp", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    drag = 0f
                    onOffset(0)
                },
                enabled = enabled
            ) { Text(stringResource(R.string.settings_position_reset)) }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        preview?.invoke()
        Column(Modifier.weight(1f).padding(start = if (preview == null) 0.dp else 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/**
 * Issue #4: the Shizuku icon-hiding control.
 *
 * Owns its own Shizuku state (running / permission / last result) so it never blocks the rest of the
 * screen. The permission callback re-reads the wish from [DuoPrefs] instead of closing over a value a
 * recomposition may have replaced.
 */
@Composable
private fun IconHidingSetting(enabled: Boolean) {
    val context = LocalContext.current
    var wanted by remember { mutableStateOf(DuoPrefs.hideStockIcons(context)) }
    var running by remember { mutableStateOf(StockIconHider.isShizukuRunning()) }
    var granted by remember { mutableStateOf(StockIconHider.isPermissionGranted()) }
    var note by remember { mutableStateOf<String?>(null) }

    fun apply(hide: Boolean) {
        when {
            !running -> note = null
            !granted -> if (hide) StockIconHider.requestPermission() else {
                note = context.getString(R.string.settings_icons_need_permission)
            }
            else -> StockIconHider.apply(context, hide) { ok ->
                note = if (ok) null else context.getString(R.string.settings_icons_failed)
            }
        }
    }

    DisposableEffect(Unit) {
        StockIconHider.observePermissionResult { grantedNow ->
            granted = grantedNow
            if (grantedNow && DuoPrefs.hideStockIcons(context)) {
                StockIconHider.apply(context, true) { ok ->
                    note = if (ok) null else context.getString(R.string.settings_icons_failed)
                }
            }
        }
        // Shizuku may have been started after the screen opened, so check once when the row appears.
        running = StockIconHider.isShizukuRunning()
        granted = StockIconHider.isPermissionGranted()
        onDispose { StockIconHider.stopObservingPermissionResult() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingSwitch(
            label = stringResource(R.string.settings_hide_icons),
            detail = stringResource(R.string.settings_hide_icons_detail),
            checked = wanted,
            enabled = enabled
        ) { next ->
            wanted = next
            DuoPrefs.writeHideStockIcons(context, next)
            apply(next)
        }
        val status = when {
            !running -> stringResource(R.string.settings_icons_no_shizuku)
            !granted -> stringResource(R.string.settings_icons_need_permission)
            note != null -> note!!
            else -> stringResource(R.string.settings_icons_ready)
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        if (running && !granted) {
            OutlinedButton(onClick = { StockIconHider.requestPermission() }, enabled = enabled) {
                Text(stringResource(R.string.settings_icons_grant))
            }
        }
    }
}

/**
 * FR-09: the fixed snapshot the setting demos morph from and to.
 *
 * One battery level for every demo, so the rows are comparable and the only thing that changes between
 * the two ends is the setting being demonstrated.
 */
private fun demo(
    level: Int = DEMO_LEVEL,
    charging: Boolean = false,
    showPercent: Boolean = true,
    airplane: Boolean = false,
    dnd: Boolean = false
) = DuoMapping.visual(
    level = level,
    charging = charging,
    saver = false,
    showPercent = showPercent,
    wifiLevel = 3,
    cellLevel = 4,
    airplane = airplane,
    dnd = dnd
)

/** FR-09: a level that shows a clear half-full ring rather than an empty or full one. */
private const val DEMO_LEVEL = 72

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            preview?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = if (preview == null) 0.dp else 12.dp)
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled)
    }
}

/** Pick one of Auto Expand's actions for a gesture. */
@Composable
private fun ActionPicker(
    label: String,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    OptionPicker(label, DuoActions.ALL, selectedKey, enabled, onSelect)
}

/** A plain labelled dropdown over a (key → label) list: no experimental API needed. */
@Composable
private fun OptionPicker(
    label: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        }
                    )
                }
            }
        }
    }
}
