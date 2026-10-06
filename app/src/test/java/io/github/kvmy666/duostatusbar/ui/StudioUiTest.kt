package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioUiTest {
    @Test fun `adaptive launcher renders the new vector mark`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val icon=context.getDrawable(R.mipmap.ic_launcher)!!
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,512,512);icon.draw(Canvas(bitmap))
        val out=File("build/studio-captures").apply {mkdirs()}
        File(out,"launcher.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(256,256))
        bitmap.recycle()
    }
    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idleFor(350,TimeUnit.MILLISECONDS)
    private fun composeView(view: View): View? {
        if(view.javaClass.simpleName=="AndroidComposeView")return view
        if(view is ViewGroup) for(i in 0 until view.childCount) composeView(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val compose=composeView(root) ?: error("Compose view missing")
        val owner=compose.javaClass.getMethod("getSemanticsOwner").invoke(compose) as SemanticsOwner
        return owner.getAllSemanticsNodes(true)
    }
    private fun text(node:SemanticsNode)=node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()
    private fun click(root:View,tag:String) {
        val node=nodes(root).first { it.config.getOrNull(SemanticsProperties.TestTag)==tag }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke());idle()
    }
    private fun confirmFeature(enable:Boolean) {
        val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val root=dialog.window!!.decorView
        val switch=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="feature-toggle"}
        assertEquals(if(enable)androidx.compose.ui.state.ToggleableState.Off else androidx.compose.ui.state.ToggleableState.On,switch.config[SemanticsProperties.ToggleableState])
        switch.config[SemanticsActions.OnClick].action!!.invoke();idle()
        nodes(root).first {text(it)=="Fechar"&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
    }
    private fun capture(root:View,name:String) {
        val metrics=root.resources.displayMetrics
        val w=(393*metrics.density).toInt();val maxH=(852*metrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(maxH,if(name=="feature-explanation")View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY))
        val h=root.measuredHeight
        root.layout(0,0,w,h);idle()
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val out=File("build/studio-captures").apply {mkdirs()}
        File(out,"$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        assertTrue("render must contain opaque pixels",bitmap.getPixel(w/2,if(name=="feature-explanation")h/2 else 10) ushr 24>0)
        bitmap.recycle()
    }
    @Test fun `all four pages compose in light and dark and retain working navigation`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        DuoPrefs.write(context,DuoSettings(enabled=true),DuoOrientation.PORTRAIT)
        for(dark in listOf(false,true)) {
            val controller=Robolectric.buildActivity(ComponentActivity::class.java)
            controller.get().setTheme(R.style.Theme_DuoStatusBar)
            val activity=controller.setup().get()
            activity.setContent {DuoTheme(dark) {Surface(color=MaterialTheme.colorScheme.background) {DuoSettingsScreen()}}}
            try {
                val root=activity.findViewById<View>(android.R.id.content);idle()
                val mode=if(dark)"dark" else "light"
                capture(root,"home-$mode")
                assertTrue(nodes(root).any {text(it).contains("Personalizar a barra")})
                for(page in listOf(StudioPage.VISUAL,StudioPage.EFFECTS,StudioPage.MORE)) {
                    click(root,"nav-${page.name}")
                    capture(root,"${page.name.lowercase()}-$mode")
                    assertTrue(nodes(root).any {text(it).contains(page.heading)})
                    assertTrue(nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="nav-${page.name}"}.config[SemanticsProperties.Selected])
                }
                click(root,"nav-HOME")
                assertTrue(nodes(root).any {it.config.getOrNull(SemanticsProperties.TestTag)=="studio-home"})
            } finally {controller.pause().stop().destroy();idle()}
        }
    }
    @Test fun `master switch commits once and global search reaches NFC from the home page`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        DuoPrefs.write(context,DuoSettings(enabled=true),DuoOrientation.PORTRAIT)
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().get()
        activity.setContent {DuoTheme {DuoSettingsScreen()}}
        try {
            val root=activity.findViewById<View>(android.R.id.content);idle();capture(root,"interaction")
            val master=nodes(root).first {text(it).contains("Personalizar a barra")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            master.config[SemanticsActions.OnClick].action!!.invoke();idle();confirmFeature(false)
            assertFalse(DuoPrefs.read(context,DuoOrientation.PORTRAIT).enabled)
            val newMaster=nodes(root).first {text(it).contains("Personalizar a barra")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            newMaster.config[SemanticsActions.OnClick].action!!.invoke();idle();confirmFeature(true)
            assertTrue(DuoPrefs.read(context,DuoOrientation.PORTRAIT).enabled)
            val search=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="studio-search"}
            search.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("NFC"));idle()
            assertTrue(nodes(root).any {text(it).contains("NFC")&&it.config.getOrNull(SemanticsActions.OnClick)!=null})
            click(root,"nav-VISUAL")
            assertTrue(nodes(root).any {text(it).contains("Desenho do anel")})
            val plus=nodes(root).first {it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Aumentar Tamanho do anel")==true}
            val oldSize=DuoPrefs.read(context,DuoOrientation.PORTRAIT).sizePercent
            plus.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertEquals(oldSize+1,DuoPrefs.read(context,DuoOrientation.PORTRAIT).sizePercent)
        } finally {controller.pause().stop().destroy();idle()}
    }
    @Test fun `language picker renders flags and every language card accepts selection`() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().get();val selected=mutableListOf<String>()
        activity.setContent {DuoTheme {Surface(modifier=androidx.compose.ui.Modifier.fillMaxSize()) {androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {LanguagePicker(true) {selected.add(it)}}}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content);idle();capture(root,"language-picker")
            for(name in listOf("Português","English","Español")) {
                val node=nodes(root).first { text(it).contains(name)&&it.config.getOrNull(SemanticsActions.OnClick)!=null }
                assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke());idle()
            }
            assertEquals(listOf("pt-BR","en","es"),selected)
        } finally {controller.pause().stop().destroy();idle()}
    }
    @Test fun `English and Spanish pages use translated navigation headings and controls`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        try {for(tag in listOf("en","es")) {
            io.github.kvmy666.duostatusbar.i18n.UiText.initialize(context,tag)
            val controller=Robolectric.buildActivity(ComponentActivity::class.java)
            controller.get().resources.updateConfiguration(android.content.res.Configuration(context.resources.configuration).apply {setLocales(android.os.LocaleList.forLanguageTags(tag))},controller.get().resources.displayMetrics)
            controller.get().setTheme(R.style.Theme_DuoStatusBar)
            val activity=controller.setup().get()
            activity.setContent {DuoTheme {DuoSettingsScreen()}}
            try {
                val root=activity.findViewById<View>(android.R.id.content);idle();capture(root,"home-$tag")
                assertFalse(nodes(root).any {text(it).contains("Personalizar a barra")})
                for(page in listOf(StudioPage.VISUAL,StudioPage.EFFECTS,StudioPage.MORE)) {
                    click(root,"nav-${page.name}");capture(root,"${page.name.lowercase()}-$tag")
                    assertTrue(nodes(root).any {text(it).contains(page.heading)})
                    assertFalse(nodes(root).any {text(it).contains("Experiência expandida")||text(it).contains("Seu resumo")})
                }
            } finally {controller.pause().stop().destroy();idle()}
        }} finally {io.github.kvmy666.duostatusbar.i18n.UiText.initialize(context,"pt-BR")}
    }

    @Test fun `feature explanation opens without changing preference and cancel has no side effect`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        io.github.kvmy666.duostatusbar.i18n.UiText.initialize(context,"pt-BR")
        val controller=Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_DuoStatusBar)
        val activity=controller.setup().get();var changed=0
        activity.setContent {DuoTheme {Surface {SettingSwitch("NFC","Mostra NFC quando ativo.",false,onChange={changed++})}}}
        try {
            val root=activity.findViewById<View>(android.R.id.content);idle();capture(root,"feature-row")
            nodes(root).first {text(it).contains("NFC")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertEquals(0,changed)
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val dialogRoot=dialog.window!!.decorView;capture(dialogRoot,"feature-explanation")
            assertTrue(nodes(dialogRoot).any {text(it).contains("Como funciona")})
            nodes(dialogRoot).first {text(it)=="Fechar"&&it.config.getOrNull(SemanticsActions.OnClick)!=null}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertEquals(0,changed)
        } finally {controller.pause().stop().destroy();idle()}
    }
    @Test fun `feature guides follow language changes after their first access`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        try {
            for(tag in listOf("pt-BR","en","es")) {
                io.github.kvmy666.duostatusbar.i18n.UiText.initialize(context,tag)
                val label=io.github.kvmy666.duostatusbar.i18n.UiText.t("NFC")
                val guide=FeatureGuide.find(label)!!
                val explanation=io.github.kvmy666.duostatusbar.i18n.UiText.t(guide.explanation)
                assertTrue(explanation.isNotBlank())
                if(tag!="pt-BR")assertNotEquals(guide.explanation,explanation)
            }
        } finally {io.github.kvmy666.duostatusbar.i18n.UiText.initialize(context,"pt-BR")}
    }

}
