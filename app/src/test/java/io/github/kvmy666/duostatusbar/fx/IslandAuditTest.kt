package io.github.kvmy666.duostatusbar.fx

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class IslandAuditTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Before fun language() { UiText.initialize(context,"pt-BR") }
    private fun draw(view:View)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun touch(view:View,action:Int,x:Float,y:Float) {
        MotionEvent.obtain(0,100,action,x,y,0).also { view.onTouchEvent(it);it.recycle() }
    }
    private fun summary(state:IslandState=IslandState(72,items=SlotIcon.entries.map { IslandItem(it,iconLabel(it)) }),height:Int=1600,action:(String)->Unit={},dismiss:()->Unit={}):IslandSummary.SummaryCanvas =
        IslandSummary.SummaryCanvas(context,state,animate=false,action=action,dismissed=dismiss).apply { layout(0,0,1080,height) }

    @Test fun `expanded and compact media targets are distinct forty eight dp and remain usable`() {
        val d=context.resources.displayMetrics.density
        for(height in listOf(1600,740)) {
            val actions=mutableListOf<String>();val view=summary(height=height,action={actions.add(it)})
            draw(view).recycle()
            for(key in listOf("previous","play","next","wifi","bluetooth","volume","torch")) {
                val bounds=requireNotNull(view.actionBounds(key))
                assertTrue("$key width",bounds.width()>=48*d-1)
                assertTrue("$key height",bounds.height()>=48*d-1)
                touch(view,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY())
                touch(view,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY())
            }
            assertEquals(listOf("previous","play","next","wifi","bluetooth","volume","torch"),actions)
            val buttons=listOf("previous","play","next").map { requireNotNull(view.actionBounds(it)) }
            assertTrue(buttons[0].right<=buttons[1].left+.1f&&buttons[1].right<=buttons[2].left+.1f)
            view.dispose()
        }
    }

    @Test fun `a dragged finger returning to its starting control cannot accidentally play music`() {
        val actions=mutableListOf<String>();val view=summary(action={actions.add(it)})
        draw(view).recycle();val hit=requireNotNull(view.actionBounds("play"))
        touch(view,MotionEvent.ACTION_DOWN,hit.centerX(),hit.centerY())
        touch(view,MotionEvent.ACTION_MOVE,hit.centerX(),hit.centerY()+100)
        touch(view,MotionEvent.ACTION_MOVE,hit.centerX(),hit.centerY())
        touch(view,MotionEvent.ACTION_UP,hit.centerX(),hit.centerY())
        assertTrue(actions.isEmpty())
        view.dispose()
    }

    @Test fun `screen reader can scroll every state and activate the actual close action`() {
        var closed=0;val view=summary(dismiss={closed++})
        val before=draw(view)
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null))
        val after=draw(view);assertFalse(before.sameAs(after))
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals(1,closed)
        before.recycle();after.recycle();view.dispose()
    }

    @Test fun `music redraws while visible playing and stops on pause hide close and detach`() {
        val activityController=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity=activityController.get();val root=FrameLayout(activity)
        val playing=IslandState(72,playback=PlaybackSnapshot(true,15000,180000,SystemClock.elapsedRealtime(),title="Demo"))
        val view=IslandSummary.SummaryCanvas(activity,playing,animate=false) {}
        root.addView(view,FrameLayout.LayoutParams(1080,1600));activity.setContentView(root)
        val main=shadowOf(Looper.getMainLooper());main.idle()
        view.layout(0,0,1080,1600);draw(view).recycle()
        val shadow=shadowOf(view);shadow.clearWasInvalidated()
        main.idleFor(Duration.ofMillis(100));assertTrue("active playback advances the panel",shadow.wasInvalidated())

        view.update(playing.copy(playback=playing.playback.copy(playing=false)));draw(view).recycle();shadow.clearWasInvalidated()
        main.idleFor(Duration.ofMillis(400));assertFalse("paused playback has no repeating redraw",shadow.wasInvalidated())

        view.update(playing);draw(view).recycle();view.visibility=View.GONE;shadow.clearWasInvalidated()
        main.idleFor(Duration.ofMillis(400));assertFalse("hidden panel has no repeating redraw",shadow.wasInvalidated())

        view.visibility=View.VISIBLE;draw(view).recycle();view.close();shadow.clearWasInvalidated()
        main.idleFor(Duration.ofMillis(400));assertFalse("closing cancels the clock",shadow.wasInvalidated())
        root.removeView(view);shadow.clearWasInvalidated();main.idleFor(Duration.ofMillis(400))
        assertFalse("detached panel leaves no clock",shadow.wasInvalidated())
        activityController.pause().stop().destroy()
    }

    @Test fun `an offscreen recording item does not start a redraw clock`() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity=controller.get();val root=FrameLayout(activity)
        val items=(0..25).map { IslandItem(SlotIcon.BLUETOOTH,"Bluetooth") }+IslandItem(SlotIcon.RECORD,"Recording")
        val view=IslandSummary.SummaryCanvas(activity,IslandState(72,items=items),animate=false) {}
        root.addView(view,FrameLayout.LayoutParams(1080,1600));activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle();view.layout(0,0,1080,1600);draw(view).recycle()
        val shadow=shadowOf(view);shadow.clearWasInvalidated()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        assertFalse("animation outside the viewport cannot wake the display",shadow.wasInvalidated())
        root.removeView(view);controller.pause().stop().destroy()
    }

    private fun glyph(painter:StatusIconPainter,icon:SlotIcon,frame:EffectFrame,opacity:Float=1f,background:Int=Color.TRANSPARENT,shortcut:Boolean=false):Bitmap {
        val bitmap=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        canvas.drawColor(background);canvas.translate(64f,64f)
        if(shortcut)painter.drawShortcut(canvas,icon,Color.BLACK,opacity,frame) else painter.draw(canvas,icon,Color.BLACK,opacity,frame)
        return bitmap
    }
    @Test fun `paused audio glyph is static active audio animates and notification holes respect background`() {
        val painter=StatusIconPainter()
        val paused=glyph(painter,SlotIcon.MEDIA,EffectFrame(motionMs=0,musicPlaying=false))
        val later=glyph(painter,SlotIcon.MEDIA,EffectFrame(motionMs=1500,musicPlaying=false));assertTrue(paused.sameAs(later))
        val playing=glyph(painter,SlotIcon.MEDIA,EffectFrame(motionMs=0,musicPlaying=true))
        val moving=glyph(painter,SlotIcon.MEDIA,EffectFrame(motionMs=1500,musicPlaying=true));assertFalse(playing.sameAs(moving))
        val notification=glyph(painter,SlotIcon.NOTIFICATION,EffectFrame(),background=Color.WHITE)
        assertEquals("transparent punctuation remains visible on light surfaces",Color.WHITE,notification.getPixel(64,63))
        listOf(paused,later,playing,moving,notification).forEach { it.recycle() }
    }
    @Test fun `reuse does not leak paint state and red recording dot obeys fade opacity`() {
        val reused=StatusIconPainter()
        val frame=EffectFrame(headphoneBattery=65,chargeRemainingMs=600000,recordElapsedMs=80000,networkText="4G",musicPlaying=true,motionMs=500)
        for(icon in SlotIcon.entries) {
            val shared=glyph(reused,icon,frame,.5f);val fresh=glyph(StatusIconPainter(),icon,frame,.5f)
            assertTrue("independent painter state: $icon",shared.sameAs(fresh));shared.recycle();fresh.recycle()
        }
        val recording=glyph(reused,SlotIcon.RECORD_TIME,frame,.5f)
        assertTrue("dot fades with its timer",Color.alpha(recording.getPixel(64,47)) in 110..140);recording.recycle()
        val shortcut=glyph(reused,SlotIcon.VOLUME,frame,shortcut=true)
        val readout=glyph(reused,SlotIcon.VOLUME,frame);assertFalse("volume shortcut is a speaker, never a fake 50 percent",shortcut.sameAs(readout))
        shortcut.recycle();readout.recycle()
    }
}
