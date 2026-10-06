package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.kvmy666.duostatusbar.DuoCanvasStill
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The settings search gate shared by the section cards: a row is shown when the query appears in its
 * label or its detail. Built by `DuoSettingsScreen`'s local `matches` helper.
 */
internal fun interface SearchGate {
    operator fun invoke(vararg text: String): Boolean
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
 * A leftover adb stage override (see `StageOverride`): the module is pinned to the simple drawing by a
 * `Settings.Global` value the app cannot write. This explains it and offers the root-assisted clear so
 * the user can get animations back without a computer.
 */
@Composable
internal fun StageOverrideAlert(
    value: Int,
    busy: Boolean,
    command: String,
    failed: Boolean,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.settings_override_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_override_message, value),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    if (failed) R.string.settings_override_failed else R.string.settings_override_command,
                    command
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onClear, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (busy) R.string.settings_override_clearing else R.string.settings_override_clear
                    )
                )
            }
        }
    }
}

/**
 * The module-health card (red): the module is not running, is running old code after an update, or is
 * loaded but cannot report. It offers the two ways to recover and a one-tap report, which is what a
 * "no report in the log" report needs to become actionable.
 */
@Composable
internal fun ModuleHealthCard(
    state: ModuleState,
    busy: Boolean,
    onRestart: () -> Unit,
    onReboot: () -> Unit,
    onReport: () -> Unit
) {
    val title = when (state) {
        ModuleState.NEEDS_RESTART -> R.string.module_health_restart_title
        ModuleState.NOT_REPORTING -> R.string.module_health_noreport_title
        else -> R.string.module_health_never_title
    }
    val text = when (state) {
        ModuleState.NEEDS_RESTART -> R.string.module_health_restart_text
        ModuleState.NOT_REPORTING -> R.string.module_health_noreport_text
        else -> R.string.settings_module_never
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRestart, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_restart))
            }
            OutlinedButton(onClick = onReboot, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_reboot))
            }
            OutlinedButton(onClick = onReport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (busy) R.string.settings_collecting else R.string.settings_report_bug))
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
internal fun PositionEditor(
    offsetDp: Int,
    enabled: Boolean,
    label: String? = null,
    hint: String? = null,
    visual: DuoVisual? = null,
    onOffset: (Int) -> Unit
) {
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(offsetDp.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val latestOffset by rememberUpdatedState(onOffset)
    val savedOffset by rememberUpdatedState(offsetDp)
    LaunchedEffect(offsetDp) { if (!dragging) drag = offsetDp.toFloat() }
    val limit = DuoPrefs.MAX_OFFSET.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label ?: stringResource(R.string.settings_position), style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF101014))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; latestOffset(drag.roundToInt()) },
                        onDragCancel = { dragging = false; drag = savedOffset.toFloat() }
                    ) { change, amount ->
                        change.consume()
                        drag = (drag + amount / density.density).coerceIn(-limit, limit)
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
            DuoCanvasStill(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(32.dp),
                visual = visual ?: DuoMapping.visual(
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
                text = hint ?: stringResource(R.string.settings_position_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(UiText.format("{0} dp", drag.toInt()), style = MaterialTheme.typography.bodySmall)
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
    var explaining by remember(label) { mutableStateOf(false) }
    Row(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.32f))
            .border(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f),RoundedCornerShape(14.dp))
            .clickable(role=Role.Button,onClickLabel=UiText.t("Ver explicação e controles")) { explaining=true }
            .heightIn(min=76.dp).padding(horizontal=12.dp,vertical=12.dp),
        horizontalArrangement=Arrangement.SpaceBetween,
        verticalAlignment=Alignment.CenterVertically
    ) {
        preview?.invoke()
        Column(Modifier.weight(1f).padding(start=if(preview==null)0.dp else 12.dp,end=10.dp),
            verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label,style=MaterialTheme.typography.bodyLarge,
                color=MaterialTheme.colorScheme.onSurface)
            if(!detail.isNullOrBlank()) Text(detail,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(UiText.t(if(checked)"Ativo" else "Desativado"),style=MaterialTheme.typography.labelSmall,
                color=if(checked)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal=9.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(UiText.t("Configurar"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
            StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.onPrimaryContainer,Modifier.size(14.dp))
        }
    }
    if(explaining) FeaturePresentation(label,detail,checked,enabled,
        onDismiss={explaining=false},onChange=onChange)

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

    val onPermission: (Boolean) -> Unit = remember {
        { grantedNow ->
            granted = grantedNow
            if (grantedNow && DuoPrefs.hideStockIcons(context)) {
                StockIconHider.apply(context, true) { ok ->
                    note = if (ok) null else context.getString(R.string.settings_icons_failed)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        StockIconHider.observePermissionResult(onPermission)
        // Shizuku may have been started after the screen opened, so check once when the row appears.
        running = StockIconHider.isShizukuRunning()
        granted = StockIconHider.isPermissionGranted()
        onDispose { StockIconHider.stopObservingPermissionResult(onPermission) }
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
    formatValue: ((Float) -> String)? = null,
    onChange: (Float) -> Unit
) {
    var draft by remember { mutableStateOf(ControlDraft(value.coerceIn(range.start,range.endInclusive))) }
    val latestChange by rememberUpdatedState(onChange)
    LaunchedEffect(value,range,enabled) {
        draft=if(enabled)draft.external(value.coerceIn(range.start,range.endInclusive))
            else ControlDraft(value.coerceIn(range.start,range.endInclusive))
    }
    val percent=label.endsWith("%")
    val title=if(percent)label.removeSuffix("${value.toInt()}%").trimEnd(' ',':') else label
    val badge=formatValue?.invoke(draft.value) ?: "${draft.value.roundToInt()}${if(percent)"%" else ""}"
    val increment=if(steps>0)(range.endInclusive-range.start)/(steps+1) else 1f
    fun commit(result: Float) {
        val next=result.coerceIn(range.start,range.endInclusive).roundToInt().toFloat()
        draft=ControlDraft(next)
        if(next!=value)latestChange(next)
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            preview?.invoke()
            Text(title,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
            Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Text(badge,Modifier.padding(horizontal=10.dp,vertical=6.dp),
                    style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={commit(draft.value-increment)},enabled=enabled&&draft.value>range.start,
                modifier=Modifier.background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)).semantics { contentDescription=UiText.format("Diminuir {0}", title) }) {
                StudioGlyph(StudioSymbol.MINUS,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(17.dp))
            }
            Slider(
                modifier=Modifier.weight(1f),value=draft.value,
                onValueChange={draft=draft.move(it,range)},
                onValueChangeFinished={commit(draft.value)},valueRange=range,steps=steps,enabled=enabled,
                colors=SliderDefaults.colors(inactiveTrackColor=MaterialTheme.colorScheme.surfaceVariant)
            )
            IconButton(onClick={commit(draft.value+increment)},enabled=enabled&&draft.value<range.endInclusive,
                modifier=Modifier.background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)).semantics { contentDescription=UiText.format("Aumentar {0}", title) }) {
                StudioGlyph(StudioSymbol.PLUS,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(17.dp))
            }
        }
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
    OverlayBackdrop(expanded)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,style=MaterialTheme.typography.bodyLarge)
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.7f))
                .border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(14.dp))
                .clickable(enabled=enabled,role=Role.Button){expanded=true}
                .heightIn(min=50.dp).padding(horizontal=14.dp,vertical=12.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text(options.firstOrNull{it.first==selectedKey}?.second ?: selectedKey,
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.primary.copy(alpha=if(enabled)1f else .4f),
                    modifier=Modifier.weight(1f))
                StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(16.dp).rotate(90f))
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                options.forEach { (key,name) ->
                    DropdownMenuItem(text={Text(name)},
                        trailingIcon=if(key==selectedKey){{StudioGlyph(StudioSymbol.CHECK,MaterialTheme.colorScheme.primary,Modifier.size(18.dp))}}else null,
                        onClick={expanded=false;onSelect(key)})
                }
            }
        }
    }
}

/**
 * The diagnostics privilege control (root / Shizuku).
 *
 * Root is optional and is only used to **read** the system log into a bug report the user explicitly
 * sends, so it is never requested until the user asks — no root prompt appears uninvited. Shizuku is the
 * alternative that also works with a non-rooted phone started over adb. Both are best-effort: with
 * neither, a report still carries the module's own log and the device facts.
 */
@Composable
internal fun RootAccessSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(DuoPrefs.rootAllowed(context)) }
    var running by remember { mutableStateOf(StockIconHider.isShizukuRunning()) }
    var granted by remember { mutableStateOf(StockIconHider.isPermissionGranted()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val onPermission: (Boolean) -> Unit = remember {
        { grantedNow -> granted = grantedNow }
    }

    DisposableEffect(Unit) {
        StockIconHider.observePermissionResult(onPermission)
        // Re-read when the row appears: Shizuku may have been started after the screen opened.
        running = StockIconHider.isShizukuRunning()
        granted = StockIconHider.isPermissionGranted()
        onDispose { StockIconHider.stopObservingPermissionResult(onPermission) }
    }

    // If root was granted before, verify it still is. Revoking it in the root manager (KernelSU Next /
    // SukiSU) otherwise leaves the stored flag `true` and the guide below never appears.
    LaunchedEffect(Unit) {
        if (allowed) {
            val ok = withContext(Dispatchers.IO) { RootLogs.recheckRoot(context) }
            if (!ok) {
                allowed = false
                result = context.getString(R.string.settings_root_result_failed)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_root_detail),
            style = MaterialTheme.typography.bodySmall
        )
        val status = when {
            allowed -> stringResource(R.string.settings_root_granted)
            running && granted -> stringResource(R.string.settings_root_shizuku)
            running -> stringResource(R.string.settings_icons_need_permission)
            else -> stringResource(R.string.settings_root_denied)
        }
        val elevated = allowed || (running && granted)
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = if (elevated) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
        // The outcome of the last attempt, so a tap that fails says why instead of looking dead.
        result?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!allowed) {
            // A screenshot of the root manager's per-app Superuser screen, with the terse steps below it,
            // so the one case that needs a manual toggle is shown rather than described.
            // The animated GIF of the root manager's per-app Superuser toggle. Compose's Image does not
            // animate a GIF, so it is hosted in an ImageView. ImageDecoder is used explicitly, because
            // `setImageResource` does not always hand back an animated drawable.
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = ctx.getString(R.string.settings_root_guide_image)
                        runCatching {
                            val src = android.graphics.ImageDecoder.createSource(
                                ctx.resources, R.drawable.guide_root_superuser
                            )
                            when (val d = android.graphics.ImageDecoder.decodeDrawable(src)) {
                                is android.graphics.drawable.AnimatedImageDrawable -> {
                                    d.repeatCount =
                                        android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                                    setImageDrawable(d)
                                    d.start()
                                }
                                else -> setImageDrawable(d)
                            }
                        }.onFailure {
                            setImageResource(R.drawable.guide_root_superuser)
                        }
                        // AnimatedImageDrawable only runs once it is attached and visible; start it again
                        // after the view is attached so the loop always begins.
                        post {
                            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            )
            Text(
                text = stringResource(R.string.settings_root_guide_steps),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.settings_root_guide_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick = {
                    if (!busy) {
                        busy = true
                        result = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { RootLogs.requestRoot(context) }
                            allowed = ok
                            busy = false
                            result = context.getString(
                                if (ok) R.string.settings_root_result_ok
                                else R.string.settings_root_result_failed
                            )
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (busy) R.string.settings_root_checking else R.string.settings_root_allow
                    )
                )
            }
        }
        if (running && !granted) {
            OutlinedButton(
                onClick = { StockIconHider.requestPermission() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_icons_grant))
            }
        }
    }
}

