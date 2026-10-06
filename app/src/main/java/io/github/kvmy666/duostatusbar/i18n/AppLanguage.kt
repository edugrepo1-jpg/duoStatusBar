package io.github.kvmy666.duostatusbar.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.LocaleList
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.settings.*

internal object AppLanguage {
    val supported=listOf("pt-BR","en","es")
    private fun prefs(context:Context)=context.getSharedPreferences("duo-language",Context.MODE_PRIVATE)
    fun selected(context:Context)=prefs(context).getString("tag","pt-BR").orEmpty().takeIf { it in supported } ?: "pt-BR"
    fun chosen(context:Context)=prefs(context).getBoolean("chosen",false)
    fun localized(base:Context):Context=base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(selected(base))) })
    fun choose(context:Context,tag:String) {
        if(tag !in supported)return
        prefs(context).edit().putString("tag",tag).putBoolean("chosen",true).commit()
        UiText.initialize(context,tag)
        for(orientation in listOf(DuoOrientation.PORTRAIT,DuoOrientation.LANDSCAPE)) {
            val settings=DuoPrefs.read(context,orientation)
            val options=ExperienceOptions.decode(settings.experienceJson).copy(language=tag)
            DuoPrefs.write(context,settings.copy(experienceJson=options.encode()),orientation)
        }
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
        SettingsBridge.push(context)
        context.getSystemService(LocaleManager::class.java)?.applicationLocales=LocaleList.forLanguageTags(tag)
    }
}
