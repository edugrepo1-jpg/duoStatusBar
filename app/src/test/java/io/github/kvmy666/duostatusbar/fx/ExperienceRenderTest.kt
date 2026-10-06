package io.github.kvmy666.duostatusbar.fx
import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExperienceRenderTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun ring(frame:EffectFrame,thickness:Int=100):Bitmap {
        val view=DuoCanvasView(context).apply {animationsEnabled=false;arrivalEnabled=false;thickPercent=thickness}
        view.layout(0,0,240,272)
        view.render(DuoMapping.visual(72,frame.charging,false,true,3,4,false,networkText="5G",animateCharge=false))
        view.effects=frame.copy(managedSlots=true,iconRadius=55.5f-8f*thickness/100f-2f)
        val bitmap=Bitmap.createBitmap(240,272,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        assertTrue(view.isReady);view.teardown();return bitmap
    }
    private fun write(bitmap:Bitmap,name:String) {
        val out=File("build/experience-captures").apply {mkdirs()}
        File(out,name).outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
    }
    @Test fun `all icons enlarged earbuds compass offline warning music and transitions render natively`() {
        val frames=SlotIcon.entries.map { it to EffectFrame(slot=SlotFrame(it),headphoneBattery=65,networkText="5G",motionMs=1500,volumePercent=60,recordElapsedMs=97000,chargeRemainingMs=36*60000L) }+
            listOf(SlotIcon.AIRPODS to EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=15,iconPercent=200),
                SlotIcon.LOCATION to EffectFrame(slot=SlotFrame(SlotIcon.LOCATION),compass=true,compassDegrees=90f,iconPercent=200),
                SlotIcon.WIFI_OFFLINE to EffectFrame(slot=SlotFrame(SlotIcon.WIFI_OFFLINE),iconPercent=200),
                SlotIcon.MEDIA to EffectFrame(slot=SlotFrame(SlotIcon.MEDIA),musicPlaying=true,musicProgress=.35f,albumColor=0xFF8BAAFF.toInt(),motionMs=1500),
                SlotIcon.BOLT to EffectFrame(slot=SlotFrame(SlotIcon.BOLT),charging=true,chargeMs=1500,motionMs=1500))+
            listOf(0f,.5f,1f).map { SlotIcon.BLUETOOTH to EffectFrame(slot=SlotFrame(SlotIcon.BLUETOOTH,it,.8f+.2f*it,it),drawIcons=true) }
        val sheet=Bitmap.createBitmap(1000,((frames.size+3)/4)*305,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(sheet);canvas.drawColor(0xFF101014.toInt())
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.LTGRAY;textSize=13f}
        frames.forEachIndexed {i,(icon,frame)->
            val b=ring(frame);val x=i%4*250;val y=i/4*305
            canvas.drawBitmap(b,x.toFloat(),y.toFloat(),null)
            canvas.drawText("${iconLabel(icon)} · ${frame.iconPercent}%",x+8f,y+290f,text);b.recycle()
        }
        write(sheet,"icons.png");sheet.recycle()
    }
    @Test fun `earbud percentage turns red at fifteen and compass changes rendered direction`() {
        fun pixels(b:Bitmap,predicate:(Int)->Boolean):Int {var n=0;for(y in 205..240)for(x in 75..165)if(predicate(b.getPixel(x,y)))n++;b.recycle();return n}
        val red=pixels(ring(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=15))) {Color.red(it)>180&&Color.green(it)<130&&Color.alpha(it)>100}
        val green=pixels(ring(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=16))) {Color.green(it)>150&&Color.red(it)<120&&Color.alpha(it)>100}
        assertTrue("red percentage",red>10);assertTrue("green percentage",green>10)
        val a=ring(EffectFrame(slot=SlotFrame(SlotIcon.LOCATION),compass=true,compassDegrees=0f))
        val b=ring(EffectFrame(slot=SlotFrame(SlotIcon.LOCATION),compass=true,compassDegrees=90f))
        assertFalse(a.sameAs(b));a.recycle();b.recycle()
    }
    @Test fun `summary canvas draws all states and scrolls rather than truncating its list`() {
        val items=SlotIcon.entries.map {IslandItem(it,iconLabel(it),"Ativo")}
        val view=IslandSummary.SummaryCanvas(context,IslandState(72,false,items),animate=false,insetTop=70f) {}
        view.layout(0,0,1000,1800)
        val b=Bitmap.createBitmap(1000,1800,Bitmap.Config.ARGB_8888);view.draw(Canvas(b));write(b,"island.png")
        assertEquals(Color.TRANSPARENT,b.getPixel(500,20));assertTrue(Color.alpha(b.getPixel(500,100))>200)
        assertTrue(view.contentDescription.toString().contains("Captura de tela"))
        fun touch(action:Int,y:Float) {val event=android.view.MotionEvent.obtain(0,100,action,400f,y,0);view.onTouchEvent(event);event.recycle()}
        touch(android.view.MotionEvent.ACTION_DOWN,1500f);touch(android.view.MotionEvent.ACTION_MOVE,100f);touch(android.view.MotionEvent.ACTION_UP,100f)
        val scrolled=Bitmap.createBitmap(1000,1800,Bitmap.Config.ARGB_8888);view.draw(Canvas(scrolled));assertFalse(b.sameAs(scrolled))
        b.recycle();scrolled.recycle()
    }
}
