package io.github.kvmy666.duostatusbar.ui
import android.os.Looper
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExperienceUiTest {
    @Test fun `battery color dialog validates input and releases the blurred backdrop`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val a=c.setup().get();val backdrop=OverlayBackdropState();var selected=ExperienceOptions(customBatteryColors=true)
        a.setContent {DuoTheme {CompositionLocalProvider(LocalOverlayBackdrop provides backdrop) {Column {
            var options by remember {mutableStateOf(selected)}
            BatteryColorsSection(options,true){options=it;selected=it}
        }}}}
        try {
            val root=a.findViewById<View>(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(5000,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,5000);idle()
            click(root,"Escolher cor: 0–20%");assertTrue(backdrop.active)
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();val modal=dialog.window!!.decorView
            modal.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.AT_MOST));modal.layout(0,0,1179,modal.measuredHeight);idle()
            val screenshot=android.graphics.Bitmap.createBitmap(modal.width,modal.height,android.graphics.Bitmap.Config.ARGB_8888)
            modal.draw(android.graphics.Canvas(screenshot));val folder=java.io.File("build/ui-gallery").apply {mkdirs()}
            java.io.File(folder,"cor-bateria-pt-BR.png").outputStream().use {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
            val input=nodes(modal).first {it.config.getOrNull(SemanticsProperties.TestTag)=="battery-color-hex"}
            input.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString("XYZ"));idle()
            val invalid=nodes(modal).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text=="Aplicar cor"}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            assertTrue(invalid.config.contains(SemanticsProperties.Disabled));assertEquals(ExperienceOptions().batteryLow,selected.batteryLow)
            nodes(modal).first {it.config.getOrNull(SemanticsProperties.TestTag)=="battery-color-hex"}.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString("123ABC"));idle()
            click(modal,"Aplicar cor");assertEquals(0xFF123ABC.toInt(),selected.batteryLow);assertFalse(backdrop.active)
            click(root,"Escolher cor: 21–80%");assertTrue(backdrop.active)
            click(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView,"Fechar");assertFalse(backdrop.active)
            assertEquals(ExperienceOptions().batteryMid,selected.batteryMid)
        } finally {c.pause().stop().destroy();idle()}
    }
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)
    private fun compose(view:View):View? {
        if(view.javaClass.simpleName=="AndroidComposeView")return view
        if(view is ViewGroup)for(i in 0 until view.childCount)compose(view.getChildAt(i))?.let {return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val view=compose(root)!!;val owner=view.javaClass.getMethod("getSemanticsOwner").invoke(view) as SemanticsOwner
        return owner.getAllSemanticsNodes(true)
    }
    private fun click(root:View,label:String) {
        val node=nodes(root).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==label}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke());idle()
    }
    @Test fun `preview draft remains local until Apply and timing preset is committed together`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val a=c.setup().get();var saved:ExperienceOptions?=null
        a.setContent {DuoTheme {Column {EffectsPreview(DuoSettings(enabled=true),SearchGate {true}) {saved=it}}}}
        try {
            val root=a.findViewById<View>(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(7000,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,7000);idle()
            click(root,"Abrir prévia interativa");click(root,"Rápido");click(root,"Experimentar tudo")
            assertNull(saved);click(root,"Desbloquear");click(root,"Captura");assertNull(saved)
            click(root,"Pausar");click(root,"Retomar");click(root,"Aplicar na barra")
            assertEquals(ExperienceOptions.ALL.copy(dwellMs=1600,exitMs=80,entryMs=120),saved)
        } finally {c.pause().stop().destroy();idle()}
    }
    @Test fun `activation keeps chosen icon scale and compass can be disabled independently`() {
        val c=Robolectric.buildActivity(ComponentActivity::class.java);c.get().setTheme(R.style.Theme_DuoStatusBar)
        val a=c.setup().get();var current=DuoSettings(enabled=true,experienceJson=ExperienceOptions(iconPercent=200).encode())
        a.setContent {DuoTheme {Column {var settings by remember {mutableStateOf(current)};ExperienceSection(settings,{settings=it;current=it},SearchGate {true})}}}
        try {
            val root=a.findViewById<View>(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(7000,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,7000);idle()
            click(root,"Ativar todas as novidades")
            assertEquals(ExperienceOptions.ALL.copy(iconPercent=200),ExperienceOptions.decode(current.experienceJson))
            val toggle=nodes(root).first {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text=="GPS com bússola"}==true&&it.config.getOrNull(SemanticsActions.OnClick)!=null}
            toggle.config[SemanticsActions.OnClick].action!!.invoke();idle()
            assertTrue("opening the explanation must not commit",ExperienceOptions.decode(current.experienceJson).compass)
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            nodes(dialog.window!!.decorView).first {it.config.getOrNull(SemanticsProperties.TestTag)=="feature-toggle"}.config[SemanticsActions.OnClick].action!!.invoke();idle()
            click(dialog.window!!.decorView,"Fechar")
            assertFalse(ExperienceOptions.decode(current.experienceJson).compass)
            assertTrue(ExperienceOptions.decode(current.experienceJson).music)
        } finally {c.pause().stop().destroy();idle()}
    }
}
