package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kvmy666.duostatusbar.fx.*

@Composable internal fun AnimationTimingEditor(options:ExperienceOptions,enabled:Boolean,onChange:(ExperienceOptions)->Unit) {
    var selected by remember { mutableStateOf(SlotIcon.WIFI) }
    var choosing by remember { mutableStateOf(false) }
    SettingSwitch(UiText.t("Somente Wi-Fi ou dados"),UiText.t("Exibe apenas a conexão de rede, sem carrossel nem efeitos."),options.networkOnly,enabled) { onChange(options.copy(networkOnly=it)) }
    SettingSwitch(UiText.t("Transições com fade"),UiText.t("Termina a saída antes de iniciar a entrada do próximo ícone."),options.fadeEnabled,enabled&&!options.networkOnly) { onChange(options.copy(fadeEnabled=it)) }
    SettingSwitch(UiText.t("Tempo único para todos"),UiText.t("Usa a mesma duração para os ícones de todos os aplicativos. Seus tempos individuais ficam guardados."),options.universalTiming,enabled&&!options.networkOnly) {onChange(options.copy(universalTiming=it))}
    LabelledSlider(UiText.format("Permanência: {0} s", options.dwellMs/1000),options.dwellMs/1000f,1f..60f,enabled=enabled&&!options.networkOnly,formatValue={UiText.format("{0} s",it.toInt())}) { onChange(options.copy(dwellMs=it.toInt()*1000)) }
    Text(UiText.t("Música e gravações permanecem até parar. A duração acima controla os demais ícones. O check de desbloqueio dura 3 segundos."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(UiText.t("Captura, volume e notificações são avisos breves. Música e microfone ficam até parar; a gravação pode alternar o ponto e o cronômetro no mesmo evento."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    LabelledSlider(UiText.format("Saída do ícone: {0} ms", options.exitMs),options.exitMs.toFloat(),60f..600f,enabled=enabled&&!options.networkOnly&&options.fadeEnabled) {onChange(options.copy(exitMs=it.toInt()))}
    LabelledSlider(UiText.format("Entrada do ícone: {0} ms", options.entryMs),options.entryMs.toFloat(),60f..800f,enabled=enabled&&!options.networkOnly&&options.fadeEnabled) {onChange(options.copy(entryMs=it.toInt()))}
    Text(UiText.t("A saída termina antes da entrada. Em intervalos curtos, os fades são reduzidos para caber no tempo escolhido."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(!options.universalTiming) {
    Box {
        OutlinedButton(onClick={choosing=true},enabled=enabled&&!options.networkOnly) { Text(UiText.format("Tempo por ícone: {0}", iconLabel(selected))) }
        DropdownMenu(expanded=choosing,onDismissRequest={choosing=false}) {
            SlotIcon.entries.filter { it !in setOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE,SlotIcon.RECORD,SlotIcon.RECORD_TIME,SlotIcon.VOLUME,SlotIcon.SCREENSHOT,SlotIcon.NOTIFICATION) }.forEach { icon -> DropdownMenuItem(text={Text(iconLabel(icon))},onClick={selected=icon;choosing=false}) }
        }
    }
    val own=selected.name in options.iconSeconds
    SettingSwitch(UiText.t("Usar tempo próprio"),UiText.t("Sem esta opção, o ícone segue o tempo padrão."),own,enabled&&!options.networkOnly) {
        onChange(options.copy(iconSeconds=if(it)options.iconSeconds+(selected.name to (options.dwellMs/1000)) else options.iconSeconds-selected.name))
    }
    if(own)LabelledSlider(UiText.format("{0}: {1} s", iconLabel(selected), options.iconSeconds.getValue(selected.name)),options.iconSeconds.getValue(selected.name).toFloat(),1f..60f,enabled=enabled&&!options.networkOnly) {
        onChange(options.copy(iconSeconds=options.iconSeconds+(selected.name to it.toInt())))
    }
    }
}
