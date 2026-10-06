package io.github.kvmy666.duostatusbar.fx

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.i18n.UiText
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IslandCompactTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val d=context.resources.displayMetrics.density
    @Before fun language() { UiText.initialize(context,"pt-BR") }
    private fun state()=IslandState(72,items=SlotIcon.entries.map {IslandItem(it,iconLabel(it))},
        playback=PlaybackSnapshot(true,30000,180000,SystemClock.elapsedRealtime(),title="Música de demonstração",color=0xFF6884CC.toInt()))
    private fun draw(view:View)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun touch(view:View,action:Int,x:Float,y:Float) {
        MotionEvent.obtain(0,100,action,x,y,0).also {view.onTouchEvent(it);it.recycle()}
    }
    private fun drag(view:View,fromY:Float,toY:Float) {
        touch(view,MotionEvent.ACTION_DOWN,view.width/2f,fromY)
        touch(view,MotionEvent.ACTION_MOVE,view.width/2f,toY)
        touch(view,MotionEvent.ACTION_UP,view.width/2f,toY)
    }
    private fun compact(actions:MutableList<String> = mutableListOf(),sizes:MutableList<Boolean> = mutableListOf()):IslandSummary.SummaryCanvas {
        lateinit var view:IslandSummary.SummaryCanvas
        view=IslandSummary.SummaryCanvas(context,state(),animate=false,startCompact=true,expandedBodyHeight=1600f,
            action={actions.add(it)},resizeWindow={ expanded ->sizes.add(expanded);view.layout(0,0,if(expanded)1080 else (232*d).toInt(),if(expanded)1600 else (64*d).toInt()) }) {}
        view.layout(0,0,(232*d).toInt(),(64*d).toInt());return view
    }
    @Test fun `pill starts small swipe down opens rich panel swipe up reduces actual requested size`() {
        val sizes=mutableListOf<Boolean>();val view=compact(sizes=sizes)
        draw(view).recycle();assertFalse(view.isExpanded)
        assertTrue(view.displayedBounds().height()<=64*d);assertTrue(view.displayedBounds().width()<=232*d)
        assertNull("hidden shortcuts have no hit regions",view.actionBounds("wifi"))
        drag(view,32*d,90*d);draw(view).recycle()
        assertTrue(view.isExpanded);assertEquals(1080,view.width);assertEquals(1600,view.height)
        assertNotNull(view.actionBounds("wifi"));assertNotNull(view.actionBounds("next"))
        // Collapse is a header gesture, leaving vertical state-list scrolling independent.
        drag(view,60*d,5*d);draw(view).recycle()
        assertFalse(view.isExpanded);assertEquals((232*d).toInt(),view.width);assertEquals((64*d).toInt(),view.height)
        assertEquals(listOf(true,false),sizes);view.dispose()
    }
    @Test fun `compact play remains forty eight dp and pill body tap expands without media action`() {
        val actions=mutableListOf<String>();val view=compact(actions=actions);draw(view).recycle()
        val play=requireNotNull(view.actionBounds("play"));assertTrue(play.width()>=48*d-1);assertTrue(play.height()>=48*d-1)
        touch(view,MotionEvent.ACTION_DOWN,play.centerX(),play.centerY());touch(view,MotionEvent.ACTION_UP,play.centerX(),play.centerY())
        assertEquals(listOf("play"),actions);assertFalse(view.isExpanded)
        touch(view,MotionEvent.ACTION_DOWN,80*d,32*d);touch(view,MotionEvent.ACTION_UP,80*d,32*d)
        assertTrue(view.isExpanded);assertEquals(listOf("play"),actions);view.dispose()
    }
    @Test fun `cancelled or second pointer cannot toggle a media button or resize panel`() {
        val actions=mutableListOf<String>();val sizes=mutableListOf<Boolean>();val view=compact(actions,sizes);draw(view).recycle()
        val play=requireNotNull(view.actionBounds("play"));val x=play.centerX();val y=play.centerY()
        touch(view,MotionEvent.ACTION_DOWN,x,y);touch(view,MotionEvent.ACTION_CANCEL,x,y);touch(view,MotionEvent.ACTION_UP,x,y)
        touch(view,MotionEvent.ACTION_DOWN,x,y);touch(view,MotionEvent.ACTION_POINTER_DOWN,x,y);touch(view,MotionEvent.ACTION_UP,x,y)
        assertTrue(actions.isEmpty());assertTrue(sizes.isEmpty());assertFalse(view.isExpanded);assertFalse(view.isPressed);view.dispose()
    }
    @Test fun `screen reader sees only compact actions and can expand and collapse without swiping`() {
        val view=compact();draw(view).recycle()
        val info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info)
        assertTrue(info.actionList.any {it.id==0x01000020});assertTrue(info.actionList.any {it.id==0x01000001})
        assertFalse(info.actionList.any {it.id==0x01000003});assertFalse(info.isScrollable)
        assertFalse(view.performAccessibilityAction(0x01000003,null))
        assertTrue(view.performAccessibilityAction(0x01000020,null));draw(view).recycle();assertTrue(view.isExpanded)
        assertTrue(view.performAccessibilityAction(0x01000020,null));assertFalse(view.isExpanded);view.dispose()
    }
    @Test fun `production popup physically resizes and respects display and safe areas`() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible();val activity=controller.get()
        val root=FrameLayout(activity);val anchor=View(activity);root.addView(anchor,FrameLayout.LayoutParams(100,80));activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle();anchor.layout(0,0,100,80)
        val summary=IslandSummary(activity,false) {};summary.update(state());assertTrue(summary.open(anchor,false))
        val small=requireNotNull(summary.windowSize);assertTrue(small.first<=232*d+1)
        val panel=requireNotNull(summary.currentView);panel.setExpanded(true)
        val full=requireNotNull(summary.windowSize);assertTrue(full.first>=small.first);assertTrue(full.second>small.second)
        val metrics=activity.resources.displayMetrics;assertTrue(full.first<=metrics.widthPixels);assertTrue(full.second<=metrics.heightPixels)
        panel.setExpanded(false);assertEquals(small,summary.windowSize);summary.dismiss();assertNull(summary.windowSize)
        controller.pause().stop().destroy()
    }
    @Test fun `layout bounds survive portrait landscape asymmetric cutout and tiny available screen`() {
        val cases=listOf(Triple(1272,2772,Insets.of(0,141,0,90)),Triple(2772,1272,Insets.of(141,0,90,0)),Triple(200,100,Insets.of(40,30,40,30)),Triple(1,1,Insets.NONE))
        for((width,height,insets) in cases) {
            val layout=IslandGeometry.layout(width,height,3f,insets,26)
            assertTrue(layout.left>=insets.left.coerceAtMost(width-1));assertTrue(layout.left+layout.width<=width)
            assertTrue(layout.top>=0);assertTrue(layout.top+layout.height<=height-insets.bottom.coerceAtMost(height-1))
            assertTrue(layout.compactWidth in 1..layout.width);assertTrue(layout.compactHeight in 1..layout.height)
        }
    }
    @Test fun `native captures reflect actual compact and expanded designs`() {
        val view=compact();val folder=File("build/experience-captures").apply {mkdirs()}
        fun capture(name:String) { val bitmap=draw(view);File(folder,name).outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle() }
        capture("island-pill.png");view.setExpanded(true);capture("island-expanded.png");view.dispose()
    }
}
