package io.github.kvmy666.duostatusbar.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.*
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Real Compose controls must wake a sleeping simulation, not wait for the next carousel slot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
class PreviewDeepAuditTest {
    @Before fun pauseAutomaticVsync() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }
    private fun idle(ms:Long=100)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList())
    private fun click(root:View,label:String) {
        val compose=descendants(root).first {it.javaClass.simpleName=="AndroidComposeView"}
        val owner=compose.javaClass.getMethod("getSemanticsOwner").invoke(compose) as SemanticsOwner
        val node=owner.getAllSemanticsNodes(true).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==label}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke());idle()
    }
    private fun fixture(animations:Boolean=true,assertion:(View,DuoCanvasView)->Unit) {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().visible().get()
        activity.setContent {DuoTheme {Column {EffectsPreview(DuoSettings(enabled=true,animationsEnabled=animations),SearchGate {true})}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content)
            idle();assertTrue("Fixture must attach before native measurement",root.isAttachedToWindow)
            root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(7000,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,7000);idle()
            click(root,"Abrir prévia interativa");idle(600)
            val canvas=descendants(root).filterIsInstance<DuoCanvasView>().single()
            assertion(root,canvas)
        } finally {controller.pause().stop().destroy();idle()}
    }
    @Test fun `unlock wakes the settled preview immediately`() = fixture {root,canvas ->
        assertEquals(-1L,canvas.effects.checkMs)
        click(root,"Desbloquear")
        assertTrue("Unlock cannot wait for the next three-second carousel deadline",canvas.effects.checkMs in 0..250)
    }
    @Test fun `critical pulse wakes the settled preview immediately`() = fixture {root,canvas ->
        assertEquals(-1L,canvas.effects.pulseMs)
        click(root,"Bateria fraca")
        assertTrue("Critical pulse cannot expire before the simulation wakes",canvas.effects.pulseMs in 0..250)
    }
    @Test fun `screenshot wakes and enters before the normal carousel deadline`() = fixture {root,canvas ->
        click(root,"Captura");idle(400)
        assertEquals(io.github.kvmy666.duostatusbar.fx.SlotIcon.SCREENSHOT,canvas.effects.slot.icon)
        assertTrue(canvas.effects.slot.opacity>.9f)
    }
    @Test fun `master disabled passes static motion semantics to the real painter`() = fixture(false) {root,canvas ->
        assertFalse("Preview must disable the same icon pulse and wobble as the real status bar",canvas.effects.motionEnabled)
        click(root,"Desbloquear");assertEquals(-1L,canvas.effects.checkMs)
        click(root,"Bateria fraca");assertEquals(-1L,canvas.effects.pulseMs)
    }
}
