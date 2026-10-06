package io.github.kvmy666.duostatusbar.fx

import org.json.JSONObject

/** One optional, versioned settings column; missing or malformed values preserve the working bar. */
internal data class ExperienceOptions(
    val music: Boolean=false, val albumColors: Boolean=false, val chargeEstimate: Boolean=false,
    val screenshot: Boolean=false, val volume: Boolean=false, val recordingTime: Boolean=false,
    val island: Boolean=false, val drawIcons: Boolean=false,
    val dwellMs: Int=3000, val exitMs: Int=120, val entryMs: Int=160,
    val compass:Boolean=false, val iconPercent:Int=100
) {
    fun encode(): String = JSONObject().apply {
        put("v",1)
        put("music",music);put("album",albumColors);put("charge",chargeEstimate)
        put("shot",screenshot);put("volume",volume);put("record",recordingTime)
        put("island",island);put("draw",drawIcons)
        put("dwell",dwellMs.coerceIn(1200,10000));put("exit",exitMs.coerceIn(60,600));put("entry",entryMs.coerceIn(60,800))
        put("compass",compass);put("iconSize",iconPercent.coerceIn(60,200))
    }.toString()
    companion object {
        val ALL=ExperienceOptions(true,true,true,true,true,true,true,true,compass=true)
        fun decode(raw: String): ExperienceOptions = try {
            val j=JSONObject(raw)
            ExperienceOptions(j.optBoolean("music"),j.optBoolean("album"),j.optBoolean("charge"),
                j.optBoolean("shot"),j.optBoolean("volume"),j.optBoolean("record"),j.optBoolean("island"),j.optBoolean("draw"),
                j.optInt("dwell",3000).coerceIn(1200,10000),j.optInt("exit",120).coerceIn(60,600),j.optInt("entry",160).coerceIn(60,800),
                j.optBoolean("compass"),j.optInt("iconSize",100).coerceIn(60,200))
        } catch (_:Exception) { ExperienceOptions() }
    }
}

internal data class PlaybackSnapshot(
    val playing:Boolean=false, val positionMs:Long=-1, val durationMs:Long=0,
    val updatedAt:Long=0, val speed:Float=1f, val color:Int=0, val title:String=""
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
    if(ms<0)return "Calculando"
    if(ms==0L)return "Completa"
    val minutes=((ms+59999)/60000).coerceAtLeast(1)
    return if(minutes>=60) "~${minutes/60}h${(minutes%60).toString().padStart(2,'0')}" else "~${minutes}min"
}

internal fun iconLabel(icon:SlotIcon):String=when(icon) {
    SlotIcon.WIFI->"Wi-Fi";SlotIcon.NETWORK->"Rede móvel";SlotIcon.AIRPLANE->"Modo Avião";SlotIcon.DND->"Não Perturbe"
    SlotIcon.BLUETOOTH->"Bluetooth";SlotIcon.NFC->"NFC";SlotIcon.SHARE->"Hotspot";SlotIcon.AIRPODS->"Fones"
    SlotIcon.BOLT->"Carregando";SlotIcon.CAMERA->"Câmera";SlotIcon.MICROPHONE->"Microfone";SlotIcon.ALARM->"Alarme"
    SlotIcon.VPN->"VPN";SlotIcon.LOCATION->"Localização";SlotIcon.SILENT->"Silencioso";SlotIcon.VIBRATE->"Vibração"
    SlotIcon.MEDIA->"Música";SlotIcon.WIRELESS->"Carga sem fio";SlotIcon.TORCH->"Lanterna";SlotIcon.RECORD->"Gravação de tela"
    SlotIcon.WIFI_OFFLINE->"Wi-Fi sem internet";SlotIcon.CHARGE_TIME->"Previsão de carga";SlotIcon.RECORD_TIME->"Tempo de gravação"
    SlotIcon.VOLUME->"Volume";SlotIcon.SCREENSHOT->"Captura de tela";SlotIcon.NOTIFICATION->"Notificação"
}
