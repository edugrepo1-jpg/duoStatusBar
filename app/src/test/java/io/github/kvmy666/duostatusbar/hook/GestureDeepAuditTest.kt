package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class GestureDeepAuditTest {
    private fun idle(ms:Long)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    @Test fun `disabling tap action cancels an already queued single tap confirmation`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val sent=mutableListOf<Intent>()
        val context=object:ContextWrapper(activity.get()) {override fun sendBroadcast(intent:Intent) {sent.add(intent)}}
        val view=View(activity.get());activity.get().setContentView(view);view.layout(0,0,100,100)
        val gestures=ElementGestures(context)
        gestures.install(view,"toggle_wifi","no_action","no_action")
        val point=IntArray(2);view.getLocationOnScreen(point);val now=SystemClock.uptimeMillis()
        val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0]+50f,point[1]+50f,0)
        val up=MotionEvent.obtain(now,now+10,MotionEvent.ACTION_UP,point[0]+50f,point[1]+50f,0)
        try {
            assertTrue(gestures.handle(down,view));gestures.handle(up,view)
            gestures.install(view,"no_action","no_action","no_action")
            idle(650);assertTrue(sent.isEmpty())
        } finally {down.recycle();up.recycle();gestures.clear();activity.pause().stop().destroy()}
    }
    @Test fun `disabling gestures during a press cancels pending old summary`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view=View(activity.get());activity.get().setContentView(view);view.layout(0,0,100,100)
        val gestures=ElementGestures(activity.get());var opened=0
        gestures.install(view,"no_action","no_action","no_action") {opened++;true}
        val point=IntArray(2);view.getLocationOnScreen(point)
        val now=SystemClock.uptimeMillis()
        val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0]+50f,point[1]+50f,0)
        try {
            assertTrue(gestures.handle(down,view));idle(100)
            gestures.install(view,"no_action","no_action","no_action")
            idle(650);assertEquals(0,opened)
            assertFalse(view.isClickable)
        } finally {down.recycle();gestures.clear();activity.pause().stop().destroy()}
    }
    @Test fun `changed action set cancels old press and permits a fresh long press`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view=View(activity.get());activity.get().setContentView(view);view.layout(0,0,100,100)
        val gestures=ElementGestures(activity.get());var opened=0
        gestures.install(view,"wifi","no_action","no_action") {opened++;true}
        val point=IntArray(2);view.getLocationOnScreen(point)
        fun down():MotionEvent {val now=SystemClock.uptimeMillis();return MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0]+50f,point[1]+50f,0)}
        val first=down()
        try {
            assertTrue(gestures.handle(first,view));idle(100)
            gestures.install(view,"bluetooth","no_action","no_action") {opened++;true}
            idle(650);assertEquals(0,opened)
            view.getLocationOnScreen(point)
            val second=down();try {assertTrue(gestures.handle(second,view));idle(650);assertEquals(1,opened)} finally {second.recycle()}
        } finally {first.recycle();gestures.clear();activity.pause().stop().destroy()}
    }
    @Test fun `hidden press target never opens a summary from its delayed callback`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view=View(activity.get());activity.get().setContentView(view);view.layout(0,0,100,100)
        val gestures=ElementGestures(activity.get());var opened=0
        gestures.install(view,"no_action","no_action","no_action") {opened++;true}
        val point=IntArray(2);view.getLocationOnScreen(point);val now=SystemClock.uptimeMillis()
        val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0]+50f,point[1]+50f,0)
        try {
            assertTrue(gestures.handle(down,view));idle(100)
            view.alpha=0f
            idle(650);assertEquals(0,opened)
        } finally {down.recycle();gestures.clear();activity.pause().stop().destroy()}
    }
    @Test fun `removed press target cancels delayed action even if its own visibility is still visible`() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view=View(activity.get());activity.get().setContentView(view);view.layout(0,0,100,100)
        val gestures=ElementGestures(activity.get());var opened=0
        gestures.install(view,"no_action","no_action","no_action") {opened++;true}
        val point=IntArray(2);view.getLocationOnScreen(point);val now=SystemClock.uptimeMillis()
        val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0]+50f,point[1]+50f,0)
        try {
            assertTrue(gestures.handle(down,view));idle(100)
            activity.get().setContentView(View(activity.get()))
            assertFalse(view.isAttachedToWindow)
            idle(650);assertEquals(0,opened)
        } finally {down.recycle();gestures.clear();activity.pause().stop().destroy()}
    }
}
