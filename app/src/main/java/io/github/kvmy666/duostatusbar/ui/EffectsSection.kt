package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings

private data class EffectOption(val bit: Int,val label: String,val detail: String)
private val effectOptions get()=listOf(
    EffectOption(1,UiText.t("Check no desbloqueio"),UiText.t("Confirmação exclusiva por 3 segundos, com saída suave.")),
    EffectOption(4,UiText.t("Alternância de ícones"),UiText.t("Os estados ativos se alternam. Um sai antes de outro entrar.")),
    EffectOption(8192,UiText.t("Todos os indicadores"),UiText.t("Fones, privacidade, alarme, VPN, GPS, mídia, lanterna e mais.")),
    EffectOption(4096,"NFC",UiText.t("Inclui o NFC na alternância quando estiver ligado.")),
    EffectOption(8,UiText.t("Roteador Wi-Fi"),UiText.t("Mostra quando você compartilha sua conexão.")),
    EffectOption(16,UiText.t("Aviso de fones"),UiText.t("Exibe os fones e a bateria quando a leitura está disponível.")),
    EffectOption(256,UiText.t("Brilho durante a carga"),UiText.t("Brilho inicial por 3 segundos e movimento nos arcos durante a carga.")),
    EffectOption(512,UiText.t("Bateria fraca"),UiText.t("Três pulsos abaixo de 10% e um aviso discreto.")),
    EffectOption(1024,UiText.t("Acabamento de vidro"),UiText.t("Borda delicada, transparência e desfoque quando disponível.")),
    EffectOption(2048,UiText.t("Movimento com molas"),UiText.t("Mais suavidade nas expansões e transições.")),
    EffectOption(2,UiText.t("Alinhamento automático"),UiText.t("Alinha a altura ao relógio e preserva a posição escolhida.")),
    EffectOption(128,UiText.t("Expansão ao acender"),UiText.t("O anel se expande ao acender a tela e se recolhe ao apagar.")),
    EffectOption(32,UiText.t("Ocultar na câmera"),UiText.t("Esconde o anel e os toques enquanto a câmera está aberta.")),
    EffectOption(64,UiText.t("Pausar no bolso"),UiText.t("Pausa ao detectar o bolso ou a tela virada para baixo."))
)
@Composable
internal fun GeometrySection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    if(!search(UiText.t("Desenho do anel"),UiText.t("Grossura da bateria"),UiText.t("Espessura"),UiText.t("Traço"),UiText.t("Escala global"),UiText.t("Tamanho"),UiText.t("Altura do anel")))return
    StudioCard {
        SectionTitle(UiText.t("Desenho do anel"))
        Text(UiText.t("Defina o tamanho e a presença do seu anel."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LabelledSlider(UiText.format("Tamanho do anel: {0}%", settings.sizePercent),settings.sizePercent.toFloat(),
            DuoPrefs.MIN_SIZE.toFloat()..DuoPrefs.MAX_SIZE.toFloat(),enabled=settings.enabled) {onUpdate(settings.copy(sizePercent=it.toInt()))}
        LabelledSlider(UiText.format("Espessura do traço: {0}%", settings.thickPercent),settings.thickPercent.toFloat(),1f..300f,enabled=settings.enabled) {onUpdate(settings.copy(thickPercent=it.toInt()))}
        LabelledSlider(UiText.format("Escala global: {0}%", settings.globalPercent),settings.globalPercent.toFloat(),0f..100f,enabled=settings.enabled) {onUpdate(settings.copy(globalPercent=it.toInt()))}
        Text(UiText.t("O tamanho respeita o espaço disponível na barra."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
internal fun EffectsSection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    val groups=listOf(UiText.t("Ícones e confirmação") to setOf(1,4,8192,4096,8,16),UiText.t("Energia") to setOf(256,512),UiText.t("Acabamento e contexto") to setOf(1024,2048,2,128,32,64))
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
