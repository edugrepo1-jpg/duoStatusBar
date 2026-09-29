package io.github.kvmy666.duostatusbar.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.DuoRivePreview
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings

// ------------------------------------------------------------------ Battery icon
@Composable
internal fun CustomizeSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate,
    onRestart: () -> Unit
) {
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
            ) { onUpdate(settings.copy(enabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_percent),
                detail = stringResource(R.string.settings_percent_detail),
                checked = settings.showPercent,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(off = demo(showPercent = false), on = demo(showPercent = true))
                }
            ) { onUpdate(settings.copy(showPercent = it)) }

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
            ) { onUpdate(settings.copy(sizePercent = it.toInt())) }
            Text(
                text = stringResource(R.string.settings_size_restart),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onRestart,
                enabled = settings.enabled,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_restart)) }

            SettingSwitch(
                label = stringResource(R.string.settings_live_apply),
                detail = stringResource(R.string.settings_live_apply_detail),
                checked = settings.liveApply,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(liveApply = it)) }

            PositionEditor(
                offsetDp = settings.offsetX,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(offsetX = it)) }
        }
    }
}

// -------------------------------------------------------------------- Animations
@Composable
internal fun AnimationsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
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
            ) { onUpdate(settings.copy(animationsEnabled = it)) }

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
                onUpdate(settings.copy(revealMs = DuoPrefs.REVEAL_CHOICES[index]))
            }

            SettingSwitch(
                label = stringResource(R.string.settings_anim_arrival),
                detail = stringResource(R.string.settings_anim_arrival_detail),
                checked = settings.arrivalEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoRivePreview(fireReveal = true) { demo() }
                }
            ) { onUpdate(settings.copy(arrivalEnabled = it)) }
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
            ) { onUpdate(settings.copy(departureEnabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_anim_charging),
                detail = stringResource(R.string.settings_anim_charging_detail),
                checked = settings.chargingEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoRivePreview { phase -> demo(charging = phase >= 0.5f) }
                }
            ) { onUpdate(settings.copy(chargingEnabled = it)) }
        }
    }
}

// ------------------------------------------------------------------- Appearance
@Composable
internal fun AppearanceSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
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
            ) { onUpdate(settings.copy(useRive = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_clock_font),
                detail = stringResource(R.string.settings_clock_font_detail),
                checked = settings.systemClockFont,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(systemClockFont = it)) }
            OptionPicker(
                label = stringResource(R.string.settings_icon_color),
                options = listOf(
                    "auto" to stringResource(R.string.settings_icon_color_auto),
                    "black" to stringResource(R.string.settings_icon_color_black),
                    "white" to stringResource(R.string.settings_icon_color_white)
                ),
                selectedKey = settings.iconColor,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(iconColor = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_network_only),
                detail = stringResource(R.string.settings_network_only_detail),
                checked = settings.networkOnly,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(networkOnly = it)) }
            OptionPicker(
                label = stringResource(R.string.settings_sim),
                options = listOf(
                    "auto" to stringResource(R.string.settings_sim_auto),
                    "sim1" to stringResource(R.string.settings_sim_1),
                    "sim2" to stringResource(R.string.settings_sim_2)
                ),
                selectedKey = settings.simChoice,
                enabled = settings.enabled
            ) { onUpdate(settings.copy(simChoice = it)) }
            Text(
                text = stringResource(R.string.settings_sim_detail),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// -------------------------------------------------------------- Status bar icons
// Issue #4: an optional Shizuku path for hiding the stock icons when the module's own
// view-hiding leaves them behind. Independent of the LSPosed module, so it is safe when unused.
@Composable
internal fun IconsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate
) {
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
            ) { onUpdate(settings.copy(hideOtherIcons = it)) }
            IconHidingSetting(enabled = settings.enabled)
        }
    }
}

// ------------------------------------------------------------------ Tap actions
@Composable
internal fun ActionsSection(
    settings: DuoSettings,
    onUpdate: (DuoSettings) -> Unit,
    matches: SearchGate,
    autoExpand: Boolean
) {
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
            ) { onUpdate(settings.copy(tapAction = it)) }
            ActionPicker(
                label = stringResource(R.string.settings_double_tap),
                selectedKey = settings.doubleTapAction,
                enabled = autoExpand && settings.enabled
            ) { onUpdate(settings.copy(doubleTapAction = it)) }
            ActionPicker(
                label = stringResource(R.string.settings_long_press),
                selectedKey = settings.longPressAction,
                enabled = autoExpand && settings.enabled
            ) { onUpdate(settings.copy(longPressAction = it)) }
        }
    }
}

// ------------------------------------------------------------------------ About
@Composable
internal fun AboutSection(
    matches: SearchGate,
    status: String,
    history: List<String>,
    moduleLoadAt: Long,
    problem: String,
    onProblemChange: (String) -> Unit,
    collecting: Boolean,
    onCollect: () -> Unit,
    logSent: Boolean,
    exporting: Boolean,
    onExport: () -> Unit,
    checkUpdates: Boolean,
    onToggleUpdates: (Boolean) -> Unit,
    checkingUpdate: Boolean,
    onCheckNow: () -> Unit,
    updateMessage: String,
    updateUrl: String?
) {
    val context = LocalContext.current
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
                onValueChange = onProblemChange,
                label = { Text(stringResource(R.string.settings_problem)) },
                placeholder = { Text(stringResource(R.string.settings_problem_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onCollect,
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
                onClick = onExport,
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
            ) { on -> onToggleUpdates(on) }
            OutlinedButton(
                onClick = onCheckNow,
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

/** FR-25: the five arrival speeds, slowest first, matching [DuoPrefs.REVEAL_CHOICES] reversed. */
private val SPEED_LABELS = listOf("Slow", "Relaxed", "Normal", "Brisk", "Fast")
