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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NfcParticlePainterTest {
    private fun image(p:NfcParticlePainter,time:Long,reduced:Boolean=false)=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888).also {
        p.draw(Canvas(it),600f,200f,time,reducedMotion=reduced,statusBarHeightPx=48f)
    }
    private fun nonempty(b:Bitmap,left:Int=0,top:Int=0,right:Int=b.width,bottom:Int=b.height):Int {
        var count=0
        for(y in top until bottom)for(x in left until right)if(Color.alpha(b.getPixel(x,y))>0)count++
        return count
    }
    @Test fun `finite envelope leaves no pixels or running effect at endpoint`() {
        val p=NfcParticlePainter()
        for(t in listOf(-1L,0L,3000L,Long.MAX_VALUE)) {
            val b=image(p,t);assertEquals(0,nonempty(b));b.recycle()
        }
        val b=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888)
        assertFalse(p.draw(Canvas(b),600f,200f,3000L))
        assertFalse(p.draw(Canvas(b),Float.NaN,200f,1000L))
        assertFalse(p.draw(Canvas(b),600f,200f,1000L,alpha=Float.NaN))
        assertEquals(0,nonempty(b));b.recycle()
    }
    @Test fun `fixed seeds are deterministic and reduced motion freezes spatial pattern`() {
        val first=image(NfcParticlePainter(),900)
        val repeated=image(NfcParticlePainter(),900)
        val later=image(NfcParticlePainter(),1700)
        assertTrue(first.sameAs(repeated));assertFalse(first.sameAs(later))
        val reducedEarly=image(NfcParticlePainter(),500,true)
        val reducedLate=image(NfcParticlePainter(),2000,true)
        assertTrue(reducedEarly.sameAs(reducedLate))
        listOf(first,repeated,later,reducedEarly,reducedLate).forEach {it.recycle()}
    }
    @Test fun `transparent particles span full header and spill below status bar`() {
        val b=image(NfcParticlePainter(),1200)
        assertTrue(nonempty(b,0,0,150,200)>0)
        assertTrue(nonempty(b,450,0,600,200)>0)
        assertTrue(nonempty(b,0,48,600,160)>0)
        // It is a sparse transparent field, not an opaque replacement of the original status bar.
        assertTrue(nonempty(b,0,0,600,48)<600*48/3)
        assertEquals(0,nonempty(b,0,199,600,200))
        b.recycle()
    }
    @Test fun `native preview emits a three second sequence without host integration`() {
        val directory=File("build/nfc-particle-preview").apply {mkdirs()}
        val transparent=File("build/nfc-particle-preview/transparent").apply {mkdirs()}
        val painter=NfcParticlePainter()
        for(index in 0..60) {
            val b=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888)
            painter.draw(Canvas(b),600f,200f,index*50L,statusBarHeightPx=48f)
            File(transparent,"frame-%03d.png".format(index)).outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
            val background=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888)
            background.eraseColor(0xFF0B111A.toInt())
            Canvas(background).drawBitmap(b,0f,0f,null)
            File(directory,"frame-%03d.png".format(index)).outputStream().use {background.compress(Bitmap.CompressFormat.PNG,100,it)}
            background.recycle();b.recycle()
        }
        assertTrue(File(directory,"frame-060.png").length()>0)
    }
}
