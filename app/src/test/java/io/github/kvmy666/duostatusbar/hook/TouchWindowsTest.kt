package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],manifest=Config.NONE)
class TouchWindowsTest {
    private class Shade(c:Context):FrameLayout(c) {
        override fun dispatchTouchEvent(event:MotionEvent):Boolean=super.dispatchTouchEvent(event)
    }
    @Test fun `a replacement landscape window uses the existing inherited hook`() {
        val c=ApplicationProvider.getApplicationContext<Context>();val registry=TouchWindows()
        val portrait=FrameLayout(c);val landscape=FrameLayout(c);val unrelated=FrameLayout(c)
        val method=registry.register(portrait)!!
        assertNull(registry.register(landscape));assertNull(registry.register(portrait))
        assertTrue(registry.accepts(method,portrait));assertTrue(registry.accepts(method,landscape))
        assertFalse(registry.accepts(method,unrelated))
    }
    @Test fun `a shade override calling super cannot route the same event twice`() {
        val c=ApplicationProvider.getApplicationContext<Context>();val registry=TouchWindows()
        val normal=FrameLayout(c);val shade=Shade(c)
        val inherited=registry.register(normal)!!;val override=registry.register(shade)!!
        assertTrue(registry.accepts(override,shade));assertFalse(registry.accepts(inherited,shade))
        assertFalse(registry.accepts(override,normal));assertTrue(registry.accepts(inherited,normal))
    }
}
