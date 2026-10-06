package io.github.kvmy666.duostatusbar.fx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ForegroundAuditTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @After fun restoreOptions(){Fx.sync(ModuleSettings.DEFAULT,context)}

    @Test fun `latest actual start stays foreground and previous still-active event resumes`() {
        val events=ForegroundEvents()
        assertEquals(SlotIcon.MEDIA,events.update(listOf(SlotIcon.WIFI,SlotIcon.MEDIA)))
        assertEquals(SlotIcon.MICROPHONE,events.update(listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE)))
        assertEquals(SlotIcon.RECORD,events.update(listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE,SlotIcon.RECORD)))
        repeat(1000) {assertEquals(SlotIcon.RECORD,events.update(listOf(SlotIcon.RECORD,SlotIcon.MEDIA,SlotIcon.MICROPHONE,SlotIcon.BLUETOOTH)))}
        assertEquals(SlotIcon.MICROPHONE,events.update(listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE)))
        assertEquals(SlotIcon.MEDIA,events.update(listOf(SlotIcon.MEDIA,SlotIcon.WIFI)))
        assertNull(events.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH)))
    }
    @Test fun `resume is a new start and outranks an older microphone recording`() {
        val events=ForegroundEvents()
        events.update(listOf(SlotIcon.MEDIA));events.update(listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE))
        events.update(listOf(SlotIcon.MICROPHONE))
        assertEquals(SlotIcon.MEDIA,events.update(listOf(SlotIcon.MICROPHONE,SlotIcon.MEDIA)))
    }
    @Test fun `initial simultaneous detection has reproducible tie order without inventing timestamps`() {
        val a=ForegroundEvents();val b=ForegroundEvents()
        val events=listOf(SlotIcon.MEDIA,SlotIcon.MICROPHONE,SlotIcon.RECORD)
        assertEquals(SlotIcon.RECORD,a.update(events));assertEquals(a.current,b.update(events.reversed()))
        a.clear();assertNull(a.current)
    }
    @Test fun `held occupant remains for twenty minutes independent of one-second background interval`() {
        val cycle=SlotCycle();cycle.configure(ExperienceOptions(dwellMs=1000),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.MEDIA,SlotIcon.BLUETOOTH),0);cycle.hold(SlotIcon.MEDIA,100)
        assertEquals(SlotIcon.WIFI,cycle.frame(160).icon)
        assertEquals(.5f,cycle.frame(160).opacity,.001f)
        for(time in 400L..1200000L step 5000) {
            assertEquals(SlotFrame(SlotIcon.MEDIA),cycle.frame(time))
            assertEquals(Long.MAX_VALUE,cycle.nextDelay(time))
        }
        cycle.hold(null,1200000);cycle.frame(1200120)
        assertEquals(SlotFrame(SlotIcon.WIFI),cycle.frame(1200280))
    }
    @Test fun `check pause and WiFi preference never replace still active foreground`() {
        val cycle=SlotCycle();cycle.update(listOf(SlotIcon.WIFI,SlotIcon.MEDIA),0);cycle.hold(SlotIcon.MEDIA,0)
        cycle.frame(300);cycle.pause(500);cycle.resume(3660);cycle.preferWifi(3660)
        assertEquals(SlotIcon.MEDIA,cycle.frame(4000).icon)
    }
    @Test fun `held transfer uses full sequential exit then entry without resetting on polls`() {
        val cycle=SlotCycle();cycle.update(listOf(SlotIcon.WIFI,SlotIcon.MEDIA,SlotIcon.RECORD),0)
        cycle.hold(SlotIcon.MEDIA,100);cycle.frame(380)
        cycle.hold(SlotIcon.RECORD,1000)
        repeat(20) {cycle.hold(SlotIcon.RECORD,1000+it*10L)}
        assertEquals(SlotIcon.MEDIA,cycle.frame(1060).icon)
        assertEquals(.5f,cycle.frame(1060).opacity,.001f)
        assertEquals(SlotFrame(SlotIcon.RECORD,0f,.8f,0f),cycle.frame(1120))
        assertEquals(SlotFrame(SlotIcon.RECORD),cycle.frame(1280))
    }
    @Test fun `universal timing ignores stored custom overrides without deleting them`() {
        val options=ExperienceOptions(dwellMs=60000,fadeEnabled=false,iconSeconds=mapOf("WIFI" to 1),universalTiming=true)
        val cycle=SlotCycle();cycle.configure(options,0);cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        assertEquals(SlotIcon.WIFI,cycle.frame(59999).icon);assertEquals(SlotIcon.BLUETOOTH,cycle.frame(60000).icon)
        assertEquals(mapOf("WIFI" to 1),options.iconSeconds)
    }
    @Test fun `individual timing and maximum fades never silently lengthen one-second interval`() {
        val cycle=SlotCycle();cycle.configure(ExperienceOptions(dwellMs=60000,exitMs=600,entryMs=800,
            iconSeconds=mapOf("WIFI" to 1,"BLUETOOTH" to 2),universalTiming=false),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        cycle.frame(657);cycle.frame(800)
        assertEquals(SlotIcon.BLUETOOTH,cycle.frame(1000).icon)
        assertEquals(0f,cycle.frame(1000).opacity,.005f)
        assertEquals(SlotFrame(SlotIcon.BLUETOOTH),cycle.frame(1500))
        cycle.frame(2800);cycle.frame(3000)
        assertEquals(SlotFrame(SlotIcon.WIFI),cycle.frame(3500))
    }
    @Test fun `idle has no render callback and continuous shapes are capped at thirty frames`() {
        assertEquals(Long.MAX_VALUE,EffectCadence.delay(Long.MAX_VALUE,false,false,false))
        assertEquals(33L,EffectCadence.delay(Long.MAX_VALUE,false,true,false))
        assertEquals(1000L,EffectCadence.delay(Long.MAX_VALUE,false,false,true))
        assertEquals(16L,EffectCadence.delay(Long.MAX_VALUE,true,true,true))
        assertEquals(11L,EffectCadence.delay(11,false,true,true))
        assertEquals(2000L,EffectCadence.delay(Long.MAX_VALUE,false,false,false,2000))
    }
    @Test fun `known paused session removes media even when AudioManager is stale`() {
        val (controller,view)=fixture()
        try {
            val runtime=get(controller,"experience")
            set(controller,"music",true);set(runtime,"playback",PlaybackSnapshot(playing=false));set(runtime,"playbackKnown",true)
            call(controller,"updateCycle");call(controller,"draw")
            assertFalse((get(controller,"activeIcons") as List<*>).contains(SlotIcon.MEDIA))
            set(runtime,"playback",PlaybackSnapshot(playing=true));call(controller,"updateCycle");call(controller,"draw")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertEquals(SlotIcon.MEDIA,view.effects.slot.icon)
            set(runtime,"playback",PlaybackSnapshot(playing=false));call(controller,"updateCycle");call(controller,"draw")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertNotEquals(SlotIcon.MEDIA,view.effects.slot.icon)
        } finally {controller.stop();view.teardown()}
    }
    @Test fun `screen recording interrupts music until stop then music returns`() {
        val (controller,view)=fixture()
        Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions.ALL.copy(recordingTime=false).encode()),context)
        try {
            set(controller,"music",true);call(controller,"updateCycle");call(controller,"draw")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertEquals(SlotIcon.MEDIA,view.effects.slot.icon)
            set(get(controller,"indicators"),"recording",true);call(controller,"updateCycle");call(controller,"draw")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(30000,TimeUnit.MILLISECONDS)
            assertEquals(SlotIcon.RECORD,view.effects.slot.icon)
            set(get(controller,"indicators"),"recording",false);call(controller,"updateCycle");call(controller,"draw")
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertEquals(SlotIcon.MEDIA,view.effects.slot.icon)
        } finally {controller.stop();view.teardown()}
    }
    @Test fun `unlock remains exclusive and held microphone resumes after its three seconds`() {
        val (controller,view)=fixture()
        try {
            set(get(controller,"indicators"),"microphone",true);call(controller,"updateCycle");call(controller,"draw")
            controller.broadcast(Intent(Intent.ACTION_USER_PRESENT))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1500,TimeUnit.MILLISECONDS)
            assertEquals(1f,EffectTimeline.check(view.effects.checkMs),0f)
            assertEquals(0f,EffectTimeline.checkNormal(view.effects.checkMs),0f)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2000,TimeUnit.MILLISECONDS)
            assertEquals(-1L,view.effects.checkMs);assertEquals(SlotIcon.MICROPHONE,view.effects.slot.icon)
        } finally {controller.stop();view.teardown()}
    }
    @Test fun `temporary volume cannot hide active foreground event`() {
        val (controller,view)=fixture()
        try {
            set(controller,"music",true);call(controller,"updateCycle");call(controller,"draw")
            val spotlight=controller.javaClass.getDeclaredMethod("spotlight",SlotIcon::class.java,Long::class.javaPrimitiveType).apply {isAccessible=true}
            spotlight.invoke(controller,SlotIcon.VOLUME,2000L)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS)
            assertEquals(SlotIcon.MEDIA,view.effects.slot.icon)
            assertNull(getNullable(controller,"temporary"))
        } finally {controller.stop();view.teardown()}
    }
    @Test fun `confirmed relayed pause stays paused beyond heartbeat expiry`() {
        Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions.ALL.encode()),context)
        val runtime=RuntimeExperience(context,Handler(Looper.getMainLooper()),{},{});runtime.start()
        try {
            val receiver=get(runtime,"receiver") as BroadcastReceiver
            receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("playing",true))
            assertTrue(runtime.playback.playing);assertTrue(runtime.playbackKnown)
            receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("playing",false))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(40000,TimeUnit.MILLISECONDS)
            runtime.refresh();assertFalse(runtime.playback.playing);assertTrue(runtime.playbackKnown)
        } finally {runtime.stop()}
    }
    @Test fun `relay heartbeat remains valid at thirty seconds and expires at thirty five`() {
        Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions.ALL.encode()),context)
        val runtime=RuntimeExperience(context,Handler(Looper.getMainLooper()),{},{});runtime.start()
        try {
            (get(runtime,"receiver") as BroadcastReceiver).onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("playing",true))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(30000,TimeUnit.MILLISECONDS)
            runtime.refresh();assertTrue(runtime.playback.playing)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(5000,TimeUnit.MILLISECONDS)
            runtime.refresh();assertFalse(runtime.playback.playing)
        } finally {runtime.stop()}
    }
    @Test fun `starting while screen is off never schedules an animated charge render loop`() {
        val (controller,view)=fixture()
        try {
            Shadows.shadowOf(context.getSystemService(android.os.PowerManager::class.java)).setIsInteractive(false)
            set(controller,"running",false);controller.start()
            assertEquals(false,get(controller,"screen"))
            controller.battery(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(android.os.BatteryManager.EXTRA_PLUGGED,1),72,true)
            assertEquals(-1L,view.effects.chargeMs)
            val handler=get(controller,"handler") as Handler
            val tick=get(controller,"tick") as Runnable
            assertFalse(handler.hasCallbacks(tick))
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(5000,TimeUnit.MILLISECONDS)
            assertFalse(handler.hasCallbacks(tick))
            assertEquals(-1L,view.effects.chargeMs)
        } finally {controller.stop();view.teardown()}
    }
    @Test fun `unknown playback state has a stable timestamp across repeated snapshots`() {
        val session=android.media.session.MediaSession(context,"Unknown session")
        try {
            val first=RuntimeExperience.snapshot(session.controller)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(10000,TimeUnit.MILLISECONDS)
            assertEquals(first,RuntimeExperience.snapshot(session.controller))
            assertEquals(0L,first.updatedAt)
        } finally {session.release()}
    }
    private fun fixture():Pair<EffectsController,DuoCanvasView> {
        Fx.sync(ModuleSettings.DEFAULT.copy(experienceJson=ExperienceOptions.ALL.encode()),context)
        val host=DuoIconHost(context)
        val view=DuoCanvasView(context).apply {layout(0,0,120,136);animationsEnabled=false;render(DuoMapping.visual(72,false,false,true,3,4,false))}
        set(host,"element",view)
        val controller=EffectsController(context,host){}
        set(controller,"running",true)
        return controller to view
    }
    private fun set(owner:Any,name:String,value:Any?) {owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(owner,value)}
    private fun get(owner:Any,name:String):Any=getNullable(owner,name)!!
    private fun getNullable(owner:Any,name:String):Any?=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(owner)
    private fun call(owner:Any,name:String)=owner.javaClass.getDeclaredMethod(name).apply {isAccessible=true}.invoke(owner)
}
