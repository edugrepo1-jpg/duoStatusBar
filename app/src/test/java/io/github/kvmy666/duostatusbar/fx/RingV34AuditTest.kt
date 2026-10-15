package io.github.kvmy666.duostatusbar.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RingV34AuditTest {
    private fun image(icon:SlotIcon,reveal:Float):IntArray {
        val bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.translate(80f,80f)
        StatusIconPainter().draw(canvas,icon,Color.WHITE,1f,EffectFrame(
            slot=SlotFrame(icon,reveal=reveal),drawIcons=true,volumePercent=55,
            chargeRemainingMs=600000,networkText="5G",timerRemainingMs=60000))
        return IntArray(160*160).also {bitmap.getPixels(it,0,160,0,0,160,160);bitmap.recycle()}
    }
    @Test fun `every live status has finite entrance and visible settled artwork`() {
        for(icon in SlotIcon.entries) {
            val entrance=image(icon,.5f)
            val settled=image(icon,1f)
            assertTrue("No settled artwork for $icon",settled.any { Color.alpha(it)>0 })
            assertTrue("No contour entrance for $icon",entrance.any { Color.alpha(it)>0 })
            assertFalse("Entrance must differ for $icon",entrance.contentEquals(settled))
        }
    }
    @Test fun `volume text reveal stays inside centered circle`() {
        // Reveal 0.95 includes most of the text contour. A former double-centering bug
        // shifted the text left beyond the -34 volume arc, unlike the settled label.
        val pixels=image(SlotIcon.VOLUME,.95f)
        val occupied=pixels.indices.filter {Color.alpha(pixels[it])>10}
        val left=occupied.minOf {it%160};val right=occupied.maxOf {it%160}
        assertTrue("Volume overflow left: $left",left>=43)
        assertTrue("Volume overflow right: $right",right<=117)
    }
    @Test fun `album palette cannot tint battery and check holds until final fade`() {
        val painter=EffectPainter()
        val frame=EffectFrame(musicPlaying=true,musicProgress=.5f,albumColor=Color.MAGENTA)
        assertEquals(Color.WHITE,painter.tint(Color.WHITE,frame))
        assertEquals(painter.tint(Color.GREEN,frame.copy(charging=true)),painter.tint(Color.GREEN,frame.copy(charging=true,albumColor=Color.RED)))
        for(time in listOf(500L,1000L,2000L,2800L))assertEquals(1f,EffectTimeline.check(time),.001f)
        assertEquals(0f,EffectTimeline.check(3000),0f)
        assertEquals(0f,EffectTimeline.checkNormal(2999),0f)
    }
}
