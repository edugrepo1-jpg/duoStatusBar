package io.github.kvmy666.duostatusbar.ui

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
    private fun capture(root:View,name:String) {
        val metrics=root.resources.displayMetrics
        val w=(393*metrics.density).toInt();val h=(852*metrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        root.layout(0,0,w,h);idle()
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val out=File("build/studio-captures").apply {mkdirs()}
        File(out,"$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        assertTrue("render must contain opaque pixels",bitmap.getPixel(w/2,10) ushr 24>0)
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
            master.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertFalse(DuoPrefs.read(context,DuoOrientation.PORTRAIT).enabled)
            val newMaster=nodes(root).first {text(it).contains("Personalizar a barra")&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            newMaster.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertTrue(DuoPrefs.read(context,DuoOrientation.PORTRAIT).enabled)
            val search=nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="studio-search"}
            search.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("NFC"));idle()
            assertTrue(nodes(root).any {text(it).contains("NFC")&&it.config.getOrNull(SemanticsProperties.ToggleableState)!=null})
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
        activity.setContent {DuoTheme {Surface {LanguagePicker(true) {selected.add(it)}}}}
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

}
