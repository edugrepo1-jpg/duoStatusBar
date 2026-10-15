package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.TelemetryCounter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeDeepAuditTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun idle(ms: Long) = Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)

    @Test fun `three hundred shade layouts produce one settled callback after the final layout`() {
        val root=View(context);var calls=0
        val work=RootBoundWork(Handler(Looper.getMainLooper())) { root }
        repeat(300) { work.post(root,400) { calls++ };idle(1) }
        idle(398);assertEquals(0,calls)
        idle(1);assertEquals(1,calls)
        idle(5000);assertEquals(1,calls)
    }

    @Test fun `obsolete root cannot run a queued operation or replace work belonging to the new root`() {
        val old=View(context);val replacement=View(context);var current=old;val calls=mutableListOf<String>()
        val work=RootBoundWork(Handler(Looper.getMainLooper())) { current }
        work.post(old,100) { calls.add("old") }
        current=replacement
        idle(100);assertTrue(calls.isEmpty())
        work.post(replacement,100) { calls.add("replacement") }
        work.post(old,10) { calls.add("late old layout") }
        idle(100);assertEquals(listOf("replacement"),calls)
    }

    @Test fun `off cancellation removes pending attachment and explicit reenable can schedule again`() {
        val root=View(context);var starts=0
        val work=RootBoundWork(Handler(Looper.getMainLooper())) { root }
        work.post(root,2500) { starts++ }
        idle(1000);work.cancel();idle(3000);assertEquals(0,starts)
        work.post(root,2500) { starts++ };idle(2500);assertEquals(1,starts)
    }

    @Test fun `attachment retry stays singular and stops when its root is replaced`() {
        val old=View(context);var current=old;var tries=0
        val work=RootBoundWork(Handler(Looper.getMainLooper())) { current }
        fun retry() {work.post(old,100) {tries++;retry()}}
        retry();idle(350);assertEquals(3,tries)
        current=View(context);idle(5000);assertEquals(3,tries)
        work.cancel()
    }

    private class BatteryMeterView(c:Context):View(c)
    private class ReceiverContext(app:Context):ContextWrapper(app) {
        val receivers=mutableListOf<BroadcastReceiver>()
        val battery=Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL,72).putExtra(BatteryManager.EXTRA_SCALE,100)
            .putExtra(BatteryManager.EXTRA_PLUGGED,0)
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter):Intent? =
            if(receiver==null)battery else super.registerReceiver(receiver,filter)
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter,flags:Int):Intent? {
            if(receiver!=null)receivers.add(receiver)
            return if(receiver==null)battery else null
        }
        override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter,permission:String?,scheduler:Handler?,flags:Int):Intent? {
            if(receiver!=null)receivers.add(receiver)
            return null
        }
        override fun unregisterReceiver(receiver:BroadcastReceiver) {receivers.remove(receiver)}
    }
    private fun field(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(owner)

    @Test fun `disabled module stops real monitor registrations and indicator timers before restoring stock icons`() {
        val c=ReceiverContext(context)
        Settings.Global.putInt(c.contentResolver,DuoGuard.KEY_STAGE,1)
        val root=FrameLayout(c).apply {layout(0,0,1000,120)}
        val strip=LinearLayout(c).apply {layout(0,0,120,80)}
        val battery=BatteryMeterView(c)
        strip.addView(battery,LinearLayout.LayoutParams(60,42));root.addView(strip)
        battery.layout(0,0,60,42)
        val host=DuoIconHost(c);val monitor=DuoStateMonitor(c,host)
        try {
            assertTrue(host.attach(root));assertEquals(View.GONE,battery.visibility)
            // The settings bridge is process-owned and must survive OFF to receive re-enable.
            val bridgeReceivers=c.receivers.toSet()
            monitor.start();idle(250)
            assertTrue(c.receivers.toSet().size>bridgeReceivers.size)
            val effects=field(monitor,"effects")!!
            assertEquals(true,field(effects,"running"))
            stopDuoRuntime(monitor,host)
            assertEquals(bridgeReceivers,c.receivers.toSet());assertEquals(false,field(effects,"running"))
            assertNull(host.duo);assertEquals(View.VISIBLE,battery.visibility)
            assertEquals(60,battery.layoutParams.width);assertEquals(42,battery.layoutParams.height)
            val before=field(effects,"loggedSlot")
            idle(10000)
            assertEquals(before,field(effects,"loggedSlot"))
            assertFalse((0 until strip.childCount).any {strip.getChildAt(it) is DuoCanvasView})
            // Real re-enable starts a new monitor; old receiver instances remain unregistered.
            assertTrue(host.attach(root))
            val next=DuoStateMonitor(c,host);next.start();assertTrue(c.receivers.toSet().size>bridgeReceivers.size)
            stopDuoRuntime(next,host);assertEquals(bridgeReceivers,c.receivers.toSet())
        } finally {stopDuoRuntime(monitor,host);Settings.Global.putString(c.contentResolver,DuoGuard.KEY_STAGE,null)}
    }
}
