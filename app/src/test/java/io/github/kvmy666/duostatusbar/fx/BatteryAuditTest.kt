package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class BatteryAuditTest {
    @Test fun `periodic inventories coalesce and events bypass interval only after work finishes`() {
        val gate=IndicatorRefreshGate()
        assertTrue(gate.begin(0))
        assertFalse(gate.begin(100,true)) // One worker request even during a broadcast burst.
        gate.finish()
        assertFalse(gate.begin(2000))
        assertTrue(gate.begin(2000,true))
        gate.finish()
        assertFalse(gate.begin(11999))
        assertTrue(gate.begin(12000))
        gate.finish();gate.reset()
        assertTrue(gate.begin(12001))
    }

    @Test fun `media heartbeat is absent while paused stopped or screen off`() {
        assertTrue(ExperienceRelayService.shouldHeartbeat(true,true,true,true,true))
        assertFalse(ExperienceRelayService.shouldHeartbeat(true,true,true,false,true))
        assertFalse(ExperienceRelayService.shouldHeartbeat(true,true,false,true,true))
        assertFalse(ExperienceRelayService.shouldHeartbeat(false,true,true,true,true))
        assertFalse(ExperienceRelayService.shouldHeartbeat(true,false,true,true,true))
        // The only polling fallback is a ROM where session callbacks could not be registered.
        assertTrue(ExperienceRelayService.shouldHeartbeat(true,true,true,false,false))
        assertEquals(30000L,ExperienceRelayService.HEARTBEAT_MS)
    }

    @Test fun `sensor bursts cannot redraw faster than ten heading samples per second`() {
        var previous=-1000L
        val accepted=mutableListOf<Long>()
        for(now in 0L..999L)if(HeadingObserver.sampleDue(previous,now)) { accepted.add(now);previous=now }
        assertEquals((0L..900L step 100L).toList(),accepted)
        assertEquals(2f,HeadingObserver.shortestDelta(359f,1f),0f)
    }

    @Test fun `relay reconnect does not accumulate receivers and destroy releases them`() {
        val context=ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadow=Shadows.shadowOf(context)
        val baseline=shadow.registeredReceivers.size
        val service=Robolectric.buildService(ExperienceRelayService::class.java).create().get()
        service.onListenerConnected();service.onListenerConnected()
        assertEquals(baseline+1,shadow.registeredReceivers.size)
        service.onListenerDisconnected()
        assertEquals(baseline,shadow.registeredReceivers.size)
        service.onListenerConnected()
        assertEquals(baseline+1,shadow.registeredReceivers.size)
        service.onDestroy()
        assertEquals(baseline,shadow.registeredReceivers.size)
    }

    @Test fun `stopped volume observer ignores all later audio broadcasts`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val audio=context.getSystemService(AudioManager::class.java)
        val results=mutableListOf<Int>()
        val observer=VolumeObserver(context,Handler(Looper.getMainLooper())) { results.add(it) }
        observer.start();observer.stop();observer.stop()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,1,0)
        context.sendBroadcast(Intent(VolumeObserver.ACTION).putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE",AudioManager.STREAM_MUSIC))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(results.isEmpty())
    }
}
