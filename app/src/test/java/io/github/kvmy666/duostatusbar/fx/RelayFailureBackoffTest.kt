package io.github.kvmy666.duostatusbar.fx

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class RelayFailureBackoffTest {
    private fun set(owner:Any,name:String,value:Any)=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(owner,value)
    private fun get(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(owner)

    @Test fun `overdue heartbeat backs off after failure and successful publication clears retry`() {
        val service=Robolectric.buildService(ExperienceRelayService::class.java).create().get()
        val main=Shadows.shadowOf(Looper.getMainLooper())
        set(service,"connected",true);set(service,"music",true);set(service,"watching",true)
        set(service,"screen",true);set(service,"latest",PlaybackSnapshot(playing=true))
        set(service,"sentAt",SystemClock.elapsedRealtime()-30000L)
        val failure=service.javaClass.getDeclaredMethod("mediaFailure",Throwable::class.java).apply {isAccessible=true}
        val schedule=service.javaClass.getDeclaredMethod("scheduleHeartbeat").apply {isAccessible=true}
        val publish=service.javaClass.getDeclaredMethod("publish",Boolean::class.javaPrimitiveType).apply {isAccessible=true}
        try {
            failure.invoke(service,SecurityException("Synthetic revoked access"));schedule.invoke(service)
            val deadline=SystemClock.uptimeMillis()+30000L
            assertTrue(main.nextScheduledTaskTime.toMillis()>=deadline)
            main.idleFor(Duration.ofSeconds(29))
            assertTrue((get(service,"latest") as PlaybackSnapshot).playing)
            schedule.invoke(service)
            assertTrue(main.nextScheduledTaskTime.toMillis()>=deadline)
            publish.invoke(service,true)
            assertEquals(0L,get(service,"retryNotBefore"))
            assertFalse((get(service,"latest") as PlaybackSnapshot).playing)
            main.idleFor(Duration.ofMinutes(1))
            assertFalse((get(service,"latest") as PlaybackSnapshot).playing)
        } finally {service.onDestroy()}
    }
}
