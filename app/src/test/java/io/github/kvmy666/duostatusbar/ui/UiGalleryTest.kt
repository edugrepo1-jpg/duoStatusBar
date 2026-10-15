package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.*
import io.github.kvmy666.duostatusbar.fx.AuroraOptions
import io.github.kvmy666.duostatusbar.fx.AuroraText
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.TimeUnit

/** Native app renders and interaction evidence, never an AI illustration of an unimplemented UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiGalleryTest {
    @Before fun realFrameTiming() {org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))}
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(400,TimeUnit.MILLISECONDS)
    private fun compose(view:View):View? {
        if(view.javaClass.simpleName=="AndroidComposeView")return view
        if(view is ViewGroup)for(i in 0 until view.childCount)compose(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val view=compose(root)?:error("Compose missing")
        return (view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner).getAllSemanticsNodes(true)
    }
    private fun text(n:SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()
    private fun byTag(root:View,tag:String)=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)==tag}
    private fun click(root:View,tag:String){assertTrue(byTag(root,tag).config[SemanticsActions.OnClick].action!!.invoke());idle()}
    private fun blurred(root:View)=nodes(root).any {it.config.getOrNull(BackdropBlurred)==true}
    private fun capture(root:View,name:String,dialog:Boolean=false) {
        idle();assertTrue("Capture fixture must be attached",root.isAttachedToWindow)
        root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,if(dialog)View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY))
        root.layout(0,0,1179,root.measuredHeight);idle()
        val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(b))
        val out=File("build/ui-gallery").apply {mkdirs()}
        File(out,"$name.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
    private fun activity(tag:String="pt-BR")=Robolectric.buildActivity(ComponentActivity::class.java).also { it.get().resources.updateConfiguration(android.content.res.Configuration(it.get().resources.configuration).apply {setLocales(android.os.LocaleList.forLanguageTags(tag))},it.get().resources.displayMetrics);it.get().setTheme(R.style.Theme_DuoStatusBar) }.setup().visible()

    @Test fun `reference switch persists its state while modal is open and blur ends on close`() {
        val context=ApplicationProvider.getApplicationContext<Context>();UiText.initialize(context,"pt-BR")
        DuoPrefs.write(context,DuoSettings(enabled=true))
        val c=activity();val a=c.get();a.setContent {DuoTheme(false){DuoSettingsScreen()}}
        try {
            val root=a.findViewById<View>(android.R.id.content);capture(root,"01-inicio-claro")
            assertFalse(blurred(root))
            // Header switch commits directly; a feature row still opens a didactic blurred modal.
            click(root,"feature-toggle");assertFalse(DuoPrefs.read(context).enabled);assertFalse(blurred(root))
            click(root,"feature-toggle");assertTrue(DuoPrefs.read(context).enabled);assertFalse(blurred(root))
            click(root,"nav-EFFECTS")
            click(root,"settings-group-indicators")
            nodes(root).first {text(it).contains("Inclui o NFC na alternância")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            val dialog=ShadowDialog.getLatestDialog();val front=dialog.window!!.decorView
            assertTrue(blurred(root));assertTrue(dialog.isShowing)
            capture(front,"nfc-explicacao-inicial",true)
            click(front,"feature-toggle")
            assertEquals(0,DuoPrefs.read(context).featFlags and 4096);assertTrue(dialog.isShowing);assertTrue(blurred(root))
            assertEquals(ToggleableState.Off,byTag(front,"feature-toggle").config[SemanticsProperties.ToggleableState])
            capture(front,"02-nfc-desativado",true)
            click(front,"feature-toggle");assertEquals(4096,DuoPrefs.read(context).featFlags and 4096)
            assertEquals(ToggleableState.On,byTag(front,"feature-toggle").config[SemanticsProperties.ToggleableState])
            capture(front,"03-nfc-ativado",true)
            nodes(front).first {text(it)=="Fechar"&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle();assertFalse(dialog.isShowing);assertFalse(blurred(root));capture(root,"04-inicio-apos-fechar")
        } finally {c.pause().stop().destroy();idle()}
    }
    @Test fun `overlapping popup leases cannot remove each others blur and disposal removes it`() {
        val state=OverlayBackdropState();val c=activity();val a=c.get();var one by mutableStateOf(true);var two by mutableStateOf(true)
        a.setContent {CompositionLocalProvider(LocalOverlayBackdrop provides state){if(one)OverlayBackdrop();if(two)OverlayBackdrop()}}
        try {
            idle();assertTrue(state.active);one=false;idle();assertTrue(state.active)
            two=false;idle();assertFalse(state.active);one=true;idle();assertTrue(state.active)
        } finally {c.pause().stop().destroy();idle()}
        assertFalse(state.active)
    }
    @Test fun `gallery covers every page scroll section both themes and three languages`() {
        val context=ApplicationProvider.getApplicationContext<Context>();DuoPrefs.write(context,DuoSettings(enabled=true))
        try {for((tag,dark) in listOf("pt-BR" to false,"pt-BR" to true,"en" to false,"es" to false)) {
            UiText.initialize(context,tag)
            val c=activity(tag);val a=c.get();a.setContent {DuoTheme(dark){DuoSettingsScreen()}}
            try {
                val root=a.findViewById<View>(android.R.id.content)
                val suffix="$tag-${if(dark)"escuro" else "claro"}"
                for(page in StudioPage.entries) {
                    capture(root,"medicao");click(root,"nav-${page.name}")
                    capture(root,"${page.name.lowercase()}-$suffix-01")
                    assertTrue(nodes(root).any {text(it).contains(page.heading)})
                    if(tag!="pt-BR")continue
                    // Capture compact navigation first, then deliberately reveal each category.
                    repeat(3) {
                        val collapsed=nodes(root).filter {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("settings-group-")==true && it.config.getOrNull(SemanticsProperties.StateDescription)=="Fechado"}.mapNotNull {it.config.getOrNull(SemanticsProperties.TestTag)}
                        collapsed.forEach { tag -> click(root,tag); capture(root,"${page.name.lowercase()}-$suffix-$tag"); click(root,"settings-back") }
                    }
                    capture(root,"${page.name.lowercase()}-$suffix-expanded")
                    var part=2
                    while(part<=24) {
                        val scroll=nodes(root).firstOrNull {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null&&it.config.getOrNull(SemanticsActions.ScrollBy)!=null}?:break
                        val range=scroll.config[SemanticsProperties.VerticalScrollAxisRange]
                        val before=range.value();if(before>=range.maxValue()-1)break
                        scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,1850f);idle()
                        if(range.value()<=before+1)break
                        capture(root,"${page.name.lowercase()}-$suffix-${part.toString().padStart(2,'0')}");part++
                    }
                }
            } finally {c.pause().stop().destroy();idle()}
        }} finally {UiText.initialize(context,"pt-BR")}
        File("build/ui-gallery/medicao.png").delete()
    }
    @Test fun `expanded interactive preview renders all scroll sections without applying simulation`() {
        val context=ApplicationProvider.getApplicationContext<Context>();UiText.initialize(context,"pt-BR")
        val c=activity();val a=c.get();var applied=false
        a.setContent {DuoTheme(false){Surface {Column(Modifier.fillMaxSize().padding(androidx.compose.ui.unit.Dp(16f)).verticalScroll(rememberScrollState())) {
            EffectsPreview(DuoSettings(enabled=true),SearchGate {true}) {applied=true}
        }}}}
        try {
            val root=a.findViewById<View>(android.R.id.content);capture(root,"previa-fechada")
            nodes(root).first {text(it).contains("Abrir prévia interativa")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            // Pause the simulation while capturing still images; this does not apply any preferences.
            nodes(root).first {text(it)=="Pausar"&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            capture(root,"previa-interativa-01")
            for(part in 2..12) {
                val scroll=nodes(root).first {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null&&it.config.getOrNull(SemanticsActions.ScrollBy)!=null}
                val range=scroll.config[SemanticsProperties.VerticalScrollAxisRange];val before=range.value()
                if(before>=range.maxValue()-1)break
                scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,1850f);idle()
                if(range.value()<=before+1)break
                capture(root,"previa-interativa-${part.toString().padStart(2,'0')}")
            }
            assertFalse(applied)
        } finally {c.pause().stop().destroy();idle()}
    }
    @Test fun `all feature explanation cards render and switch off on and unavailable are represented`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        UiText.initialize(context,"pt-BR")
        val field=FeatureGuide::class.java.getDeclaredField("entries").apply {isAccessible=true}
        @Suppress("UNCHECKED_CAST") val existing=field.get(null) as List<FeatureGuide>
        val guides=existing+AuroraOptions.KEYS.mapNotNull {FeatureGuide.find(AuroraText.title(it))}
        try {for((index,guide) in guides.withIndex()) {
            UiText.initialize(context,"pt-BR");val c=activity();val a=c.get();var enabled by mutableStateOf(false)
            a.setContent {DuoTheme(true){FeaturePresentation(guide.title,null,enabled,true,{}, {enabled=it})}}
            try {
                idle();val front=ShadowDialog.getLatestDialog().window!!.decorView
                capture(front,"funcao-${(index+1).toString().padStart(2,'0')}",true)
                assertTrue("Guide ${guide.title} must expose its explanation after native layout",nodes(front).any {text(it).contains(UiText.t("Como funciona"))})
                if(guide.title=="NFC") {click(front,"feature-toggle");capture(front,"nfc-ativado",true)}
            } finally {c.pause().stop().destroy();idle()}
        }
        for(tag in listOf("en","es")) {
            UiText.initialize(context,tag);val c=activity(tag);val a=c.get()
            a.setContent {DuoTheme {FeaturePresentation("NFC",UiText.t("Mostra NFC quando ativo."),true,true,{}, {})}}
            try {idle();capture(ShadowDialog.getLatestDialog().window!!.decorView,"nfc-$tag",true)} finally {c.pause().stop().destroy();idle()}
        }
        val c=activity();val a=c.get();UiText.initialize(context,"pt-BR")
        a.setContent {DuoTheme {FeaturePresentation("NFC",UiText.t("Mostra NFC quando ativo."),false,false,{}, {})}}
        try {
            idle();val front=ShadowDialog.getLatestDialog().window!!.decorView
            capture(front,"nfc-indisponivel",true)
            val toggle=byTag(front,"feature-toggle")
            assertNotNull("Unavailable NFC must stay disabled",toggle.config.getOrNull(SemanticsProperties.Disabled))
            val bounds=toggle.boundsInWindow;val viewport=a.resources.displayMetrics
            assertTrue("Unavailable toggle $bounds must remain within its physical viewport",
                bounds.left>=0&&bounds.top>=0&&bounds.right<=viewport.widthPixels+1&&bounds.bottom<=viewport.heightPixels+1)
            assertTrue("Unavailable toggle retains its touch area",bounds.width>=48*viewport.density-1&&bounds.height>=48*viewport.density-1)
        } finally {c.pause().stop().destroy();idle()}
        } finally {UiText.initialize(context,"pt-BR")}
    }
}

