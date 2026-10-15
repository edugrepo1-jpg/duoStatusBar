package io.github.kvmy666.duostatusbar.fx

import android.app.Activity
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
class NfcParticleOverlayTest {
    @Test fun `decorative window spans display without focus or touch and expires`() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity=controller.get()
        val anchor=FrameLayout(activity)
        activity.setContentView(anchor)
        val effect=NfcParticleOverlay(activity)
        assertTrue(effect.show(anchor,true))
        val window=requireNotNull(effect.currentWindow)
        assertFalse(window.isFocusable);assertFalse(window.isTouchable);assertFalse(window.isOutsideTouchable)
        assertTrue(window.width>0);assertEquals((116*activity.resources.displayMetrics.density).toInt(),window.height)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100))
        assertFalse(effect.isShowing)
        assertNull(effect.currentWindow)
        controller.pause().stop().destroy()
    }
    @Test fun `disabled detached and explicitly stopped effects leave no window`() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity=controller.get();val anchor=FrameLayout(activity)
        val effect=NfcParticleOverlay(activity)
        assertFalse(effect.show(anchor,true))
        activity.setContentView(anchor)
        assertFalse(effect.show(anchor,false))
        assertTrue(effect.show(anchor,true,true));effect.dismiss();assertFalse(effect.isShowing)
        assertTrue(effect.show(anchor,true))
        activity.setContentView(FrameLayout(activity))
        assertFalse(effect.isShowing)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertNull(effect.currentWindow)
        controller.pause().stop().destroy()
    }
}
