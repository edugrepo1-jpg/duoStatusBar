package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class EffectsControllerTest {
    @Test fun `keyguard dismissal without user present still shows the full unlock check`() {
        val (controller,view)=fixture()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val keyguard=Shadows.shadowOf(context.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        set(controller,"running",false)
        controller.start()
        try {
            controller.broadcast(Intent(Intent.ACTION_SCREEN_OFF))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(9000,TimeUnit.MILLISECONDS)
            controller.broadcast(Intent(Intent.ACTION_SCREEN_ON))
            assertEquals(-1L,view.effects.checkMs)
            keyguard.setKeyguardLocked(false)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(250,TimeUnit.MILLISECONDS)
            assertTrue("actual dismissal must trigger without USER_PRESENT",view.effects.checkMs>=0)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            assertEquals(1f,EffectTimeline.check(view.effects.checkMs),0f)
            assertEquals(0f,EffectTimeline.checkNormal(view.effects.checkMs),0f)
            controller.broadcast(Intent(Intent.ACTION_USER_PRESENT))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1350,TimeUnit.MILLISECONDS)
            assertTrue("late broadcast must not restart the check",EffectTimeline.check(view.effects.checkMs)<1f)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertEquals(-1L,view.effects.checkMs)
        } finally { controller.stop();view.teardown() }
    }
    private fun set(owner:Any,name:String,value:Any) {
        owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(owner,value)
    }
    private fun get(owner:Any,name:String):Any = owner.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(owner)
    private fun call(owner:Any,name:String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible=true }.invoke(owner)
    private fun fixture():Pair<EffectsController,DuoCanvasView> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val host=DuoIconHost(context)
        val view=DuoCanvasView(context).apply {
            layout(0,0,120,136);animationsEnabled=false
            render(DuoMapping.visual(72,false,false,true,3,4,false))
        }
        set(host,"element",view)
        val controller=EffectsController(context,host) { }
        set(controller,"running",true)
        return controller to view
    }
    @Test fun `unknown charger power still animates and never monopolizes the carousel`() {
        val (controller,view)=fixture()
        set(controller,"airplane",true);set(controller,"bluetooth",true)
        val indicators=get(controller,"indicators")
        set(indicators,"torch",true);set(indicators,"locationEnabled",true)
        controller.battery(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,1),72,true)
        assertTrue(view.effects.charging)
        assertTrue(view.effects.chargeMs>=0)
        val seen=mutableSetOf<SlotIcon>()
        for(i in 0..20) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(3200,TimeUnit.MILLISECONDS)
            view.effects.slot.icon?.let(seen::add)
        }
        assertTrue(seen.containsAll(listOf(SlotIcon.AIRPLANE,SlotIcon.BLUETOOTH,SlotIcon.LOCATION,SlotIcon.TORCH,SlotIcon.BOLT)))
        controller.stop();view.teardown()
    }
    @Test fun `unlock after a long lock screen has exclusive check despite camera charge audio and pocket`() {
        val (controller,view)=fixture()
        set(controller,"camera",true);set(controller,"charging",true)
        set(controller,"bluetooth",true);set(controller,"audioAt",android.os.SystemClock.uptimeMillis())
        set(controller,"screenOnAt",-100000L);set(controller,"screen",false)
        set(controller,"pocket",true);set(controller,"stoppedAt",android.os.SystemClock.uptimeMillis())
        call(controller,"updateCycle")
        controller.broadcast(Intent(Intent.ACTION_USER_PRESENT))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
        assertTrue(view.effects.checkMs in 1450..1550)
        assertEquals(-1L,view.effects.audioMs)
        assertEquals(1f,EffectTimeline.check(view.effects.checkMs),0f)
        assertEquals(0f,EffectTimeline.checkNormal(view.effects.checkMs),0f)
        assertEquals(1f,view.alpha,0f)
        assertTrue(view.effects.charging)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1350,TimeUnit.MILLISECONDS)
        assertTrue(EffectTimeline.check(view.effects.checkMs) in .1f.. .9f)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400,TimeUnit.MILLISECONDS)
        assertEquals(-1L,view.effects.checkMs)
        controller.stop();view.teardown()
    }
    @Test fun `brief proximity noise does not repeatedly freeze the carousel`() {
        val (controller,view)=fixture()
        val request=controller.javaClass.getDeclaredMethod("requestPocket",Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        repeat(5) {
            request.invoke(controller,true)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(250,TimeUnit.MILLISECONDS)
            assertFalse(get(controller,"pocket") as Boolean)
            request.invoke(controller,false)
        }
        request.invoke(controller,true)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
        assertTrue(get(controller,"pocket") as Boolean)
        request.invoke(controller,false)
        assertFalse(get(controller,"pocket") as Boolean)
        controller.stop();view.teardown()
    }
    @Test fun `new volume event waits for exclusive unlock then returns to carousel with charge estimate`() {
        val (controller,view)=fixture();val context=ApplicationProvider.getApplicationContext<Context>()
        Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions.ALL.encode()),context)
        try {
            set(controller,"charging",true);set(controller,"bluetooth",true);call(controller,"updateCycle")
            controller.broadcast(Intent(Intent.ACTION_USER_PRESENT))
            val spotlight=controller.javaClass.getDeclaredMethod("spotlight",SlotIcon::class.java,Long::class.javaPrimitiveType).apply {isAccessible=true}
            spotlight.invoke(controller,SlotIcon.VOLUME,2000L)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            assertEquals(1f,EffectTimeline.check(view.effects.checkMs),0f)
            assertEquals(0f,EffectTimeline.checkNormal(view.effects.checkMs),0f)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2000,TimeUnit.MILLISECONDS)
            assertEquals(-1L,view.effects.checkMs);assertEquals(SlotIcon.VOLUME,view.effects.slot.icon)
            assertTrue("entry must finish after check and both sequential fades: ${view.effects.slot}",view.effects.slot.opacity>.9f)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2300,TimeUnit.MILLISECONDS)
            assertNotEquals(SlotIcon.VOLUME,view.effects.slot.icon)
            val seen=mutableSetOf<SlotIcon>()
            repeat(8) {Shadows.shadowOf(Looper.getMainLooper()).idleFor(3200,TimeUnit.MILLISECONDS);view.effects.slot.icon?.let(seen::add)}
            assertTrue(seen.containsAll(listOf(SlotIcon.BLUETOOTH,SlotIcon.BOLT,SlotIcon.CHARGE_TIME)))
        } finally {controller.stop();view.teardown();Fx.sync(ModuleSettings.DEFAULT,context)}
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `expanded summary uses attached window and closes on screen off`() {
        val activity=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val (controller,view)=fixture();activity.get().setContentView(view)
        val decor=activity.get().window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(2400,android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0,0,1080,2400);view.layout(0,0,120,136)
        val host=get(controller,"host") as DuoIconHost
        val summary=get(host,"summary") as IslandSummary
        try {
            assertTrue(summary.open(view,false));assertTrue(get(host,"summaryOpen") as Boolean)
            assertEquals(0f,view.alpha,0f)
            controller.broadcast(Intent(Intent.ACTION_SCREEN_OFF))
            assertFalse(get(host,"summaryOpen") as Boolean)
            assertEquals(1f,view.alpha,0f)
        } finally {controller.stop();view.teardown();activity.pause().stop().destroy()}
    }
}
