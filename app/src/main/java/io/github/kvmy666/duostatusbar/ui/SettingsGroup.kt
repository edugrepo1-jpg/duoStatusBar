package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.rotate

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.semantics.Role

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.unit.dp

import io.github.kvmy666.duostatusbar.fx.AuroraText

internal class SettingsNavigation(val selected:String, val open:(String)->Unit, val back:()->Unit)

internal val LocalSettingsNavigation=staticCompositionLocalOf<SettingsNavigation?> { null }

internal val LocalSettingsTopic=staticCompositionLocalOf { "" }

internal val LocalSettingsAdvanced=staticCompositionLocalOf { false }

/** Disclosure affects presentation only: controls retain their original preference ownership. */

@Composable internal fun SettingsGroup(

    id: String,

    title: String,

    description: String,

    query: String,

    content: @Composable () -> Unit

) {

    val navigation=LocalSettingsNavigation.current

    var expanded by rememberSaveable(id) { mutableStateOf(false) }

    val selected=navigation?.selected ?: if(expanded) id else ""

    if(query.isNotBlank()) {

        Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)

        CompositionLocalProvider(LocalSettingsTopic provides id, LocalSettingsAdvanced provides true) { content() }

        return

    }

    if(selected.isNotEmpty()&&selected!=id)return

    if(selected==id) {

        TextButton(onClick={if(navigation!=null)navigation.back() else expanded=false},modifier=Modifier.heightIn(min=48.dp).testTag("settings-back")) {

            Text(AuroraText.t("‹ Voltar aos assuntos","‹ Back to topics","‹ Volver a temas"))

        }

        Text(title,style=MaterialTheme.typography.headlineSmall)

        Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)

        CompositionLocalProvider(LocalSettingsTopic provides id) {content()}

        return

    }

    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {

        Row(Modifier.fillMaxWidth().testTag("settings-group-$id")

            .clickable(role=Role.Button,onClick={if(navigation!=null)navigation.open(id) else expanded=true})

            .semantics { stateDescription=AuroraText.t("Fechado","Collapsed","Cerrado") }

            .padding(18.dp).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically,

            horizontalArrangement=Arrangement.spacedBy(14.dp)) {

            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {

                Text(title,style=MaterialTheme.typography.titleMedium)

                Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)

                Text(AuroraText.t("Abrir ajustes","Open settings","Abrir ajustes"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)

            }

            StudioGlyph(StudioSymbol.ARROW,MaterialTheme.colorScheme.primary,Modifier.size(22.dp))

        }

    }

}

