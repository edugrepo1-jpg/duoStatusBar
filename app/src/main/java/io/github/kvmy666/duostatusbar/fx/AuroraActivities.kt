package io.github.kvmy666.duostatusbar.fx

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

internal data class ActivityClock(val began:Long,val accumulated:Long=0,val running:Boolean=true,val duration:Long=0) {
    fun elapsed(now:Long)=(accumulated+if(running)(now-began).coerceAtLeast(0) else 0).let {if(duration>0)it.coerceAtMost(duration)else it}
    fun remaining(now:Long)=if(duration>0)(duration-elapsed(now)).coerceAtLeast(0) else -1
    fun pause(now:Long)=copy(accumulated=elapsed(now),began=now,running=false)
    fun resume(now:Long)=copy(began=now,running=true)
}
internal data class AuroraActivitySnapshot(val timerElapsedMs:Long=-1,val timerRemainingMs:Long=-1,val timerRunning:Boolean=false,
    val stopwatchElapsedMs:Long=-1,val stopwatchRunning:Boolean=false,val completedAtUptime:Long=-1,val completedSource:String="")

/** Own visual timers use elapsed realtime and a single expiry callback; never acquire a wakelock. */
internal object AuroraActivities {
    private val main=Handler(Looper.getMainLooper())
    private val listeners=linkedSetOf<()->Unit>()
    var timer:ActivityClock?=null;private set
    var stopwatch:ActivityClock?=null;private set
    private var options=AuroraOptions()
    private var completedAt=-1L
    private var completedSource=""
    private val expiry=Runnable { snapshot(SystemClock.elapsedRealtime());notifyChanged() }
    fun configure(next:AuroraOptions){options=next;if(!next.timer){timer=null;stopwatch=null;main.removeCallbacks(expiry)}}
    fun addListener(listener:()->Unit){listeners.add(listener);schedule(SystemClock.elapsedRealtime())}
    fun removeListener(listener:()->Unit){listeners.remove(listener);if(listeners.isEmpty())main.removeCallbacks(expiry)}
    private fun notifyChanged(){listeners.toList().forEach {it()}}
    fun completed(source:String="") {
        if(!options.taskComplete)return
        val now=SystemClock.uptimeMillis()
        if(options.singleNotices&&!AuroraMotion.latestNotice(completedAt,now))return
        completedAt=now;completedSource=RuntimeExperience.sourceIdentity(source);notifyChanged()
    }
    /** Maps the smartphone Clock's wall-clock anchor once to monotonic time; no fabricated timer. */
    fun observeClock(timerWhen:Long,stopwatchWhen:Long,wallNow:Long=System.currentTimeMillis(),now:Long=SystemClock.elapsedRealtime()) {
        if(!options.timer)return
        val remaining=(timerWhen-wallNow).takeIf {timerWhen>0&&it in 1..7*86400000L}
        timer=remaining?.let {ActivityClock(now,duration=it)}
        val elapsed=(wallNow-stopwatchWhen).takeIf {stopwatchWhen>0&&it in 0..7*86400000L}
        stopwatch=elapsed?.let {ActivityClock(now,accumulated=it)}
        schedule(now);notifyChanged()
    }
    fun command(key:String) {
        if(!options.timer)return
        val now=SystemClock.elapsedRealtime()
        when(key){
            "timerStart"->timer=timer?.takeIf {it.remaining(now)>0}?.resume(now) ?: ActivityClock(now,duration=options.timerMinutes.coerceIn(1,60)*60000L)
            "timerPause"->timer=timer?.let {if(it.running)it.pause(now)else if(it.remaining(now)>0)it.resume(now)else it}
            "timerReset"->timer=null
            "timerPlus"->timer=timer?.let {it.copy(duration=(it.duration+15000).coerceAtMost(86400000))}
            "stopwatchStart"->stopwatch=stopwatch?.resume(now) ?: ActivityClock(now)
            "stopwatchPause"->stopwatch=stopwatch?.let {if(it.running)it.pause(now)else it.resume(now)}
            "stopwatchReset"->stopwatch=null
            else->return
        }
        schedule(now);notifyChanged()
    }
    private fun schedule(now:Long){main.removeCallbacks(expiry);timer?.takeIf {it.running&&it.remaining(now)>0}?.let {main.postDelayed(expiry,it.remaining(now))}}
    fun snapshot(now:Long):AuroraActivitySnapshot {
        val current=timer
        if(current!=null&&current.running&&current.remaining(now)==0L){timer=current.pause(now);completedAt=SystemClock.uptimeMillis();completedSource="";main.removeCallbacks(expiry)}
        return AuroraActivitySnapshot(timer?.elapsed(now) ?: -1,timer?.remaining(now) ?: -1,timer?.running==true,stopwatch?.elapsed(now) ?: -1,stopwatch?.running==true,completedAt,completedSource)
    }
    internal fun reset(){main.removeCallbacks(expiry);timer=null;stopwatch=null;completedAt=-1;completedSource="";options=AuroraOptions()}
}
