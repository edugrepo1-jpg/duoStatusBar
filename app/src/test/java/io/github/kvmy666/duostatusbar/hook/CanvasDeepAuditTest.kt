package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class CanvasDeepAuditTest {
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `playing music bars remain identical when motion is disabled`() {
        fun pixels(time:Long,motion:Boolean):IntArray {
            val image=android.graphics.Bitmap.createBitmap(120,136,android.graphics.Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(image);canvas.translate(60f,77f)
            val frame=io.github.kvmy666.duostatusbar.fx.EffectFrame(
                slot=io.github.kvmy666.duostatusbar.fx.SlotFrame(io.github.kvmy666.duostatusbar.fx.SlotIcon.MEDIA),
                motionMs=time,musicPlaying=true,motionEnabled=motion)
            io.github.kvmy666.duostatusbar.fx.StatusIconPainter().draw(canvas,frame.slot.icon!!,android.graphics.Color.WHITE,1f,frame)
            return IntArray(120*136).also {image.getPixels(it,0,120,0,0,120,136);image.recycle()}
        }
        assertArrayEquals("The static master must affect music, even when a timer updates the island",pixels(0,false),pixels(200,false))
        assertFalse("The enabled wave must really animate",pixels(0,true).contentEquals(pixels(200,true)))
    }
    private fun idle(ms:Long)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    private fun pending(view:DuoCanvasView)=view.javaClass.getDeclaredField("pending").apply {isAccessible=true}.getBoolean(view)
    private fun fixture(block:(FrameLayout,DuoCanvasView)->Unit) {
        val controller=Robolectric.buildActivity(Activity::class.java).create().start().resume()
        val root=FrameLayout(controller.get());val view=DuoCanvasView(controller.get())
        root.addView(view,FrameLayout.LayoutParams(120,136));controller.get().setContentView(root)
        controller.visible().windowFocusChanged(true)
        val decor=controller.get().window.decorView
        val windowRoot=ReflectionHelpers.callInstanceMethod<Any>(decor,"getViewRootImpl")
        // Robolectric has no window server. Android's dispatch only invokes callbacks; it never
        // updates AttachInfo.mWindowVisibility, which is what getWindowVisibility actually reads.
        // Deliver the missing server state at ViewRoot, then its shared AttachInfo and callback.
        ReflectionHelpers.callInstanceMethod<Void>(windowRoot,"handleAppVisibility",ClassParameter.from(java.lang.Boolean.TYPE,true))
        idle(20)
        val attached=ReflectionHelpers.getField<Any>(view,"mAttachInfo")
        assertNotNull("A real attached window is required by this fixture",attached)
        ReflectionHelpers.setField(attached,"mWindowVisibility",View.VISIBLE)
        decor.dispatchWindowVisibilityChanged(View.VISIBLE)
        root.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));root.layout(0,0,1080,2400)
        view.render(DuoMapping.visual(72,false,false,true,3,4,false));idle(1800)
        assertTrue("The animation fixture must attach its window",view.isAttachedToWindow)
        assertEquals("The animation fixture must have a visible window",View.VISIBLE,view.windowVisibility)
        try {block(root,view)} finally {view.teardown();controller.pause().stop().destroy()}
    }
    @Test fun `hidden parent never queues animation work for a new battery state`() = fixture {root,view ->
        assertTrue(view.isShown);assertFalse(pending(view))
        root.visibility=View.GONE
        view.render(DuoMapping.visual(20,false,false,true,3,4,false))
        assertFalse("A hidden shade/header must not run a frame-rate tween",pending(view))
        idle(100);assertFalse(pending(view))
        root.visibility=View.VISIBLE
        assertTrue(view.isShown)
        idle(1800);assertFalse(pending(view))
        val shown=view.javaClass.getDeclaredField("shown").apply {isAccessible=true}.get(view) as DuoVisual
        assertEquals(20,shown.batteryLevel)
    }
    @Test fun `becoming hidden cancels the pending tween and becoming visible resumes it`() = fixture {root,view ->
        view.render(DuoMapping.visual(30,false,false,true,3,4,false));assertTrue(pending(view))
        root.visibility=View.INVISIBLE
        assertFalse("A callback already queued when the shade closes must be removed",pending(view))
        root.visibility=View.VISIBLE
        idle(1800);assertFalse(pending(view))
        val shown=view.javaClass.getDeclaredField("shown").apply {isAccessible=true}.get(view) as DuoVisual
        assertEquals(30,shown.batteryLevel)
    }
    @Test fun `repeated active notifications preserve the animation clock and hidden effects only cache state`() = fixture {_,view ->
        view.render(DuoMapping.visual(30,false,false,true,3,4,false))
        val clock=view.javaClass.getDeclaredField("lastFrame").apply {isAccessible=true}
        val before=clock.getLong(view)
        assertTrue(before>0)
        repeat(20){view.setRenderActive(true)}
        assertEquals("Repeated controller events must not restart the fill step",before,clock.getLong(view))
        view.setRenderActive(false)
        val hiddenFrame=io.github.kvmy666.duostatusbar.fx.EffectFrame(hideCells=true,motionMs=100)
        view.effects=hiddenFrame
        assertSame(hiddenFrame,view.effects)
        assertFalse("Caching a hidden effect must not queue frame work",pending(view))
        view.setRenderActive(true)
        idle(1800)
        assertFalse(pending(view))
        assertSame(hiddenFrame,view.effects)
    }
}
