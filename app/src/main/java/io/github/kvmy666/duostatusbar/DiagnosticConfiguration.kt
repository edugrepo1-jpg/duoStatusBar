package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoSettings

/** Re-encode the known preference schema. Unknown imported JSON/text never enters technical logs. */
internal object DiagnosticConfiguration {
    fun render(settings:DuoSettings):String {
        val actions=DuoActions.ALL.map {it.first}.toSet()
        fun action(value:String)=value.takeIf {it in actions} ?: "unknown"
        return settings.copy(
            tapAction=action(settings.tapAction),doubleTapAction=action(settings.doubleTapAction),longPressAction=action(settings.longPressAction),
            iconColor=settings.iconColor.takeIf {it in setOf("auto","black","white")} ?: "unknown",
            simChoice=settings.simChoice.takeIf {it in setOf("auto","sim1","sim2")} ?: "unknown",
            dndMode=settings.dndMode.takeIf {it in setOf("middle","dots","off")} ?: "unknown",
            experienceJson=ExperienceOptions.decode(settings.experienceJson).encode()
        ).toString()
    }
}
