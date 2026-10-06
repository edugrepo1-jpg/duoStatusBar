package io.github.kvmy666.duostatusbar.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.DuoSettings

@Composable internal fun ShadeSection(settings:DuoSettings,onUpdate:(DuoSettings)->Unit,search:SearchGate) {
    if(!search(UiText.t("Painéis do sistema"),UiText.t("Mostrar nas notificações"),UiText.t("Mostrar nos ajustes rápidos")))return
    StudioCard {
        SectionTitle(UiText.t("Painéis do sistema"))
        SettingSwitch(UiText.t("Mostrar nas notificações"),UiText.t("Inclui o anel no cabeçalho do painel de notificações."),settings.showNotifications,settings.enabled) {onUpdate(settings.copy(showNotifications=it))}
        LabelledSlider(UiText.format("Tamanho nas notificações: {0}%",settings.notificationSize),settings.notificationSize.toFloat(),50f..200f,enabled=settings.enabled&&settings.showNotifications) {onUpdate(settings.copy(notificationSize=it.toInt()))}
        HorizontalDivider()
        SettingSwitch(UiText.t("Mostrar nos ajustes rápidos"),UiText.t("Inclui o anel no cabeçalho dos ajustes rápidos."),settings.showQuickSettings,settings.enabled) {onUpdate(settings.copy(showQuickSettings=it))}
        LabelledSlider(UiText.format("Tamanho nos ajustes rápidos: {0}%",settings.quickSettingsSize),settings.quickSettingsSize.toFloat(),50f..200f,enabled=settings.enabled&&settings.showQuickSettings) {onUpdate(settings.copy(quickSettingsSize=it.toInt()))}
        Text(UiText.t("Cada painel tem seu próprio tamanho. O limite físico do cabeçalho evita recortes; o tamanho da barra normal não muda."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
