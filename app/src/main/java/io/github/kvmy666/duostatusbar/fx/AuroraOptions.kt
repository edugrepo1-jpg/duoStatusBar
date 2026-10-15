package io.github.kvmy666.duostatusbar.fx

import org.json.JSONArray
import org.json.JSONObject

/** Additive options. Existing profiles and old pixels remain intact until explicitly enabled. */
internal data class AuroraOptions(val enabled:Set<String> = emptySet(),val timerMinutes:Int=5) {
    val unlockGlow get()="unlockGlow" in enabled
    val unlockBuild get()="unlockBuild" in enabled
    val privacyPair get()="privacyPair" in enabled
    val privacyOrigins get()="privacyOrigins" in enabled
    val headphoneConfirm get()="headphoneConfirm" in enabled
    val shutter get()="shutter" in enabled
    val singleNotices get()="singleNotices" in enabled
    val batterySource get()="batterySource" in enabled
    val chargeStyles get()="chargeStyles" in enabled
    val touchResponse get()="touchResponse" in enabled
    val interruptibleIsland get()="interruptibleIsland" in enabled
    val adaptiveContrast get()="adaptiveContrast" in enabled
    val safeGeometry get()="safeGeometry" in enabled
    val fingerExpansion get()="fingerExpansion" in enabled
    val threeLevels get()="threeLevels" in enabled
    val landscapeLayout get()="landscapeLayout" in enabled
    val contextActions get()="contextActions" in enabled
    val openSource get()="openSource" in enabled
    val timer get()="timer" in enabled
    val voiceCall get()="voiceCall" in enabled
    val taskComplete get()="taskComplete" in enabled
    val timeline get()="timeline" in enabled
    fun toggle(key:String,value:Boolean)=copy(enabled=if(value)enabled+key else enabled-key)
    fun encode()=JSONObject().put("v",1).put("enabled",JSONArray(enabled.filter {it in KEYS})).put("timerMinutes",timerMinutes.coerceIn(1,60))
    companion object {
        val KEYS=setOf("unlockGlow","unlockBuild","privacyPair","privacyOrigins","headphoneConfirm","shutter","singleNotices","batterySource","chargeStyles","touchResponse","interruptibleIsland","adaptiveContrast","safeGeometry","fingerExpansion","threeLevels","landscapeLayout","contextActions","openSource","timer","voiceCall","taskComplete","timeline")
        val ALL=AuroraOptions(KEYS)
        fun decode(j:JSONObject?):AuroraOptions {
            if(j==null)return AuroraOptions()
            val values=j.optJSONArray("enabled") ?: JSONArray()
            return AuroraOptions((0 until minOf(values.length(),KEYS.size)).map {values.optString(it)}.filter {it in KEYS}.toSet(),j.optInt("timerMinutes",5).coerceIn(1,60))
        }
    }
}

internal object AuroraMotion {
    fun build(age:Long,offset:Long=0,duration:Long=400)=if(age<0)1f else EffectTimeline.smooth((age-offset).toFloat()/duration)
    fun glow(age:Long)=if(age !in 0..280)0f else EffectTimeline.smooth(age/65f)*(1f-EffectTimeline.smooth((age-150)/130f))
    fun latestNotice(previous:Long,now:Long)=previous<0||now-previous>=350L
}
