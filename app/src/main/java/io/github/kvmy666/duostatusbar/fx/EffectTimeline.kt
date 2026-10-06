package io.github.kvmy666.duostatusbar.fx

import kotlin.math.cos
import kotlin.math.PI

/** Pure timing rules: all input times are monotonic milliseconds, never wall-clock time. */
internal object EffectTimeline {
    const val CHECK_MS = 3000L
    const val UNLOCK_MS = CHECK_MS + 160L
    const val CHECK_FADE_OUT_AT = CHECK_MS - 280L
    const val AUDIO_MS = 4000L
    const val CHARGE_MS = 3000L
    const val PULSE_MS = 1800L
    fun age(now: Long, began: Long, duration: Long, allowed: Boolean) =
        (now - began).takeIf { allowed && it in 0 until duration } ?: -1L
    fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    fun check(t: Long): Float = if (t !in 0 until UNLOCK_MS) 0f else
        smooth((t - 120) / 160f) * (1f - smooth((t - CHECK_FADE_OUT_AT) / 280f))
    fun checkNormal(t: Long): Float = when {
        t !in 0 until UNLOCK_MS -> 1f
        t < 120 -> 1f - smooth(t / 120f)
        t < CHECK_MS -> 0f
        else -> smooth((t - CHECK_MS) / 160f)
    }
    fun audio(t: Long): Float = if (t !in 0 until AUDIO_MS) 0f else
        smooth((t - 120) / 160f) * (1f - smooth((t - 3720) / 120f))
    fun audioNormal(t: Long): Float = when {
        t !in 0 until AUDIO_MS -> 1f
        t < 120 -> 1f - smooth(t / 120f)
        t < 3840 -> 0f
        else -> smooth((t - 3840) / 160f)
    }
    fun unlockColor(t: Long): Float = if (t !in 0 until 400L) 0f else
        smooth(t / 80f) * (1f - smooth((t - 300) / 100f))
    fun pulse(t: Long): Float = if (t !in 0 until PULSE_MS) 0f else
        (0.9 * (0.5 - 0.5 * cos(2 * PI * t / 600.0))).toFloat()
    fun criticalCrossing(old: Int, level: Int, charging: Boolean, enabled: Boolean): Int = when {
        !enabled || charging || level !in 0..100 || old !in 0..100 -> 0
        old > 5 && level <= 5 -> 2
        old > 10 && level <= 10 -> 1
        else -> 0
    }
    fun criticalWake(level: Int, charging: Boolean, enabled: Boolean, elapsed: Long): Int = when {
        !enabled || charging || level !in 0..10 || elapsed < 60000L -> 0
        level <= 5 -> 2
        else -> 1
    }
    fun fastCharge(currentMicroamps: Int, voltageMillivolts: Int): Boolean =
        currentMicroamps != Int.MIN_VALUE && currentMicroamps > 0 && voltageMillivolts > 0 &&
            currentMicroamps.toDouble() * voltageMillivolts / 1_000_000_000.0 >= 15.0
    fun spring(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        return (1.0 - kotlin.math.exp(-7.0 * t) * cos(9.0 * t)).toFloat()
    }
}

internal enum class SlotIcon { WIFI, AIRPLANE, BLUETOOTH, SHARE, NFC, NETWORK, DND, AIRPODS, BOLT, CAMERA, MICROPHONE, ALARM, VPN, LOCATION, SILENT, VIBRATE, MEDIA, WIRELESS, TORCH, RECORD, WIFI_OFFLINE }
internal data class SlotFrame(val icon: SlotIcon?, val opacity: Float = 1f, val scale: Float = 1f)

/** Exit and entry are disjoint; one occupant never costs a periodic callback. */
internal class SlotCycle {
    private var items=emptyList<SlotIcon>()
    private var began=0L
    private var pausedAt:Long?=null
    private val swap=SequentialSwap()
    private var initialized=false
    val size get()=items.size
    fun update(next:List<SlotIcon>,now:Long) {
        val unique=next.distinct()
        if(items==unique)return
        if(!initialized){initialized=true;items=unique;began=now;swap.update(items.firstOrNull()?.ordinal ?: -1,now,false);return}
        val before=frame(now).icon
        val phase=((pausedAt ?: now)-began).coerceAtLeast(0)%3000L
        val urgent=unique.firstOrNull { it !in items && it in listOf(SlotIcon.CAMERA,SlotIcon.MICROPHONE,SlotIcon.RECORD) }
        items=unique
        val chosen=urgent ?: before?.takeIf { it in items } ?: items.firstOrNull()
        val index=items.indexOf(chosen).coerceAtLeast(0)
        // Retain elapsed dwell time when the active set changes; polling must not starve later icons.
        began=(pausedAt ?: now)-index*3000L-if(urgent!=null)0L else phase
        swap.update(chosen?.ordinal ?: -1,now,true)
    }
    fun pause(now:Long){if(pausedAt==null)pausedAt=now}
    fun resume(now:Long){pausedAt?.let { began+=now-it;swap.shiftTime(now-it) };pausedAt=null}
    fun preferWifi(now:Long){
        val preferred=items.firstOrNull { it==SlotIcon.WIFI || it==SlotIcon.WIFI_OFFLINE } ?: return
        began=now-items.indexOf(preferred)*3000L;swap.update(preferred.ordinal,now,false)
    }
    fun frame(now:Long):SlotFrame {
        val time=pausedAt ?: now
        val elapsed=(time-began).coerceAtLeast(0)
        val index=if(items.isEmpty())0 else ((elapsed/3000L + if(elapsed%3000L>=2880L&&items.size>1)1 else 0)%items.size).toInt()
        val transitionAt=if(items.size>1)began+(elapsed/3000L)*3000L+if(elapsed%3000L>=2880L)2880L else -120L else time
        swap.update(items.getOrNull(index)?.ordinal ?: -1,transitionAt,time>=began)
        val frame=swap.frame(time)
        return SlotFrame(SlotIcon.entries.getOrNull(frame.key),frame.opacity,.8f+.2f*frame.opacity)
    }
    fun nextDelay(now:Long):Long {
        if(pausedAt!=null)return Long.MAX_VALUE
        if(swap.moving(now))return 16L
        if(items.size<=1)return Long.MAX_VALUE
        val phase=(now-began).coerceAtLeast(0)%3000L
        return if(phase>=2880L)16L else 2880L-phase
    }
}

internal data class EffectFrame(
    val checkMs: Long = -1,
    val pulseMs: Long = -1,
    val pulseRed: Boolean = false,
    val chargeMs: Long = -1,
    val audioMs: Long = -1,
    val slot: SlotFrame = SlotFrame(null),
    val hideCells: Boolean = false,
    val criticalDot: Boolean = false,
    val glass: Boolean = false,
    val spring: Boolean = false,
    val motionMs: Long = 0,
    val headphoneBattery: Int = -1,
    val networkText: String = "",
    val managedSlots: Boolean = false,
    val charging: Boolean = false
)

internal data class SwapFrame(val key: Int, val opacity: Float)
/** A single visible occupant; interruptions finish its exit before the newest requested key enters. */
internal class SequentialSwap {
    private var current = Int.MIN_VALUE
    private var next = Int.MIN_VALUE
    private var began = 0L
    private var fromOpacity = 1f
    private var switching = false
    fun update(key: Int, now: Long, animate: Boolean) {
        if (current == Int.MIN_VALUE || !animate) { current=key; next=key; switching=false; return }
        val pose=frame(now)
        if (next == key) return
        current=pose.key; next=key; fromOpacity=pose.opacity; began=now; switching=true
    }
    fun frame(now: Long): SwapFrame {
        if (!switching) return SwapFrame(current,1f)
        val elapsed=(now-began).coerceAtLeast(0)
        if (elapsed < 120) return SwapFrame(current,fromOpacity * (1f-EffectTimeline.smooth(elapsed/120f)))
        if (elapsed < 280) return SwapFrame(next,EffectTimeline.smooth((elapsed-120)/160f))
        current=next; switching=false
        return SwapFrame(current,1f)
    }
    fun moving(now: Long): Boolean { frame(now); return switching }
    fun shiftTime(delta:Long){began+=delta}
}
