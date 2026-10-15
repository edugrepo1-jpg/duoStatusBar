package io.github.kvmy666.duostatusbar.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.delay

internal fun effectTopics()=listOf(
    "unlock" to AuroraText.t("Desbloqueio","Unlock","Desbloqueo"),
    "energy" to AuroraText.t("Energia","Energy","Energía"),
    "media" to AuroraText.t("Mídia","Media","Multimedia"),
    "indicators" to AuroraText.t("Indicadores","Indicators","Indicadores"),
    "island" to AuroraText.t("Ilha dinâmica","Dynamic island","Isla dinámica"),
    "movement" to AuroraText.t("Movimento","Motion","Movimiento"))
private fun topicBits(topic:String)=listOf(1,4,8192,4096,8,16,256,512,2048,128,32,64).filter {effectInTopic(it,topic)}
private val effectAuroraKeys=AuroraOptions.KEYS-AuroraVisualKeys
internal fun topicSwitches(options:ExperienceOptions,topic:String)=when(topic) {
    "energy"->listOf(options.chargeEstimate)
    "media"->listOf(options.music,options.albumColors,options.recordingTime,options.screenshot,options.volume)
    "indicators"->listOf(options.compass,options.nfcParticles)
    "island"->listOf(options.island)
    "movement"->listOf(options.drawIcons,options.fadeEnabled)
    else->emptyList()
}
internal fun effectTopicSummary(settings:DuoSettings,topic:String):String {
    val options=ExperienceOptions.decode(settings.experienceJson)
    val values=topicBits(topic).map {settings.featFlags and it!=0}+topicSwitches(options,topic)+effectAuroraKeys.filter {auroraInTopic(it,topic)}.map {it in options.aurora.enabled}
    return AuroraText.t("${values.count {it}} de ${values.size} ligados","${values.count {it}} of ${values.size} enabled","${values.count {it}} de ${values.size} activos")
}
internal fun setEffectTopic(settings:DuoSettings,topic:String,on:Boolean):DuoSettings {
    var o=ExperienceOptions.decode(settings.experienceJson)
    o=when(topic) {
        "energy"->o.copy(chargeEstimate=on)
        "media"->o.copy(music=on,albumColors=on,recordingTime=on,screenshot=on,volume=on)
        "indicators"->o.copy(compass=on,nfcParticles=on)
        "island"->o.copy(island=on)
        "movement"->o.copy(drawIcons=on,fadeEnabled=on)
        else->o
    }
    val keys=effectAuroraKeys.filter {auroraInTopic(it,topic)}.toSet()
    o=o.copy(aurora=o.aurora.copy(enabled=if(on)o.aurora.enabled+keys else o.aurora.enabled-keys))
    val bits=topicBits(topic).fold(0){a,b->a or b}
    return settings.copy(featFlags=if(on)settings.featFlags or bits else settings.featFlags and bits.inv(),experienceJson=o.encode())
}
@Composable internal fun EffectGroupControl(settings:DuoSettings,topic:String,update:(DuoSettings)->Unit) {
    val options=ExperienceOptions.decode(settings.experienceJson)
    val on=topicBits(topic).any {settings.featFlags and it!=0}||topicSwitches(options,topic).any {it}||effectAuroraKeys.any {auroraInTopic(it,topic)&&it in options.aurora.enabled}
    SettingSwitch(AuroraText.t("Ativar grupo","Enable group","Activar grupo"),effectTopicSummary(settings,topic),on,settings.enabled){update(setEffectTopic(settings,topic,it))}
}
internal fun applyEffectProfile(settings:DuoSettings,profile:Int):DuoSettings {
    var result=settings
    effectTopics().forEach {result=setEffectTopic(result,it.first,profile==2)}
    val o=ExperienceOptions.decode(result.experienceJson)
    // Profiles modify only effect switches. All geometry, colors, gestures and timing are preserved.
    result=result.copy(animationsEnabled=profile!=0,experienceJson=o.copy(networkOnly=profile==0,
        chargeEstimate=profile>0,screenshot=profile>0,volume=profile>0,recordingTime=profile>0).encode())
    return result
}
internal fun currentEffectProfile(settings:DuoSettings):Int=(0..2).firstOrNull {applyEffectProfile(settings,it)==settings} ?: 3
@Composable internal fun EffectProfiles(settings:DuoSettings,update:(DuoSettings)->Unit) {
    var pending by remember {mutableStateOf<Int?>(null)}
    var previous by remember {mutableStateOf<DuoSettings?>(null)}
    LaunchedEffect(previous){if(previous!=null){delay(8000);previous=null}}
    val names=listOf(AuroraText.t("Minimalista","Minimal","Minimalista"),AuroraText.t("Padrão","Standard","Estándar"),AuroraText.t("Completo","Complete","Completo"),AuroraText.t("Personalizado","Custom","Personalizado"))
    StudioCard {
        SectionTitle(AuroraText.t("Perfil · ${names[currentEffectProfile(settings)]}","Profile · ${names[currentEffectProfile(settings)]}","Perfil · ${names[currentEffectProfile(settings)]}"))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            (0..2).forEach {i->OutlinedButton(onClick={pending=i},modifier=Modifier.weight(1f),contentPadding=PaddingValues(5.dp)){Text(names[i],style=MaterialTheme.typography.labelSmall)}}
        }
        if(previous!=null)TextButton(onClick={previous?.let {old->
            // Undo restores effect switches without undoing any later visual adjustment.
            val now=ExperienceOptions.decode(settings.experienceJson);val before=ExperienceOptions.decode(old.experienceJson)
            update(settings.copy(featFlags=old.featFlags,animationsEnabled=old.animationsEnabled,experienceJson=before.copy(iconPercent=now.iconPercent,customBatteryColors=now.customBatteryColors,batteryLow=now.batteryLow,batteryMid=now.batteryMid,batteryHigh=now.batteryHigh,islandAppearance=now.islandAppearance).encode()))
        };previous=null}){Text(AuroraText.t("Desfazer perfil","Undo profile","Deshacer perfil"))}
    }
    pending?.let {choice->
        OverlayBackdrop()
        AlertDialog(onDismissRequest={pending=null},title={Text(names[choice])},text={Text(AuroraText.t("Muda apenas os efeitos. Tamanho, cores e posição permanecem. Você poderá desfazer por 8 segundos.","Changes effects only. Size, colors and position stay unchanged. Undo is available for 8 seconds.","Solo cambia efectos. Tamaño, colores y posición se conservan. Puedes deshacer durante 8 segundos."))},confirmButton={TextButton(onClick={previous=settings;update(applyEffectProfile(settings,choice));pending=null}){Text(AuroraText.t("Aplicar","Apply","Aplicar"))}},dismissButton={TextButton(onClick={pending=null}){Text(AuroraText.t("Cancelar","Cancel","Cancelar"))}})
    }
}
@Composable internal fun PinnedBarPreview(settings:DuoSettings,test:Boolean,onTest:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp,modifier=Modifier.fillMaxWidth().testTag("pinned-preview")) {
        Row(Modifier.padding(horizontal=20.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AndroidView(modifier=Modifier.size(52.dp,58.dp).background(Color(0xFF101114),androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),factory={DuoCanvasView(it).apply{animationsEnabled=false}},onRelease={it.teardown()},update={view->
                val o=ExperienceOptions.decode(settings.experienceJson)
                view.thickPercent=settings.thickPercent;view.globalPercent=settings.globalPercent
                view.effects=EffectFrame(iconPercent=o.iconPercent,motionEnabled=false,batteryColors=o.takeIf{it.customBatteryColors}?.let{BatteryBandColors(it.batteryLow,it.batteryMid,it.batteryHigh)})
                view.render(DuoMapping.visual(72,false,false,settings.showPercent,3,4,false,percentHeight=settings.percentHeight,wifiDots=settings.wifiDots))
            })
            Column(Modifier.weight(1f)) {Text(AuroraText.t("Prévia","Preview","Vista previa"),style=MaterialTheme.typography.titleSmall);Text(AuroraText.t("Simulação · Wi-Fi · 72%","Simulation · Wi-Fi · 72%","Simulación · Wi-Fi · 72%"),style=MaterialTheme.typography.labelSmall)}
            if(test)TextButton(onClick=onTest){Text(AuroraText.t("Testar","Test","Probar"))}
        }
    }
}
@Composable internal fun FullPreviewDialog(settings:DuoSettings,close:()->Unit,update:(ExperienceOptions)->Unit) {
    OverlayBackdrop()
    Dialog(onDismissRequest=close,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TextButton(onClick=close){Text(AuroraText.t("Fechar prévia","Close preview","Cerrar vista previa"))}
                EffectsPreview(settings,SearchGate {true},update)
            }
        }
    }
}
@Composable internal fun FirstRunTour(settings:DuoSettings,update:(DuoSettings)->Unit,prefs:SharedPreferences) {
    var visible by remember {mutableStateOf(!prefs.getBoolean("tour_v34",false))}
    var step by rememberSaveable {mutableStateOf(0)}
    if(!visible)return
    fun done(){visible=false;prefs.edit().putBoolean("tour_v34",true).apply()}
    StudioCard {
        SectionTitle(AuroraText.t("Comece aqui · ${step+1}/3","Start here · ${step+1}/3","Empieza aquí · ${step+1}/3"))
        Text(when(step){0->AuroraText.t("Ative a barra no botão do topo. Em Ajustes › Avançado, conecte o acesso necessário.","Enable the bar at the top. Connect required access in Settings › Advanced.","Activa la barra arriba. Conecta el acceso necesario en Ajustes › Avanzado.");1->AuroraText.t("Escolha um perfil acima. Ele muda somente os efeitos.","Choose a profile above. It changes effects only.","Elige un perfil arriba. Solo cambia efectos.");else->AuroraText.t("A prévia acompanha seus ajustes em Visual e Efeitos. Você pode mudar tudo depois.","The preview follows your settings in Visual and Effects. Everything can be changed later.","La vista previa sigue tus ajustes en Visual y Efectos. Puedes cambiar todo después.")})
        Row {TextButton(onClick={if(step<2)step++ else done()}){Text(AuroraText.t(if(step<2)"Próximo" else "Concluir",if(step<2)"Next" else "Done",if(step<2)"Siguiente" else "Terminar"))};TextButton(onClick={done()}){Text(AuroraText.t("Pular","Skip","Omitir"))}}
    }
}
