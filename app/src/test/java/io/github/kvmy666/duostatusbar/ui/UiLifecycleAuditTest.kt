package io.github.kvmy666.duostatusbar.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.kvmy666.duostatusbar.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiLifecycleAuditTest {
    @Before fun pauseAutomaticVsync() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }
    private fun idle(ms:Long)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    @Test fun `diagnostics polling stops in background and resumes without waiting a full interval`() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().visible().get();var calls=0
        activity.setContent {ObserveScreenWhileStarted {calls++}}
        try {
            idle(100);assertTrue(calls>0)
            val before=calls;idle(1600);assertTrue(calls>before)
            controller.pause().stop();idle(100);val stopped=calls
            idle(15000);assertEquals("No background diagnostics reads",stopped,calls)
            controller.start().resume();idle(100);assertTrue("Refresh immediately after returning",calls>stopped)
        } finally {controller.pause().stop().destroy();idle(100)}
    }
    @Test fun `external capability state is reread when returning from a permission manager`() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().visible().get();var available=false;var observed:Boolean?=null;var calls=0
        activity.setContent {ObserveAccessOnResume {observed=available;calls++}}
        try {
            idle(100);assertEquals(false,observed)
            controller.pause().stop();val stopped=calls;available=true;idle(100)
            assertEquals(stopped,calls);assertEquals(false,observed)
            controller.start().resume();idle(100);assertEquals(true,observed)
            assertTrue(calls>stopped)
        } finally {controller.pause().stop().destroy();idle(100)}
    }
}
