package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class ShadeRestorationTest {
    private class CombinedStatusView(c:Context):View(c)
    @Test fun `two headers restore stock visibility and dimensions independently`() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val first=FrameLayout(c);val second=FrameLayout(c)
        val a=CombinedStatusView(c);val b=CombinedStatusView(c)
        first.addView(a,FrameLayout.LayoutParams(60,24));second.addView(b,FrameLayout.LayoutParams(80,32))
        val ah=StockIconHider();val bh=StockIconHider()
        ah.hideReplaced(first,null);bh.hideReplaced(second,null)
        assertEquals(View.GONE,a.visibility);assertEquals(View.GONE,b.visibility)
        ah.restore()
        assertEquals(View.VISIBLE,a.visibility);assertEquals(60,a.layoutParams.width)
        assertEquals(View.GONE,b.visibility);assertEquals(0,b.layoutParams.width)
        bh.restore();assertEquals(View.VISIBLE,b.visibility);assertEquals(80,b.layoutParams.width)
    }
    @Test fun `an unrelated status view remains visible when only replaced icons are hidden`() {
        val c=ApplicationProvider.getApplicationContext<Context>();val row=FrameLayout(c)
        val ordinary=View(c);row.addView(ordinary,FrameLayout.LayoutParams(25,25))
        StockIconHider().hideReplaced(row,null)
        assertEquals(View.VISIBLE,ordinary.visibility);assertEquals(25,ordinary.layoutParams.width)
    }
}
