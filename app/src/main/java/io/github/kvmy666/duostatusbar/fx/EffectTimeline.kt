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

internal enum class SlotIcon { WIFI, AIRPLANE, BLUETOOTH, SHARE, NFC, NETWORK, DND, AIRPODS, BOLT, CAMERA, MICROPHONE, ALARM, VPN, LOCATION, SILENT, VIBRATE, MEDIA, WIRELESS, TORCH, RECORD, WIFI_OFFLINE, CHARGE_TIME, RECORD_TIME, VOLUME, SCREENSHOT, NOTIFICATION }
internal data class SlotFrame(val icon: SlotIcon?, val opacity: Float = 1f, val scale: Float = 1f, val reveal: Float = 1f)

/** Exit and entry are disjoint; one occupant never costs a periodic callback. */
internal class SlotCycle {
    private var items=emptyList<SlotIcon>()
    private var began=0L
    private var pausedAt:Long?=null
    private val swap=SequentialSwap()
    private var initialized=false
    private var period=3000L
    private var durations=emptyMap<String,Int>()
    private var fades=true
    private fun span(icon:SlotIcon)=durations[icon.name]?.times(1000L) ?: period
    private fun prefix(index:Int)=items.take(index).sumOf { span(it) }
    private data class Position(val index:Int,val phase:Long,val start:Long)
    private fun position(time:Long):Position {
        if(items.isEmpty())return Position(0,0,time)
        val total=items.sumOf { span(it) };val elapsed=(time-began).coerceAtLeast(0)
        var phase=elapsed%total;var start=began+elapsed-phase
        for(index in items.indices){val length=span(items[index]);if(phase<length)return Position(index,phase,start);phase-=length;start+=length}
        return Position(0,0,start)
    }
    private var exit=120L
    private var entry=160L
    private var held:SlotIcon?=null
    private var heldAt=0L
    private var heldClock=false
    private var temporary:SlotIcon?=null
    private var temporaryAt=0L
    private var returnAt=Long.MIN_VALUE
    fun configure(options:ExperienceOptions,now:Long) {
        val pose=frame(now).icon;val phase=position(pausedAt ?: now).phase
        val next=options.dwellMs.coerceIn(1000,60000).toLong()
        val nextDurations=if(options.universalTiming) emptyMap() else options.iconSeconds
        val changed=next!=period || durations!=nextDurations || fades!=options.fadeEnabled
        period=next;durations=nextDurations;fades=options.fadeEnabled
        // Even a one-second interval must finish both fades before the next handoff.
        val shortest=minOf(period,durations.values.minOrNull()?.times(1000L) ?: period)
        val requestedExit=options.exitMs.coerceIn(60,600).toLong()
        val requestedEntry=options.entryMs.coerceIn(60,800).toLong()
        val ratio=minOf(1.0,(shortest-200).toDouble()/(requestedExit+requestedEntry))
        exit=(requestedExit*ratio).toLong().coerceAtLeast(60)
        entry=(requestedEntry*ratio).toLong().coerceAtLeast(60);swap.configure(exit,entry)
        if(changed&&items.isNotEmpty()) { val index=items.indexOf(pose).coerceAtLeast(0);began=(pausedAt ?: now)-prefix(index)-phase.coerceAtMost(span(items[index])-1) }
        if(!fades)swap.update(pose?.ordinal ?: -1,now,false)
    }
    fun transient(icon:SlotIcon?,now:Long) {
        if(icon==temporary)return
        frame(now);temporary=icon;temporaryAt=now;returnAt=now
    }
    /** Foreground ownership changes only on a real start/stop, never on a polling tick. */
    fun hold(icon:SlotIcon?,now:Long,recordingClock:Boolean=false) {
        heldClock=recordingClock&&icon==SlotIcon.RECORD
        if(icon==held)return
        frame(now);held=icon;heldAt=now;returnAt=now
        if(icon==null) {
            val network=items.indexOfFirst { it==SlotIcon.WIFI||it==SlotIcon.WIFI_OFFLINE||it==SlotIcon.NETWORK }.coerceAtLeast(0)
            began=(pausedAt ?: now)-prefix(network)
        }
    }
    val size get()=items.size
    fun update(next:List<SlotIcon>,now:Long) {
        val unique=next.distinct()
        if(items==unique)return
        if(!initialized){initialized=true;items=unique;began=now;swap.update(items.firstOrNull()?.ordinal ?: -1,now,false);return}
        val before=frame(now).icon
        val phase=position(pausedAt ?: now).phase
        val urgent=unique.firstOrNull { it !in items && it in listOf(SlotIcon.CAMERA,SlotIcon.MICROPHONE,SlotIcon.RECORD) }
        items=unique
        val chosen=urgent ?: before?.takeIf { it in items } ?: items.firstOrNull()
        val index=items.indexOf(chosen).coerceAtLeast(0)
        // Retain elapsed dwell time when the active set changes; polling must not starve later icons.
        began=(pausedAt ?: now)-prefix(index)-if(urgent!=null||items.isEmpty())0L else phase.coerceAtMost(span(items[index])-1)
        if(temporary==null&&held==null)swap.update(chosen?.ordinal ?: -1,now,fades)
    }
    fun pause(now:Long){if(pausedAt==null)pausedAt=now}
    fun resume(now:Long){pausedAt?.let { began+=now-it;swap.shiftTime(now-it) };pausedAt=null}
    fun preferWifi(now:Long){
        if(held!=null)return
        val preferred=items.firstOrNull { it==SlotIcon.WIFI || it==SlotIcon.WIFI_OFFLINE } ?: return
        began=now-prefix(items.indexOf(preferred));swap.update(preferred.ordinal,now,false)
    }
    fun frame(now:Long):SlotFrame {
        val time=pausedAt ?: now
        val pos=position(time)
        val length=items.getOrNull(pos.index)?.let { span(it) } ?: period
        val fadeOut=if(fades)exit else 0L
        val changing=items.size>1&&pos.phase>=length-fadeOut
        val index=if(changing)(pos.index+1)%items.size else pos.index
        val transitionAt=if(items.size>1)pos.start+if(changing)length-fadeOut else -fadeOut else time
        val heldStep=if(heldClock)((time-heldAt).coerceAtLeast(0)+fadeOut)/period else 0L
        val heldTarget=if(heldClock&&heldStep%2==1L)SlotIcon.RECORD_TIME else held
        val heldTransition=if(heldClock)maxOf(heldAt,heldAt+heldStep*period-fadeOut) else heldAt
        swap.update((heldTarget ?: temporary ?: items.getOrNull(index))?.ordinal ?: -1,
            if(held!=null)heldTransition else if(temporary!=null)temporaryAt else maxOf(transitionAt,returnAt),fades&&time>=began)
        val frame=swap.frame(time)
        return SlotFrame(SlotIcon.entries.getOrNull(frame.key),frame.opacity,.8f+.2f*frame.opacity,frame.reveal)
    }
    fun nextDelay(now:Long):Long {
        if(pausedAt!=null)return Long.MAX_VALUE
        if(swap.moving(now))return 16L
        if(held!=null) {
            if(!heldClock)return Long.MAX_VALUE
            val phase=(now-heldAt).coerceAtLeast(0)%period
            val out=if(fades)exit else 0L
            return if(phase>=period-out)16L else period-out-phase
        }
        if(temporary!=null||items.size<=1)return Long.MAX_VALUE
        val pos=position(now);val length=span(items[pos.index])
        val fadeOut=if(fades)exit else 0L
        return if(pos.phase>=length-fadeOut)16L else length-fadeOut-pos.phase
    }
}

/** Newest observed active foreground event wins. Repeated state reports never refresh its age.
 * Android can report several starts together; tie order is deterministic: screen capture, then mic,
 * then media. We cannot infer an earlier unobserved timestamp from a batch callback. */
internal class ForegroundEvents {
    private var serial=0L
    private val active=mutableMapOf<SlotIcon,Long>()
    var current:SlotIcon?=null; private set
    fun update(icons:Collection<SlotIcon>):SlotIcon? {
        val present=icons.filter { it==SlotIcon.MEDIA||it==SlotIcon.MICROPHONE||it==SlotIcon.RECORD }.toSet()
        active.keys.retainAll(present)
        for(icon in listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE,SlotIcon.RECORD))
            if(icon in present&&icon !in active)active[icon]=++serial
        current=active.maxByOrNull { it.value }?.key
        return current
    }
    fun clear(){active.clear();current=null}
}

/** Draw only changing pixels. Millisecond fades stay smooth, lasting effects use at most 30 fps. */
internal object EffectCadence {
    fun delay(cycleDelay:Long,transition:Boolean,continuous:Boolean,seconds:Boolean,deadline:Long=Long.MAX_VALUE):Long {
        val animation=when { transition->16L;continuous->33L;seconds->1000L;else->Long.MAX_VALUE }
        return minOf(cycleDelay,animation,deadline.coerceAtLeast(1))
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
    val charging: Boolean = false,
    val musicPlaying: Boolean = false, val musicProgress: Float = -1f, val albumColor: Int = 0,
    val chargeRemainingMs: Long = -1, val recordElapsedMs: Long = 0, val volumePercent: Int = -1,
    val drawIcons: Boolean = false, val iconPercent:Int=100, val iconRadius:Float=47.5f,
    val compassDegrees:Float=0f, val compass:Boolean=false
)

internal data class SwapFrame(val key: Int, val opacity: Float, val reveal:Float=1f)
/** A single visible occupant; interruptions finish its exit before the newest requested key enters. */
internal class SequentialSwap {
    private var exit=120L
    private var entry=160L
    fun configure(exitMs:Long,entryMs:Long){exit=exitMs.coerceIn(60,600);entry=entryMs.coerceIn(60,800)}
    private var current = Int.MIN_VALUE
    private var next = Int.MIN_VALUE
    private var began = 0L
    private var fromOpacity = 1f
    private var fromReveal = 1f
    private var switching = false
    fun update(key: Int, now: Long, animate: Boolean) {
        if (current == Int.MIN_VALUE || !animate) { current=key; next=key; switching=false; return }
        val pose=frame(now)
        if (next == key) return
        current=pose.key; next=key; fromOpacity=pose.opacity;fromReveal=pose.reveal; began=now; switching=true
    }
    fun frame(now: Long): SwapFrame {
        if (!switching) return SwapFrame(current,1f)
        val elapsed=(now-began).coerceAtLeast(0)
        if (elapsed < exit) return SwapFrame(current,fromOpacity * (1f-EffectTimeline.smooth(elapsed/exit.toFloat())),fromReveal)
        if (elapsed < exit+entry) { val amount=EffectTimeline.smooth((elapsed-exit)/entry.toFloat());return SwapFrame(next,amount,amount) }
        current=next; switching=false
        return SwapFrame(current,1f)
    }
    fun moving(now: Long): Boolean { frame(now); return switching }
    fun shiftTime(delta:Long){began+=delta}
}
