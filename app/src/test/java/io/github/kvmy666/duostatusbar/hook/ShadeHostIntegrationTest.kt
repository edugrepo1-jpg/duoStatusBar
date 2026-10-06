package io.github.kvmy666.duostatusbar.hook

import android.content.*
import android.database.*
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class ShadeHostIntegrationTest {
    private class BatteryMeterView(c:Context):View(c)
    private class StatusIconContainer(c:Context):View(c)
    private class NotificationPanelHeader(c:Context):FrameLayout(c)
    private class SecQuickStatusBarHeader(c:Context):FrameLayout(c)
    private class Store(var saved:DuoSettings):ContentProvider() {
        override fun onCreate()=true
        override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(DuoSettingsProvider.rowFor(saved,1))}
        override fun getType(u:Uri):String?=null
        override fun insert(u:Uri,v:ContentValues?):Uri?=null
        override fun delete(u:Uri,s:String?,a:Array<out String>?)=0
        override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
    }
    @Test fun `host attaches once restores only the disabled panel resizes independently and tears down all slots`() {
        val c=ApplicationProvider.getApplicationContext<Context>();val provider=Store(DuoSettings(enabled=true,useRive=false,notificationSize=70,quickSettingsSize=160))
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY,provider)
        Settings.Global.putInt(c.contentResolver,"duo_statusbar_stage",1)
        fun strip(parent:ViewGroup)=LinearLayout(c).also {it.addView(StatusIconContainer(c),LinearLayout.LayoutParams(50,80));it.addView(BatteryMeterView(c),LinearLayout.LayoutParams(60,80));parent.addView(it);it.layout(0,0,180,100);it.getChildAt(1).layout(50,0,110,80)}
        val ordinary=FrameLayout(c);ordinary.layout(0,0,1000,150);strip(ordinary)
        val shade=FrameLayout(c);val n=NotificationPanelHeader(c);val q=SecQuickStatusBarHeader(c);shade.addView(n);shade.addView(q);val ns=strip(n);val qs=strip(q)
        val nb=ns.getChildAt(1);val qb=qs.getChildAt(1);val host=DuoIconHost(c)
        try {
            assertTrue(host.attach(ordinary));host.attachShadePanels(shade,true);host.attachShadePanels(shade,true)
            assertEquals(1,(0 until ns.childCount).count {ns.getChildAt(it) is DuoCanvasView});assertEquals(1,(0 until qs.childCount).count {qs.getChildAt(it) is DuoCanvasView})
            val nr=(0 until ns.childCount).map {ns.getChildAt(it)}.filterIsInstance<DuoCanvasView>().single()
            val qr=(0 until qs.childCount).map {qs.getChildAt(it)}.filterIsInstance<DuoCanvasView>().single()
            assertTrue(nr.layoutParams.width<qr.layoutParams.width)
            assertEquals(View.GONE,nb.visibility);assertEquals(View.GONE,qb.visibility)
            provider.saved=provider.saved.copy(showNotifications=false,quickSettingsSize=55)
            host.refreshSettings();assertEquals(View.GONE,nr.visibility);assertEquals(View.VISIBLE,nb.visibility);assertEquals(View.GONE,qb.visibility)
            assertTrue(qr.layoutParams.width<nr.layoutParams.width)
        } finally {host.teardown();Settings.Global.putString(c.contentResolver,"duo_statusbar_stage",null)}
        assertEquals(View.VISIBLE,nb.visibility);assertEquals(View.VISIBLE,qb.visibility)
        assertFalse((0 until ns.childCount).any {ns.getChildAt(it) is DuoCanvasView});assertFalse((0 until qs.childCount).any {qs.getChildAt(it) is DuoCanvasView})
    }
}
