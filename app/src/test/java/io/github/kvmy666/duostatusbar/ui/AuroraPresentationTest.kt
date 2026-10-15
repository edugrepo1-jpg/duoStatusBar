package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.TelemetryLedger
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.fx.SlotIcon
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.*
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
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuroraPresentationTest {
    @Before fun frames() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        UiText.initialize(ApplicationProvider.getApplicationContext<Context>(),"pt-BR")
    }
    @Test fun `Aurora palettes preserve contrast in both themes`() {
        assertEquals(Color(0xFFF5F6FA),StudioLight.background)
        assertEquals(Color(0xFF0B0D12),StudioDark.background)
        assertEquals(Color(0xFF191C24),StudioDark.surface)
        assertEquals(Color(0xFF10233F),StudioDark.onPrimary)
    }
    @Test fun `Aurora real text roles reach AA contrast in both themes`() {
        fun contrast(a:Color,b:Color):Double {
            val first=a.luminance().toDouble();val second=b.luminance().toDouble()
            return (maxOf(first,second)+0.05)/(minOf(first,second)+0.05)
        }
        assertTrue("White filled button label",contrast(Color.White,StudioBlue)>=4.5)
        for(scheme in listOf(StudioLight,StudioDark)) {
            assertTrue("Default filled controls including root activation",contrast(scheme.onPrimary,scheme.primary)>=4.5)
            assertTrue("Active navigation",contrast(scheme.onPrimaryContainer,scheme.primaryContainer)>=4.5)
            assertTrue("Surface links",contrast(scheme.primary,scheme.surface)>=4.5)
            assertTrue("Background links",contrast(scheme.primary,scheme.background)>=4.5)
            assertTrue("Secondary surface text",contrast(scheme.onSurfaceVariant,scheme.surface)>=4.5)
        }
    }
    private fun report(body:String)="${TelemetryLedger.MARKER}\n$body\n${TelemetryLedger.END_MARKER}"
    @Test fun `diagnostics reject absent truncated and unrelated icon evidence`() {
        assertNull(iconReportEvidence("feature.wifi detected=true reader_ok=true executions=8",SlotIcon.WIFI))
        assertNull(iconReportEvidence("${TelemetryLedger.MARKER}\nfeature.wifi detected=true",SlotIcon.WIFI))
        assertNull(iconReportEvidence(report("feature.nfc detected=true reader_ok=true executions=8"),SlotIcon.WIFI))
        val observed=iconReportEvidence(report("feature.wifi detected=false reader_ok=true executions=8"),SlotIcon.WIFI)!!
        assertEquals(false,observed.detected);assertEquals(true,observed.readerOk);assertEquals(8L,observed.executions)
    }
    @Test fun `past execution never overrides current opt out or inactive state`() {
        val settings=DuoSettings(enabled=false,experienceJson=ExperienceOptions(networkOnly=true).encode())
        val lines=iconPresentationReasons(settings,ModuleState.NEVER_LOADED,SlotIcon.MEDIA,
            report("feature.media detected=false reader_ok=true executions=12"))
        assertTrue(lines.any {it.contains("desligada nesta orientação")})
        assertTrue(lines.any {it.contains("LSPosed")})
        assertTrue(lines.any {it.startsWith("Somente Wi-Fi")})
        assertTrue(lines.any {it.contains("inativo no último relatório")})
        assertFalse(lines.any {it.contains("registrou um desenho")})
    }
    @Test fun `no report never claims that an icon works`() {
        val lines=iconPresentationReasons(DuoSettings(enabled=true),ModuleState.OK,SlotIcon.NFC,"")
        assertTrue(lines.any {it.startsWith("Não há observação")})
        assertFalse(lines.any {it.contains("registrou um desenho")})
    }
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(250,TimeUnit.MILLISECONDS)
    private fun compose(view:View):View? {
        if(view.javaClass.simpleName=="AndroidComposeView")return view
        if(view is ViewGroup)for(i in 0 until view.childCount)compose(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val view=compose(root)!!
        return (view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner).getAllSemanticsNodes(true)
    }
    @Test fun `Aurora home buttons route to the correct settings categories`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=c.setup().visible().get();var selected:StudioPage?=null
        activity.setContent {DuoTheme(false) {Column {StudioHome(DuoSettings(enabled=true),ModuleState.OK,{}, {selected=it})}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content)
            idle();assertTrue("Fixture must attach before native measurement",root.isAttachedToWindow)
            root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY))
            root.layout(0,0,1179,2556);idle()
            for((tag,page) in listOf("home-customize" to StudioPage.VISUAL,"home-test-effects" to StudioPage.EFFECTS)) {
                val button=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
                assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke());assertEquals(page,selected)
            }
        } finally {c.pause().stop().destroy();idle()}
    }
    @Test fun `header master changes configuration without claiming module loading`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=c.setup().visible().get();var updated:DuoSettings?=null
        activity.setContent {DuoTheme {Column {StudioHeader(StudioPage.HOME,false,false,DuoSettings(enabled=true),ModuleState.NEVER_LOADED,{updated=it})}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content);idle()
            assertTrue(nodes(root).any {it.config.getOrNull(SemanticsProperties.Text)?.any {text->text.text=="Ativação pendente"}==true})
            val toggle=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="feature-toggle"}
            assertTrue(toggle.config[SemanticsActions.OnClick].action!!.invoke());assertEquals(false,updated?.enabled)
        } finally {c.pause().stop().destroy();idle()}
    }
    @Test fun `default home exposes all three destinations above navigation`() {
        val context=ApplicationProvider.getApplicationContext<Context>();DuoPrefs.write(context,DuoSettings(enabled=true))
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=c.setup().visible().get();activity.setContent {DuoTheme(false) {DuoSettingsScreen()}}
        try {
            val root=activity.findViewById<View>(android.R.id.content)
            idle();assertTrue("Fixture must attach before native measurement",root.isAttachedToWindow)
            root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY))
            root.layout(0,0,1179,2556);idle()
            val nav=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="nav-HOME"}
            for(symbol in listOf(StudioSymbol.VISUAL,StudioSymbol.EFFECTS,StudioSymbol.ISLAND)) {
                val route=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="home-route-${symbol.name}"}
                assertTrue("$symbol must stay above the navigation",route.boundsInWindow.bottom<=nav.boundsInWindow.top)
                assertTrue("$symbol must keep its full touch area",route.boundsInWindow.height>=64*root.resources.displayMetrics.density-1)
            }
        } finally {c.pause().stop().destroy();idle()}
    }
}

