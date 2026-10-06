package io.github.kvmy666.duostatusbar.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** Demonstrates the APK's actual Canvas painter; simulated inputs are labelled explicitly. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EffectsPreview(settings:DuoSettings,search:SearchGate) {
    if(!search("Prévia dos ícones e efeitos","Demonstração","Experimente o movimento"))return
    var expanded by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<DuoCanvasView?>(null) }
    var selected by remember { mutableStateOf<SlotIcon?>(null) }
    var menu by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var ghost by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(65f) }
    var checkAt by remember { mutableLongStateOf(-100000L) }
    var chargeAt by remember { mutableLongStateOf(-100000L) }
    var pulseAt by remember { mutableLongStateOf(-100000L) }
    val cycle=remember { SlotCycle() }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    Button(onClick={expanded=!expanded}) { Text(if(expanded)"Fechar demonstração" else "Ver ícones e efeitos") }
    if(!expanded)return
    Text("Demonstração com estados simulados",style=MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(StudioInk),contentAlignment=Alignment.Center) {
        AndroidView(modifier=Modifier.size(168.dp,190.dp),factory={ctx->DuoCanvasView(ctx).also { view=it }},onRelease={it.teardown();view=null},update={it.thickPercent=settings.thickPercent;it.globalPercent=settings.globalPercent;it.alpha=if(ghost)0f else 1f})
    }
    LaunchedEffect(view,selected,paused,level,settings) {
        val canvas=view ?: return@LaunchedEffect
        cycle.update(selected?.let { listOf(it) } ?: SlotIcon.entries,SystemClock.uptimeMillis())
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                if(paused){delay(500);continue}
                withFrameNanos {
                    if(!paused) {
                        val time=SystemClock.uptimeMillis()
                        val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,true)
                        if(check in 120..135)cycle.preferWifi(time)
                        val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,true)
                        val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,true)
                        canvas.render(DuoMapping.visual(if(pulse>=0)5 else 72,false,false,true,3,4,false,networkText="5G"))
                        canvas.effects=EffectFrame(checkMs=check,chargeMs=charge,pulseMs=pulse,pulseRed=true,slot=cycle.frame(time),managedSlots=true,headphoneBattery=level.toInt(),networkText="5G",motionMs=time,glass=settings.featFlags and 1024!=0,spring=settings.featFlags and 2048!=0,charging=charge>=0,criticalDot=pulse>=0)
                    }
                }
            }
        }
    }
    Box {
        OutlinedButton(onClick={menu=true}) { Text(selected?.let(::iconName) ?: "Alternar todos os ícones") }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            DropdownMenuItem(text={Text("Alternar todos")},onClick={selected=null;menu=false})
            SlotIcon.entries.forEach { icon->DropdownMenuItem(text={Text(iconName(icon))},onClick={selected=icon;menu=false}) }
        }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Button(onClick={checkAt=SystemClock.uptimeMillis()}) { Text("Check") }
        Button(onClick={chargeAt=SystemClock.uptimeMillis()}) { Text("Carga") }
        Button(onClick={pulseAt=SystemClock.uptimeMillis()}) { Text("Bateria fraca") }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={paused=!paused;if(paused)cycle.pause(SystemClock.uptimeMillis()) else cycle.resume(SystemClock.uptimeMillis())}) { Text(if(paused)"Retomar" else "Pausar") }
        OutlinedButton(onClick={view?.reveal(900)}) { Text("Entrada") }
        OutlinedButton(onClick={ghost=!ghost}) { Text(if(ghost)"Mostrar" else "Ocultar") }
    }
    LabelledSlider("Bateria dos fones: ${level.toInt()}%",level,0f..100f) { level=it }
}
private fun iconName(icon:SlotIcon)=when(icon) {
    SlotIcon.WIFI->"Wi-Fi";SlotIcon.NETWORK->"Rede móvel";SlotIcon.AIRPLANE->"Modo Avião";SlotIcon.DND->"Não Perturbe"
    SlotIcon.BLUETOOTH->"Bluetooth";SlotIcon.NFC->"NFC";SlotIcon.SHARE->"Hotspot";SlotIcon.AIRPODS->"Fones"
    SlotIcon.BOLT->"Raio de carga";SlotIcon.CAMERA->"Câmera";SlotIcon.MICROPHONE->"Microfone";SlotIcon.ALARM->"Alarme"
    SlotIcon.VPN->"VPN";SlotIcon.LOCATION->"GPS ativo";SlotIcon.SILENT->"Silencioso";SlotIcon.VIBRATE->"Vibração"
    SlotIcon.MEDIA->"Mídia";SlotIcon.WIRELESS->"Carga sem fio";SlotIcon.TORCH->"Lanterna";SlotIcon.RECORD->"Gravação da tela";SlotIcon.WIFI_OFFLINE->"Wi-Fi sem internet"
}
