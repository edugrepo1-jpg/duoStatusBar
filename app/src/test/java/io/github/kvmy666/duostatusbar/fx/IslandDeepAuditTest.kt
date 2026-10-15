package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
class IslandDeepAuditTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun draw(view:IslandSummary.SummaryCanvas)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
    private fun touch(view:IslandSummary.SummaryCanvas,x:Float,y:Float) {
        for(type in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP))MotionEvent.obtain(0,100,type,x,y,0).also {view.onTouchEvent(it);it.recycle()}
    }
    @Test fun `absent media has no misleading transport controls or accessibility actions`() {
        val actions=mutableListOf<String>()
        val view=IslandSummary.SummaryCanvas(context,IslandState(72,items=listOf(IslandItem(SlotIcon.WIFI,"Wi-Fi"))),animate=false,action={actions.add(it)}){}.apply {layout(0,0,1080,1600)}
        draw(view).recycle()
        for(key in listOf("previous","play","next"))assertNull("$key without a player",view.actionBounds(key))
        val node=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(node)
        for(index in 0..2){assertFalse(node.actionList.any {it.id==0x01000000+index});assertFalse(view.performAccessibilityAction(0x01000000+index,null))}
        assertTrue(actions.isEmpty());view.dispose()
    }
    @Test fun `removed media cannot be invoked through stale hit regions before next draw`() {
        val actions=mutableListOf<String>()
        val state=IslandState(72,playback=PlaybackSnapshot(playing=true,title="Demo"))
        val view=IslandSummary.SummaryCanvas(context,state,animate=false,action={actions.add(it)}){}.apply {layout(0,0,1080,1600)}
        draw(view).recycle();val hit=requireNotNull(view.actionBounds("play"))
        view.update(state.copy(playback=PlaybackSnapshot()))
        touch(view,hit.centerX(),hit.centerY());assertTrue(actions.isEmpty());view.dispose()
    }
    @Test fun `a known paused player without a track title remains resumable`() {
        val view=IslandSummary.SummaryCanvas(context,IslandState(72,playback=PlaybackSnapshot(sourcePackage="test.player")),animate=false){}.apply {layout(0,0,1080,1600)}
        draw(view).recycle();assertNotNull(view.actionBounds("play"))
        assertTrue(view.performAccessibilityAction(0x01000001,null));view.dispose()
    }
    @Test fun `compact timed icons never invent a zero recording timer or calculation label`() {
        fun image(icon:SlotIcon,detail:String):Bitmap {
            val state=IslandState(72,items=listOf(IslandItem(icon,"Event",detail)))
            val view=IslandSummary.SummaryCanvas(context,state,animate=false,startCompact=true){}.apply {layout(0,0,696,192)}
            return draw(view).also {view.dispose()}
        }
        // The truthful duration lives in the item's detail. Its compact glyph must be the same
        // neutral dot/bolt used in the expanded grid, never a readout from an empty EffectFrame.
        for((timer,glyph) in listOf(SlotIcon.RECORD_TIME to SlotIcon.RECORD,SlotIcon.CHARGE_TIME to SlotIcon.BOLT)) {
            val actual=image(timer,"1:23");val expected=image(glyph,"1:23")
            val iconActual=Bitmap.createBitmap(actual,20,40,130,110)
            val iconExpected=Bitmap.createBitmap(expected,20,40,130,110)
            assertTrue("$timer truthful compact glyph",iconActual.sameAs(iconExpected))
            listOf(actual,expected,iconActual,iconExpected).forEach {it.recycle()}
        }
    }
}
