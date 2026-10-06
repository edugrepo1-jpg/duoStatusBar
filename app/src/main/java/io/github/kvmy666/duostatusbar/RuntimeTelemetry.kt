package io.github.kvmy666.duostatusbar

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLongArray

/** Fixed-size, process-local evidence. No polling, user content, package inventory or per-frame logs. */
internal enum class TelemetryCounter {
    CANVAS_DRAW, CANVAS_DRAW_NS, CANVAS_FAILURE, EFFECT_TICK, INDICATOR_TICK,
    INVENTORY_QUERY, PROJECTION_QUERY, CACHE_HIT, SENSOR_SAMPLE, SENSOR_START,
    SENSOR_STOP, CONTROLLER_START, CONTROLLER_STOP, DIAGNOSTIC_EXPORT
}

internal enum class TelemetryFeature {
    WIFI, WIFI_OFFLINE, NETWORK, AIRPLANE, DND, BLUETOOTH, NFC, SHARE, AIRPODS,
    BOLT, CAMERA, MICROPHONE, ALARM, VPN, LOCATION, SILENT, VIBRATE, MEDIA,
    WIRELESS, TORCH, RECORD, CHARGE_TIME, RECORD_TIME, VOLUME, SCREENSHOT,
    NOTIFICATION, UNLOCK_CHECK, CHARGE_EFFECT, CRITICAL_PULSE, FADE, DRAW_ICONS,
    COMPASS, ISLAND, POCKET, GLASS, SPRING, ALBUM_COLORS
}

internal class TelemetryLedger(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val counts = AtomicLongArray(TelemetryCounter.entries.size)
    private val lock = Any()
    private data class Feature(var enabled: Boolean? = null, var detected: Boolean? = null,
        var probeOk: Boolean? = null, var transitions: Long = 0, var executions: Long = 0,
        var failures: Long = 0, var lastEvidenceMs: Long = -1)
    private val features = Array(TelemetryFeature.entries.size) { Feature() }
    private val began = clock()
    private var batteryFirst: BatteryReading? = null
    private var batteryLast: BatteryReading? = null
    private var batterySamples = 0L
    private var batterySegments = 0L
    private var controllerRunning = false
    private var screen = false
    private var pocket = false
    private var lifecycleAt = began
    private var visibleMs = 0L
    private var pausedMs = 0L
    private var stoppedMs = 0L

    internal data class BatteryReading(val at: Long, val level: Int?, val charging: Boolean?,
        val temperatureDeciC: Int?, val voltageMv: Int?, val powerSave: Boolean?)

    fun increment(counter: TelemetryCounter, amount: Long = 1) {
        if (amount > 0) counts.addAndGet(counter.ordinal, amount)
    }
    fun count(counter: TelemetryCounter): Long = counts.get(counter.ordinal)
    fun configure(feature: TelemetryFeature, enabled: Boolean) = synchronized(lock) {
        val state = features[feature.ordinal]
        if (state.enabled != enabled) state.transitions++
        state.enabled = enabled
    }
    fun detect(feature: TelemetryFeature, active: Boolean) = synchronized(lock) {
        val state = features[feature.ordinal]
        if (state.detected != active) state.transitions++
        state.detected = active
        state.lastEvidenceMs = clock()
    }
    /** Probe success only means its reader ran. It does not prove the icon was shown. */
    fun probe(feature: TelemetryFeature, success: Boolean) = synchronized(lock) {
        val state = features[feature.ordinal]
        state.probeOk = success
        if (!success) state.failures++
        state.lastEvidenceMs = clock()
    }
    /** Record a successful visible draw/event once per transition, never a success per animation tick. */
    fun executed(feature: TelemetryFeature) = synchronized(lock) {
        features[feature.ordinal].also { it.executions++; it.lastEvidenceMs = clock() }
        Unit
    }
    fun lifecycle(running: Boolean, screenOn: Boolean, inPocket: Boolean) = synchronized(lock) {
        if(controllerRunning == running && screen == screenOn && pocket == inPocket)return@synchronized
        accumulate(clock())
        controllerRunning = running; screen = screenOn; pocket = inPocket
    }
    private fun accumulate(now: Long) {
        val elapsed = (now - lifecycleAt).coerceAtLeast(0)
        when { !controllerRunning -> stoppedMs += elapsed; screen && !pocket -> visibleMs += elapsed; else -> pausedMs += elapsed }
        lifecycleAt = now
    }
    /** Broadcast samples cost no timer/Binder call. A plug-state or level reversal starts a new segment. */
    fun battery(sample: BatteryReading) = synchronized(lock) {
        val last = batteryLast
        if (last != null && sample.at - last.at < 60_000 && sample.charging == last.charging) return@synchronized
        if (last == null || sample.charging != last.charging || sample.at < last.at ||
            (sample.level != null && last.level != null &&
                ((sample.charging == false && sample.level > last.level) ||
                    (sample.charging == true && sample.level < last.level)))) {
            batteryFirst = sample; batterySegments++
        }
        batteryLast = sample; batterySamples++
    }
    fun report(): String = synchronized(lock) {
        val now = clock(); accumulate(now)
        buildString {
            appendLine(MARKER)
            appendLine("schema=1 scope=current process since start; elapsedMs=${(now-began).coerceAtLeast(0)}")
            appendLine("evidence=enabled is configuration; detected is observed state; reader_ok is access; executions are successful draws/events, not hardware certification")
            appendLine("health=unverified when no observation; failure is explicit reader/render failure; inactive is not a failure")
            appendLine("lifecycle visibleMs=$visibleMs pausedMs=$pausedMs stoppedMs=$stoppedMs running=$controllerRunning screen=$screen pocket=$pocket")
            TelemetryCounter.entries.forEach { appendLine("counter.${it.name.lowercase()}=${counts.get(it.ordinal)}") }
            val draws = counts.get(TelemetryCounter.CANVAS_DRAW.ordinal)
            appendLine("drawWallTimeMs=${counts.get(TelemetryCounter.CANVAS_DRAW_NS.ordinal)/1_000_000} draws=$draws (elapsed inside our Canvas callback, not CPU or energy attribution)")
            val first = batteryFirst; val last = batteryLast
            appendLine("battery.scope=whole device observation; module-specific mAh/CPU attribution=unavailable without profiler; no claim that device drain was caused by module")
            appendLine("battery.samples=$batterySamples segments=$batterySegments sampleSource=ACTION_BATTERY_CHANGED throttleMs=60000")
            if (last == null) appendLine("battery.status=unverified (no broadcast sample)")
            else {
                appendLine("battery.level=${last.level ?: "unknown"} charging=${last.charging ?: "unknown"} temperatureDeciC=${last.temperatureDeciC ?: "unknown"} voltageMv=${last.voltageMv ?: "unknown"} powerSave=${last.powerSave ?: "unknown"} sampleAgeMs=${(now-last.at).coerceAtLeast(0)}")
                val interval = if(first==null)0 else last.at-first.at
                val delta = if(first?.level==null || last.level==null)null else last.level-first.level
                appendLine("battery.segmentMs=$interval battery.segmentDeltaPercent=${delta ?: "unknown"}")
                // Coarse percentages and short captures cannot support a meaningful drain estimate.
                if(last.charging == false && interval >= 600_000 && delta != null && delta <= 0)
                    appendLine("battery.observedDeviceDrainPercentPerHour=${(-delta*3_600_000.0/interval)} precision=coarse integer percent")
                else appendLine("battery.observedDeviceDrainPercentPerHour=unverified (needs >=10min same unplugged segment)")
            }
            TelemetryFeature.entries.forEach { feature ->
                val f = features[feature.ordinal]
                val health = when { f.probeOk == false -> "failed_reader"; f.enabled == false -> "disabled"; f.detected == false && f.probeOk == true -> "inactive"; f.executions > 0 && f.detected != false -> "execution_observed"; else -> "unverified" }
                appendLine("feature.${feature.name.lowercase()} enabled=${f.enabled ?: "unknown"} detected=${f.detected ?: "unknown"} reader_ok=${f.probeOk ?: "unknown"} health=$health executions=${f.executions} failures=${f.failures} configOrStateChanges=${f.transitions} lastEvidenceAgeMs=${if(f.lastEvidenceMs<0)"unknown" else (now-f.lastEvidenceMs).coerceAtLeast(0)}")
            }
            appendLine(END_MARKER)
        }
    }
    companion object {
        const val MARKER = "=== rootless runtime evidence ==="
        const val END_MARKER = "=== end runtime evidence ==="
        fun strip(text: String): String {
            val start = text.indexOf(MARKER)
            if(start<0)return text
            val end = text.indexOf(END_MARKER, start)
            return if(end<0)text.substring(0,start) else text.removeRange(start,(end+END_MARKER.length).coerceAtMost(text.length)).trimStart('\r','\n')
        }
    }
}

internal object RuntimeTelemetry {
    private val ledger = TelemetryLedger()
    fun increment(counter: TelemetryCounter, amount: Long = 1) = ledger.increment(counter, amount)
    fun configure(feature: TelemetryFeature, enabled: Boolean) = ledger.configure(feature, enabled)
    fun detect(feature: TelemetryFeature, active: Boolean) = ledger.detect(feature, active)
    fun probe(feature: TelemetryFeature, success: Boolean) = ledger.probe(feature, success)
    fun executed(feature: TelemetryFeature) = ledger.executed(feature)
    fun lifecycle(running: Boolean, screen: Boolean, pocket: Boolean) = ledger.lifecycle(running,screen,pocket)
    fun battery(sample: TelemetryLedger.BatteryReading) = ledger.battery(sample)
    fun report() = ledger.report()
    fun decorate(raw: String): String = report()+"\n"+TelemetryLedger.strip(raw)
}
