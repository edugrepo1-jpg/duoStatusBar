package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.settings.*
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.fx.EffectFrame
import io.github.kvmy666.duostatusbar.fx.BatteryBandColors

internal enum class StudioPage(private val labelKey: String, private val headingKey: String, private val subtitleKey: String, val glyph: StudioSymbol) {
    HOME("Início","Sua barra, seu jeito","Visual, efeitos e controles em um só lugar.",StudioSymbol.HOME),
    VISUAL("Visual","Desenho do anel","Ajuste cada detalhe do seu anel.",StudioSymbol.VISUAL),
    EFFECTS("Efeitos","Ícones e animações","Toque em uma função para entender e ativar.",StudioSymbol.EFFECTS),
    MORE("Ajustes","Configurações","Conexão, gestos, idiomas e diagnóstico.",StudioSymbol.SETTINGS);
    val label get()=UiText.t(labelKey)
    val heading get()=UiText.t(headingKey)
    val subtitle get()=UiText.t(subtitleKey)
    fun visible(current: StudioPage, query: String) = current == this || query.isNotBlank()
}
internal enum class StudioSymbol { HOME,VISUAL,EFFECTS,SETTINGS,SEARCH,ARROW,PLUS,MINUS,CHECK,ISLAND }

/** Original vectors, no icon font or continuous animation. */
@Composable internal fun StudioGlyph(symbol: StudioSymbol, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    val surface=MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        scale(size.width/24f,size.height/24f,pivot=Offset.Zero) {
            val stroke=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x,y),Offset(x2,y2),1.8f,StrokeCap.Round)
            when(symbol) {
                StudioSymbol.HOME -> {
                    val p=Path().apply { moveTo(3f,10f);lineTo(12f,3f);lineTo(21f,10f);moveTo(5f,9f);lineTo(5f,20f);lineTo(10f,20f);lineTo(10f,14f);lineTo(14f,14f);lineTo(14f,20f);lineTo(19f,20f);lineTo(19f,9f) }
                    drawPath(p,color,style=stroke)
                }
                StudioSymbol.VISUAL -> {
                    val palette=Path().apply {
                        moveTo(12f,3f);cubicTo(17f,3f,21f,7f,21f,11f)
                        cubicTo(21f,15f,18f,16f,15f,14.5f);cubicTo(13f,13.5f,11f,15f,12f,17f)
                        cubicTo(14f,21f,8f,22f,5f,18f);cubicTo(1f,15f,1f,10f,4f,6f)
                        cubicTo(6f,3f,9f,3f,12f,3f);close()
                    }
                    drawPath(palette,color,style=stroke)
                    for(point in listOf(Offset(7f,8f),Offset(12f,6f),Offset(17f,9f),Offset(6f,13f)))drawCircle(color,1.25f,point)
                }
                StudioSymbol.EFFECTS -> {
                    val p=Path().apply { moveTo(13f,2f);lineTo(5f,13f);lineTo(11f,13f);lineTo(10f,22f);lineTo(19f,10f);lineTo(13f,10f);close() }
                    drawPath(p,color,style=stroke)
                }
                StudioSymbol.SETTINGS -> {
                    line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f)
                    for((x,y) in listOf(8f to 6f,16f to 12f,9f to 18f)) {
                        drawCircle(surface,2.6f,Offset(x,y));drawCircle(color,2.6f,Offset(x,y),style=stroke)
                    }
                }
                StudioSymbol.SEARCH -> { drawCircle(color,6.5f,Offset(10f,10f),style=stroke);line(15f,15f,21f,21f) }
                StudioSymbol.ARROW -> { line(9f,5f,16f,12f);line(16f,12f,9f,19f) }
                StudioSymbol.ISLAND -> { drawRoundRect(color,Offset(2f,7f),Size(20f,10f),androidx.compose.ui.geometry.CornerRadius(5f),style=stroke);drawCircle(color,1.8f,Offset(7f,12f));line(12f,12f,18f,12f) }
                StudioSymbol.PLUS -> { line(5f,12f,19f,12f);line(12f,5f,12f,19f) }
                StudioSymbol.MINUS -> line(5f,12f,19f,12f)
                StudioSymbol.CHECK -> { line(5f,12f,10f,17f);line(10f,17f,20f,6f) }
            }
        }
    }
}

@Composable internal fun StudioCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.30f))) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable internal fun StudioHeader(page: StudioPage, landscape: Boolean, searching: Boolean,
    settings:DuoSettings?=null, state:ModuleState=ModuleState.NEVER_LOADED, onUpdate:(DuoSettings)->Unit={}) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("DUO",style=MaterialTheme.typography.titleLarge)
            Text(" RECREATE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(settings!=null) Row(verticalAlignment=Alignment.CenterVertically) {
            val rootContext=androidx.compose.ui.platform.LocalContext.current
            val rootMode=io.github.kvmy666.duostatusbar.settings.RuntimeBackend.mode(rootContext)=="root"
            val rootReady=io.github.kvmy666.duostatusbar.settings.RuntimeBackend.ready(rootContext)
            Text(UiText.t(if(rootMode) {if(rootReady)"Root ativo" else "Root pendente"} else if(!settings.enabled)"Módulo desligado" else if(state==ModuleState.OK)"Módulo ativo" else "Ativação pendente"),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            RecreateSwitch(settings.enabled,true,UiText.t("Personalizar a barra")) {onUpdate(settings.copy(enabled=it))}
        }
    }
    Text(if(searching)UiText.t("Encontre seu ajuste.") else page.heading,style=MaterialTheme.typography.headlineLarge)
    Text(if(searching)UiText.t("Resultados em todas as áreas do app.") else page.subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(UiText.format("Editando: {0}",if(landscape)UiText.t("Horizontal") else UiText.t("Vertical")),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun StudioNavigation(page: StudioPage, onSelect: (StudioPage) -> Unit) {
    Surface(Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth(),
        shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.30f)),shadowElevation=3.dp) {
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            StudioPage.entries.forEach { item ->
                val active=page==item
                val tint=if(active)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(if(active)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(active,role=Role.Tab,onClick={onSelect(item)}).testTag("nav-${item.name}")
                    .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    StudioGlyph(item.glyph,tint)
                    Text(item.label,style=MaterialTheme.typography.labelSmall,color=tint)
                }
            }
        }
    }
}

@Composable internal fun StudioHome(settings: DuoSettings, state: ModuleState, onUpdate: (DuoSettings) -> Unit, onSelect: (StudioPage) -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("studio-home"),shape=RoundedCornerShape(28.dp),color=StudioInk) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(UiText.t("Prévia"),style=MaterialTheme.typography.labelSmall,color=Color(0xFFBCC4D4),
                modifier=Modifier.background(Color.White.copy(alpha=.07f),RoundedCornerShape(10.dp)).padding(horizontal=12.dp,vertical=5.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                AndroidView(modifier=Modifier.size(112.dp,124.dp),factory={DuoCanvasView(it).apply { animationsEnabled=false }},onRelease={it.teardown()},update={view->
                    view.thickPercent=settings.thickPercent;view.globalPercent=settings.globalPercent
                    val options=ExperienceOptions.decode(settings.experienceJson)
                    view.effects=EffectFrame(iconPercent=options.iconPercent,motionEnabled=false,
                        batteryColors=options.takeIf {it.customBatteryColors}?.let {BatteryBandColors(it.batteryLow,it.batteryMid,it.batteryHigh)})
                    view.render(DuoMapping.visual(72,false,false,settings.showPercent,3,4,false,percentHeight=settings.percentHeight,wifiDots=settings.wifiDots).copy(tint=android.graphics.Color.WHITE))
                })
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(UiText.t("Sua prévia"),style=MaterialTheme.typography.titleLarge,color=Color.White)
                    Text(UiText.t("Simulação · Anel · Wi-Fi · 72%"),style=MaterialTheme.typography.bodySmall,color=Color(0xFFB1BED3))
                    Button(colors=StudioFilledButtonColors(),onClick={onSelect(StudioPage.VISUAL)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("home-customize"),contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(14.dp)) {
                        Text(UiText.t("Personalizar"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        StudioGlyph(StudioSymbol.ARROW,Color.White,Modifier.size(16.dp))
                    }
                    OutlinedButton(onClick={onSelect(StudioPage.EFFECTS)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("home-test-effects"),contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(14.dp),
                        colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White),border=BorderStroke(1.dp,Color(0xFF444A57))) {
                        Text(UiText.t("Testar efeitos"),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        StudioGlyph(StudioSymbol.ARROW,Color.White,Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)) {
        StudioDestination(UiText.t("Visual"),UiText.t("Cores, tamanho e posição"),StudioSymbol.VISUAL) {onSelect(StudioPage.VISUAL)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        StudioDestination(UiText.t("Efeitos"),UiText.t("Desbloqueio, carga e transições"),StudioSymbol.EFFECTS) {onSelect(StudioPage.EFFECTS)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        StudioDestination(UiText.t("Ilha dinâmica"),UiText.t("Aparência em Visual · gestos em Efeitos"),StudioSymbol.ISLAND) {onSelect(StudioPage.EFFECTS)}
    }
    }
    val rootContext=androidx.compose.ui.platform.LocalContext.current
    val rootMode=io.github.kvmy666.duostatusbar.settings.RuntimeBackend.mode(rootContext)=="root"
    val healthy=settings.enabled&&(if(rootMode)io.github.kvmy666.duostatusbar.settings.RuntimeBackend.ready(rootContext) else state==ModuleState.OK)
    StudioDestination(UiText.t(when { !settings.enabled->"Personalização desligada";rootMode->if(healthy)"Processo iniciado e primeiro desenho confirmado." else "Aguardando confirmação do processo root.";healthy->"Módulo conectado";state==ModuleState.NEEDS_RESTART->"Atualização pronta · reinicie a barra";else->"Verificar ativação do módulo" }),
        UiText.t("Confira a conexão e o diagnóstico em Ajustes."),StudioSymbol.SETTINGS) {onSelect(StudioPage.MORE)}
}

@Composable private fun StudioDestination(title:String, detail:String, symbol:StudioSymbol,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role=Role.Button,onClick=onClick).testTag("home-route-${symbol.name}").heightIn(min=64.dp).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        StudioGlyph(symbol,MaterialTheme.colorScheme.primary,Modifier.size(28.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(18.dp))
    }
}
