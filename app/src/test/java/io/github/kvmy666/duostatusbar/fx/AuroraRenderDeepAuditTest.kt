package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuroraRenderDeepAuditTest {
    @Test fun `headphone confirmation hands off through zero opacity before the carousel returns`() {
        val frame=EffectFrame(aurora=AuroraOptions(setOf("headphoneConfirm")))
        assertTrue(AuroraRender.headphone(frame.copy(headphoneConnectMs=1199)))
        assertTrue(AuroraRender.headphoneOpacity(frame.copy(headphoneConnectMs=1199))<.001f)
        assertFalse(AuroraRender.headphone(frame.copy(headphoneConnectMs=1200)))
        assertEquals(0f,AuroraRender.headphoneReturnOpacity(frame.copy(headphoneConnectMs=1200)),0f)
        assertEquals(.5f,AuroraRender.headphoneReturnOpacity(frame.copy(headphoneConnectMs=1280)),.001f)
        assertEquals(1f,AuroraRender.headphoneReturnOpacity(frame.copy(headphoneConnectMs=1360)),0f)
        assertEquals(1f,AuroraRender.headphoneReturnOpacity(frame.copy(headphoneConnectMs=1200,motionEnabled=false)),0f)
    }
    private fun options(vararg keys:String)=AuroraOptions(keys.toSet())
    private fun pixels(frame:EffectFrame,iconOnly:Boolean=false,number:Boolean=true,deviceCharging:Boolean=false):IntArray {
        val image=Bitmap.createBitmap(120,136,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image)
        if(iconOnly) {canvas.translate(60f,77.5f);StatusIconPainter().draw(canvas,frame.slot.icon!!,Color.WHITE,1f,frame)}
        else {
            val view=DuoCanvasView(ApplicationProvider.getApplicationContext<Context>())
            view.layout(0,0,120,136);view.effects=frame
            val visual=DuoMapping.visual(72,deviceCharging,false,true,3,4,false)
            view.render(if(number)visual else visual.copy(percentText=""));view.draw(canvas);assertTrue(view.isReady);view.teardown()
        }
        return IntArray(120*136).also {image.getPixels(it,0,120,0,0,120,136);image.recycle()}
    }
    private fun count(pixels:IntArray,x0:Int,x1:Int,y0:Int,y1:Int,predicate:(Int)->Boolean={Color.alpha(it)>10})=
        (y0 until y1).sumOf {y->(x0 until x1).count {x->predicate(pixels[y*120+x])}}
    @Test fun `Aurora off ignores new source and headset event ages without changing legacy pixels`() {
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.WIFI),managedSlots=true)
        assertArrayEquals(pixels(frame),pixels(frame.copy(headphoneConnectMs=200,batterySourceMs=300,wirelessCharging=true,headphoneBattery=65)))
        assertEquals(1f,AuroraRender.build(frame.copy(checkMs=0),0),0f)
    }
    @Test fun `unlock reconstruction is arc then number then dots and returns fully built`() {
        val frame=EffectFrame(checkMs=200,aurora=options("unlockBuild"),managedSlots=true)
        val early=pixels(frame)
        assertTrue(count(early,0,30,40,105)>0)
        // Arc caps may touch this rectangular region: compare identical geometry without a number.
        assertArrayEquals("Digits must not appear before their phase",pixels(frame,number=false),early)
        assertEquals(0,count(early,20,100,112,136))
        val middle=pixels(frame.copy(checkMs=1500))
        assertTrue(count(middle,35,85,0,45)>count(pixels(frame.copy(checkMs=1500),number=false),35,85,0,45))
        assertEquals(0,count(middle,20,100,112,136))
        assertTrue(count(pixels(frame.copy(checkMs=2500)),20,100,112,136)>0)
        assertEquals(1f,AuroraRender.build(frame.copy(checkMs=-1),2),0f)
    }
    @Test fun `unlock glow reaches custom battery number and signal dots`() {
        val frame=EffectFrame(checkMs=100,batteryColors=BatteryBandColors(Color.RED,Color.RED,Color.RED),aurora=options("unlockGlow"))
        val lit=pixels(frame)
        val green:(Int)->Boolean={Color.alpha(it)>50&&Color.green(it)>Color.red(it)+15}
        assertTrue("Custom palette cannot suppress unlock illumination of digits",count(lit,35,85,0,45,green)>0)
        assertTrue("Signal dots share the illumination",count(lit,20,100,112,136,green)>0)
        val normal=pixels(frame.copy(aurora=AuroraOptions()))
        assertEquals(0,count(normal,35,85,0,45,green))
        val phased=frame.copy(aurora=options("unlockGlow","unlockBuild"))
        assertTrue(count(pixels(phased.copy(checkMs=1500)),35,85,0,45,green)>0)
        assertTrue(count(pixels(phased.copy(checkMs=2500)),20,100,112,136,green)>0)
    }
    @Test fun `headphone confirmation owns centre for exactly1200ms without another icon behind`() {
        val frame=EffectFrame(headphoneConnectMs=700,aurora=options("headphoneConfirm"),managedSlots=true,headphoneBattery=65,slot=SlotFrame(SlotIcon.WIFI))
        val wifi=pixels(frame);val bluetooth=pixels(frame.copy(slot=SlotFrame(SlotIcon.BLUETOOTH)))
        assertArrayEquals(wifi,bluetooth)
        assertTrue(AuroraRender.headphone(frame.copy(headphoneConnectMs=1199)))
        assertFalse(AuroraRender.headphone(frame.copy(headphoneConnectMs=1200)))
        assertFalse(AuroraRender.headphone(frame.copy(motionEnabled=false)))
    }
    @Test fun `battery source transition is bounded opacity and never synthesizes a percentage`() {
        val frame=EffectFrame(batterySourceMs=300,aurora=options("batterySource"),slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=65)
        val device=DuoMapping.visual(72,false,false,true,3,4,false)
        assertEquals("65",BatteryPresentation.visual(device,frame).percentText)
        assertEquals("72",BatteryPresentation.visual(device,frame.copy(headphoneBattery=-1)).percentText)
        assertEquals(0f,AuroraRender.sourceOpacity(frame),0f)
        for(age in -1L..700L)assertTrue(AuroraRender.sourceOpacity(frame.copy(batterySourceMs=age)) in 0f..1f)
        assertEquals(1f,AuroraRender.sourceOpacity(frame.copy(motionEnabled=false)),0f)
    }
    @Test fun `wireless charge waves differ from cable and both stop motion under master off`() {
        val base=EffectFrame(charging=true,motionMs=120,managedSlots=true,aurora=options("chargeStyles"))
        assertFalse(pixels(base).contentEquals(pixels(base.copy(wirelessCharging=true))))
        for(wireless in listOf(false,true)) {
            val still=base.copy(wirelessCharging=wireless,motionEnabled=false)
            assertArrayEquals(pixels(still),pixels(still.copy(motionMs=800)))
        }
        val legacy=base.copy(motionEnabled=false,aurora=AuroraOptions())
        assertArrayEquals(pixels(legacy),pixels(legacy.copy(motionMs=800)))
    }
    @Test fun `call timer stopwatch and completed task render visible distinct occupants`() {
        val images=listOf(SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH,SlotIcon.TASK_DONE).map {icon->
            pixels(EffectFrame(slot=SlotFrame(icon),callElapsedMs=65000,timerElapsedMs=10000,timerRemainingMs=50000,stopwatchElapsedMs=34000,taskCompleteMs=400),true)
        }
        images.forEach {assertTrue(count(it,15,105,35,115)>30)}
        for(i in images.indices)for(j in 0 until i)assertFalse(images[i].contentEquals(images[j]))
    }
    @Test fun `safe geometry may reduce oversized icons and stroke without changing opt out bounds`() {
        assertEquals(3f,AuroraRender.thickness(300,false),0f)
        assertEquals(1.6f,AuroraRender.thickness(300,true),0f)
        assertEquals(1f,AuroraRender.iconScale(2f,12f,35f,false),0f)
        assertTrue(AuroraRender.iconScale(2f,12f,35f,true)<1f)
        assertEquals(Color.BLACK,AuroraRender.opposite(Color.WHITE));assertEquals(Color.WHITE,AuroraRender.opposite(Color.BLACK))
    }
    @Test fun `contrast and shutter alter their dedicated icon only when selected`() {
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.SCREENSHOT,reveal=.5f))
        assertFalse(pixels(frame,true).contentEquals(pixels(frame.copy(aurora=options("shutter")),true)))
        val nfc=frame.copy(slot=SlotFrame(SlotIcon.NFC))
        assertFalse(pixels(nfc,true).contentEquals(pixels(nfc.copy(aurora=options("adaptiveContrast")),true)))
    }
    @Test fun `touch response is bounded and opt out does not change artwork`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val view=DuoCanvasView(context);view.layout(0,0,120,136)
        view.effects=EffectFrame(aurora=options("touchResponse"));view.render(DuoMapping.visual(72,false,false,true,3,4,false))
        fun capture():IntArray {
            val bitmap=Bitmap.createBitmap(120,136,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap));return IntArray(120*136).also {bitmap.getPixels(it,0,120,0,0,120,136);bitmap.recycle()}
        }
        val normal=capture();view.touchResponse(true);assertFalse(normal.contentEquals(capture()))
        view.touchResponse(false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(220,java.util.concurrent.TimeUnit.MILLISECONDS)
        assertArrayEquals(normal,capture())
        view.effects=EffectFrame();view.touchResponse(true);assertArrayEquals(normal,capture());view.teardown()
    }
    @Test fun `completed task illuminates ring but never steals a foreground occupant`() {
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.MEDIA),managedSlots=true,musicPlaying=true,motionEnabled=true,
            taskCompleteMs=500,aurora=options("taskComplete","singleNotices"))
        val completion=pixels(frame);val ordinary=pixels(frame.copy(taskCompleteMs=-1))
        for(y in 55 until 100)for(x in 36 until 84)assertEquals("Foreground music must remain identical",ordinary[y*120+x],completion[y*120+x])
        assertFalse(ordinary.contentEquals(completion))
        assertEquals(0f,AuroraRender.taskGlow(frame.copy(taskCompleteMs=1800)),0f)
        assertEquals(0f,AuroraRender.taskGlow(frame.copy(motionEnabled=false)),0f)
        assertEquals(0f,AuroraRender.taskGlow(frame.copy(aurora=AuroraOptions())),0f)
    }
    @Test fun `unlock glow and construction share exactly3000ms including final check exit`() {
        val frame=EffectFrame(aurora=options("unlockGlow","unlockBuild"))
        for(age in listOf(1L,280L,700L,1500L,2500L,2720L,2999L))
            assertTrue("Illumination must remain active throughout the check at ${age}ms",AuroraRender.glow(frame.copy(checkMs=age))>0f)
        for(age in listOf(-1L,3000L,3001L,3160L))assertEquals(0f,AuroraRender.glow(frame.copy(checkMs=age)),0f)
        for(phase in 0..2) {
            assertEquals(0f,AuroraRender.build(frame.copy(checkMs=phase*1000L),phase),0f)
            assertEquals(1f,AuroraRender.build(frame.copy(checkMs=(phase+1)*1000L),phase),0f)
        }
        assertTrue("The final phase cannot finish early",AuroraRender.build(frame.copy(checkMs=2999),2)<1f)
        for(phase in 0..2)assertEquals(1f,AuroraRender.build(frame.copy(checkMs=3000),phase),0f)
        assertEquals(0f,AuroraRender.glow(frame.copy(checkMs=1500,motionEnabled=false)),0f)
        assertEquals(1f,AuroraRender.build(frame.copy(checkMs=1500,motionEnabled=false),2),0f)
    }
    @Test fun `custom battery bands never recolour phone charging or remove its effects`() {
        val palette=BatteryBandColors(Color.RED,Color.RED,Color.RED)
        for(aurora in listOf(AuroraOptions(),options("chargeStyles")))for(wireless in listOf(false,true)) {
            val frame=EffectFrame(charging=true,wirelessCharging=wireless,motionMs=450,managedSlots=true,aurora=aurora)
            assertArrayEquals("Charging art and colour must ignore discharge bands",pixels(frame,deviceCharging=true),pixels(frame.copy(batteryColors=palette),deviceCharging=true))
            assertArrayEquals("A charging effect frame also protects a delayed visual update",pixels(frame),pixels(frame.copy(batteryColors=palette)))
        }
        val delayedFrame=EffectFrame(managedSlots=true)
        assertArrayEquals("Charging visual state protects a delayed effect frame",pixels(delayedFrame,deviceCharging=true),pixels(delayedFrame.copy(batteryColors=palette),deviceCharging=true))
        assertFalse("Saved bands still apply after unplugging",pixels(delayedFrame).contentEquals(pixels(delayedFrame.copy(batteryColors=palette))))
        val headset=EffectFrame(charging=true,headphoneBattery=65,managedSlots=true,slot=SlotFrame(SlotIcon.AIRPODS))
        assertFalse("The headset is a separate source and keeps its chosen bands",pixels(headset,deviceCharging=true).contentEquals(pixels(headset.copy(batteryColors=palette),deviceCharging=true)))
    }
}
