package io.github.kvmy666.duostatusbar.hook

import android.content.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.fx.EffectTimeline
import io.github.kvmy666.duostatusbar.fx.SlotIcon
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], manifest=Config.NONE)
class DuoStateMonitorTest {
    /** Reproduces the ReceiverDispatcher handler identity check reported by the Samsung. */
    private class ReceiverContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        data class Registration(val receiver: BroadcastReceiver, val filter: IntentFilter, val handler: Handler?)
        val registrations = mutableListOf<Registration>()
        var denyBluetooth = false
        var failCoreOnce = false
        val battery = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 14).putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
            .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? =
            if (receiver == null && filter.hasAction(Intent.ACTION_BATTERY_CHANGED)) battery else super.registerReceiver(receiver, filter)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? =
            register(receiver, filter, null)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, permission: String?, scheduler: Handler?, flags: Int): Intent? =
            register(receiver, filter, scheduler)

        private fun register(receiver: BroadcastReceiver?, filter: IntentFilter, scheduler: Handler?): Intent? {
            if (receiver == null) return battery
            if (filter.hasAction(Intent.ACTION_BATTERY_CHANGED) && failCoreOnce) {
                failCoreOnce = false
                throw SecurityException("core registration denied once")
            }
            if (filter.hasAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) && denyBluetooth)
                throw SecurityException("Bluetooth registration denied")
            registrations.firstOrNull { it.receiver === receiver }?.let {
                check(it.handler === scheduler) { "Receiver registered with differing handler" }
            }
            registrations.add(Registration(receiver, filter, scheduler))
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            check(registrations.removeAll { it.receiver === receiver }) { "Receiver not registered" }
        }
        fun dispatch(intent: Intent) {
            registrations.toList().filter { it.filter.hasAction(intent.action) }
                .forEach { it.receiver.onReceive(this, intent) }
        }
    }
    private fun get(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(owner)!!
    private fun set(owner: Any, name: String, value: Any) = owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(owner, value)
    private class Fixture {
        val context = ReceiverContext()
        val host = DuoIconHost(context)
        val view = DuoCanvasView(context).apply { layout(0,0,120,136) }
        val monitor = DuoStateMonitor(context,host)
    }
    private fun fixture() = Fixture().also { set(it.host,"element",it.view) }
    private fun idle(ms: Long) = Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    @Test fun `full startup reads charging battery and displays bolt then exclusive unlock check`() {
        val f = fixture()
        try {
            f.monitor.start()
            val effects = get(f.monitor,"effects")
            assertTrue("effects must start after receiver registration", get(effects,"running") as Boolean)
            assertTrue(f.view.effects.charging)
            assertTrue(f.view.effects.chargeMs >= 0)
            val seen = mutableSetOf<SlotIcon>()
            repeat(7) { idle(3200); f.view.effects.slot.icon?.let(seen::add) }
            assertTrue("charging bolt must participate in real startup carousel", SlotIcon.BOLT in seen)
            f.context.dispatch(Intent(Intent.ACTION_SCREEN_OFF))
            idle(600)
            f.context.dispatch(Intent(Intent.ACTION_SCREEN_ON))
            f.context.dispatch(Intent(Intent.ACTION_USER_PRESENT))
            idle(1500)
            assertTrue(f.view.effects.checkMs in 1450..1550)
            assertEquals(1f,EffectTimeline.check(f.view.effects.checkMs),0f)
            assertEquals(0f,EffectTimeline.checkNormal(f.view.effects.checkMs),0f)
            idle(1350)
            assertTrue(EffectTimeline.check(f.view.effects.checkMs) in .1f.. .9f)
            idle(400)
            assertEquals(-1L,f.view.effects.checkMs)
        } finally { f.monitor.stop(); f.view.teardown() }
        assertTrue(f.context.registrations.isEmpty())
    }
    @Test fun `Bluetooth registration denied does not disable charging or unlock`() {
        val f = fixture()
        f.context.denyBluetooth = true
        try {
            f.monitor.start()
            assertTrue(get(get(f.monitor,"effects"),"running") as Boolean)
            assertTrue(f.view.effects.charging)
            f.context.dispatch(Intent(Intent.ACTION_USER_PRESENT)); idle(1500)
            assertTrue(f.view.effects.checkMs in 1450..1550)
        } finally { f.monitor.stop(); f.view.teardown() }
        assertTrue(f.context.registrations.isEmpty())
    }
    @Test fun `failed core startup can be retried without a false registered latch`() {
        val f = fixture()
        f.context.failCoreOnce = true
        try {
            f.monitor.start()
            assertFalse(get(f.monitor,"registered") as Boolean)
            assertFalse(get(get(f.monitor,"effects"),"running") as Boolean)
            assertTrue(f.context.registrations.isEmpty())
            f.monitor.start()
            assertTrue(get(get(f.monitor,"effects"),"running") as Boolean)
            assertTrue(f.view.effects.charging)
        } finally { f.monitor.stop(); f.view.teardown() }
    }
    @Test fun `stop releases both receivers and restarting does not duplicate dispatch`() {
        val f = fixture()
        try {
            repeat(2) {
                f.monitor.start(); f.monitor.start()
                assertEquals(3,f.context.registrations.size)
                assertEquals(3,f.context.registrations.map {it.receiver}.distinct().size)
                assertEquals(1,f.context.registrations.count {it.filter.hasAction(Intent.ACTION_BATTERY_CHANGED)})
                assertEquals(1,f.context.registrations.count {it.filter.hasAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)})
                assertEquals(1,f.context.registrations.count {it.filter.hasAction(io.github.kvmy666.duostatusbar.fx.RuntimeExperience.ACTION)})
                assertNotSame(f.context.registrations[0].receiver,f.context.registrations[1].receiver)
                f.context.dispatch(Intent(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
                    .putExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE,android.bluetooth.BluetoothAdapter.STATE_ON))
                assertTrue(get(get(f.monitor,"effects"),"bluetooth") as Boolean)
                f.monitor.stop(); f.monitor.stop()
                assertTrue(f.context.registrations.isEmpty())
                assertFalse(get(get(f.monitor,"effects"),"running") as Boolean)
            }
        } finally { f.monitor.stop(); f.view.teardown() }
    }
}
