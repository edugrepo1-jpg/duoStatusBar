package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ExistingWindowsDeepAuditTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun view(type:Int)=View(context).apply {layoutParams=WindowManager.LayoutParams().apply {this.type=type}}
    @Suppress("UNCHECKED_CAST")
    private fun roots(vararg views:View,block:()->Unit) {
        val cls=Class.forName("android.view.WindowManagerGlobal")
        val instance=cls.getMethod("getInstance").invoke(null)
        val roots=cls.getDeclaredField("mViews").apply {isAccessible=true}.get(instance) as MutableList<View>
        val originals=roots.toList()
        try {roots.clear();roots.addAll(views);block()} finally {roots.clear();roots.addAll(originals)}
    }
    @Test fun `late injection prefers the real status window even when shade was added first`() {
        val shade=view(AppContextResolver.TYPE_NOTIFICATION_SHADE);val status=view(AppContextResolver.TYPE_STATUS_BAR)
        roots(shade,status) {assertSame(status,AppContextResolver.findExistingStatusBarWindow())}
    }
    @Test fun `shade-only historical fallback remains available when no status window exists`() {
        val shade=view(AppContextResolver.TYPE_NOTIFICATION_SHADE)
        roots(view(1),shade) {assertSame(shade,AppContextResolver.findExistingStatusBarWindow())}
    }
    @Test fun `unrelated application window is not adopted`() {
        roots(view(1)) {assertNull(AppContextResolver.findExistingStatusBarWindow())}
    }
    @Test fun `late injection separately captures shade even when real status window is preferred`() {
        val shade=view(AppContextResolver.TYPE_NOTIFICATION_SHADE);val status=view(AppContextResolver.TYPE_STATUS_BAR)
        roots(status,view(1),shade) {
            assertSame(status,AppContextResolver.findExistingStatusBarWindow())
            assertSame(shade,AppContextResolver.findExistingNotificationShadeWindow())
        }
    }
}
