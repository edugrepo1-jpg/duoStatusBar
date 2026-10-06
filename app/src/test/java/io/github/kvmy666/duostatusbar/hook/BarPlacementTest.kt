package io.github.kvmy666.duostatusbar.hook
import android.app.Activity
import org.robolectric.Robolectric
import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class BarPlacementTest {
    @Test fun `Samsung 89 pixel bar fits whole 136 unit artboard at large saved sizes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val strip = FrameLayout(context).apply { layout(952,34,1041,89) }
        val bar = FrameLayout(context).apply { layout(0,0,1080,89) }
        val geometry = SlotGeometry(context, RomDetection.forThisRom("samsung","samsung","r8s",""))
        geometry.capture(314,58)
        val side = geometry.sidePx(strip,bar)
        assertTrue(RingGeometry.elementHeightPx(side) <= 89)
        assertEquals(78, side)
        geometry.applySize(60)
        assertEquals(34,geometry.sidePx(strip,bar))
    }
    @Test fun `manual position stays inside window and never accumulates translation`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar=FrameLayout(activity)
        activity.setContentView(bar)
        val view=FrameLayout(activity)
        bar.addView(view)
        bar.layout(0,0,1080,89);view.layout(952,34,1030,122)
        assertTrue(view.isAttachedToWindow)
        assertEquals(89,bar.height)
        assertEquals(88,view.height)
        view.translationX=537f;view.translationY=1200f
        BarPlacement.keepInside(view,bar)
        assertEquals(50f,view.translationX,0f)
        assertEquals(-33f,view.translationY,0f)
        repeat(100) { BarPlacement.keepInside(view,bar) }
        assertEquals(50f,view.translationX,0f)
        assertEquals(-33f,view.translationY,0f)
        view.translationX=-400f
        BarPlacement.keepInside(view,bar)
        assertEquals(-400f,view.translationX,0f)
    }
    @Test fun `centering a nested bar uses its position instead of full screen center`() {
        val context=Robolectric.buildActivity(Activity::class.java).setup().get()
        val geometry=SlotGeometry(context,RomDetection.forThisRom("samsung","samsung","r8s",""))
        val window=FrameLayout(context)
        context.setContentView(window)
        val bar=FrameLayout(context);window.addView(bar)
        val strip=FrameLayout(context);bar.addView(strip)
        window.layout(0,0,1080,2000);bar.layout(0,200,1080,289);strip.layout(952,34,1041,89)
        assertTrue(strip.isAttachedToWindow)
        assertEquals(-17f,geometry.windowCenterShiftY(strip,bar),.01f)
        assertEquals(0f,geometry.windowCenterShiftY(strip,window),.01f)
    }
}
