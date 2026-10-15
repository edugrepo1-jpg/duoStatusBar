package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.settings.DuoSettings

internal enum class AuroraTimelineScene { UNLOCK,SWAP }
internal data class AuroraTimelinePose(val frame:EffectFrame,val durationMs:Long,val phase:String)

/** Rebuild from zero on every scrub. Rewinding has no residual state and never runs a frame timer. */
internal fun auroraTimelinePose(scene:AuroraTimelineScene,time:Long,settings:DuoSettings,options:ExperienceOptions):AuroraTimelinePose {
    val motion=settings.animationsEnabled&&!options.networkOnly
    val base=EffectFrame(managedSlots=true,slot=SlotFrame(SlotIcon.WIFI),motionMs=time,aurora=options.aurora,
        iconPercent=options.iconPercent,motionEnabled=motion,drawIcons=options.drawIcons&&motion,
        iconRadius=(55.5f-8f*settings.thickPercent/100f-2f).coerceAtLeast(12f),
        batteryColors=options.takeIf {it.customBatteryColors}?.let {BatteryBandColors(it.batteryLow,it.batteryMid,it.batteryHigh)})
    if(scene==AuroraTimelineScene.UNLOCK) {
        val age=time.coerceIn(0,EffectTimeline.UNLOCK_MS)
        val unlock=motion&&(settings.featFlags and io.github.kvmy666.duostatusbar.BuildConfig.FEATURE_MASK and 1 != 0 || options.aurora.unlockGlow || options.aurora.unlockBuild)
        val phase=when {
            !motion->AuroraText.t("Movimento desativado","Motion disabled","Movimiento desactivado")
            !unlock->AuroraText.t("Desbloqueio desativado","Unlock effects disabled","Efectos de desbloqueo desactivados")
            age<120->AuroraText.t("Saída do ícone anterior","Previous icon exits","Salida del icono anterior")
            age<280->AuroraText.t("Entrada do check","Check enters","Entrada del check")
            age<EffectTimeline.CHECK_FADE_OUT_AT->AuroraText.t("Check visível","Check visible","Check visible")
            age<EffectTimeline.CHECK_MS->AuroraText.t("Saída do check","Check exits","Salida del check")
            age<EffectTimeline.UNLOCK_MS->AuroraText.t("Entrada do próximo ícone","Next icon enters","Entrada del próximo icono")
            else->AuroraText.t("Transição concluída","Transition complete","Transición completada")
        }
        return AuroraTimelinePose(base.copy(checkMs=EffectTimeline.age(age,0,EffectTimeline.UNLOCK_MS,unlock)),EffectTimeline.UNLOCK_MS,phase)
    }
    val cycle=SlotCycle().apply {
        configure(options.copy(fadeEnabled=motion&&options.fadeEnabled),0)
        update(listOf(SlotIcon.WIFI),0)
        hold(SlotIcon.BLUETOOTH,0)
    }
    val (exit,entry)=cycle.transitionDurations()
    val total=exit+entry
    val age=time.coerceIn(0,total)
    val slot=cycle.frame(age)
    val phase=when {
        !motion||!options.fadeEnabled->AuroraText.t("Troca direta","Instant switch","Cambio directo")
        age<exit->AuroraText.t("Saída do ícone anterior","Previous icon exits","Salida del icono anterior")
        age<total->AuroraText.t("Entrada do próximo ícone","Next icon enters","Entrada del próximo icono")
        else->AuroraText.t("Transição concluída","Transition complete","Transición completada")
    }
    return AuroraTimelinePose(base.copy(slot=slot),total,phase)
}

@Composable internal fun AuroraTimelinePreview(settings:DuoSettings,options:ExperienceOptions) {
    var scene by remember {mutableStateOf(AuroraTimelineScene.UNLOCK)}
    var position by remember(scene) {mutableFloatStateOf(0f)}
    val pose=auroraTimelinePose(scene,position.toLong(),settings,options)
    SectionTitle(AuroraText.title("timeline"))
    Text(AuroraText.description("timeline"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=scene==AuroraTimelineScene.UNLOCK,onClick={scene=AuroraTimelineScene.UNLOCK},label={Text(AuroraText.t("Desbloqueio","Unlock","Desbloqueo"))})
        FilterChip(selected=scene==AuroraTimelineScene.SWAP,onClick={scene=AuroraTimelineScene.SWAP},label={Text(AuroraText.t("Troca de ícones","Icon switch","Cambio de iconos"))})
    }
    Box(Modifier.fillMaxWidth().background(StudioInk,MaterialTheme.shapes.medium).height(200.dp),contentAlignment=Alignment.Center) {
        AndroidView(factory={DuoCanvasView(it).apply {animationsEnabled=false;arrivalEnabled=false}},onRelease={it.teardown()},modifier=Modifier.size(148.dp,168.dp).testTag("aurora-timeline-canvas"),update={view->
            view.thickPercent=settings.thickPercent;view.globalPercent=settings.globalPercent;view.effects=pose.frame
            view.render(DuoMapping.visual(72,false,false,settings.showPercent,3,4,false,percentHeight=settings.percentHeight,wifiDots=settings.wifiDots).copy(tint=android.graphics.Color.WHITE))
        })
    }
    Text(pose.phase,style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("aurora-timeline-phase"))
    Text(AuroraText.t("Simulação pausada · ${position.toInt()} / ${pose.durationMs} ms","Paused simulation · ${position.toInt()} / ${pose.durationMs} ms","Simulación pausada · ${position.toInt()} / ${pose.durationMs} ms"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(value=position.coerceIn(0f,pose.durationMs.toFloat()),onValueChange={position=it},valueRange=0f..maxOf(1L,pose.durationMs).toFloat(),enabled=pose.durationMs>0,modifier=Modifier.testTag("aurora-timeline-slider"))
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={position=(position-100f).coerceAtLeast(0f)}) {Text(AuroraText.t("Voltar 100 ms","Back 100 ms","Retroceder 100 ms"))}
        OutlinedButton(onClick={position=(position+100f).coerceAtMost(pose.durationMs.toFloat())}) {Text(AuroraText.t("Avançar 100 ms","Forward 100 ms","Avanzar 100 ms"))}
    }
}
