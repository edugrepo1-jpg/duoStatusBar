package io.github.kvmy666.duostatusbar.fx

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], manifest=Config.NONE)
class UnlockObserverTest {
    private class Fixture(initial: Boolean?) {
        var locked = initial
        var reads = 0
        val events = mutableListOf<String>()
        val observer = UnlockObserver(Handler(Looper.getMainLooper()),
            { reads++; locked }, {}, events::add)
    }
    private fun idle(ms: Long) = Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)

    @Test fun `starting on unlocked phone and ordinary screen wake do not invent unlocks`() {
        val f=Fixture(false)
        f.observer.start(true);idle(5000)
        f.observer.screenChanged(false);idle(1000)
        f.observer.screenChanged(true);idle(5000)
        assertTrue(f.events.isEmpty())
        f.observer.stop()
    }
    @Test fun `PIN dismissal without broadcast or biometric callback fires once`() {
        val f=Fixture(true)
        f.observer.start(true);idle(9000)
        assertTrue(f.events.isEmpty())
        f.locked=false;idle(250)
        assertEquals(1,f.events.size)
        idle(5000);f.observer.userPresent();f.observer.authenticated();idle(6000)
        assertEquals("late duplicate sources must not trigger a second check",1,f.events.size)
        f.observer.stop()
    }
    @Test fun `authentication while padlock is still showing waits for actual dismissal`() {
        val f=Fixture(true)
        f.observer.start(true);f.observer.authenticated();idle(1000)
        assertTrue(f.events.isEmpty())
        f.locked=false;idle(100)
        assertEquals(1,f.events.size)
        f.observer.stop()
    }
    @Test fun `fast biometric wake is confirmed even before a showing state was sampled`() {
        val f=Fixture(false)
        f.observer.start(true)
        f.observer.screenChanged(false)
        f.observer.authenticated();idle(100)
        assertTrue(f.events.isEmpty())
        f.observer.screenChanged(true)
        assertEquals(1,f.events.size)
        f.observer.stop()
    }
    @Test fun `keyguard appearing just after screen off is sampled then polling sleeps`() {
        val f=Fixture(false)
        f.observer.start(true);f.observer.screenChanged(false)
        f.locked=true;idle(250)
        val count=f.reads;idle(10000)
        assertEquals("display off must not continually query keyguard",count,f.reads)
        f.locked=false;f.observer.screenChanged(true)
        assertEquals(1,f.events.size)
        f.observer.stop()
    }
    @Test fun `another real lock and dismissal can trigger a new check`() {
        val f=Fixture(true)
        f.observer.start(true);f.locked=false;idle(250)
        f.locked=true;f.observer.screenChanged(false);idle(5000)
        f.observer.screenChanged(true);f.locked=false;idle(250)
        assertEquals(2,f.events.size)
        f.observer.stop()
    }
    @Test fun `two fast biometric unlocks remain independent even when lock sampling misses both`() {
        val f=Fixture(false)
        f.observer.start(true)
        repeat(2) {
            f.observer.screenChanged(false);f.observer.authenticated()
            f.observer.screenChanged(true);idle(5000)
        }
        assertEquals(2,f.events.size)
        f.observer.stop()
    }
    @Test fun `stop cancels pending sampling and restarting does not fabricate a transition`() {
        val f=Fixture(true)
        f.observer.start(true);f.observer.stop()
        val count=f.reads;f.locked=false;idle(6000)
        assertEquals(count,f.reads);assertTrue(f.events.isEmpty())
        f.observer.start(true);idle(6000)
        assertTrue(f.events.isEmpty())
        f.observer.stop()
    }
    @Test fun `unavailable keyguard query preserves observed lock and confirmed broadcast fallback`() {
        val f=Fixture(true)
        f.observer.start(true);f.locked=null;idle(1000)
        assertTrue(f.events.isEmpty())
        f.locked=false;idle(250)
        assertEquals(1,f.events.size)
        f.observer.stop()
        val missing=Fixture(null)
        missing.observer.start(true);missing.observer.userPresent()
        assertEquals(1,missing.events.size)
        missing.observer.stop()
    }
}
