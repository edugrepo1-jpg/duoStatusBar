package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
class AuroraPreviewTest {
    @Before fun setupFrames() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        UiText.initialize(ApplicationProvider.getApplicationContext<Context>(),"pt-BR")
    }
    private fun idle(ms:Long=100)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms,TimeUnit.MILLISECONDS)
    private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList())
    private fun nodes(root:View):List<SemanticsNode> {
        val view=descendants(root).first {it.javaClass.simpleName=="AndroidComposeView"}
        return (view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner).getAllSemanticsNodes(true)
    }
    private fun click(root:View,label:String) {
        assertTrue(nodes(root).first {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text==label}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke());idle()
    }
    @Test fun `simulated headphone confirmation and task completion wake the real preview`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=c.setup().visible().get()
        activity.setContent {DuoTheme {Column {EffectsPreview(DuoSettings(enabled=true,experienceJson=ExperienceOptions.ALL.encode()),SearchGate {true})}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content)
            idle();assertTrue("Fixture must attach before native measurement",root.isAttachedToWindow)
            root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(7000,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,7000);idle()
            click(root,"Abrir prévia interativa");idle(200)
            val canvas=descendants(root).filterIsInstance<DuoCanvasView>().single()
            assertEquals(AuroraOptions.KEYS,canvas.effects.aurora.enabled)
            assertEquals(-1L,canvas.effects.headphoneConnectMs)
            click(root,AuroraText.t("Conectar fones","Connect headphones","Conectar auriculares"))
            assertTrue(canvas.effects.headphoneConnectMs in 0..250)
            click(root,AuroraText.title("taskComplete"))
            assertTrue(canvas.effects.taskCompleteMs in 0..250)
        } finally {c.pause().stop().destroy();idle()}
    }
}
