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
class IslandLiveInteractionTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val d=context.resources.displayMetrics.density
    private fun draw(v:IslandSummary.SummaryCanvas) {
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b));b.recycle()
    }
    private fun event(v:IslandSummary.SummaryCanvas,action:Int,x:Float,y:Float) {
        MotionEvent.obtain(0,0,action,x,y,0).also { v.onTouchEvent(it);it.recycle() }
    }
    private fun media()=IslandState(72,foreground=SlotIcon.MEDIA,
        items=listOf(IslandItem(SlotIcon.MEDIA,"Music"),IslandItem(SlotIcon.WIFI_OFFLINE,"Offline")),
        playback=PlaybackSnapshot(playing=true,title="Track"),aurora=AuroraOptions.ALL)

    @Test fun `replacement control under a held finger is never activated`() {
        val actions=mutableListOf<String>()
        val timer=IslandState(72,foreground=SlotIcon.CALL,items=listOf(IslandItem(SlotIcon.CALL,"Call")),communication=true,aurora=AuroraOptions.ALL)
        val v=IslandSummary.SummaryCanvas(context,timer,animate=false,expandedBodyHeight=420*d,action={actions.add(it)}){}.apply {layout(0,0,(352*d).toInt(),(420*d).toInt())}
        draw(v)
        // A live call action changes to previous-track while the finger is held.
        val x=110*d;val y=134*d
        assertTrue(requireNotNull(v.actionBounds("call")).contains(x,y))
        event(v,MotionEvent.ACTION_DOWN,x,y)
        v.update(media());draw(v)
        assertTrue(requireNotNull(v.actionBounds("previous")).contains(x,y))
        event(v,MotionEvent.ACTION_UP,x,y)
        assertTrue(actions.isEmpty())
        val nfc=requireNotNull(v.actionBounds("nfc"))
        event(v,MotionEvent.ACTION_DOWN,nfc.centerX(),nfc.centerY())
        event(v,MotionEvent.ACTION_UP,nfc.centerX(),nfc.centerY())
        assertEquals(listOf("nfc"),actions)
        v.dispose()
    }

    @Test fun `offline status outside compact view does not accelerate music redraws`() {
        val v=IslandSummary.SummaryCanvas(context,media(),animate=true,startCompact=true,expandedBodyHeight=420*d){}.apply {layout(0,0,(352*d).toInt(),(420*d).toInt())}
        val delay=v.javaClass.getDeclaredMethod("motionDelayMs").apply {isAccessible=true}
        draw(v)
        assertEquals(33L,delay.invoke(v))
        v.update(media().copy(foreground=SlotIcon.WIFI_OFFLINE));draw(v)
        assertEquals(33L,delay.invoke(v))
        v.update(media().copy(items=listOf(IslandItem(SlotIcon.WIFI,"Wi-Fi")),playback=PlaybackSnapshot(),foreground=SlotIcon.WIFI));draw(v)
        assertEquals(1000L,delay.invoke(v))
        v.dispose()
    }
}
