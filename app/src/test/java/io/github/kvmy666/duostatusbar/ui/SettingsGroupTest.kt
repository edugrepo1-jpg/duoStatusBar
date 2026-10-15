package io.github.kvmy666.duostatusbar.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import io.github.kvmy666.duostatusbar.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
class SettingsGroupTest {
    private fun compose(v:View):View? {
        if(v.javaClass.simpleName=="AndroidComposeView")return v
        if(v is ViewGroup)for(i in 0 until v.childCount)compose(v.getChildAt(i))?.let{return it}
        return null
    }
    private fun nodes(root:View):List<SemanticsNode> {
        val v=compose(root)?:error("Compose missing")
        return (v.javaClass.getMethod("getSemanticsOwner").invoke(v) as SemanticsOwner).getAllSemanticsNodes(true)
    }
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)
    @Test fun `collapsed categories reveal controls and search bypasses collapsed state`() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
        val c=Robolectric.buildActivity(ComponentActivity::class.java)
        c.get().setTheme(R.style.Theme_DuoStatusBar)
        val a=c.setup().visible().get()
        var query by mutableStateOf("")
        a.setContent {DuoTheme {SettingsGroup("test","Category","Explanation",query) {Text("Real setting")}}}
        try {
            val root=a.findViewById<View>(android.R.id.content);idle()
            fun hasControl()=nodes(root).any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="Real setting"}==true}
            fun toggle() {nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="settings-group-test"}.config[SemanticsActions.OnClick].action!!.invoke();idle()}
            assertFalse(hasControl());toggle();assertTrue(hasControl());nodes(root).first {it.config.getOrNull(SemanticsProperties.TestTag)=="settings-back"}.config[SemanticsActions.OnClick].action!!.invoke();idle();assertFalse(hasControl())
            query="Real";idle();assertTrue("Search must reveal controls in a closed category",hasControl())
            query="";idle();assertFalse("Clearing search restores the previous collapsed state",hasControl())
        } finally {c.pause().stop().destroy();idle()}
    }
}
