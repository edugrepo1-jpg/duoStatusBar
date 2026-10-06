package io.github.kvmy666.duostatusbar.fx

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ChargeArcTest {
    private data class Arc(val box:RectF,val start:Float,val sweep:Float,val glow:Boolean)
    private class RecordingCanvas:Canvas() {
        val arcs=mutableListOf<Arc>()
        override fun drawArc(oval:RectF,startAngle:Float,sweepAngle:Float,useCenter:Boolean,paint:Paint) {
            if(sweepAngle>.001f)arcs.add(Arc(RectF(oval),startAngle,sweepAngle,paint.shader!=null))
        }
    }
    @Test fun `charging glow stays on actual battery track in both gap modes and every thickness`() {
        for(percent in listOf(true,false))for(thick in listOf(40,100,300)) {
            val view=DuoCanvasView(ApplicationProvider.getApplicationContext())
            view.animationsEnabled=false;view.thickPercent=thick;view.layout(0,0,120,136)
            view.render(DuoMapping.visual(72,false,false,percent,3,4,false))
            view.effects=EffectFrame(charging=true,managedSlots=true,motionMs=500)
            val canvas=RecordingCanvas();view.draw(canvas)
            assertTrue("renderer ready",view.isReady)
            val glow=canvas.arcs.filter { it.glow }
            val track=canvas.arcs.filter { !it.glow }.take(glow.size)
            assertEquals(if(percent)2 else 1,glow.size)
            assertEquals(track.size,glow.size)
            track.zip(glow).forEach { (base,beam)->
                assertEquals(base.box,beam.box)
                assertEquals(base.start,beam.start,.0001f)
                assertEquals(base.sweep,beam.sweep,.0001f)
            }
            view.teardown()
        }
    }
}
