package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.fx.Events
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import kotlinx.coroutines.delay

private data class EffectOption(val bit: Int, val label: String, val detail: String)
private val effectOptions = listOf(
    EffectOption(1, "Check no desbloqueio", "Check exclusivo por 3 segundos, após biometria ou PIN, com saída em fade out."),
    EffectOption(2, "Alinhamento automático", "Alinha a altura aos dígitos do relógio, mantendo a posição horizontal escolhida."),
    EffectOption(4, "Rotação de ícones", "Alterna os estados ativos a cada 3 segundos, com saída antes da entrada."),
    EffectOption(8, "Indicador de roteador Wi-Fi", "Mostra o compartilhamento quando o hotspot tá ligado."),
    EffectOption(16, "Aviso de fones", "Mostra os fones por 4 segundos e a bateria, quando o sistema fornece a leitura."),
    EffectOption(32, "Ocultar na câmera", "Deixa o anel invisível e sem toques enquanto a câmera tá aberta."),
    EffectOption(64, "Pausar no bolso", "Pausa os efeitos no bolso ou com a tela virada pra baixo."),
    EffectOption(128, "Expansão ao acender", "O anel sai do relógio ao acender a tela e volta ao apagar."),
    EffectOption(256, "Brilho de carga rápida", "Brilho por 3 segundos ao conectar qualquer carregador e feixe contínuo durante a carga."),
    EffectOption(512, "Alerta de bateria fraca", "Pulsa três vezes abaixo de 10% e deixa um ponto discreto."),
    EffectOption(1024, "Vidro no anel", "Borda fina e fundo translúcido, com desfoque quando disponível."),
    EffectOption(2048, "Movimento com molas", "Suaviza as expansões, retrações e trocas de ícones."),
    EffectOption(8192, "Todos os indicadores", "AirPods, privacidade, alarme, VPN, GPS, mídia, lanterna, carga sem fio e gravação."),
    EffectOption(4096, "NFC no anel", "Inclui o NFC na rotação de ícones quando ele tá ligado no celular.")
)

@Composable
internal fun EffectsSection(settings: DuoSettings, onUpdate: (DuoSettings) -> Unit, search: SearchGate) {
    if (search("Grossura da bateria", "Espessura do anel")) {
        LabelledSlider("Grossura da bateria: ${settings.thickPercent}%", settings.thickPercent.toFloat(), 1f..300f) {
            onUpdate(settings.copy(thickPercent = it.toInt()))
        }
    }
    if (search("Escala global", "Tamanho total do anel")) {
        LabelledSlider("Escala global: ${settings.globalPercent}%", settings.globalPercent.toFloat(), 0f..100f) {
            onUpdate(settings.copy(globalPercent = it.toInt()))
        }
    }
    effectOptions.filter { BuildConfig.FEATURE_MASK and it.bit != 0 && search(it.label, it.detail) }.forEach { option ->
        SettingSwitch(option.label, option.detail, settings.featFlags and option.bit != 0) { enabled ->
            onUpdate(settings.copy(featFlags = if (enabled) settings.featFlags or option.bit else settings.featFlags and option.bit.inv()))
        }
    }
}
