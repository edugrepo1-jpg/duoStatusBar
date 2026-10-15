package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.i18n.UiText

/** Parent content alone is blurred; the modal remains sharp and no bitmap is captured. */
@Stable internal class OverlayBackdropState {
    private val owners = mutableStateMapOf<Any, Unit>()
    val active: Boolean get() = owners.isNotEmpty()
    fun open(owner: Any) { owners[owner] = Unit }
    fun close(owner: Any) { owners.remove(owner) }
}
internal val LocalOverlayBackdrop = compositionLocalOf<OverlayBackdropState?> { null }
internal val BackdropBlurred = SemanticsPropertyKey<Boolean>("BackdropBlurred")
@Composable internal fun OverlayBackdrop(visible: Boolean = true) {
    val state = LocalOverlayBackdrop.current
    val owner = remember { Any() }
    DisposableEffect(state, visible) {
        if (visible) state?.open(owner)
        onDispose { state?.close(owner) }
    }
}

/** Reference-style control: blue/gray track, black thumb, real switch semantics and 48dp target. */
@Composable internal fun RecreateSwitch(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val position by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f,
        androidx.compose.animation.core.tween(150), label="switch-thumb")
    Box(Modifier.size(64.dp,48.dp).testTag("feature-toggle")
        .toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=onChange)
        .semantics { contentDescription=label;stateDescription=UiText.t(if(checked)"Ativo" else "Desativado") },contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(56.dp,32.dp)) {
            val color=if(checked)Color(0xFF168DE2) else Color(0xFF606064)
            drawRoundRect(color.copy(alpha=if(enabled)1f else .4f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))
            val radius=size.height*.36f
            drawCircle(Color(0xFF101012).copy(alpha=if(enabled)1f else .6f),radius,
                androidx.compose.ui.geometry.Offset(size.height/2+(size.width-size.height)*position,size.height/2))
        }
    }
}

internal fun featureIcon(label:String):SlotIcon {
    when(AuroraOptions.KEYS.firstOrNull {AuroraText.title(it)==label}) {
        "unlockGlow","unlockBuild"->return SlotIcon.NOTIFICATION
        "headphoneConfirm","batterySource"->return SlotIcon.AIRPODS
        "chargeStyles"->return SlotIcon.BOLT
        "shutter"->return SlotIcon.SCREENSHOT
        "privacyPair","privacyOrigins"->return SlotIcon.MICROPHONE
        "timer"->return SlotIcon.TIMER
        "voiceCall"->return SlotIcon.CALL
        "taskComplete"->return SlotIcon.TASK_DONE
    }
    return when(FeatureGuide.find(label)?.title ?: label) {
    "Modo Avião"->SlotIcon.AIRPLANE;"Cores por porcentagem"->SlotIcon.BOLT
    "NFC"->SlotIcon.NFC;"Aviso de fones"->SlotIcon.AIRPODS
    "GPS com bússola"->SlotIcon.LOCATION;"Anel musical","Cores da capa"->SlotIcon.MEDIA
    "Captura de tela"->SlotIcon.SCREENSHOT;"Volume no anel"->SlotIcon.VOLUME
    "Tempo de gravação"->SlotIcon.RECORD;"Roteador Wi-Fi"->SlotIcon.SHARE
    "Brilho durante a carga"->SlotIcon.BOLT;"Previsão de carga"->SlotIcon.CHARGE_TIME
    "Ocultar na câmera"->SlotIcon.CAMERA;"Check no desbloqueio"->SlotIcon.NOTIFICATION
    "Bateria fraca"->SlotIcon.BOLT; else->SlotIcon.WIFI
    }
}

/** Read the SystemUI report stored by its authenticated return channel, never the app's own ledger. */
internal data class IconReportEvidence(val detected:Boolean?,val readerOk:Boolean?,val executions:Long?)
internal fun iconReportEvidence(dump:String,icon:SlotIcon):IconReportEvidence? {
    val marker=io.github.kvmy666.duostatusbar.TelemetryLedger.MARKER
    val start=dump.indexOf(marker)
    val end=dump.indexOf(io.github.kvmy666.duostatusbar.TelemetryLedger.END_MARKER,start.coerceAtLeast(0))
    if(start<0||end<start)return null
    val line=dump.substring(start,end).lineSequence().firstOrNull {it.startsWith("feature.${icon.name.lowercase()} ")} ?: return null
    val values=line.split(' ').drop(1).mapNotNull {token->token.split('=',limit=2).takeIf {it.size==2}?.let {it[0] to it[1]}}.toMap()
    fun flag(key:String)=when(values[key]){"true"->true;"false"->false;else->null}
    return IconReportEvidence(flag("detected"),flag("reader_ok"),values["executions"]?.toLongOrNull()?.takeIf {it>=0})
}

internal fun iconPresentationReasons(settings:io.github.kvmy666.duostatusbar.settings.DuoSettings,
    state:io.github.kvmy666.duostatusbar.settings.ModuleState,icon:SlotIcon,dump:String,stageOverride:Int?=null):List<String> = buildList {
    if(!settings.enabled)add("A personalização está desligada nesta orientação.")
    if(stageOverride==0)add("O bloqueio de desenvolvimento está desligando o módulo. Confira o aviso em Ajustes.")
    if(state!=io.github.kvmy666.duostatusbar.settings.ModuleState.OK)add(when(state) {
        io.github.kvmy666.duostatusbar.settings.ModuleState.NEVER_LOADED->"O módulo ainda não informou carregamento. Confira o LSPosed e o escopo SystemUI."
        io.github.kvmy666.duostatusbar.settings.ModuleState.NEEDS_RESTART->"A barra ainda executa a versão anterior. Reinicie o SystemUI para aplicar a atualização."
        else->"O módulo carregou, mas não enviou um relatório. Não é possível confirmar a exibição."
    })
    val options=ExperienceOptions.decode(settings.experienceJson)
    if(options.networkOnly&&icon !in setOf(SlotIcon.WIFI,SlotIcon.WIFI_OFFLINE,SlotIcon.NETWORK))add("Somente Wi-Fi ou dados oculta os outros ícones. Seus ajustes continuam guardados.")
    if(icon==SlotIcon.AIRPLANE&&!settings.showAirplane)add("O indicador de modo avião está desativado em Visual.")
    fun flag(bit:Int)=settings.featFlags and io.github.kvmy666.duostatusbar.BuildConfig.FEATURE_MASK and bit != 0
    if(icon==SlotIcon.MEDIA&&!options.music&&!flag(8192))add("Ative o anel musical em Efeitos para mostrar a reprodução.")
    if(icon==SlotIcon.NFC&&!flag(4096))add("O indicador NFC está desativado em Efeitos.")
    if(icon==SlotIcon.SHARE&&!flag(8))add("O indicador de roteador Wi-Fi está desativado em Efeitos.")
    val evidence=iconReportEvidence(dump,icon)
    when {
        evidence==null->add("Não há observação deste ícone no último relatório. Gere um diagnóstico com o estado ativo.")
        evidence.readerOk==false->add("O último relatório registrou falha ao consultar este estado. Confira o acesso e gere um novo diagnóstico.")
        evidence.detected==false->add("O estado estava inativo no último relatório. Isso não significa uma falha.")
        evidence.executions!=null&&evidence.executions>0->add("O último relatório registrou um desenho deste ícone. Isso não confirma que ele esteja visível agora.")
        else->add("O relatório ainda não confirma um desenho deste ícone. Pode haver outro evento em primeiro plano.")
    }
    add("Música e gravações permanecem em primeiro plano até terminar. O evento mais recente tem prioridade; os demais aguardam.")
}

@Composable internal fun IconPresentationDiagnostic(settings:io.github.kvmy666.duostatusbar.settings.DuoSettings,
    state:io.github.kvmy666.duostatusbar.settings.ModuleState,dump:String,stageOverride:Int?=null,onRefresh:()->Unit) {
    var visible by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf(SlotIcon.WIFI)}
    OutlinedButton(onClick={visible=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("icon-diagnostic")) {
        Text(UiText.t("Por que este ícone não aparece?"))
    }
    if(visible) {
        OverlayBackdrop()
        Dialog(onDismissRequest={visible=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.padding(20.dp).widthIn(max=520.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp).heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(UiText.t("Por que este ícone não aparece?"),style=MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        OptionPicker(UiText.t("Escolha o ícone"),SlotIcon.entries.map {it.name to iconLabel(it)},selected.name,true) { selected=SlotIcon.valueOf(it) }
                        Text(UiText.t("Configuração atual e última observação da barra. Este painel não simula sucesso nem mede o estado ao vivo."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        iconPresentationReasons(settings,state,selected,dump,stageOverride).forEach {key->Text(UiText.t(key),style=MaterialTheme.typography.bodyMedium)}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick=onRefresh,modifier=Modifier.heightIn(min=48.dp)) {Text(UiText.t("Atualizar diagnóstico"))}
                        OutlinedButton(onClick={visible=false},modifier=Modifier.heightIn(min=48.dp)) {Text(UiText.t("Fechar"))}
                    }
                }
            }
        }
    }
}

@Composable internal fun FeaturePresentation(label:String,detail:String?,checked:Boolean,enabled:Boolean,
    onDismiss:()->Unit,onChange:(Boolean)->Unit) {
    OverlayBackdrop()
    val guide=FeatureGuide.find(label)
    val auroraKey=AuroraOptions.KEYS.firstOrNull {AuroraText.title(it)==label}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(30.dp),
            modifier=Modifier.padding(20.dp).widthIn(max=520.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp).heightIn(max=520.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(label,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    RecreateSwitch(checked,enabled,label,onChange)
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    AndroidView(factory={context -> DuoCanvasView(context).apply {
                        animationsEnabled=false;arrivalEnabled=false
                        effects=EffectFrame(checkMs=when {auroraKey=="unlockGlow"->200;auroraKey=="unlockBuild"||guide?.title=="Check no desbloqueio"->420;else->-1},
                            slot=SlotFrame(featureIcon(label)),managedSlots=true,headphoneBattery=65,networkText="5G",volumePercent=65,
                            aurora=AuroraOptions(enabled=setOfNotNull(auroraKey)),timerRemainingMs=300000,timerElapsedMs=0,callElapsedMs=18000,
                            headphoneConnectMs=if(auroraKey=="headphoneConfirm")420 else -1,
                            chargeMs=if(auroraKey=="chargeStyles")500 else -1,charging=auroraKey=="chargeStyles",motionMs=500,
                            taskCompleteMs=if(auroraKey=="taskComplete")500 else -1)
                        render(DuoMapping.visual(level=72,charging=false,saver=false,showPercent=true,wifiLevel=3,cellLevel=4,airplane=false).copy(tint=android.graphics.Color.WHITE))
                    }},onRelease={it.teardown()},modifier=Modifier.size(88.dp).background(StudioInk,RoundedCornerShape(50)))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(detail ?: UiText.t("Controla este ajuste da sua barra de status."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(guide!=null) {
                            Text(UiText.t("Como funciona"),style=MaterialTheme.typography.labelLarge)
                            Text(UiText.t(guide.explanation),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(UiText.t(if(checked)"Esta função está ativada." else "Esta função está desativada."),style=MaterialTheme.typography.bodySmall,color=if(checked)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if(guide!=null) {
                    Text(UiText.t(guide.use),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier=Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),RoundedCornerShape(14.dp)).padding(12.dp))
                }
                if(!enabled)Text(UiText.t("Este ajuste está indisponível agora. Ative a personalização ou a opção da qual ele depende."),color=MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End).heightIn(min=48.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.primary),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {Text(UiText.t("Fechar"))}
            }
        }
    }
}

internal enum class AppThemeMode { SYSTEM,LIGHT,DARK }
internal object ThemePreference {
    fun read(context:Context)=runCatching {AppThemeMode.valueOf(context.getSharedPreferences("duo_app_appearance",Context.MODE_PRIVATE).getString("mode","SYSTEM")!!)}.getOrDefault(AppThemeMode.SYSTEM)
    fun write(context:Context,mode:AppThemeMode){context.getSharedPreferences("duo_app_appearance",Context.MODE_PRIVATE).edit().putString("mode",mode.name).apply()}
}
@Composable internal fun ThemePicker() {
    val context=LocalContext.current
    var mode by remember {mutableStateOf(ThemePreference.read(context))}
    SectionTitle(UiText.t("Tema"))
    Text(UiText.t("Escolha a aparência do aplicativo. O anel continua seguindo a cor da barra de status."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        AppThemeMode.entries.forEach {choice ->
            val name=UiText.t(when(choice){AppThemeMode.SYSTEM->"Sistema";AppThemeMode.LIGHT->"Claro";AppThemeMode.DARK->"Escuro"})
            FilterChip(selected=choice==mode,onClick={mode=choice;ThemePreference.write(context,choice)},label={Text(name)},modifier=Modifier.weight(1f))
        }
    }
}
