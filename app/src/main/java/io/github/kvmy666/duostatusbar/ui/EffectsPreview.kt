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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var wireless by remember { mutableStateOf(false) }
    var headphonesAt by remember {mutableLongStateOf(-100000L)}
    var taskAt by remember {mutableLongStateOf(-100000L)}
    var activityStarts by remember {mutableStateOf(emptyMap<SlotIcon,Long>())}
    fun elapsed(icon:SlotIcon,time:Long)=(time-(activityStarts[icon] ?: time)).coerceAtLeast(0)
    var auroraMenu by remember {mutableStateOf(false)}
    var previewOnScreen by remember {mutableStateOf(false)}
    val rootView=LocalView.current
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
    OverlayBackdrop(menu || islandVisible || auroraMenu)
    DisposableEffect(island) { onDispose { island.dismiss() } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    Button(colors=StudioFilledButtonColors(),onClick={expanded=!expanded;if(!expanded)island.dismiss()}) { Text(if(expanded)UiText.t("Fechar prévia") else UiText.t("Abrir prévia interativa")) }
    if(!expanded)return
    Text(UiText.t("Estados simulados · os ajustes só chegam à barra ao tocar em Aplicar"),style=MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(210.dp).onGloballyPositioned {coordinates->
        val bounds=coordinates.boundsInWindow()
        val height=rootView.rootView.height.takeIf {it>0} ?: rootView.height
        val width=rootView.rootView.width.takeIf {it>0} ?: rootView.width
        previewOnScreen=bounds.bottom>0&&bounds.top<height&&bounds.right>0&&bounds.left<width
    }.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(StudioInk),contentAlignment=Alignment.Center) {
        AndroidView(modifier=Modifier.size(168.dp,190.dp),factory={ctx->DuoCanvasView(ctx).also { canvas->
            view=canvas;canvas.setOnLongClickListener { island.open(canvas,true) }
        }},onRelease={island.dismiss();it.teardown();view=null},update={it.thickPercent=settings.thickPercent;it.globalPercent=settings.globalPercent;it.animationsEnabled=settings.animationsEnabled})
    }
    // A one-shot button is a real input too: a settled single icon may sleep indefinitely.
    LaunchedEffect(view,selected,paused,phone,headphones,progress,volume,charging,wireless,playing,recording,draft,
        checkAt,chargeAt,pulseAt,temporary,temporaryAt,musicAt,recordAt,headphonesAt,taskAt,activityStarts,previewOnScreen,settings.animationsEnabled) {
        val canvas=view ?: return@LaunchedEffect
        if(!previewOnScreen)return@LaunchedEffect
        val motion=settings.animationsEnabled&&!draft.networkOnly
        cycle.configure(draft.copy(fadeEnabled=draft.fadeEnabled&&motion),clock.now())
        if(draft.networkOnly){temporary=null;cycle.transient(null,clock.now())}
        val icons=selected.toMutableList().apply {
            if(!playing)remove(SlotIcon.MEDIA)
            if(!recording){remove(SlotIcon.RECORD);remove(SlotIcon.RECORD_TIME)}
            if(charging){add(if(wireless)SlotIcon.WIRELESS else SlotIcon.BOLT);if(draft.chargeEstimate)add(SlotIcon.CHARGE_TIME)}
            if(playing)add(SlotIcon.MEDIA)
            if(recording){add(SlotIcon.RECORD);if(draft.recordingTime)add(SlotIcon.RECORD_TIME)}
        }.distinct()
        val visibleIcons=if(draft.networkOnly)listOfNotNull(icons.firstOrNull { it==SlotIcon.WIFI||it==SlotIcon.WIFI_OFFLINE } ?: icons.firstOrNull { it==SlotIcon.NETWORK }) else icons
        cycle.update(visibleIcons,clock.now())
        cycle.hold(foreground.update(if(draft.networkOnly)emptyList() else visibleIcons),clock.now(),draft.recordingTime)
        withContext(Dispatchers.Main.immediate) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                if(paused){delay(Long.MAX_VALUE);continue}
                withFrameNanos {
                    val time=clock.now()
                    if(SlotIcon.TIMER in selected&&elapsed(SlotIcon.TIMER,time)>=draft.aurora.timerMinutes*60000L) {
                        selected=selected-SlotIcon.TIMER
                        taskAt=time;temporary=SlotIcon.TASK_DONE;temporaryAt=Long.MIN_VALUE
                    }
                    val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,motion)
                    if(check>=0)cycle.pause(time)
                    else cycle.resume(time)
                    if(temporary!=null&&check<0&&!draft.networkOnly) {
                        if(temporaryAt==Long.MIN_VALUE)temporaryAt=time
                        cycle.transient(temporary,time)
                        if(time-temporaryAt>2000){temporary=null;cycle.transient(null,time)}
                    }
                    val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,motion)
                    val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,motion)
                    canvas.render(DuoMapping.visual(if(pulse>=0)5 else phone.toInt(),charging,false,true,3,4,false,networkText="5G"))
                    val position=((progress+(if(playing)(time-musicAt)/1800f else 0f))/100f).coerceIn(0f,1f)
                    canvas.effects=EffectFrame(checkMs=check,chargeMs=charge,pulseMs=pulse,pulseRed=true,
                        slot=cycle.frame(time),managedSlots=true,headphoneBattery=headphones.toInt(),networkText="5G",motionMs=time,
                        glass=settings.featFlags and 1024!=0,spring=motion&&settings.featFlags and 2048!=0,charging=charging&&motion,criticalDot=pulse>=0,
                        musicPlaying=playing&&draft.music&&!draft.networkOnly,musicProgress=if(draft.music)position else -1f,
                        albumColor=if(draft.albumColors)0xFF8BAAFF.toInt() else 0,chargeRemainingMs=36*60000L,
                        recordElapsedMs=(time-recordAt).coerceAtLeast(0),volumePercent=volume.toInt(),drawIcons=draft.drawIcons&&motion,iconPercent=draft.iconPercent,
                        iconRadius=(55.5f-8f*settings.thickPercent/100f-2f).coerceAtLeast(12f),
                        compass=draft.compass,compassDegrees=(time/40f)%360,motionEnabled=motion,
                        batteryColors=draft.takeIf {it.customBatteryColors}?.let {BatteryBandColors(it.batteryLow,it.batteryMid,it.batteryHigh)},
                        aurora=draft.aurora,wirelessCharging=charging&&wireless,
                        headphoneConnectMs=EffectTimeline.age(time,headphonesAt,1200,motion&&draft.aurora.headphoneConfirm),
                        callElapsedMs=elapsed(SlotIcon.CALL,time),timerElapsedMs=elapsed(SlotIcon.TIMER,time),
                        timerRemainingMs=(draft.aurora.timerMinutes*60000L-elapsed(SlotIcon.TIMER,time)).coerceAtLeast(0),
                        stopwatchElapsedMs=elapsed(SlotIcon.STOPWATCH,time),
                        taskCompleteMs=EffectTimeline.age(time,taskAt,1800,!draft.networkOnly&&draft.aurora.taskComplete))
                    island.update(IslandState(phone.toInt(),charging,icons.map { icon->IslandItem(icon,iconLabel(icon),when(icon){
                        SlotIcon.MEDIA->UiText.t("Faixa de demonstração");SlotIcon.CHARGE_TIME->"~36min"
                        SlotIcon.RECORD_TIME->durationLabel((time-recordAt).coerceAtLeast(0))
                        SlotIcon.CALL,SlotIcon.STOPWATCH->durationLabel(elapsed(icon,time))
                        SlotIcon.TIMER->durationLabel((draft.aurora.timerMinutes*60000L-elapsed(SlotIcon.TIMER,time)).coerceAtLeast(0));else->UiText.t("Simulado")
                    },elapsedMs=elapsed(icon,time),remainingMs=if(icon==SlotIcon.TIMER)(draft.aurora.timerMinutes*60000L-elapsed(SlotIcon.TIMER,time)).coerceAtLeast(0) else -1,
                        timerRunning=icon==SlotIcon.TIMER||icon==SlotIcon.STOPWATCH) },playback=PlaybackSnapshot(playing,(position*180000).toLong(),180000,android.os.SystemClock.elapsedRealtime(),color=0xFF526CBB.toInt(),title=UiText.t("Faixa de demonstração")),chargeRemainingMs=if(charging)36*60000L else -1,headphoneBattery=headphones.toInt(),aurora=draft.aurora,appearance=draft.islandAppearance,communication=SlotIcon.CALL in icons))
                }
                val time=clock.now()
                val check=EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,motion)
                val charge=EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,motion)
                val pulse=EffectTimeline.age(time,pulseAt,EffectTimeline.PULSE_MS,motion)
                val frame=cycle.frame(time)
                val continuous=motion&&(charging||frame.icon in setOf(SlotIcon.MEDIA,SlotIcon.RECORD,SlotIcon.SHARE)||
                    (draft.compass&&frame.icon==SlotIcon.LOCATION)||OfflineWifiPulse.active(frame.icon,check>=0,motion))
                val headphoneAge=EffectTimeline.age(time,headphonesAt,1200,motion&&draft.aurora.headphoneConfirm)
                val taskAge=EffectTimeline.age(time,taskAt,1800,!draft.networkOnly&&draft.aurora.taskComplete)
                val transition=check>=0||charge>=0||pulse>=0||frame.opacity<1f||headphoneAge>=0||(taskAge>=0&&motion)
                val deadline=if(temporary!=null&&temporaryAt!=Long.MIN_VALUE)maxOf(1L,temporaryAt+2000-time) else Long.MAX_VALUE
                delay(EffectCadence.delay(cycle.nextDelay(time),transition,continuous,
                    frame.icon in setOf(SlotIcon.RECORD_TIME,SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH)||(playing&&draft.music&&!draft.networkOnly),
                    minOf(deadline,if(taskAge>=0)1800-taskAge else Long.MAX_VALUE)))
            }
        }
        }
    }
    Box {
        OutlinedButton(onClick={menu=true}) { Text(UiText.format("Escolher estados ativos ({0})", selected.size)) }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            DropdownMenuItem(text={Text(UiText.t("Ativar todos"))},onClick={selected=SlotIcon.entries.toSet();playing=true;recording=true;musicAt=clock.now();recordAt=clock.now();activityStarts=setOf(SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH).associateWith {clock.now()}})
            DropdownMenuItem(text={Text(UiText.t("Limpar seleção"))},onClick={selected=emptySet();playing=false;recording=false})
            SlotIcon.entries.forEach { icon ->
                DropdownMenuItem(text={Text(iconLabel(icon))},leadingIcon={Checkbox(icon in selected,onCheckedChange=null)},
                    onClick={selected=if(icon in selected)selected-icon else selected+icon;if(icon==SlotIcon.MEDIA){playing=icon in selected;musicAt=clock.now()};if(icon==SlotIcon.RECORD||icon==SlotIcon.RECORD_TIME){recording=icon in selected;recordAt=clock.now()};if(icon in setOf(SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH))activityStarts=activityStarts+(icon to clock.now())})
            }
        }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Button(colors=StudioFilledButtonColors(),onClick={checkAt=clock.now()}) { Text(UiText.t("Desbloquear")) }
        OutlinedButton(onClick={temporary=SlotIcon.SCREENSHOT;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Captura")) }
        OutlinedButton(onClick={temporary=SlotIcon.NOTIFICATION;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Notificação")) }
        OutlinedButton(onClick={temporary=SlotIcon.VOLUME;temporaryAt=Long.MIN_VALUE}) { Text(UiText.t("Volume")) }
        OutlinedButton(onClick={pulseAt=clock.now()}) { Text(UiText.t("Bateria fraca")) }
        OutlinedButton(onClick={headphonesAt=clock.now();selected=selected+SlotIcon.AIRPODS;temporary=SlotIcon.AIRPODS;temporaryAt=Long.MIN_VALUE}) {Text(AuroraText.t("Conectar fones","Connect headphones","Conectar auriculares"))}
        OutlinedButton(onClick={taskAt=clock.now();temporary=SlotIcon.TASK_DONE;temporaryAt=Long.MIN_VALUE}) {Text(AuroraText.title("taskComplete"))}
    }
    SettingSwitch(UiText.t("Simular carga"),UiText.t("Raio, brilho e estimativa."),charging,true){charging=it;chargeAt=clock.now()}
    SettingSwitch(AuroraText.t("Simular indução","Simulate wireless charging","Simular inducción"),AuroraText.description("chargeStyles"),wireless,true){wireless=it;charging=true;chargeAt=clock.now()}
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
    SettingSwitch(UiText.t("Desenho dos traços"),UiText.t("Experimentar a entrada desenhada."),draft.drawIcons,true){draft=draft.copy(drawIcons=it)}
    Box {
        OutlinedButton(onClick={auroraMenu=true}) {Text(AuroraText.t("Recursos Aurora na prévia","Aurora preview features","Funciones Aurora en la vista previa"))}
        DropdownMenu(expanded=auroraMenu,onDismissRequest={auroraMenu=false}) {
            AuroraOptions.KEYS.forEach {key->
                DropdownMenuItem(text={Text(AuroraText.title(key))},leadingIcon={Checkbox(key in draft.aurora.enabled,onCheckedChange=null)},
                    onClick={draft=draft.copy(aurora=draft.aurora.toggle(key,key !in draft.aurora.enabled))})
            }
        }
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={draft=ExperienceOptions.ALL.copy(dwellMs=draft.dwellMs,exitMs=draft.exitMs,entryMs=draft.entryMs,iconPercent=draft.iconPercent,iconSeconds=draft.iconSeconds,networkOnly=draft.networkOnly,fadeEnabled=draft.fadeEnabled,language=draft.language,universalTiming=draft.universalTiming,customBatteryColors=draft.customBatteryColors,batteryLow=draft.batteryLow,batteryMid=draft.batteryMid,batteryHigh=draft.batteryHigh,aurora=ExperienceOptions.ALL.aurora.copy(timerMinutes=draft.aurora.timerMinutes))}) { Text(UiText.t("Experimentar tudo")) }
        OutlinedButton(onClick={paused=!paused;if(paused){clock.pause();cycle.pause(clock.now())}else{clock.resume();cycle.resume(clock.now())}}) { Text(if(paused)UiText.t("Retomar") else UiText.t("Pausar")) }
        OutlinedButton(onClick={view?.let { island.open(it,settings.animationsEnabled) }}) { Text(UiText.t("Abrir resumo")) }
        Button(colors=StudioFilledButtonColors(),onClick={onApply(draft)},enabled=settings.enabled) { Text(UiText.t("Aplicar na barra")) }
    }
}

internal class SimulationClock(private val real:()->Long={SystemClock.uptimeMillis()}) {
    private var offset=0L
    private var stopped:Long?=null
    fun now()=(stopped ?: real())-offset
    fun pause(){if(stopped==null)stopped=real()}
    fun resume(){stopped?.let { offset+=real()-it };stopped=null}
}
