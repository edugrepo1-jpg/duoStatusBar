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
    @Test fun `headphone battery moves to the ring and compass changes rendered direction`() {
        fun pixels(b:Bitmap,predicate:(Int)->Boolean):Int {var n=0;for(y in 0..95)for(x in 45..195)if(predicate(b.getPixel(x,y)))n++;b.recycle();return n}
        val red=pixels(ring(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=15))) {Color.red(it)>180&&Color.green(it)<130&&Color.alpha(it)>100}
        val green=pixels(ring(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=81))) {Color.green(it)>150&&Color.red(it)<120&&Color.alpha(it)>100}
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
    @Test fun `offline slash pulses while disabled animation stays still and headphones have an open centre`() {
        fun glyph(icon:SlotIcon,time:Long,enabled:Boolean=true):Bitmap {
            val b=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
            val c=Canvas(b);c.translate(120f,120f);c.scale(3f,3f)
            StatusIconPainter().draw(c,icon,Color.WHITE,1f,EffectFrame(motionMs=time,motionEnabled=enabled))
            return b
        }
        val bright=glyph(SlotIcon.WIFI_OFFLINE,0)
        val normal=glyph(SlotIcon.WIFI,0)
        for(y in 0 until 240)for(x in 0 until 240) {
            // Outside the diagonal and its separation halo the underlying artwork is identical.
            val distance=kotlin.math.abs((x-120)-(y-120)*16f/18f)
            if(distance>26f)assertEquals("Wi-Fi base at $x,$y",normal.getPixel(x,y),bright.getPixel(x,y))
        }
        normal.recycle()
        val dim=glyph(SlotIcon.WIFI_OFFLINE,250)
        assertFalse(bright.sameAs(dim))
        val red=bright.getPixel(120,117)
        assertTrue("diagonal crosses the centre",Color.red(red)>200&&Color.green(red)<70)
        val stillA=glyph(SlotIcon.WIFI_OFFLINE,0,false)
        val stillB=glyph(SlotIcon.WIFI_OFFLINE,250,false)
        assertTrue(stillA.sameAs(stillB))
        val phones=glyph(SlotIcon.AIRPODS,0)
        assertEquals("open centre",0,Color.alpha(phones.getPixel(120,120)))
        assertTrue("left pad",Color.alpha(phones.getPixel(78,153))>200)
        assertTrue("right pad",Color.alpha(phones.getPixel(162,153))>200)
        listOf(bright,dim,stillA,stillB,phones).forEach {it.recycle()}
    }
}
