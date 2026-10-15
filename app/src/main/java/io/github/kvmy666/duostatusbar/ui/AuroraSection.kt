package io.github.kvmy666.duostatusbar.ui



import android.content.Context

import android.content.Intent

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.platform.testTag

import androidx.compose.ui.unit.dp

import io.github.kvmy666.duostatusbar.fx.*

import io.github.kvmy666.duostatusbar.settings.*



/** Every Aurora option has one destination; runtime keys and existing settings remain unchanged. */

internal val AuroraVisualKeys=setOf("adaptiveContrast","safeGeometry","batterySource","privacyOrigins","threeLevels","landscapeLayout")

internal val AuroraEffectGroups=listOf(

    listOf("unlockGlow","unlockBuild","privacyPair","headphoneConfirm","shutter","singleNotices","chargeStyles"),

    listOf("touchResponse","interruptibleIsland","fingerExpansion","contextActions","openSource"),

    listOf("timer","voiceCall","taskComplete"),

    listOf("timeline")

)



internal fun auroraTimerIntent(command:String):Intent? = command.takeIf {it in setOf("timerStart","timerPause","timerReset","timerPlus","stopwatchStart","stopwatchPause","stopwatchReset")}?.let {

    Intent(RuntimeExperience.ACTION).setPackage(SettingsBridge.SYSTEMUI).putExtra("timerCommand",it)

}

internal fun sendAuroraTimerCommand(context:Context,command:String):Boolean {

    val intent=auroraTimerIntent(command) ?: return false

    return runCatching {

    // RuntimeExperience requires SettingsBridge.PERMISSION from the sender. SystemUI does not hold

    // our signature permission, so using it as sendBroadcast's receiverPermission would block delivery.

    context.sendBroadcast(intent)

    true

    }.getOrDefault(false)

}



@Composable internal fun AuroraSection(settings:DuoSettings,options:ExperienceOptions,onUpdate:(ExperienceOptions)->Unit,search:SearchGate,page:StudioPage) {

    val groups=when(page) {

        StudioPage.VISUAL->listOf(

            AuroraText.t("Leitura e bateria","Legibility and battery","Lectura y baterÃ­a") to listOf("adaptiveContrast","safeGeometry","batterySource"),

            AuroraText.t("AparÃªncia da ilha","Island appearance","Apariencia de la isla") to listOf("privacyOrigins","threeLevels","landscapeLayout"))

        StudioPage.EFFECTS->listOf(

            AuroraText.t("ConfirmaÃ§Ãµes e energia","Confirmations and charging","Confirmaciones y energÃ­a"),

            AuroraText.t("Gestos da ilha","Island gestures","Gestos de la isla"),

            AuroraText.t("Atividades em primeiro plano","Foreground activities","Actividades en primer plano"),

            AuroraText.t("Inspecionar animaÃ§Ãµes","Inspect animations","Inspeccionar animaciones")

        ).zip(AuroraEffectGroups)

        else->emptyList()

    }

    groups.forEach { (title,keys)->

        val visible=keys.filter {auroraInTopic(it,LocalSettingsTopic.current) && (it!="timeline"||LocalSettingsAdvanced.current) && (search(title)||search(AuroraText.title(it),AuroraText.description(it)))}

        if(visible.isNotEmpty())StudioCard {

            SectionTitle(title)

            visible.forEachIndexed {index,key->

                if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)

                SettingSwitch(AuroraText.title(key),AuroraText.description(key),key in options.aurora.enabled,settings.enabled) {value->

                    onUpdate(options.copy(aurora=options.aurora.toggle(key,value)))

                }

            }

            if(keys.any {it in setOf("privacyOrigins","threeLevels","landscapeLayout","touchResponse","interruptibleIsland","fingerExpansion","contextActions","openSource")}&&!options.island)

                Text(AuroraText.t("Para usar estes recursos, ative Resumo expansÃ­vel em Efeitos.","To use these features, enable Expandable summary in Effects.","Para usar estas funciones, activa Resumen expandible en Efectos."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)

            if("timer" in visible&&options.aurora.timer)AuroraTimerControls(settings.enabled,options.aurora) {onUpdate(options.copy(aurora=it))}

        }

    }

    if(LocalSettingsTopic.current in setOf("","movement")&&LocalSettingsAdvanced.current&&page==StudioPage.EFFECTS&&options.aurora.timeline&&search(AuroraText.title("timeline"),AuroraText.description("timeline"))) {

        StudioCard {AuroraTimelinePreview(settings,options)}

    }

}



@Composable private fun AuroraTimerControls(enabled:Boolean,options:AuroraOptions,onUpdate:(AuroraOptions)->Unit) {

    val context=LocalContext.current

    Text(AuroraText.t("Acompanha o temporizador e o cronômetro do Relógio pelo acesso às notificações. A contagem só aparece quando o Relógio publica os dados de cronômetro do Android; notificações personalizadas podem não ser compatíveis.", "Follows your Clock timer and stopwatch through notification access. The count appears only when Clock publishes Android chronometer data; custom notifications may not be compatible.", "Sigue el temporizador y cronómetro de Reloj mediante el acceso a notificaciones. Solo aparece cuando Reloj publica datos de cronómetro de Android; las notificaciones personalizadas pueden no ser compatibles."),style=MaterialTheme.typography.bodySmall)

    OutlinedButton(enabled=enabled,onClick={runCatching {context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}) {

        Text(AuroraText.t("Abrir Relógio", "Open Clock", "Abrir Reloj"))

    }

}


internal fun auroraInTopic(key:String,topic:String)=when(topic) {
    "unlock"->key in setOf("unlockGlow","unlockBuild")
    "energy"->key=="chargeStyles"
    "media"->key in setOf("shutter","timer","voiceCall","taskComplete")
    "indicators"->key in setOf("privacyPair","headphoneConfirm","singleNotices")
    "island"->key in setOf("touchResponse","interruptibleIsland","fingerExpansion","contextActions","openSource")
    "movement"->key=="timeline"
    "ring"->key in setOf("adaptiveContrast","safeGeometry")
    "colors"->key=="batterySource"
    "island-look"->key in setOf("privacyOrigins","threeLevels","landscapeLayout")
    "percentage","position"->false
    else->true
}
