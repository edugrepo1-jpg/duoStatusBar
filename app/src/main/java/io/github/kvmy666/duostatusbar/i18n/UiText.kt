package io.github.kvmy666.duostatusbar.i18n

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** App UI and module-owned Canvas labels share the same local translation catalogue. */
internal object UiText {
    @Volatile var language="pt-BR"
    private val catalogs=ConcurrentHashMap<String,Map<String,String>>()
    fun initialize(context:Context,tag:String) {
        language=tag
        if(catalogs.isNotEmpty())return
        val own=if(context.packageName=="io.github.RECREATE.statusbar")context else
            runCatching { context.createPackageContext("io.github.RECREATE.statusbar",Context.CONTEXT_IGNORE_SECURITY) }.getOrDefault(context)
        for(code in listOf("pt-BR","en","es"))runCatching {
            val json=JSONObject(own.assets.open("translations/$code.json").bufferedReader().use { it.readText() })
            catalogs[code]=json.keys().asSequence().associateWith { json.getString(it) }
        }
    }
    fun t(key:String):String=catalogs[language]?.get(key) ?: key
    fun format(key:String,vararg values:Any?):String {
        var result=t(key)
        values.forEachIndexed { index,value -> result=result.replace("{$index}",value?.toString().orEmpty()) }
        return result
    }
}
