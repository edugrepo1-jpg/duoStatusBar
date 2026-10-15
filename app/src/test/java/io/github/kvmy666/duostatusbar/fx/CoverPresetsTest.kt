package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverPresetsTest {
    @Test fun `all twenty presets render and freeze when motion is disabled`() {
        assertEquals(21,CoverEffects.names.size)
        val p=CoverEffectPainter();val bounds=RectF(40f,40f,104f,104f)
        for(id in 1..20) {
            val a=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
            val b=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888)
            p.draw(Canvas(a),bounds,id,0,Color.CYAN,.6f,false)
            p.draw(Canvas(b),bounds,id,3500,Color.CYAN,.6f,false)
            assertTrue("Paused preset $id must not change with clock",a.sameAs(b))
            var pixels=0
            for(y in 0 until 144)for(x in 0 until 144)if(Color.alpha(a.getPixel(x,y))>0)pixels++
            assertTrue("Preset $id must have visible geometry",pixels>0)
            a.recycle();b.recycle()
        }
    }
    @Test fun `appearance survives settings persistence without altering battery colors`() {
        val a=IslandAppearance(88,92,20,false)
        val original=ExperienceOptions(islandAppearance=a,customBatteryColors=true,batteryLow=Color.RED)
        val copy=ExperienceOptions.decode(original.encode())
        assertEquals(a,copy.islandAppearance);assertEquals(Color.RED,copy.batteryLow)
        assertTrue(copy.customBatteryColors)
    }
}
