package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
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
    if(!search(UiText.t("Prévia dos ícones e efeitos"),UiText.t("Demonstração"),UiText.t("Experimente o movimento"),UiText.t("Ritmo"),UiText.t("Simulador")))return
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
    val foreground=remember { ForegroundEvents() }
    val clock=remember { SimulationClock() }
    var islandVisible by remember { mutableStateOf(false) }
    val island=remember { IslandSummary(context,interactive=false) { islandVisible=it } }
    OverlayBackdrop(menu || islandVisible)
    DisposableEffect(island) { onDispose { island.dismiss() } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    Button(onClick={expanded=!expanded;if(!expanded)island.dismiss()}) { Text(if(expanded)UiText.t("Fechar prévia") else UiText.t("Abrir prévia interativa")) }
    if(!expanded)return
    Text(UiText.t("Estados simulados · os ajustes só chegam à barra ao tocar em Aplicar"),style=MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(210.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(StudioInk),contentAlignment=Alignment.Center) {
        AndroidView(modifier=Modifier.size(168.dp,190.dp),factory={ctx->DuoCanvasView(ctx).also { canvas->
            view=canvas;canvas.setOnLongClickListener { island.open(canvas,true) }
        }},onRelease={island.dismiss();it.teardown();view=null},update={it.thickPercent=settings.thickPercent;it.globalPercent=settings.globalPercent;it.animationsEnabled=settings.animationsEnabled})
    }
    LaunchedEffect(view,selected,paused,phone,headphones,progress,volume,charging,playing,recording,draft) {
        val canvas=view ?: return@LaunchedEffect
        cycle.configure(draft,clock.now())
        if(draft.networkOnly){temporary=null;cycle.transient(null,clock.now())}
        val icons=selected.toMutableList().apply {
            if(!playing)remove(SlotIcon.MEDIA)
            if(!recording){remove(SlotIcon.RECORD);remove(SlotIcon.RECORD_TIME)}
            if(charging){add(SlotIcon.BOLT);if(draft.chargeEstimate)add(SlotIcon.CHARGE_TIME)}
            if(playing)add(SlotIcon.MEDIA)
            if(recording){add(SlotIcon.RECORD);if(draft.recordingTime)add(SlotIcon.RECORD_TIME)}
        }.distinct()
        val visibleIcons=if(draft.networkOnly)listOfNotNull(icons.firstOrNull { it==SlotIcon.WIFI||it==SlotIcon.WIFI_OFFLINE } ?: icons.firstOrNull { it==SlotIcon.NETWORK }) else icons
        cycle.update(visibleIcons,clock.now())
        cycle.hold(foreground.update(if(draft.networkOnly)emptyList() else visibleIcons),clock.now(),draft.recordingTime)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                if(paused){delay(Long.MAX_VALUE);continue}
                withFrameNanos {
                    val time=clock.now()
                    val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,!draft.networkOnly)
                    if(check>=0)cycle.pause(time)
                    else cycle.resume(time)
                    if(temporary!=null&&check<0&&!draft.networkOnly) {
                        if(temporaryAt==Long.MIN_VALUE)temporaryAt=time
                        cycle.transient(temporary,time)
                        if(time-temporaryAt>2000){temporary=null;cycle.transient(null,time)}
                    }
                    val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,!draft.networkOnly)
                    val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,!draft.networkOnly)
                    canvas.render(DuoMapping.visual(if(pulse>=0)5 else phone.toInt(),charging,false,true,3,4,false,networkText="5G"))
                    val position=((progress+(if(playing)(time-musicAt)/1800f else 0f))/100f).coerceIn(0f,1f)
                    canvas.effects=EffectFrame(checkMs=check,chargeMs=charge,pulseMs=pulse,pulseRed=true,
                        slot=cycle.frame(time),managedSlots=true,headphoneBattery=headphones.toInt(),networkText="5G",motionMs=time,
                        glass=settings.featFlags and 1024!=0,spring=settings.featFlags and 2048!=0,charging=charging&&!draft.networkOnly,criticalDot=pulse>=0,
                        musicPlaying=playing&&draft.music&&!draft.networkOnly,musicProgress=if(draft.music)position else -1f,
                        albumColor=if(draft.albumColors)0xFF8BAAFF.toInt() else 0,chargeRemainingMs=36*60000L,
                        recordElapsedMs=(time-recordAt).coerceAtLeast(0),volumePercent=volume.toInt(),drawIcons=draft.drawIcons&&!draft.networkOnly,iconPercent=draft.iconPercent,
                        iconRadius=(55.5f-8f*settings.thickPercent/100f-2f).coerceAtLeast(12f),
                        compass=draft.compass,compassDegrees=(time/40f)%360)
                    island.update(IslandState(phone.toInt(),charging,icons.map { icon->IslandItem(icon,iconLabel(icon),when(icon){
                        SlotIcon.MEDIA->UiText.t("Faixa de demonstração");SlotIcon.CHARGE_TIME->"~36min"
                        SlotIcon.RECORD_TIME->durationLabel((time-recordAt).coerceAtLeast(0));else->UiText.t("Simulado")
                    }) },playback=PlaybackSnapshot(playing,(position*180000).toLong(),180000,android.os.SystemClock.elapsedRealtime(),color=0xFF526CBB.toInt(),title=UiText.t("Faixa de demonstração")),chargeRemainingMs=if(charging)36*60000L else -1,headphoneBattery=headphones.toInt()))
                }
                val time=clock.now()
                val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,!draft.networkOnly)
                val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,!draft.networkOnly)
                val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,!draft.networkOnly)
                val frame=cycle.frame(time)
                val continuous=!draft.networkOnly&&(charging||playing||recording||frame.icon==SlotIcon.MEDIA||frame.icon==SlotIcon.RECORD||(draft.compass&&frame.icon==SlotIcon.LOCATION))
                val transition=check>=0||charge>=0||pulse>=0||frame.opacity<1f
                val deadline=if(temporary!=null&&temporaryAt!=Long.MIN_VALUE)maxOf(1L,temporaryAt+2000-time) else Long.MAX_VALUE
                delay(EffectCadence.delay(cycle.nextDelay(time),transition,continuous,frame.icon==SlotIcon.RECORD_TIME,deadline))
            }
        }
    }
    Box {
        OutlinedButton(onClick={menu=true}) { Text(UiText.format("Escolher estados ativos ({0})", selected.size)) }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            DropdownMenuItem(text={Text(UiText.t("Ativar todos"))},onClick={selected=SlotIcon.entries.toSet();playing=true;recording=true;musicAt=clock.now();recordAt=clock.now()})
            DropdownMenuItem(text={Text(UiText.t("Limpar seleção"))},onClick={selected=emptySet();playing=false;recording=false})
            SlotIcon.entries.forEach { icon ->
                DropdownMenuItem(text={Text(iconLabel(icon))},leadingIcon={Checkbox(icon in selected,onCheckedChange=null)},
                    onClick={selected=if(icon in selected)selected-icon else selected+icon;if(icon==SlotIcon.MEDIA){playing=icon in selected;musicAt=clock.now()};if(icon==SlotIcon.RECORD||icon==SlotIcon.RECORD_TIME){recording=icon in selected;recordAt=clock.now()}})
            }
        }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Button(onClick={checkAt=clock.now()}) { Text(UiText.t("Desbloquear")) }
        OutlinedButton(onClick={temporary=SlotIcon.SCREENSHOT;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Captura")) }
        OutlinedButton(onClick={temporary=SlotIcon.NOTIFICATION;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Notificação")) }
        OutlinedButton(onClick={temporary=SlotIcon.VOLUME;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Volume")) }
        OutlinedButton(onClick={pulseAt=clock.now()}) { Text(UiText.t("Bateria fraca")) }
    }
    SettingSwitch(UiText.t("Simular carga"),UiText.t("Raio, brilho e estimativa."),charging,true){charging=it;chargeAt=clock.now()}
    SettingSwitch(UiText.t("Simular música"),UiText.t("Ondas e progresso de uma faixa."),playing,true){playing=it;musicAt=clock.now()}
    SettingSwitch(UiText.t("Simular gravação"),UiText.t("Indicador vermelho e tempo."),recording,true){recording=it;recordAt=clock.now()}
    LabelledSlider(UiText.format("Bateria do celular: {0}%", phone.toInt()),phone,0f..100f){phone=it}
    LabelledSlider(UiText.format("Bateria dos fones: {0}%", headphones.toInt()),headphones,0f..100f){headphones=it}
    LabelledSlider(UiText.format("Progresso da música: {0}%", progress.toInt()),progress,0f..100f){progress=it;musicAt=clock.now()}
    LabelledSlider(UiText.format("Volume: {0}%", volume.toInt()),volume,0f..100f){volume=it;temporary=SlotIcon.VOLUME;temporaryAt=Long.MIN_VALUE}
    LabelledSlider(UiText.format("Ícones internos: {0}%", draft.iconPercent),draft.iconPercent.toFloat(),60f..200f){draft=draft.copy(iconPercent=it.toInt())}
    SettingSwitch(UiText.t("Bússola simulada"),UiText.t("O GPS gira nesta demonstração."),draft.compass,true){draft=draft.copy(compass=it)}
    AnimationTimingEditor(draft,true) {draft=it}
    SectionTitle(UiText.t("Ritmo da prévia"))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={draft=draft.copy(dwellMs=4500,exitMs=280,entryMs=400)}) { Text(UiText.t("Discreto")) }
        OutlinedButton(onClick={draft=draft.copy(dwellMs=3000,exitMs=160,entryMs=240)}) { Text(UiText.t("Fluido")) }
        OutlinedButton(onClick={draft=draft.copy(dwellMs=1600,exitMs=80,entryMs=120)}) { Text(UiText.t("Rápido")) }
    }
    LabelledSlider(UiText.format("Saída do ícone: {0} ms", draft.exitMs),draft.exitMs.toFloat(),60f..600f){draft=draft.copy(exitMs=it.toInt())}
    LabelledSlider(UiText.format("Entrada do ícone: {0} ms", draft.entryMs),draft.entryMs.toFloat(),60f..800f){draft=draft.copy(entryMs=it.toInt())}
    SettingSwitch(UiText.t("Desenho dos traços"),UiText.t("Experimentar a entrada desenhada."),draft.drawIcons,true){draft=draft.copy(drawIcons=it)}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={draft=ExperienceOptions.ALL.copy(dwellMs=draft.dwellMs,exitMs=draft.exitMs,entryMs=draft.entryMs,iconPercent=draft.iconPercent,iconSeconds=draft.iconSeconds,networkOnly=draft.networkOnly,fadeEnabled=draft.fadeEnabled,language=draft.language,universalTiming=draft.universalTiming)}) { Text(UiText.t("Experimentar tudo")) }
        OutlinedButton(onClick={paused=!paused;if(paused){clock.pause();cycle.pause(clock.now())}else{clock.resume();cycle.resume(clock.now())}}) { Text(if(paused)UiText.t("Retomar") else UiText.t("Pausar")) }
        OutlinedButton(onClick={view?.let { island.open(it,settings.animationsEnabled) }}) { Text(UiText.t("Abrir resumo")) }
        Button(onClick={onApply(draft)},enabled=settings.enabled) { Text(UiText.t("Aplicar na barra")) }
    }
}

internal class SimulationClock(private val real:()->Long={SystemClock.uptimeMillis()}) {
    private var offset=0L
    private var stopped:Long?=null
    fun now()=(stopped ?: real())-offset
    fun pause(){if(stopped==null)stopped=real()}
    fun resume(){stopped?.let { offset+=real()-it };stopped=null}
}
