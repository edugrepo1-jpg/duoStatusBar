package io.github.kvmy666.duostatusbar.ui

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

@Composable internal fun ExperienceSection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    val context=LocalContext.current
    val options=ExperienceOptions.decode(settings.experienceJson)
    fun update(next:ExperienceOptions)=onUpdate(settings.copy(experienceJson=next.encode()))
    val requestPhotos=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SettingsBridge.push(context) }
    if(search("Novidades","Anel musical","Previsão de carga","Captura de tela","Volume","Gravação","Dynamic Island","Resumo","Desenho dos ícones"))StudioCard {
        SectionTitle("Experiência expandida")
        Text("As novidades mantêm seu anel e a confirmação do desbloqueio.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={update(ExperienceOptions.ALL.copy(dwellMs=options.dwellMs,exitMs=options.exitMs,entryMs=options.entryMs,iconPercent=options.iconPercent))},enabled=settings.enabled) { Text("Ativar todas as novidades") }
        SettingSwitch("Anel musical","Ondas durante a reprodução e progresso da faixa.",options.music,settings.enabled){update(options.copy(music=it))}
        SettingSwitch("Cores da capa","Detalhes do anel acompanham a capa do álbum.",options.albumColors,settings.enabled){update(options.copy(albumColors=it))}
        SettingSwitch("Previsão de carga","Alterna com o raio e mostra uma estimativa até 100%.",options.chargeEstimate,settings.enabled){update(options.copy(chargeEstimate=it))}
        SettingSwitch("Captura de tela","Confirma a captura salva com um obturador animado.",options.screenshot,settings.enabled){update(options.copy(screenshot=it))}
        SettingSwitch("Volume no anel","Mostra o nível por um instante ao ajustar o volume.",options.volume,settings.enabled){update(options.copy(volume=it))}
        SettingSwitch("Tempo de gravação","Alterna a bolinha vermelha com o tempo decorrido.",options.recordingTime,settings.enabled){update(options.copy(recordingTime=it))}
        SettingSwitch("Resumo expansível","Segure o anel para abrir todos os estados em uma pílula.",options.island,settings.enabled){update(options.copy(island=it))}
        SettingSwitch("GPS com bússola","O indicador acompanha o norte magnético ao girar o aparelho.",options.compass,settings.enabled){update(options.copy(compass=it))}
        LabelledSlider("Ícones internos: ${options.iconPercent}%",options.iconPercent.toFloat(),60f..200f,enabled=settings.enabled){update(options.copy(iconPercent=it.toInt()))}
        Text("O aumento respeita o espaço livre dentro do anel.",style=MaterialTheme.typography.bodySmall)
        SettingSwitch("Desenhar os ícones","Os traços surgem durante a entrada, após a saída do anterior.",options.drawIcons,settings.enabled){update(options.copy(drawIcons=it))}
    }
    if(search("Acesso para música e captura","Música","Captura de tela","Permissões"))StudioCard {
        SectionTitle("Música e captura")
        Text("Conecte o app aos eventos do aparelho se eles não aparecerem na barra. O processamento fica no celular; as capturas não são abertas nem enviadas.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }}) { Text("Conectar aos eventos do aparelho") }
        OutlinedButton(onClick={requestPhotos.launch(android.Manifest.permission.READ_MEDIA_IMAGES)}) { Text("Permitir identificar capturas salvas") }
    }
}
