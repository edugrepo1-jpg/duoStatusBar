package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.i18n.UiText
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.settings.*

/** Features keep their storage keys; each control has exactly one category. */
@Composable internal fun ExperienceSection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate,page:StudioPage=StudioPage.EFFECTS) {
    val topic=LocalSettingsTopic.current
    val advanced=LocalSettingsAdvanced.current
    val context=LocalContext.current
    val options=ExperienceOptions.decode(settings.experienceJson)
    fun update(next:ExperienceOptions)=onUpdate(settings.copy(experienceJson=next.encode()))
    val requestPhotos=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SettingsBridge.push(context) }
    if(page==StudioPage.VISUAL&&topic in setOf("","ring")&&search(UiText.t("Ícones internos"),UiText.t("Tamanho"))) StudioCard {
        SectionTitle(UiText.t("Ícones internos"))
        LabelledSlider(UiText.format("Ícones internos: {0}%",options.iconPercent),options.iconPercent.toFloat(),60f..200f,enabled=settings.enabled){update(options.copy(iconPercent=it.toInt()))}
        Text(UiText.t("O aumento respeita o espaço livre dentro do anel."),style=MaterialTheme.typography.bodySmall)
    }
    if(page==StudioPage.VISUAL&&topic in setOf("","colors")&&search(UiText.t("Cores da bateria"),UiText.t("Fones"))) BatteryColorsSection(options,settings.enabled,::update)
    if(page==StudioPage.EFFECTS) {
        if(topic in setOf("","movement")&&search(UiText.t("Ritmo e transições"),UiText.t("Tempo"),UiText.t("Fade"),UiText.t("Animações")))StudioCard {
            SectionTitle(UiText.t("Ritmo e transições"))
            Text(UiText.t("Escolha o tempo de cada ícone e como ele entra e sai. O próximo só entra quando o anterior termina de sair."),style=MaterialTheme.typography.bodySmall)
            if(advanced) AnimationTimingEditor(options,settings.enabled,::update)
            else SettingSwitch(UiText.t("Transições com fade"),null,options.fadeEnabled,settings.enabled){update(options.copy(fadeEnabled=it))}
            SettingSwitch(UiText.t("Desenhar os ícones"),UiText.t("Os traços surgem durante a entrada, após a saída do anterior."),options.drawIcons,settings.enabled){update(options.copy(drawIcons=it))}
        }
        if(topic in setOf("","media")&&search(UiText.t("Música e gravação"),UiText.t("Anel musical"),UiText.t("Cores da capa"),UiText.t("Tempo de gravação")))StudioCard {
            SectionTitle(UiText.t("Música e gravação"))
            SettingSwitch(UiText.t("Anel musical"),UiText.t("Ondas durante a reprodução e progresso da faixa."),options.music,settings.enabled){update(options.copy(music=it))}
            SettingSwitch(UiText.t("Cores da capa"),UiText.t("Detalhes do anel acompanham a capa do álbum."),options.albumColors,settings.enabled){update(options.copy(albumColors=it))}
            SettingSwitch(UiText.t("Tempo de gravação"),UiText.t("Alterna a bolinha vermelha com o tempo decorrido."),options.recordingTime,settings.enabled){update(options.copy(recordingTime=it))}
        }
        if(topic in setOf("","energy")&&search(UiText.t("Previsão de carga"))) StudioCard {
            SettingSwitch(UiText.t("Previsão de carga"),UiText.t("Alterna com o raio e mostra uma estimativa até 100%."),options.chargeEstimate,settings.enabled){update(options.copy(chargeEstimate=it))}
        }
        if(topic in setOf("","media")) StudioCard {
            if(search(UiText.t("Captura de tela")))SettingSwitch(UiText.t("Captura de tela"),UiText.t("Confirma a captura salva com um obturador animado."),options.screenshot,settings.enabled){update(options.copy(screenshot=it))}
            if(search(UiText.t("Volume no anel")))SettingSwitch(UiText.t("Volume no anel"),UiText.t("Mostra o nível por um instante ao ajustar o volume."),options.volume,settings.enabled){update(options.copy(volume=it))}
        }
        if(topic in setOf("","indicators")) {
            if(search(UiText.t("GPS com bússola")))StudioCard {
                SettingSwitch(UiText.t("GPS com bússola"),UiText.t("O indicador acompanha o norte magnético ao girar o aparelho."),options.compass,settings.enabled){update(options.copy(compass=it))}
                SettingSwitch(UiText.t("Somente Wi-Fi ou dados"),null,options.networkOnly,settings.enabled){update(options.copy(networkOnly=it))}
            }
            NfcParticleSettings(options,settings.enabled,settings.animationsEnabled,search,::update)
        }
        if(topic in setOf("","island")&&search(UiText.t("Ilha dinâmica"),UiText.t("Resumo expansível")))StudioCard {
            SectionTitle(UiText.t("Ilha dinâmica"))
            SettingSwitch(UiText.t("Resumo expansível"),UiText.t("Segure o anel para abrir todos os estados em uma pílula."),options.island,settings.enabled){update(options.copy(island=it))}
            Text(UiText.t("Segure para abrir. Arraste para baixo para expandir. Toque fora para fechar."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

    }
    if((page==StudioPage.VISUAL&&topic in setOf("","island-look"))||(page==StudioPage.EFFECTS&&topic in setOf("","media"))) IslandAppearanceSettings(options,settings.enabled,search,page,::update)
    AuroraSection(settings,options,::update,search,page)
    if(page==StudioPage.MORE&&search(UiText.t("Acesso para música e captura"),UiText.t("Música"),UiText.t("Captura de tela"),UiText.t("Permissões")))StudioCard {
        SectionTitle(UiText.t("Música e captura"))
        Text(UiText.t("Conecte o app aos eventos do aparelho se eles não aparecerem na barra. O processamento fica no celular; as capturas não são abertas nem enviadas."),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }}) { Text(UiText.t("Conectar aos eventos do aparelho")) }
        OutlinedButton(onClick={requestPhotos.launch(android.Manifest.permission.READ_MEDIA_IMAGES)}) { Text(UiText.t("Permitir identificar capturas salvas")) }
    }
}

