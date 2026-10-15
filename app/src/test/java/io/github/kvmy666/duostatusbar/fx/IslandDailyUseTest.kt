package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
class IslandDailyUseTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val d=context.resources.displayMetrics.density
    private val options=AuroraOptions(setOf("threeLevels","fingerExpansion","interruptibleIsland","contextActions","landscapeLayout"))
    private fun state()=IslandState(72,items=listOf(IslandItem(SlotIcon.WIFI,"Wi-Fi"),IslandItem(SlotIcon.BLUETOOTH,"Bluetooth")),aurora=options)
    private fun draw(v:IslandSummary.SummaryCanvas) {
        val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
        try {v.draw(Canvas(bitmap))} finally {bitmap.recycle()}
    }
    // offsetLocation simulates Android changing local coordinates after a window moves,
    // while raw coordinates continue to identify the same finger on the display.
    private fun event(v:IslandSummary.SummaryCanvas,type:Int,screenX:Float,screenY:Float,windowLeft:Float) {
        MotionEvent.obtain(0,100,type,screenX,screenY,0).also {
            it.offsetLocation(-windowLeft,0f);v.onTouchEvent(it);it.recycle()
        }
    }
    @Test fun `window widening halfway through a pull does not turn vertical motion into horizontal drag`() {
        for(body in listOf(720f,1290f)) {
            var left=192f;val resizes=mutableListOf<Boolean>();val actions=mutableListOf<String>()
            lateinit var view:IslandSummary.SummaryCanvas
            view=IslandSummary.SummaryCanvas(context,state(),animate=false,startCompact=true,expandedBodyHeight=body,
                action={actions.add(it)},resizeWindow={expanded->
                    resizes.add(expanded);left=if(expanded)0f else 192f
                    view.layout(0,0,if(expanded)1080 else 696,if(expanded)body.toInt() else 192)
                }){}.apply {layout(0,0,696,192)}
            event(view,MotionEvent.ACTION_DOWN,540f,90f,left)
            event(view,MotionEvent.ACTION_MOVE,540f,160f,left)
            event(view,MotionEvent.ACTION_MOVE,540f,210f,left)
            event(view,MotionEvent.ACTION_MOVE,540f,270f,left)
            event(view,MotionEvent.ACTION_UP,540f,270f,left)
            assertEquals("single physical resize during continuous pull",listOf(true),resizes)
            assertEquals("vertical pull reaches controls despite local x shift",1,view.expansionLevel)
            assertTrue(actions.isEmpty())
            // Cancelled collapse restores the settled quick level and keeps controls usable.
            event(view,MotionEvent.ACTION_DOWN,540f,80f,left)
            event(view,MotionEvent.ACTION_MOVE,540f,20f,left)
            event(view,MotionEvent.ACTION_CANCEL,540f,20f,left)
            assertEquals(1,view.expansionLevel);draw(view);assertNotNull(view.actionBounds("wifi"));view.dispose()
        }
    }
    @Test fun `returning the finger to the starting point restores the physically compact window`() {
        val sizes=mutableListOf<Boolean>()
        val v=IslandSummary.SummaryCanvas(context,state(),animate=false,startCompact=true,expandedBodyHeight=1290f,
            resizeWindow={sizes.add(it)}){}.apply {layout(0,0,1080,1290)}
        event(v,MotionEvent.ACTION_DOWN,540f,90f,0f)
        event(v,MotionEvent.ACTION_MOVE,540f,240f,0f)
        event(v,MotionEvent.ACTION_MOVE,540f,90f,0f)
        event(v,MotionEvent.ACTION_UP,540f,90f,0f)
        assertEquals(0,v.expansionLevel);assertEquals(listOf(true,false),sizes);v.dispose()
    }
    @Test fun `primary state never reappears as a second glyph in full detail inventory`() {
        val s=state().copy(items=state().items+IslandItem(SlotIcon.WIFI,"duplicate"))
        assertEquals(listOf(SlotIcon.BLUETOOTH),s.secondaryItems().map {it.icon})
        val recording=s.copy(items=listOf(IslandItem(SlotIcon.RECORD,"Recording"),IslandItem(SlotIcon.RECORD_TIME,"Time"),IslandItem(SlotIcon.WIFI,"Wi-Fi")),foreground=SlotIcon.RECORD)
        assertEquals(listOf(SlotIcon.WIFI),recording.secondaryItems().map {it.icon})
    }
    @Test fun `full landscape keeps media controls and shortcuts inside visible surface`() {
        val v=IslandSummary.SummaryCanvas(context,state().copy(items=listOf(IslandItem(SlotIcon.MEDIA,"Music"),IslandItem(SlotIcon.WIFI,"Wi-Fi")),
            playback=PlaybackSnapshot(playing=true,title="Track")),animate=false,expandedBodyHeight=240*d){}.apply {layout(0,0,(500*d).toInt(),(240*d).toInt())}
        draw(v)
        for(key in listOf("play","next","wifi","bluetooth","nfc","torch")) {
            val rect=requireNotNull(v.actionBounds(key));assertTrue("$key bottom",rect.bottom<=v.displayedBounds().bottom)
            assertTrue("$key touch height",rect.height()>=40*d-1)
        };v.dispose()
    }
}
