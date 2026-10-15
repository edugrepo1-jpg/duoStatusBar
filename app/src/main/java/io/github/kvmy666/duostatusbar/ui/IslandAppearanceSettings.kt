package io.github.kvmy666.duostatusbar.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kvmy666.duostatusbar.fx.*
import kotlinx.coroutines.delay

@Composable internal fun IslandAppearanceSettings(options:ExperienceOptions,enabled:Boolean,search:SearchGate,page:StudioPage,onUpdate:(ExperienceOptions)->Unit) {
    val a=options.islandAppearance
    if(page==StudioPage.VISUAL&&search("Dynamic Island",AuroraText.t("Transparência","Transparency","Transparencia")))StudioCard {
        SectionTitle(AuroraText.t("Transparência da ilha","Island transparency","Transparencia de la isla"))
        Text(AuroraText.t("O fundo e a capa têm controles separados. Texto e botões permanecem legíveis.","Background and artwork have separate controls. Text and buttons remain readable.","El fondo y la portada tienen controles separados. Texto y botones permanecen legibles."))
        LabelledSlider(AuroraText.t("Opacidade do fundo","Background opacity","Opacidad del fondo")+": ${a.opacity}%",a.opacity.toFloat(),20f..100f,enabled=enabled){onUpdate(options.copy(islandAppearance=a.copy(opacity=it.toInt())))}
        LabelledSlider(AuroraText.t("Opacidade da capa","Artwork opacity","Opacidad de portada")+": ${a.coverOpacity}%",a.coverOpacity.toFloat(),20f..100f,enabled=enabled){onUpdate(options.copy(islandAppearance=a.copy(coverOpacity=it.toInt())))}
    }
    if(page!=StudioPage.EFFECTS||!search(AuroraText.t("Efeitos da capa","Artwork effects","Efectos de portada"),"Dynamic Island",AuroraText.t("Música","Music","Música")))return
    StudioCard {
        SectionTitle(AuroraText.t("Efeitos da capa","Artwork effects","Efectos de portada"))
        SettingSwitch(AuroraText.t("Progresso com ondas","Wavy progress","Progreso con ondas"),AuroraText.t("A onda se move durante a reprodução. A posição acompanha o tempo real da faixa.","The wave moves during playback. Position follows the real track time.","La onda se mueve durante la reproducción. La posición sigue el tiempo real."),a.waveProgress,enabled){onUpdate(options.copy(islandAppearance=a.copy(waveProgress=it)))}
        var candidate by remember(a.coverEffect){mutableIntStateOf(a.coverEffect)}
        var menu by remember {mutableStateOf(false)}
        Box {
            OutlinedButton(onClick={menu=true},enabled=enabled){Text(CoverEffects.label(candidate)+" ▾")}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false},modifier=Modifier.heightIn(max=320.dp)) {
                CoverEffects.names.indices.forEach {id->DropdownMenuItem(text={Text(CoverEffects.label(id))},onClick={candidate=id;menu=false})}
            }
        }
        Text(AuroraText.t("Prévia decorativa: não mede a intensidade do áudio. O efeito só se move com música tocando e ilha visível.","Decorative preview: does not measure audio amplitude. Motion runs only with music playing and the island visible.","Vista decorativa: no mide la intensidad del audio. Solo se mueve con música y la isla visible."),style=MaterialTheme.typography.bodySmall)
        CoverEffectPreview(candidate)
        Button(onClick={onUpdate(options.copy(islandAppearance=a.copy(coverEffect=candidate)))},enabled=enabled&&candidate!=a.coverEffect){Text(AuroraText.t("Aplicar efeito","Apply effect","Aplicar efecto"))}
        Text(AuroraText.t("Atual: ","Current: ","Actual: ")+CoverEffects.label(a.coverEffect),style=MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun CoverEffectPreview(effect:Int) {
    val painter=remember {CoverEffectPainter()}
    val paint=remember {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)}
    var elapsed by remember {mutableLongStateOf(0)}
    var playing by remember {mutableStateOf(false)}
    var visible by remember {mutableStateOf(false)}
    val host=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver {_,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED);if(!resumed)playing=false};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    LaunchedEffect(playing,effect,resumed,visible){if(!playing||!resumed||!visible){playing=false;return@LaunchedEffect};val start=SystemClock.uptimeMillis();while(SystemClock.uptimeMillis()-start<6000&&ValueAnimator.areAnimatorsEnabled()){withFrameNanos {elapsed=SystemClock.uptimeMillis()-start};delay(24)};playing=false}
    Canvas(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF111722)).onGloballyPositioned {
        val b=it.boundsInWindow();visible=it.isAttached&&b.width>0&&b.height>0&&b.bottom>0&&b.top<host.rootView.height&&b.right>0&&b.left<host.rootView.width
    }) {
        val c=drawContext.canvas.nativeCanvas;val side=64.dp.toPx();val r=RectF(size.width/2-side/2,size.height/2-side/2,size.width/2+side/2,size.height/2+side/2)
        painter.draw(c,r,effect,elapsed,0xFF79D9F4.toInt(),.62f,playing)
        paint.color=0xFF20445D.toInt();c.drawCircle(r.centerX(),r.centerY(),side/2,paint)
        paint.color=android.graphics.Color.WHITE;paint.textAlign=android.graphics.Paint.Align.CENTER;paint.textSize=30.dp.toPx();c.drawText("♪",r.centerX(),r.centerY()+10.dp.toPx(),paint)
    }
    OutlinedButton(onClick={playing=!playing;elapsed=0},enabled=resumed&&visible){Text(if(playing)AuroraText.t("Parar prévia","Stop preview","Detener vista")else AuroraText.t("Ver prévia · 6 segundos","Preview · 6 seconds","Ver vista · 6 segundos"))}
}
