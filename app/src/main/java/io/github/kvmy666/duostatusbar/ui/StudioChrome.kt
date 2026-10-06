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

internal enum class StudioPage(private val labelKey: String, private val headingKey: String, private val subtitleKey: String, val glyph: StudioSymbol) {
    HOME("Início","Sua barra de status","Ajuste o anel e escolha os estados que deseja acompanhar.",StudioSymbol.HOME),
    VISUAL("Visual","Desenho do anel","Ajuste cada detalhe do seu anel.",StudioSymbol.VISUAL),
    EFFECTS("Efeitos","Ícones e animações","Toque em uma função para entender e ativar.",StudioSymbol.EFFECTS),
    MORE("Ajustes","Configurações","Conexão, gestos, idiomas e diagnóstico.",StudioSymbol.SETTINGS);
    val label get()=UiText.t(labelKey)
    val heading get()=UiText.t(headingKey)
    val subtitle get()=UiText.t(subtitleKey)
    fun visible(current: StudioPage, query: String) = current == this || query.isNotBlank()
}
internal enum class StudioSymbol { HOME,VISUAL,EFFECTS,SETTINGS,SEARCH,ARROW,PLUS,MINUS,CHECK }

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
                    drawRoundRect(color,Offset(3f,3f),Size(7f,7f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke)
                    drawRoundRect(color,Offset(14f,3f),Size(7f,7f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke)
                    drawRoundRect(color,Offset(3f,14f),Size(7f,7f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke)
                    drawCircle(color,3.5f,Offset(17.5f,17.5f),style=stroke)
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

@Composable internal fun StudioHeader(page: StudioPage, landscape: Boolean, searching: Boolean) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text("DUO RECREATE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.sp)
        Text(UiText.format("Editando: {0}",if(landscape)UiText.t("Horizontal") else UiText.t("Vertical")),
            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(if(searching)UiText.t("Encontre seu ajuste.") else page.heading,style=MaterialTheme.typography.headlineLarge)
    Text(if(searching)UiText.t("Resultados em todas as áreas do app.") else page.subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun StudioNavigation(page: StudioPage, onSelect: (StudioPage) -> Unit) {
    Surface(Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth(),
        shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.30f)),shadowElevation=3.dp) {
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            StudioPage.entries.forEach { item ->
                val active=page==item
                val tint=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(Modifier.fillMaxWidth().testTag("studio-home"),shape=RoundedCornerShape(30.dp),color=StudioInk) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(UiText.t("PRÉVIA DA BARRA"),style=MaterialTheme.typography.labelSmall,color=Color(0xFFADBBD3),letterSpacing=1.7.sp)
                Image(painterResource(R.drawable.duo_mark),null,Modifier.size(38.dp))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                AndroidView(modifier=Modifier.size(100.dp,114.dp),factory={DuoCanvasView(it).apply { animationsEnabled=false }},onRelease={it.teardown()},update={view->
                    view.thickPercent=settings.thickPercent;view.globalPercent=settings.globalPercent
                    view.render(DuoMapping.visual(72,false,false,settings.showPercent,3,4,false,percentHeight=settings.percentHeight,wifiDots=settings.wifiDots))
                })
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(UiText.t("Seu desenho"),style=MaterialTheme.typography.titleLarge,color=Color.White)
                    Text(UiText.t("Confira o desenho aqui. Em Efeitos, você pode simular os eventos."),style=MaterialTheme.typography.bodySmall,color=Color(0xFFB1BED3))
                    Text(UiText.t("SIMULAÇÃO · 72%"),style=MaterialTheme.typography.labelSmall,color=StudioMint)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.06f)).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(UiText.format("Anel {0}%", settings.sizePercent),style=MaterialTheme.typography.labelSmall,color=Color.White)
                Text(UiText.format("Traço {0}%", settings.thickPercent),style=MaterialTheme.typography.labelSmall,color=Color(0xFFB1BED3))
            }
        }
    }
    StudioCard {
        SettingSwitch(UiText.t("Personalizar a barra"), UiText.t("Ative o Duo na orientação atual."),settings.enabled,onChange={onUpdate(settings.copy(enabled=it))})
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(StudioPage.MORE) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            val healthy=settings.enabled&&state==ModuleState.OK
            val status=when { !settings.enabled -> UiText.t("Personalização desligada");healthy -> UiText.t("Módulo conectado");state==ModuleState.NEEDS_RESTART -> UiText.t("Atualização pronta · reinicie a barra");else -> UiText.t("Verificar ativação do módulo") }
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if(healthy)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant))
            Text(status,Modifier.weight(1f).padding(start=9.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(16.dp))
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        StudioDestination(UiText.t("Visual"),UiText.t("Tamanho e posição"),StudioPage.VISUAL,Modifier.weight(1f),onSelect)
        StudioDestination(UiText.t("Efeitos"),UiText.t("Ícones e movimento"),StudioPage.EFFECTS,Modifier.weight(1f),onSelect)
    }
    StudioCard {
        SettingSwitch(UiText.t("Movimento suave"),UiText.t("Controle geral das animações do anel."),settings.animationsEnabled,settings.enabled,onChange={onUpdate(settings.copy(animationsEnabled=it))})
    }
}

@Composable private fun StudioDestination(title:String, detail:String, page:StudioPage, modifier:Modifier, onSelect:(StudioPage)->Unit) {
    val tint=if(page==StudioPage.VISUAL)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Surface(modifier.clip(RoundedCornerShape(24.dp)).clickable(role=Role.Button) { onSelect(page) },shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            StudioGlyph(page.glyph,tint,Modifier.size(28.dp))
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(12.dp)).padding(10.dp),
                horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(UiText.t("Abrir"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.onPrimaryContainer,Modifier.size(16.dp))
            }
        }
    }
}
