package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
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
class NfcPhoneSimulationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun `distant scenario never claims completion and every scenario stops`() {
        val p=NfcPhoneSimulation(context)
        for(t in 0L until 6000L step 50)assertEquals(1,p.stage(t,NfcPhoneSimulation.Outcome.MOVE_CLOSER))
        for(o in NfcPhoneSimulation.Outcome.entries){assertEquals(0,p.stage(-1,o));assertEquals(0,p.stage(6000,o))}
        assertEquals(2,p.stage(2000,NfcPhoneSimulation.Outcome.READ))
        assertEquals(3,p.stage(4000,NfcPhoneSimulation.Outcome.READ))
        assertEquals(4,p.stage(4000,NfcPhoneSimulation.Outcome.DECLINED));p.dispose()
    }
    @Test fun `simulations render actual ring across phone header with distinct results`() {
        val p=NfcPhoneSimulation(context)
        val a=Bitmap.createBitmap(360,600,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(360,600,Bitmap.Config.ARGB_8888)
        p.draw(Canvas(a),360f,600f,4200,NfcPhoneSimulation.Outcome.READ)
        p.draw(Canvas(b),360f,600f,4200,NfcPhoneSimulation.Outcome.DECLINED)
        assertFalse(a.sameAs(b))
        // The real ring/clock survive the effect; top quarter changes and content remains present.
        assertNotEquals(0,a.getPixel(310,30));assertNotEquals(0,a.getPixel(180,300))
        a.recycle();b.recycle();p.dispose()
    }
    @Test fun `export phone simulations for visual review`() {
        val p=NfcPhoneSimulation(context)
        for(o in listOf(NfcPhoneSimulation.Outcome.READ,NfcPhoneSimulation.Outcome.DECLINED)) {
            val dir=File("build/nfc-phone-preview/${o.name.lowercase()}").apply {mkdirs()}
            for(i in 0..120) {
                val b=Bitmap.createBitmap(360,600,Bitmap.Config.ARGB_8888)
                p.draw(Canvas(b),360f,600f,i*50L,o)
                File(dir,"frame-%03d.png".format(i)).outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))}
                b.recycle()
            }
        }
        p.dispose()
    }
}
