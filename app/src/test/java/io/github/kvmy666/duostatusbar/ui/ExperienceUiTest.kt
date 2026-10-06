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
        a.setContent {DuoTheme {Column {var settings by remember {mutableStateOf(current)};ExperienceSection(settings,{settings=it;current=it},SearchGate {it.firstOrNull()=="Novidades"})}}}
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
