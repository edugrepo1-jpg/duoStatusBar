package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import androidx.compose.foundation.Canvas
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
internal val LocalFeatureBackdrop = compositionLocalOf<(Boolean)->Unit> { {} }

internal fun featureIcon(label:String):SlotIcon = when(FeatureGuide.find(label)?.title ?: label) {
    "NFC"->SlotIcon.NFC;"Aviso de fones"->SlotIcon.AIRPODS
    "GPS com bússola"->SlotIcon.LOCATION;"Anel musical","Cores da capa"->SlotIcon.MEDIA
    "Captura de tela"->SlotIcon.SCREENSHOT;"Volume no anel"->SlotIcon.VOLUME
    "Tempo de gravação"->SlotIcon.RECORD;"Roteador Wi-Fi"->SlotIcon.SHARE
    "Brilho durante a carga"->SlotIcon.BOLT;"Previsão de carga"->SlotIcon.CHARGE_TIME
    "Ocultar na câmera"->SlotIcon.CAMERA;"Check no desbloqueio"->SlotIcon.NOTIFICATION
    "Bateria fraca"->SlotIcon.BOLT; else->SlotIcon.WIFI
}

@Composable internal fun FeaturePresentation(label:String,detail:String?,checked:Boolean,enabled:Boolean,
    onDismiss:()->Unit,onChange:(Boolean)->Unit) {
    val backdrop=LocalFeatureBackdrop.current
    DisposableEffect(Unit) {backdrop(true);onDispose {backdrop(false)}}
    val guide=FeatureGuide.find(label)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(color=StudioInk,contentColor=Color.White,shape=RoundedCornerShape(30.dp),
            modifier=Modifier.fillMaxWidth().padding(20.dp).widthIn(max=520.dp)) {
            Column(Modifier.padding(20.dp).heightIn(max=360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(label,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    Button(onClick={onChange(!checked);onDismiss()},enabled=enabled,
                        contentPadding=PaddingValues(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF29292D),contentColor=Color.White),
                        modifier=Modifier.heightIn(min=48.dp).semantics {role=Role.Switch;stateDescription=UiText.t(if(checked)"Ativo" else "Desativado")}) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Canvas(Modifier.size(42.dp,28.dp)) {
                                val stroke=Stroke(4.dp.toPx())
                                drawArc(if(checked)Color(0xFF79DFF1) else Color(0xFF77777C),95f,155f,false,style=stroke)
                                drawArc(Color.White,275f,155f,false,style=stroke)
                                drawLine(if(checked)Color(0xFF30D158) else Color(0xFFAAAAAE),
                                    androidx.compose.ui.geometry.Offset(size.width*.34f,size.height*.5f),
                                    androidx.compose.ui.geometry.Offset(size.width*.66f,size.height*.5f),4.dp.toPx())
                            }
                            Text(UiText.t(if(checked)"Desativar" else "Ativar"),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    AndroidView(factory={context -> DuoCanvasView(context).apply {
                        animationsEnabled=false;arrivalEnabled=false
                        effects=EffectFrame(checkMs=if(guide?.title=="Check no desbloqueio")420 else -1,slot=SlotFrame(featureIcon(label)),managedSlots=true,headphoneBattery=65,networkText="5G",volumePercent=65)
                        render(DuoMapping.visual(level=72,charging=false,saver=false,showPercent=true,wifiLevel=3,cellLevel=4,airplane=false).copy(tint=android.graphics.Color.WHITE))
                    }},modifier=Modifier.size(88.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(detail ?: UiText.t("Controla este ajuste da sua barra de status."),style=MaterialTheme.typography.bodySmall,color=Color(0xFFCDCDD3))
                        if(guide!=null) {
                            Text(UiText.t("Como funciona"),style=MaterialTheme.typography.labelLarge)
                            Text(UiText.t(guide.explanation),style=MaterialTheme.typography.bodySmall,color=Color(0xFFCDCDD3))
                        }
                        Text(UiText.t(if(checked)"Esta função está ativada." else "Esta função está desativada."),style=MaterialTheme.typography.bodySmall,color=if(checked)StudioMint else Color(0xFFAEAEB2))
                    }
                }
                if(guide!=null) {
                    Text(UiText.t(guide.use),style=MaterialTheme.typography.bodySmall,color=Color(0xFFAEAEB2),
                        modifier=Modifier.fillMaxWidth().background(Color(0xFF222225),RoundedCornerShape(14.dp)).padding(12.dp))
                }
                if(!enabled)Text(UiText.t("Este ajuste está indisponível agora. Ative a personalização ou a opção da qual ele depende."),color=Color(0xFFFF9CAA))
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)) {Text(UiText.t("Voltar"),color=Color(0xFF79DFF1))}
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
