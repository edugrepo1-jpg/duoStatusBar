package io.github.kvmy666.duostatusbar.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings

private data class EffectOption(val bit: Int,val label: String,val detail: String)
private val effectOptions=listOf(
    EffectOption(1,"Check no desbloqueio","Confirmação exclusiva por 3 segundos, com saída suave."),
    EffectOption(4,"Alternância de ícones","Os estados ativos se alternam. Um sai antes de outro entrar."),
    EffectOption(8192,"Todos os indicadores","Fones, privacidade, alarme, VPN, GPS, mídia, lanterna e mais."),
    EffectOption(4096,"NFC","Inclui o NFC na alternância quando estiver ligado."),
    EffectOption(8,"Roteador Wi-Fi","Mostra quando você compartilha sua conexão."),
    EffectOption(16,"Aviso de fones","Exibe os fones e a bateria quando a leitura está disponível."),
    EffectOption(256,"Brilho durante a carga","Brilho inicial por 3 segundos e movimento nos arcos durante a carga."),
    EffectOption(512,"Bateria fraca","Três pulsos abaixo de 10% e um aviso discreto."),
    EffectOption(1024,"Acabamento de vidro","Borda delicada, transparência e desfoque quando disponível."),
    EffectOption(2048,"Movimento com molas","Mais suavidade nas expansões e transições."),
    EffectOption(2,"Alinhamento automático","Alinha a altura ao relógio e preserva a posição escolhida."),
    EffectOption(128,"Expansão ao acender","O anel se expande ao acender a tela e se recolhe ao apagar."),
    EffectOption(32,"Ocultar na câmera","Esconde o anel e os toques enquanto a câmera está aberta."),
    EffectOption(64,"Pausar no bolso","Pausa ao detectar o bolso ou a tela virada para baixo.")
)
@Composable
internal fun GeometrySection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    if(!search("Desenho do anel","Grossura da bateria","Espessura","Traço","Escala global","Tamanho","Altura do anel"))return
    StudioCard {
        SectionTitle("Desenho do anel")
        Text("Defina o tamanho e a presença do seu anel.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LabelledSlider("Tamanho do anel: ${settings.sizePercent}%",settings.sizePercent.toFloat(),
            DuoPrefs.MIN_SIZE.toFloat()..DuoPrefs.MAX_SIZE.toFloat(),enabled=settings.enabled) {onUpdate(settings.copy(sizePercent=it.toInt()))}
        LabelledSlider("Espessura do traço: ${settings.thickPercent}%",settings.thickPercent.toFloat(),1f..300f,enabled=settings.enabled) {onUpdate(settings.copy(thickPercent=it.toInt()))}
        LabelledSlider("Escala global: ${settings.globalPercent}%",settings.globalPercent.toFloat(),0f..100f,enabled=settings.enabled) {onUpdate(settings.copy(globalPercent=it.toInt()))}
        Text("O tamanho respeita o espaço disponível na barra.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
internal fun EffectsSection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    val groups=listOf("Ícones e confirmação" to setOf(1,4,8192,4096,8,16),"Energia" to setOf(256,512),"Acabamento e contexto" to setOf(1024,2048,2,128,32,64))
    groups.forEach { (title,bits) ->
        val options=effectOptions.filter { it.bit in bits && BuildConfig.FEATURE_MASK and it.bit!=0 && (search(title)||search(it.label,it.detail)) }
        if(options.isNotEmpty())StudioCard {
            SectionTitle(title)
            options.forEachIndexed { index,option ->
                if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
                SettingSwitch(option.label,option.detail,settings.featFlags and option.bit!=0,settings.enabled) { enabled ->
                    onUpdate(settings.copy(featFlags=if(enabled)settings.featFlags or option.bit else settings.featFlags and option.bit.inv()))
                }
            }
        }
    }
}
