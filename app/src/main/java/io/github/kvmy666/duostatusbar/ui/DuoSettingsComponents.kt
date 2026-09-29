package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.DuoRiveStill
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.StockIconHider

/**
 * The settings search gate shared by the section cards: a row is shown when the query appears in its
 * label or its detail. Built by `DuoSettingsScreen`'s local `matches` helper.
 */
internal fun interface SearchGate {
    operator fun invoke(vararg text: String): Boolean
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/**
 * The fallback alert: the module could not draw the animated element and is using the simple drawing.
 * It offers the two routes the developer actually reads — Telegram or GitHub — and can be dismissed once
 * the log has been sent.
 */
@Composable
internal fun FallbackAlert(
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
internal fun PositionEditor(offsetDp: Int, enabled: Boolean, onOffset: (Int) -> Unit) {
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
internal fun SettingSwitch(
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
internal fun IconHidingSetting(enabled: Boolean) {
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

@Composable
internal fun LabelledSlider(
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
internal fun ActionPicker(
    label: String,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    OptionPicker(label, DuoActions.ALL, selectedKey, enabled, onSelect)
}

/** A plain labelled dropdown over a (key → label) list: no experimental API needed. */
@Composable
internal fun OptionPicker(
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
