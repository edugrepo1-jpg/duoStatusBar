package io.github.kvmy666.duostatusbar.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.fx.SlotIcon
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w852dp-h393dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiDeepAuditTest {
    @Before fun advanceRealisticFrames() {
        // A zero-duration test vsync spins the dropdown's Compose animation without advancing time.
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)
    private fun compose(view:View):View? {
        if(view.javaClass.simpleName=="AndroidComposeView")return view
        if(view is ViewGroup)for(i in 0 until view.childCount)compose(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val view=compose(root)!!
        return (view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner).getAllSemanticsNodes(true)
    }
    @Test fun `airplane explanation uses airplane glyph in each supported language`() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            for(language in listOf("pt-BR","en","es")) {
                UiText.initialize(context,language)
                assertEquals(language,SlotIcon.AIRPLANE,featureIcon(UiText.t("Modo Avião")))
                assertNotNull(language,FeatureGuide.find(UiText.t("Modo Avião")))
            }
        } finally {UiText.initialize(context,"pt-BR")}
    }
    @Config(qualifiers="w852dp-h300dp-land-xxhdpi")
    @Test fun `feature close remains reachable when landscape height is constrained`() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().visible().get();var closed=false
        activity.setContent {DuoTheme {FeaturePresentation("Todos os indicadores","Explanation ".repeat(120),true,true,{closed=true},{})}}
        try {
            idle();val modal=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            modal.measure(View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY))
            modal.layout(0,0,2000,900);idle()
            val close=nodes(modal).first {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text=="Fechar"}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            // Robolectric may reset DecorView to 0x0 on a queued traversal. Semantics use
            // the actual Android window coordinates, not that transient decor allocation.
            val viewport=activity.resources.displayMetrics
            val bounds=close.boundsInWindow
            assertTrue("Close target $bounds must remain within ${viewport.widthPixels}x${viewport.heightPixels}",
                bounds.left>=0&&bounds.top>=0&&bounds.right<=viewport.widthPixels+1&&bounds.bottom<=viewport.heightPixels+1)
            assertTrue("Close must retain a 48dp touch target",bounds.width>=48*viewport.density-1&&bounds.height>=48*viewport.density-1)
            assertTrue(close.config[SemanticsActions.OnClick].action!!.invoke());assertTrue(closed)
        } finally {controller.pause().stop().destroy();idle()}
    }
    @Test fun `missing Auto Expand cannot assign actions that will silently do nothing`() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().visible().get()
        activity.setContent {DuoTheme {ActionsSection(DuoSettings(enabled=true),{},SearchGate {true},autoExpand=false)}}
        try {
            val root=activity.findViewById<View>(android.R.id.content)
            idle();assertTrue("Fixture must attach before native measurement",root.isAttachedToWindow)
            root.measure(View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY))
            root.layout(0,0,2000,1179);idle()
            val selector=nodes(root).first {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text==DuoActions.label("no_action")}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            assertTrue(selector.config[SemanticsActions.OnClick].action!!.invoke());idle()
            val globalClass=Class.forName("android.view.WindowManagerGlobal")
            val global=globalClass.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots=globalClass.getDeclaredField("mViews").apply {isAccessible=true}.get(global) as List<View>
            val popup=roots.first {compose(it)!=null&&nodes(it).any {node->node.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text==DuoActions.label("toggle_wifi")}==true}}
            val wifi=nodes(popup).first {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text==DuoActions.label("toggle_wifi")}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            val clear=nodes(popup).first {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text==DuoActions.label("no_action")}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            assertTrue(wifi.config.contains(SemanticsProperties.Disabled));assertFalse(clear.config.contains(SemanticsProperties.Disabled))
        } finally {controller.pause().stop().destroy();idle()}
    }
}
