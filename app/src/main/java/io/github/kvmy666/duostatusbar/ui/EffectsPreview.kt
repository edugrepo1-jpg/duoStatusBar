package io.github.kvmy666.duostatusbar.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EffectsPreview(settings:DuoSettings,search:SearchGate,onApply:(ExperienceOptions)->Unit={}) {
    if(!search("Prévia dos ícones e efeitos","Demonstração","Experimente o movimento","Ritmo","Simulador"))return
    val context=LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<DuoCanvasView?>(null) }
    var selected by remember { mutableStateOf(setOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.NFC)) }
    var menu by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var draft by remember(settings.experienceJson) { mutableStateOf(ExperienceOptions.decode(settings.experienceJson)) }
    var phone by remember { mutableFloatStateOf(72f) }
    var headphones by remember { mutableFloatStateOf(65f) }
    var progress by remember { mutableFloatStateOf(35f) }
    var volume by remember { mutableFloatStateOf(60f) }
    var charging by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recordAt by remember { mutableLongStateOf(0L) }
    var musicAt by remember { mutableLongStateOf(0L) }
    var checkAt by remember { mutableLongStateOf(-100000L) }
    var chargeAt by remember { mutableLongStateOf(-100000L) }
    var pulseAt by remember { mutableLongStateOf(-100000L) }
    var temporary by remember { mutableStateOf<SlotIcon?>(null) }
    var temporaryAt by remember { mutableLongStateOf(-100000L) }
    val cycle=remember { SlotCycle() }
    val clock=remember { SimulationClock() }
    val island=remember { IslandSummary(context) {} }
    DisposableEffect(island) { onDispose { island.dismiss() } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    Button(onClick={expanded=!expanded;if(!expanded)island.dismiss()}) { Text(if(expanded)"Fechar prévia" else "Abrir prévia interativa") }
    if(!expanded)return
    Text("Estados simulados · os ajustes só chegam à barra ao tocar em Aplicar",style=MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(210.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(StudioInk),contentAlignment=Alignment.Center) {
        AndroidView(modifier=Modifier.size(168.dp,190.dp),factory={ctx->DuoCanvasView(ctx).also { canvas->
            view=canvas;canvas.setOnLongClickListener { island.open(canvas,true) }
        }},onRelease={island.dismiss();it.teardown();view=null},update={it.thickPercent=settings.thickPercent;it.globalPercent=settings.globalPercent;it.animationsEnabled=settings.animationsEnabled})
    }
    LaunchedEffect(view,selected,paused,phone,headphones,progress,volume,charging,playing,recording,draft) {
        val canvas=view ?: return@LaunchedEffect
        cycle.configure(draft,clock.now())
        val icons=selected.toMutableList().apply {
            if(charging){add(SlotIcon.BOLT);if(draft.chargeEstimate)add(SlotIcon.CHARGE_TIME)}
            if(playing)add(SlotIcon.MEDIA)
            if(recording){add(SlotIcon.RECORD);if(draft.recordingTime)add(SlotIcon.RECORD_TIME)}
        }.distinct()
        cycle.update(icons,clock.now())
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                if(paused){delay(250);continue}
                withFrameNanos {
                    val time=clock.now()
                    val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,true)
                    if(check>=0)cycle.pause(time)
                    else cycle.resume(time)
                    if(temporary!=null&&check<0) {
                        if(temporaryAt==Long.MIN_VALUE)temporaryAt=time
                        cycle.transient(temporary,time)
                        if(time-temporaryAt>2000){temporary=null;cycle.transient(null,time)}
                    }
                    val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,true)
                    val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,true)
                    canvas.render(DuoMapping.visual(if(pulse>=0)5 else phone.toInt(),charging,false,true,3,4,false,networkText="5G"))
                    val position=((progress+(if(playing)(time-musicAt)/1800f else 0f))/100f).coerceIn(0f,1f)
                    canvas.effects=EffectFrame(checkMs=check,chargeMs=charge,pulseMs=pulse,pulseRed=true,
                        slot=cycle.frame(time),managedSlots=true,headphoneBattery=headphones.toInt(),networkText="5G",motionMs=time,
                        glass=settings.featFlags and 1024!=0,spring=settings.featFlags and 2048!=0,charging=charging,criticalDot=pulse>=0,
                        musicPlaying=playing&&draft.music,musicProgress=if(draft.music)position else -1f,
                        albumColor=if(draft.albumColors)0xFF8BAAFF.toInt() else 0,chargeRemainingMs=36*60000L,
                        recordElapsedMs=(time-recordAt).coerceAtLeast(0),volumePercent=volume.toInt(),drawIcons=draft.drawIcons,iconPercent=draft.iconPercent,
                        iconRadius=(55.5f-8f*settings.thickPercent/100f-2f).coerceAtLeast(12f),
                        compass=draft.compass,compassDegrees=(time/40f)%360)
                    island.update(IslandState(phone.toInt(),charging,icons.map { icon->IslandItem(icon,iconLabel(icon),when(icon){
                        SlotIcon.MEDIA->"Faixa de demonstração";SlotIcon.CHARGE_TIME->"~36min"
                        SlotIcon.RECORD_TIME->durationLabel((time-recordAt).coerceAtLeast(0));else->"Simulado"
                    }) }))
                }
                // Yield between frames even if a preview/test frame clock dispatches immediately.
                delay(1)
            }
        }
    }
    Box {
        OutlinedButton(onClick={menu=true}) { Text("Escolher estados ativos (${selected.size})") }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            DropdownMenuItem(text={Text("Ativar todos")},onClick={selected=SlotIcon.entries.toSet()})
            DropdownMenuItem(text={Text("Limpar seleção")},onClick={selected=emptySet()})
            SlotIcon.entries.forEach { icon ->
                DropdownMenuItem(text={Text(iconLabel(icon))},leadingIcon={Checkbox(icon in selected,onCheckedChange=null)},
                    onClick={selected=if(icon in selected)selected-icon else selected+icon})
            }
        }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Button(onClick={checkAt=clock.now()}) { Text("Desbloquear") }
        OutlinedButton(onClick={temporary=SlotIcon.SCREENSHOT;temporaryAt=Long.MIN_VALUE}) { Text("Captura") }
        OutlinedButton(onClick={temporary=SlotIcon.NOTIFICATION;temporaryAt=Long.MIN_VALUE}) { Text("Notificação") }
        OutlinedButton(onClick={temporary=SlotIcon.VOLUME;temporaryAt=Long.MIN_VALUE}) { Text("Volume") }
        OutlinedButton(onClick={pulseAt=clock.now()}) { Text("Bateria fraca") }
    }
    SettingSwitch("Simular carga","Raio, brilho e estimativa.",charging,true){charging=it;chargeAt=clock.now()}
    SettingSwitch("Simular música","Ondas e progresso de uma faixa.",playing,true){playing=it;musicAt=clock.now()}
    SettingSwitch("Simular gravação","Indicador vermelho e tempo.",recording,true){recording=it;recordAt=clock.now()}
    LabelledSlider("Bateria do celular: ${phone.toInt()}%",phone,0f..100f){phone=it}
    LabelledSlider("Bateria dos fones: ${headphones.toInt()}%",headphones,0f..100f){headphones=it}
    LabelledSlider("Progresso da música: ${progress.toInt()}%",progress,0f..100f){progress=it;musicAt=clock.now()}
    LabelledSlider("Volume: ${volume.toInt()}%",volume,0f..100f){volume=it;temporary=SlotIcon.VOLUME;temporaryAt=Long.MIN_VALUE}
    LabelledSlider("Ícones internos: ${draft.iconPercent}%",draft.iconPercent.toFloat(),60f..200f){draft=draft.copy(iconPercent=it.toInt())}
    SettingSwitch("Bússola simulada","O GPS gira nesta demonstração.",draft.compass,true){draft=draft.copy(compass=it)}
    SectionTitle("Ritmo da prévia")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={draft=draft.copy(dwellMs=4500,exitMs=280,entryMs=400)}) { Text("Discreto") }
        OutlinedButton(onClick={draft=draft.copy(dwellMs=3000,exitMs=160,entryMs=240)}) { Text("Fluido") }
        OutlinedButton(onClick={draft=draft.copy(dwellMs=1600,exitMs=80,entryMs=120)}) { Text("Rápido") }
    }
    LabelledSlider("Tempo por ícone: ${draft.dwellMs} ms",draft.dwellMs.toFloat(),1200f..10000f){draft=draft.copy(dwellMs=it.toInt())}
    LabelledSlider("Fade out: ${draft.exitMs} ms",draft.exitMs.toFloat(),60f..600f){draft=draft.copy(exitMs=it.toInt())}
    LabelledSlider("Fade in: ${draft.entryMs} ms",draft.entryMs.toFloat(),60f..800f){draft=draft.copy(entryMs=it.toInt())}
    SettingSwitch("Desenho dos traços","Experimentar a entrada desenhada.",draft.drawIcons,true){draft=draft.copy(drawIcons=it)}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={draft=ExperienceOptions.ALL.copy(dwellMs=draft.dwellMs,exitMs=draft.exitMs,entryMs=draft.entryMs,iconPercent=draft.iconPercent)}) { Text("Experimentar tudo") }
        OutlinedButton(onClick={paused=!paused;if(paused){clock.pause();cycle.pause(clock.now())}else{clock.resume();cycle.resume(clock.now())}}) { Text(if(paused)"Retomar" else "Pausar") }
        OutlinedButton(onClick={view?.let { island.open(it,settings.animationsEnabled) }}) { Text("Abrir resumo") }
        Button(onClick={onApply(draft)},enabled=settings.enabled) { Text("Aplicar na barra") }
    }
}

internal class SimulationClock(private val real:()->Long={SystemClock.uptimeMillis()}) {
    private var offset=0L
    private var stopped:Long?=null
    fun now()=(stopped ?: real())-offset
    fun pause(){if(stopped==null)stopped=real()}
    fun resume(){stopped?.let { offset+=real()-it };stopped=null}
}
