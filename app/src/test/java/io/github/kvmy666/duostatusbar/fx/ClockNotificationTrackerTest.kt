package io.github.kvmy666.duostatusbar.fx

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ClockNotificationTrackerTest {
    @Test fun `only explicit clock metadata is accepted`() {
        assertNotNull(ClockNotificationTracker.decode("com.sec.android.app.clockpackage",true,true,1000))
        assertNull(ClockNotificationTracker.decode("some.call.app",true,false,1000))
        assertNull(ClockNotificationTracker.decode("com.google.android.deskclock",false,true,1000))
        assertNull(ClockNotificationTracker.decode("com.android.deskclock",true,false,0))
    }
    @Test fun `clock anchors use real remaining and elapsed times and disappearance clears them`() {
        AuroraActivities.reset()
        try {
            AuroraActivities.configure(AuroraOptions(setOf("timer")))
            AuroraActivities.observeClock(110000,80000,100000,5000)
            val state=AuroraActivities.snapshot(7000)
            assertEquals(8000L,state.timerRemainingMs)
            assertEquals(22000L,state.stopwatchElapsedMs)
            AuroraActivities.observeClock(-1,-1,100000,7000)
            assertEquals(-1L,AuroraActivities.snapshot(7000).timerRemainingMs)
            assertEquals(-1L,AuroraActivities.snapshot(7000).stopwatchElapsedMs)
        } finally {AuroraActivities.reset()}
    }
    @Test fun `disabled feature ignores clock notifications`() {
        AuroraActivities.reset()
        AuroraActivities.observeClock(110000,80000,100000,5000)
        assertEquals(-1L,AuroraActivities.snapshot(5000).timerRemainingMs)
    }
}

