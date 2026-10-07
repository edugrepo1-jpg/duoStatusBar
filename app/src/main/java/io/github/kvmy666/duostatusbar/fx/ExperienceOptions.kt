package io.github.kvmy666.duostatusbar.fx

import io.github.kvmy666.duostatusbar.i18n.UiText
import org.json.JSONObject

/** One optional, versioned settings column; missing or malformed values preserve the working bar. */
internal data class ExperienceOptions(
    val music: Boolean=false, val albumColors: Boolean=false, val chargeEstimate: Boolean=false,
    val screenshot: Boolean=false, val volume: Boolean=false, val recordingTime: Boolean=false,
    val island: Boolean=false, val drawIcons: Boolean=false,
    val dwellMs: Int=3000, val exitMs: Int=120, val entryMs: Int=160,
    val compass:Boolean=false, val iconPercent:Int=100,
    val iconSeconds:Map<String,Int> = emptyMap(), val networkOnly:Boolean=false, val fadeEnabled:Boolean=true, val language:String="pt-BR",
    val universalTiming:Boolean=true,
    val customBatteryColors:Boolean=false,
    val batteryLow:Int=0xFFFF453A.toInt(),val batteryMid:Int=0xFFFFCC00.toInt(),val batteryHigh:Int=0xFF30D158.toInt()
) {
    fun encode(): String = JSONObject().apply {
        put("v",4);put("language",language.takeIf { it in listOf("pt-BR","en","es") } ?: "pt-BR")
        put("music",music);put("album",albumColors);put("charge",chargeEstimate)
        put("shot",screenshot);put("volume",volume);put("record",recordingTime)
        put("island",island);put("draw",drawIcons)
        put("dwell",dwellMs.coerceIn(1000,60000));put("exit",exitMs.coerceIn(60,600));put("entry",entryMs.coerceIn(60,800))
        put("times",JSONObject(iconSeconds.filterKeys { key -> SlotIcon.entries.any { it.name==key } }.mapValues { it.value.coerceIn(1,60) }))
        put("networkOnly",networkOnly);put("fade",fadeEnabled);put("universal",universalTiming)
        put("compass",compass);put("iconSize",iconPercent.coerceIn(60,200))
        put("batteryColors",customBatteryColors);put("batteryLow",batteryLow or 0xFF000000.toInt());put("batteryMid",batteryMid or 0xFF000000.toInt());put("batteryHigh",batteryHigh or 0xFF000000.toInt())
    }.toString()
    companion object {
        val ALL=ExperienceOptions(true,true,true,true,true,true,true,true,compass=true)
        fun decode(raw: String): ExperienceOptions = try {
            val j=JSONObject(raw)
            ExperienceOptions(j.optBoolean("music"),j.optBoolean("album"),j.optBoolean("charge"),
                j.optBoolean("shot"),j.optBoolean("volume"),j.optBoolean("record"),j.optBoolean("island"),j.optBoolean("draw"),
                j.optInt("dwell",3000).coerceIn(1000,60000),j.optInt("exit",120).coerceIn(60,600),j.optInt("entry",160).coerceIn(60,800),
                j.optBoolean("compass"),j.optInt("iconSize",100).coerceIn(60,200),
                SlotIcon.entries.mapNotNull { icon -> j.optJSONObject("times")?.let { times ->
                    if(times.has(icon.name)) icon.name to times.optInt(icon.name,3).coerceIn(1,60) else null
                } }.toMap(),j.optBoolean("networkOnly"),j.optBoolean("fade",true),j.optString("language","pt-BR").takeIf { it in listOf("pt-BR","en","es") } ?: "pt-BR",
                j.optBoolean("universal",(j.optJSONObject("times")?.length() ?: 0)==0),
                j.optBoolean("batteryColors"),j.optInt("batteryLow",0xFFFF453A.toInt()) or 0xFF000000.toInt(),
                j.optInt("batteryMid",0xFFFFCC00.toInt()) or 0xFF000000.toInt(),j.optInt("batteryHigh",0xFF30D158.toInt()) or 0xFF000000.toInt())
        } catch (_:Exception) { ExperienceOptions() }
    }
}

internal data class PlaybackSnapshot(
    val playing:Boolean=false, val positionMs:Long=-1, val durationMs:Long=0,
    val updatedAt:Long=0, val speed:Float=1f, val color:Int=0, val title:String="",
    val sourcePackage:String=""
) {
    fun progress(now:Long):Float = if(positionMs<0||durationMs<=0) -1f else
        ((positionMs + if(playing) ((now-updatedAt).coerceAtLeast(0)*speed).toLong() else 0).toDouble()/durationMs).toFloat().coerceIn(0f,1f)
}

/** Requires observed percentage gains; no fabricated countdown while gathering samples. */
internal class ChargeEstimator {
    private var startLevel=-1
    private var startTime=0L
    private var lastLevel=-1
    private var lastTime=0L
    fun observe(level:Int,charging:Boolean,now:Long) {
        if(!charging||level !in 0..100) { startLevel=-1;lastLevel=-1;return }
        if(startLevel<0||level<lastLevel||now<lastTime) { startLevel=level;startTime=now }
        lastLevel=level;lastTime=now
    }
    fun remaining(now:Long,systemMs:Long=-1):Long {
        if(lastLevel==100)return 0
        if(startLevel<0)return -1
        if(systemMs>0)return systemMs.coerceAtMost(48*3600000L)
        val gained=lastLevel-startLevel
        if(gained<2||lastTime-startTime<60000)return -1
        return (((100-lastLevel).toDouble()*(lastTime-startTime)/gained).toLong()-(now-lastTime).coerceAtLeast(0))
            .coerceIn(60000,48*3600000L)
    }
}

internal fun durationLabel(ms:Long):String {
    val seconds=(ms.coerceAtLeast(0)/1000)
    return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
    else "%d:%02d".format(seconds/60,seconds%60)
}
internal fun estimateLabel(ms:Long):String {
    if(ms<0)return UiText.t("Calculando")
    if(ms==0L)return UiText.t("Completa")
    val minutes=((ms+59999)/60000).coerceAtLeast(1)
    return if(minutes>=60) "~${minutes/60}h${(minutes%60).toString().padStart(2,'0')}" else "~${minutes}min"
}

internal fun iconLabel(icon:SlotIcon):String=when(icon) {
    SlotIcon.WIFI->"Wi-Fi";SlotIcon.NETWORK->UiText.t("Rede móvel");SlotIcon.AIRPLANE->UiText.t("Modo Avião");SlotIcon.DND->UiText.t("Não Perturbe")
    SlotIcon.BLUETOOTH->"Bluetooth";SlotIcon.NFC->"NFC";SlotIcon.SHARE->"Hotspot";SlotIcon.AIRPODS->UiText.t("Fones")
    SlotIcon.BOLT->UiText.t("Carregando");SlotIcon.CAMERA->UiText.t("Câmera");SlotIcon.MICROPHONE->UiText.t("Microfone");SlotIcon.ALARM->UiText.t("Alarme")
    SlotIcon.VPN->"VPN";SlotIcon.LOCATION->UiText.t("Localização");SlotIcon.SILENT->UiText.t("Silencioso");SlotIcon.VIBRATE->UiText.t("Vibração")
    SlotIcon.MEDIA->UiText.t("Música");SlotIcon.WIRELESS->UiText.t("Carga sem fio");SlotIcon.TORCH->UiText.t("Lanterna");SlotIcon.RECORD->UiText.t("Gravação de tela")
    SlotIcon.WIFI_OFFLINE->UiText.t("Wi-Fi sem internet");SlotIcon.CHARGE_TIME->UiText.t("Previsão de carga");SlotIcon.RECORD_TIME->UiText.t("Tempo de gravação")
    SlotIcon.VOLUME->UiText.t("Volume");SlotIcon.SCREENSHOT->UiText.t("Captura de tela");SlotIcon.NOTIFICATION->UiText.t("Notificação")
}
