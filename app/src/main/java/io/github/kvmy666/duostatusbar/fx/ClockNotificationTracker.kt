package io.github.kvmy666.duostatusbar.fx

import android.app.Notification
import android.content.Intent
import android.service.notification.StatusBarNotification

/** Public chronometer metadata only. Never parse notification text or invent a duration. */
internal class ClockNotificationTracker {
    data class Clock(val countdown:Boolean,val whenMs:Long)
    private val entries=linkedMapOf<String,Clock>()
    fun observe(sbn:StatusBarNotification):Boolean {
        val n=sbn.notification
        val clock=decode(sbn.packageName,n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER,false),
            n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN,false),n.`when`)
        if(entries[sbn.key]==clock)return false
        entries.remove(sbn.key)
        if(clock!=null)entries[sbn.key]=clock
        return true
    }
    fun remove(key:String):Boolean=entries.remove(key)!=null
    fun clear(){entries.clear()}
    fun intent():Intent=Intent(RuntimeExperience.ACTION).putExtra("clockSnapshot",true)
        .putExtra("clockTimerWhen",entries.values.lastOrNull {it.countdown}?.whenMs ?: -1L)
        .putExtra("clockStopwatchWhen",entries.values.lastOrNull {!it.countdown}?.whenMs ?: -1L)
    companion object {
        // Confirmed application identities, not names/text supplied by arbitrary notifications.
        private val packages=setOf("com.sec.android.app.clockpackage","com.google.android.deskclock","com.android.deskclock")
        fun decode(source:String,chronometer:Boolean,countdown:Boolean,whenMs:Long):Clock? =
            if(source in packages && chronometer && whenMs>0)Clock(countdown,whenMs)else null
    }
}
