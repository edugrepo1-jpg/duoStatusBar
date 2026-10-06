package io.github.kvmy666.duostatusbar.ui
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.kvmy666.duostatusbar.DuoCanvasPreview
import io.github.kvmy666.duostatusbar.DuoCanvasStill
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoPart
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.UpdateInfo
// ------------------------------------------------------------------ Battery icon
@Composable
internal fun CustomizeSection(settings: DuoSettings,onUpdate:(DuoSettings)->Unit,matches:SearchGate,onRestart:()->Unit) {
    if(matches("Porcentagem","Número","Altura da porcentagem","Mostrar a porcentagem")) StudioCard {
        SectionTitle("Porcentagem")
        SettingSwitch(stringResource(R.string.settings_percent),"Mostre a bateria no topo do anel.",settings.showPercent,settings.enabled) {
            onUpdate(settings.copy(showPercent=it))
        }
        LabelledSlider("Altura da porcentagem: ${settings.percentHeight}%",settings.percentHeight.toFloat(),
            DuoPrefs.MIN_PERCENT_HEIGHT.toFloat()..DuoPrefs.MAX_PERCENT_HEIGHT.toFloat(),enabled=settings.enabled&&settings.showPercent) {
            onUpdate(settings.copy(percentHeight=it.toInt()))
        }
        Text(stringResource(R.string.settings_percent_height_detail),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(matches("Posição","Ícones fora do anel","Separar","Espaço nas bordas","Aplicar mudanças","Reiniciar","Horizontal","Vertical")) StudioCard {
        SectionTitle("Posição e organização")
        SettingSwitch(stringResource(R.string.settings_split),"Separe os indicadores e ajuste cada posição.",settings.splitIndicators,settings.enabled) {
            onUpdate(settings.copy(splitIndicators=it))
        }
        PositionEditor(settings.offsetX,settings.enabled,
            label=if(settings.splitIndicators)"Posição do anel" else "Posição na barra",
            visual=if(settings.splitIndicators)DuoPart.RING.apply(demo()) else null) {
            onUpdate(settings.copy(offsetX=it))
        }
        if(settings.splitIndicators) PositionEditor(settings.indicatorsOffsetX,settings.enabled,
            label="Posição dos indicadores",visual=DuoPart.INDICATORS.apply(demo())) {
            onUpdate(settings.copy(indicatorsOffsetX=it))
        }
        LabelledSlider("Espaço nas bordas: ${settings.edgePadding}%",settings.edgePadding.toFloat(),
            DuoPrefs.MIN_EDGE_PADDING.toFloat()..DuoPrefs.MAX_EDGE_PADDING.toFloat(),enabled=settings.enabled) {
            onUpdate(settings.copy(edgePadding=it.toInt()))
        }
        SettingSwitch("Aplicar na hora","As mudanças são aplicadas ao terminar o ajuste.",settings.liveApply,settings.enabled) {
            onUpdate(settings.copy(liveApply=it))
        }
        OutlinedButton(onClick=onRestart,enabled=settings.enabled,modifier=Modifier.fillMaxWidth()) {Text("Reiniciar a barra")}
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                label = stringResource(R.string.settings_anim_speed),
                value = speedIndex.toFloat(),
                range = 0f..(DuoPrefs.REVEAL_CHOICES.size - 1).toFloat(),
                steps = DuoPrefs.REVEAL_CHOICES.size - 2,
                formatValue = { SPEED_LABELS[it.toInt().coerceIn(0,4)] },
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
                    DuoSettingPreview(off=demo(),on=demo())
                }
            ) { onUpdate(settings.copy(arrivalEnabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_anim_departure),
                detail = stringResource(R.string.settings_anim_departure_detail),
                checked = settings.departureEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoSettingPreview(off=demo(),on=demo())
                }
            ) { onUpdate(settings.copy(departureEnabled = it)) }
            SettingSwitch(
                label = stringResource(R.string.settings_anim_charging),
                detail = stringResource(R.string.settings_anim_charging_detail),
                checked = settings.chargingEnabled,
                enabled = settings.enabled && settings.animationsEnabled,
                preview = {
                    DuoSettingPreview(off=demo(),on=demo(charging=true))
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
    if (matches(stringResource(R.string.section_look), 
             stringResource(R.string.settings_clock_font),
            stringResource(R.string.settings_icon_color),
            stringResource(R.string.settings_middle_slot),
            stringResource(R.string.settings_show_airplane),
            stringResource(R.string.settings_show_dnd),
            stringResource(R.string.settings_dnd_detail),
            stringResource(R.string.settings_dnd_off),
            stringResource(R.string.settings_dnd_middle),
            stringResource(R.string.settings_dnd_dots),
            stringResource(R.string.settings_wifi_dots),
            stringResource(R.string.settings_wifi_dots_detail),
            stringResource(R.string.settings_sim))
    ) Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_look))
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
            Text(
                text = stringResource(R.string.settings_middle_slot),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_middle_slot_detail),
                style = MaterialTheme.typography.bodySmall
            )
            SettingSwitch(
                label = stringResource(R.string.settings_show_airplane),
                detail = null,
                checked = settings.showAirplane,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(airplane = true, showAirplane = false),
                        on = demo(airplane = true, showAirplane = true)
                    )
                }
            ) { onUpdate(settings.copy(showAirplane = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF101014)),
                    contentAlignment = Alignment.Center
                ) {
                    DuoCanvasStill(
                        visual = dndPreview(settings.dndMode),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OptionPicker(
                        label = stringResource(R.string.settings_show_dnd),
                        options = listOf(
                            DuoPrefs.DND_OFF to stringResource(R.string.settings_dnd_off),
                            DuoPrefs.DND_MIDDLE to stringResource(R.string.settings_dnd_middle),
                            DuoPrefs.DND_DOTS to stringResource(R.string.settings_dnd_dots)
                        ),
                        selectedKey = settings.dndMode,
                        enabled = settings.enabled
                    ) { onUpdate(settings.copy(dndMode = it)) }
                    Text(
                        text = stringResource(R.string.settings_dnd_detail),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            SettingSwitch(
                label = stringResource(R.string.settings_wifi_dots),
                detail = stringResource(R.string.settings_wifi_dots_detail),
                checked = settings.wifiDots,
                enabled = settings.enabled,
                preview = {
                    DuoSettingPreview(
                        off = demo(airplane = true, wifiLevel = 2, wifiOn = true),
                        on = demo(airplane = true, wifiLevel = 2, wifiOn = true, wifiDots = true)
                    )
                }
            ) { onUpdate(settings.copy(wifiDots = it)) }
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    updateInfo: UpdateInfo?,
    onDownload: () -> Unit
) {
    val context=LocalContext.current
    var details by remember { mutableStateOf(false) }
    if(matches("Ajuda","Diagnóstico","Relatório","Logs",stringResource(R.string.section_about),
            stringResource(R.string.settings_collect_log),stringResource(R.string.settings_status))) StudioCard {
        SectionTitle("Ajuda e diagnóstico")
        Text("Conte o que aconteceu. Salve ou compartilhe um relatório quando precisar.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value=problem,onValueChange=onProblemChange,
            placeholder={Text("O que você gostaria de corrigir?")},minLines=2,
            shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick=onExport,enabled=!exporting&&!collecting,modifier=Modifier.fillMaxWidth()) {
            Text(if(exporting)"Preparando relatório…" else "Salvar relatório")
        }
        Button(onClick=onCollect,enabled=!collecting&&!exporting,modifier=Modifier.fillMaxWidth()) {
            Text(if(collecting)"Preparando relatório…" else "Enviar relatório")
        }
        if(logSent)Text(stringResource(R.string.settings_log_sent),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
        TextButton(onClick={details=!details}) {Text(if(details)"Ocultar informações do módulo" else "Ver informações do módulo")}
        if(details) {
            Text(status.ifBlank{context.getString(R.string.settings_no_status)},style=MaterialTheme.typography.bodySmall)
            Text(if(moduleLoadAt>0L)context.getString(R.string.settings_module_loaded,formatTimestamp(moduleLoadAt)) else context.getString(R.string.settings_module_never),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            history.takeLast(3).forEach {Text(it.substringAfter(' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    if(matches("Atualizações",stringResource(R.string.settings_check_updates))) StudioCard {
        SectionTitle("Atualizações")
        SettingSwitch("Buscar automaticamente","Receba avisos quando uma atualização estiver disponível.",checkUpdates,onChange=onToggleUpdates)
        OutlinedButton(onClick=onCheckNow,enabled=!checkingUpdate,modifier=Modifier.fillMaxWidth()) {
            Text(if(checkingUpdate)"Verificando…" else "Verificar agora")
        }
        if(updateMessage.isNotEmpty())Text(updateMessage,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        updateInfo?.let { info ->
            if(info.apkUrl.isNotBlank())Button(onClick=onDownload,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.settings_update_download))}
            else Text(stringResource(R.string.settings_update_no_apk),style=MaterialTheme.typography.bodySmall)
        }
    }
    if(matches("Sobre","Contato","Apoiar",stringResource(R.string.section_about),stringResource(R.string.settings_contact_developer),stringResource(R.string.settings_donate))) StudioCard {
        SectionTitle("Duo Status Bar")
        Text("Uma assinatura discreta para a sua barra de status.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(TELEGRAM_CHAT_URL)))}}) {
            Text(stringResource(R.string.settings_contact_developer))
        }
        OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(DONATE_URL)))},modifier=Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_paypal),null,Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_donate))
        }
        Text(BuildConfig.VERSION_NAME,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    dnd: Boolean = false,
    percentHeight: Int = DuoPrefs.DEFAULT_PERCENT_HEIGHT,
    wifiLevel: Int = 3,
    wifiOn: Boolean = true,
    wifiDots: Boolean = false,
    showAirplane: Boolean = true,
    showDnd: Boolean = true,
    dndDots: Boolean = false,
    cellLevel: Int = 4
) = DuoMapping.visual(
    level = level,
    charging = charging,
    saver = false,
    showPercent = showPercent,
    wifiLevel = wifiLevel,
    cellLevel = cellLevel,
    airplane = airplane,
    dnd = dnd,
    wifiOn = wifiOn,
    percentHeight = percentHeight,
    wifiDots = wifiDots,
    showAirplane = showAirplane,
    showDnd = showDnd,
    dndDots = dndDots
)
/** The Do Not Disturb row, drawn as if that status is on, in the mode the user picked. */
private fun dndPreview(mode: String) = when (mode) {
    DuoPrefs.DND_DOTS -> demo(dnd = true, showDnd = false, dndDots = true, cellLevel = 2)
    DuoPrefs.DND_OFF -> demo(dnd = true, showDnd = false)
    else -> demo(dnd = true, showDnd = true)
}
/** FR-09: a level that shows a clear half-full ring rather than an empty or full one. */
private const val DEMO_LEVEL = 72
/** FR-25: the five arrival speeds, slowest first, matching [DuoPrefs.REVEAL_CHOICES] reversed. */
private val SPEED_LABELS = listOf("Lenta", "Suave", "Normal", "Rápida", "Muito rápida")
