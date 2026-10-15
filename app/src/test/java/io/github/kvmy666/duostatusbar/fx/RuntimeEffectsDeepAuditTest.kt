package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Looper
import android.os.BatteryManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoIconHost
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class RuntimeEffectsDeepAuditTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @After fun restore(){AuroraActivities.reset();Fx.sync(ModuleSettings.DEFAULT,context)}
    private fun field(owner:Any,name:String):Any?=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(owner)
    private fun set(owner:Any,name:String,value:Any?){owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(owner,value)}
    private fun call(owner:Any,name:String){owner.javaClass.getDeclaredMethod(name).apply {isAccessible=true}.invoke(owner)}
    private fun idle(ms:Long)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    private data class Fixture(val host:DuoIconHost,val view:DuoCanvasView,val controller:EffectsController)
    private fun fixture(animations:Boolean=true,options:ExperienceOptions=ExperienceOptions(volume=true,screenshot=true)):Fixture {
        val settings=ModuleSettings.DEFAULT.copy(animationsEnabled=animations,experienceJson=options.encode())
        Fx.sync(settings,context)
        val host=DuoIconHost(context)
        val view=DuoCanvasView(context).apply {layout(0,0,120,136);animationsEnabled=false;render(DuoMapping.visual(72,false,false,true,3,4,false))}
        set(host,"element",view);set(host,"settings",settings)
        val controller=EffectsController(context,host){}
        set(controller,"running",true)
        val cycle=field(controller,"cycle") as SlotCycle
        cycle.configure(Fx.experience,SystemClock.uptimeMillis());cycle.update(listOf(SlotIcon.WIFI),SystemClock.uptimeMillis())
        return Fixture(host,view,controller)
    }

    @Test fun `static volume notice expires and returns to a quiet single WiFi icon without a new event`() {
        val f=fixture(false)
        try {
            val spotlight=f.controller.javaClass.getDeclaredMethod("spotlight",SlotIcon::class.java,Long::class.javaPrimitiveType).apply {isAccessible=true}
            spotlight.invoke(f.controller,SlotIcon.VOLUME,2000L)
            assertEquals(SlotIcon.VOLUME,f.view.effects.slot.icon)
            idle(2200)
            assertEquals(SlotIcon.WIFI,f.view.effects.slot.icon)
            assertNull(field(f.controller,"temporary"))
        } finally {f.controller.stop();f.view.teardown()}
    }

    @Test fun `screen shot notice also expires when animation master is off`() {
        val f=fixture(false)
        try {
            val spotlight=f.controller.javaClass.getDeclaredMethod("spotlight",SlotIcon::class.java,Long::class.javaPrimitiveType).apply {isAccessible=true}
            spotlight.invoke(f.controller,SlotIcon.SCREENSHOT,1800L)
            idle(2000)
            assertEquals(SlotIcon.WIFI,f.view.effects.slot.icon)
            assertNull(field(f.controller,"temporary"))
        } finally {f.controller.stop();f.view.teardown()}
    }

    @Test fun `controller shutdown clears pocket pause and sensor registration and reuse registers again`() {
        val f=fixture()
        val manager=context.getSystemService(SensorManager::class.java)
        val shadow=Shadows.shadowOf(manager)
        shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY))
        shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val listener=field(f.controller,"sensorListener") as SensorEventListener
        try {
            call(f.controller,"updateSensors")
            assertTrue(shadow.hasListener(listener))
            set(f.controller,"pocket",true);set(f.controller,"pocketCandidate",true)
            set(f.controller,"near",true);set(f.controller,"faceDown",true)
            set(f.controller,"stoppedAt",SystemClock.uptimeMillis())
            f.controller.stop()
            assertFalse(shadow.hasListener(listener))
            assertEquals(false,field(f.controller,"sensorsRegistered"))
            assertEquals(false,field(f.controller,"pocket"))
            assertEquals(false,field(f.controller,"near"));assertEquals(false,field(f.controller,"faceDown"))
            f.controller.start()
            assertTrue(shadow.hasListener(listener))
            assertEquals(false,field(f.controller,"pocket"))
            assertEquals(true,field(f.controller,"running"))
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `sensor callback queued before shutdown cannot restore pocket pause afterwards`() {
        val f=fixture()
        val listener=field(f.controller,"sensorListener") as SensorEventListener
        try {
            f.controller.stop()
            val event=ShadowSensorManager.createSensorEvent(3)
            event.sensor=ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
            event.values[2]=-9f
            listener.onSensorChanged(event)
            idle(600)
            assertEquals(false,field(f.controller,"faceDown"))
            assertEquals(false,field(f.controller,"pocketCandidate"))
            assertEquals(false,field(f.controller,"pocket"))
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `hidden camera mode releases compass even when location is the selected slot`() {
        val f=fixture()
        val manager=context.getSystemService(SensorManager::class.java)
        val shadow=Shadows.shadowOf(manager)
        shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR))
        val heading=field(f.controller,"heading") as HeadingObserver
        try {
            Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions(compass=true).encode()),context)
            val cycle=field(f.controller,"cycle") as SlotCycle
            cycle.update(listOf(SlotIcon.LOCATION),SystemClock.uptimeMillis())
            idle(400);call(f.controller,"draw")
            assertTrue(shadow.hasListener(heading))
            set(f.controller,"camera",true);call(f.controller,"draw")
            assertEquals(0f,f.view.alpha,0f)
            assertFalse(shadow.hasListener(heading))
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `call duration updates once per second even with movement disabled then stop clears ownership`() {
        val f=fixture(false,ExperienceOptions(aurora=AuroraOptions(setOf("voiceCall"))))
        try {
            set(f.controller,"communicationMode",AudioManager.MODE_IN_COMMUNICATION)
            set(f.controller,"communicationAt",SystemClock.elapsedRealtime())
            call(f.controller,"updateCycle");call(f.controller,"draw")
            assertEquals(SlotIcon.CALL,f.view.effects.slot.icon)
            val initial=f.view.effects.callElapsedMs
            idle(1250)
            assertTrue(f.view.effects.callElapsedMs>=initial+1000)
            f.controller.stop()
            assertEquals(-1L,field(f.controller,"communicationAt"))
            assertNull(field(f.controller,"foregroundIcon"))
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `paused timer releases newest foreground ownership and resumes existing media`() {
        val options=ExperienceOptions(music=true,aurora=AuroraOptions(setOf("timer")))
        val f=fixture(false,options)
        try {
            AuroraActivities.configure(options.aurora)
            set(f.controller,"music",true);call(f.controller,"updateCycle")
            assertEquals(SlotIcon.MEDIA,field(f.controller,"foregroundIcon"))
            AuroraActivities.command("timerStart");call(f.controller,"updateCycle");call(f.controller,"draw")
            assertEquals(SlotIcon.TIMER,field(f.controller,"foregroundIcon"))
            assertTrue(f.view.effects.timerRemainingMs>0)
            AuroraActivities.command("timerPause");call(f.controller,"updateCycle");call(f.controller,"draw")
            assertEquals(SlotIcon.MEDIA,field(f.controller,"foregroundIcon"))
            assertEquals(SlotIcon.MEDIA,f.view.effects.slot.icon)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `unknown system charge prediction never creates a fabricated charge time slot`() {
        val f=fixture(false,ExperienceOptions(chargeEstimate=true))
        try {
            set(f.controller,"charging",true);set(f.controller,"chargeRemaining",-1L)
            call(f.controller,"updateCycle")
            assertFalse((field(f.controller,"activeIcons") as List<*>).contains(SlotIcon.CHARGE_TIME))
            set(f.controller,"chargeRemaining",600000L);call(f.controller,"updateCycle")
            assertTrue((field(f.controller,"activeIcons") as List<*>).contains(SlotIcon.CHARGE_TIME))
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `movement disabled still switches multiple active icons at dwell deadlines without fades`() {
        val f=fixture(false)
        try {
            val cycle=field(f.controller,"cycle") as SlotCycle
            cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),SystemClock.uptimeMillis())
            call(f.controller,"draw")
            assertEquals(SlotIcon.WIFI,f.view.effects.slot.icon)
            idle(3250)
            assertEquals(SlotIcon.BLUETOOTH,f.view.effects.slot.icon)
            assertEquals(1f,f.view.effects.slot.opacity,0f)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `Aurora unlock works when the historical unlock bit is off and still expires`() {
        val options=ExperienceOptions(aurora=AuroraOptions(setOf("unlockBuild")))
        val f=fixture(true,options)
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=options.encode()),context)
        try {
            val unlock=f.controller.javaClass.getDeclaredMethod("unlock",String::class.java).apply {isAccessible=true}
            unlock.invoke(f.controller,"audit")
            call(f.controller,"draw")
            assertTrue(f.view.effects.checkMs>=0)
            idle(3300)
            assertEquals(-1L,f.view.effects.checkMs)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `Aurora wireless charging style activates without the historical charging bit`() {
        val options=ExperienceOptions(aurora=AuroraOptions(setOf("chargeStyles")))
        val f=fixture(true,options)
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=options.encode()),context)
        try {
            f.controller.battery(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_WIRELESS),72,true)
            assertTrue(f.view.effects.charging)
            assertTrue(f.view.effects.wirelessCharging)
            assertTrue(f.view.effects.chargeMs>=0)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `timer expiry releases old foreground before starting its finite completion notice`() {
        val options=ExperienceOptions(aurora=AuroraOptions(setOf("timer","taskComplete"),timerMinutes=1))
        val f=fixture(false,options)
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=options.encode()),context)
        try {
            val experience=field(f.controller,"experience") as RuntimeExperience
            experience.start()
            AuroraActivities.command("timerStart")
            assertEquals(SlotIcon.TIMER,field(f.controller,"foregroundIcon"))
            idle(60050)
            assertNull(field(f.controller,"foregroundIcon"))
            assertEquals(SlotIcon.TASK_DONE,f.view.effects.slot.icon)
            idle(2000)
            assertNotEquals(SlotIcon.TASK_DONE,f.view.effects.slot.icon)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `rotation replacement preserves unlock clock and exclusive priority over running media`() {
        val options=ExperienceOptions(music=true,aurora=AuroraOptions(setOf("unlockBuild")))
        val f=fixture(true,options)
        var rotated:DuoCanvasView?=null
        try {
            val initialSettings=ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=options.encode())
            Fx.sync(initialSettings,context)
            set(f.controller,"music",true);call(f.controller,"updateCycle")
            val unlock=f.controller.javaClass.getDeclaredMethod("unlock",String::class.java).apply {isAccessible=true}
            unlock.invoke(f.controller,"initial dismissal");call(f.controller,"draw")
            val started=field(f.controller,"checkAt")
            idle(1400)
            // A replacement Canvas receives the same controller after a real window replacement.
            rotated=DuoCanvasView(context).apply {
                layout(0,0,160,180);animationsEnabled=false
                render(DuoMapping.visual(72,false,false,true,3,4,false))
            }
            set(f.host,"element",rotated)
            val landscape=initialSettings.copy(sizePercent=165)
            set(f.host,"settings",landscape);Fx.sync(landscape,context)
            f.controller.settingsChanged()
            assertEquals(started,field(f.controller,"checkAt"))
            assertTrue(rotated.effects.checkMs in 1390..1450)
            assertEquals(0f,EffectTimeline.checkNormal(rotated.effects.checkMs),0f)
            assertEquals(-1L,rotated.effects.audioMs)
            // A late duplicate broadcast must not restart the existing three-second confirmation.
            unlock.invoke(f.controller,"late USER_PRESENT");call(f.controller,"draw")
            assertEquals(started,field(f.controller,"checkAt"))
            idle(1500)
            assertTrue(rotated.effects.checkMs in 2880..2940)
            assertTrue(EffectTimeline.check(rotated.effects.checkMs)<1f)
            idle(400)
            assertEquals(-1L,rotated.effects.checkMs)
            assertEquals(SlotIcon.MEDIA,rotated.effects.slot.icon)
        } finally {f.controller.stop();f.view.teardown();rotated?.teardown()}
    }
    @Test fun `hidden carousel handoff skips fade frame callbacks and still reaches the next icon`() {
        val f=fixture(true)
        try {
            val cycle=field(f.controller,"cycle") as SlotCycle
            cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),SystemClock.uptimeMillis())
            set(f.host,"summaryOpen",true)
            call(f.controller,"draw")
            idle(2890)
            val handoff=f.view.effects.motionMs
            idle(250)
            assertEquals("hidden handoff must not dispatch 16ms fade frames",handoff,f.view.effects.motionMs)
            idle(900)
            assertTrue(f.view.effects.motionMs>handoff)
            assertEquals(SlotIcon.BLUETOOTH,f.view.effects.slot.icon)
            assertEquals(1f,f.view.effects.slot.opacity,0f)
        } finally {f.controller.stop();f.view.teardown()}
    }
    @Test fun `open island charging skips animation frames and resumes motion after closing`() {
        val f=fixture(true,ExperienceOptions(aurora=AuroraOptions(setOf("chargeStyles"))))
        try {
            set(f.controller,"charging",true)
            set(f.host,"summaryOpen",true)
            call(f.controller,"draw")
            val initial=f.view.effects.motionMs
            idle(500)
            assertEquals("hidden charge must not run a 33ms frame loop",initial,f.view.effects.motionMs)
            idle(600)
            assertTrue("a slow callback keeps visibility recovery possible",f.view.effects.motionMs>initial)
            set(f.host,"summaryOpen",false)
            call(f.controller,"draw")
            val reopened=f.view.effects.motionMs
            idle(100)
            assertTrue("visible charge resumes animated frames",f.view.effects.motionMs>reopened)
        } finally {f.controller.stop();f.view.teardown()}
    }
}
