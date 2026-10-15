package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuroraIslandActivityTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun draw(v:IslandSummary.SummaryCanvas,name:String="") {val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));if(name.isNotEmpty())java.io.File("build/ui-gallery/$name.png").also {it.parentFile.mkdirs()}.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun `clock pause and resume exclude paused elapsed time and remaining never becomes negative`() {
        val c=ActivityClock(100,duration=1000)
        assertEquals(700L,c.remaining(400))
        val paused=c.pause(400)
        assertEquals(300L,paused.elapsed(5000));assertEquals(700L,paused.remaining(5000))
        assertEquals(400L,paused.resume(5000).elapsed(5100));assertEquals(0L,c.remaining(10000))
    }
    @Test fun `quick and full levels have separate bounds and contextual media offers previous play and next`() {
        val options=AuroraOptions(setOf("threeLevels","contextActions"))
        val state=IslandState(72,items=listOf(IslandItem(SlotIcon.MEDIA,"Music")),playback=PlaybackSnapshot(playing=true,title="Track"),aurora=options)
        val v=IslandSummary.SummaryCanvas(context,state,animate=false,startCompact=true,expandedBodyHeight=1290f){}.apply {layout(0,0,1080,1290)}
        val compact=v.displayedBounds().height()
        draw(v,"aurora-ilha-compacta");v.setLevel(1);draw(v,"aurora-ilha-controles");val quick=v.displayedBounds().height()
        assertTrue(quick>compact);assertNotNull(v.actionBounds("play"));assertNotNull(v.actionBounds("next"));assertNotNull(v.actionBounds("previous"))
        v.setLevel(2);draw(v,"aurora-ilha-detalhes");assertTrue(v.displayedBounds().height()>quick);v.dispose()
    }
    @Test fun `finger drag adjusts visible bounds before release and cancel restores settled level`() {
        val state=IslandState(72,aurora=AuroraOptions(setOf("threeLevels","fingerExpansion")))
        val v=IslandSummary.SummaryCanvas(context,state,animate=false,startCompact=true,expandedBodyHeight=1290f){}.apply {layout(0,0,1080,1290)}
        fun event(type:Int,y:Float)=MotionEvent.obtain(0,100,type,500f,y,0).also {v.onTouchEvent(it);it.recycle()}
        val original=v.displayedBounds().height();event(MotionEvent.ACTION_DOWN,100f);event(MotionEvent.ACTION_MOVE,500f)
        assertTrue(v.displayedBounds().height()>original);assertEquals(0,v.expansionLevel)
        event(MotionEvent.ACTION_CANCEL,500f);assertEquals(original,v.displayedBounds().height(),.01f);v.dispose()
    }
    @Test fun `timer action cannot remain reachable after timer is removed`() {
        val state=IslandState(72,items=listOf(IslandItem(SlotIcon.TIMER,"Timer",elapsedMs=1000,remainingMs=59000,timerRunning=true)),foreground=SlotIcon.TIMER,aurora=AuroraOptions(setOf("contextActions","timer","threeLevels")))
        val v=IslandSummary.SummaryCanvas(context,state,animate=false){}.apply {layout(0,0,1080,1290)}
        draw(v);assertNull("Observed phone timers must not expose local timer commands",v.actionBounds("timerPause"));v.update(state.copy(items=emptyList(),foreground=null));assertNull(v.actionBounds("timerPause"));v.dispose()
    }
    @Test fun `landscape composition stays within safe insets and reserves less height`() {
        val result=IslandGeometry.layout(2400,1080,3f,Insets.of(120,50,120,80),20,AuroraOptions(setOf("landscapeLayout","threeLevels")))
        assertTrue(result.width>result.height);assertTrue(result.left>=120);assertTrue(result.left+result.width<=2280);assertTrue(result.top+result.height<=1000)
    }
    @Test fun `new settings remain opt in and decoding drops unknown actions`() {
        assertTrue(AuroraOptions.decode(null).enabled.isEmpty())
        val parsed=AuroraOptions.decode(org.json.JSONObject("{\"enabled\":[\"timer\",\"remoteShell\"],\"timerMinutes\":999}"))
        assertEquals(setOf("timer"),parsed.enabled);assertEquals(60,parsed.timerMinutes)
    }
    @Test fun `pause cannot replay the completion of an already finished timer`() {
        AuroraActivities.reset()
        try {
            AuroraActivities.configure(AuroraOptions(setOf("timer","taskComplete"),1));AuroraActivities.command("timerStart")
            val now=android.os.SystemClock.elapsedRealtime()
            val finished=AuroraActivities.snapshot(now+60000)
            assertFalse(finished.timerRunning);assertEquals(0L,finished.timerRemainingMs)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(10))
            AuroraActivities.command("timerPause")
            val after=AuroraActivities.snapshot(android.os.SystemClock.elapsedRealtime())
            assertFalse(after.timerRunning);assertEquals(finished.completedAtUptime,after.completedAtUptime)
        } finally {AuroraActivities.reset()}
    }
    @Test fun `untrusted notification tags cannot grow completion cache without a bound`() {
        val tracker=NotificationProgressTracker()
        assertFalse(tracker.observe("x".repeat(1025),1,100,true));assertEquals(0,tracker.size)
        assertFalse(tracker.observe("small",30,100,true));assertTrue(tracker.observe("small",100,100,true))
    }
}
