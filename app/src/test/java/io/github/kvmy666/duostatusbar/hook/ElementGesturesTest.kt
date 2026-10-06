package io.github.kvmy666.duostatusbar.hook
import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ElementGesturesTest {
    @Test fun `a layout pass does not replace the detector tracking a tap`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val gestures=ElementGestures(context)
        val view=View(context)
        gestures.install(view,"wifi","no_action","no_action")
        val field=ElementGestures::class.java.getDeclaredField("detector").apply { isAccessible=true }
        val detector=field.get(gestures)
        assertNotNull(detector)
        repeat(50) { gestures.install(view,"wifi","no_action","no_action") }
        assertSame(detector,field.get(gestures))
        gestures.install(view,"no_action","no_action","no_action")
        assertNull(field.get(gestures))
        assertFalse(view.isClickable)
    }
}
