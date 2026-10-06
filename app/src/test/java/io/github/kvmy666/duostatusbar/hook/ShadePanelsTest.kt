package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Configuration
import android.database.MatrixCursor
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class ShadePanelsTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private class NotificationPanelHeader(c:Context):FrameLayout(c)
    private class SecQuickStatusBarHeader(c:Context):FrameLayout(c)
    private class ShadeHeader(c:Context):FrameLayout(c)
    private class KeyguardStatusBarView(c:Context):FrameLayout(c)
    private class BatteryMeterView(c:Context):View(c)
    private class CombinedStatusView(c:Context):View(c)
    private fun row(header:FrameLayout,combined:Boolean=false)=LinearLayout(context).also {strip ->
        strip.addView(if(combined)CombinedStatusView(context) else BatteryMeterView(context),LinearLayout.LayoutParams(30,40))
        header.addView(strip);header.layout(0,0,500,120);strip.layout(450,0,500,48)
    }
    @Test fun `separate OEM headers resolve only their icon rows never the full shade`() {
        val root=FrameLayout(context);val n=NotificationPanelHeader(context);val q=SecQuickStatusBarHeader(context)
        root.addView(n);root.addView(q);val ns=row(n);val qs=row(q,true)
        val found=ShadePanels.find(root)
        assertEquals(2,found.size);assertSame(ns,found[0].strip);assertSame(qs,found[1].strip)
        assertEquals(ShadePanels.Kind.NOTIFICATIONS,found[0].kind);assertEquals(ShadePanels.Kind.QUICK_SETTINGS,found[1].kind)
        assertFalse(found.any {it.header===root})
    }
    @Test fun `keyguard and battery tiles are excluded while shared headers are supported`() {
        val root=FrameLayout(context);val lock=KeyguardStatusBarView(context);val shared=ShadeHeader(context)
        root.addView(lock);root.addView(shared);row(lock);val strip=row(shared)
        root.addView(BatteryMeterView(context))
        val found=ShadePanels.find(root);assertEquals(1,found.size);assertSame(strip,found.single().strip)
        assertEquals(ShadePanels.Kind.SHARED,found.single().kind)
    }
    @Test fun `panel enablement and size are independent including shared header expansion`() {
        val s=ModuleSettings.DEFAULT.copy(enabled=true,sizePercent=170,showNotifications=false,notificationSize=70,showQuickSettings=true,quickSettingsSize=160)
        assertFalse(ShadePanels.enabled(s,ShadePanels.Kind.NOTIFICATIONS,true));assertTrue(ShadePanels.enabled(s,ShadePanels.Kind.QUICK_SETTINGS,false))
        assertFalse(ShadePanels.enabled(s,ShadePanels.Kind.SHARED,false));assertTrue(ShadePanels.enabled(s,ShadePanels.Kind.SHARED,true))
        assertEquals(70,ShadePanels.size(s,ShadePanels.Kind.SHARED,false));assertEquals(160,ShadePanels.size(s,ShadePanels.Kind.SHARED,true));assertEquals(170,s.sizePercent)
        assertFalse(ShadePanels.enabled(s.copy(enabled=false),ShadePanels.Kind.QUICK_SETTINGS,true))
    }
    @Test fun `new panel settings survive storage provider and orientation without changing ordinary size`() {
        val p=DuoSettings(enabled=true,sizePercent=120,showNotifications=false,notificationSize=75,showQuickSettings=true,quickSettingsSize=185)
        val l=p.copy(showNotifications=true,notificationSize=140,showQuickSettings=false,quickSettingsSize=55)
        DuoPrefs.write(context,p,DuoOrientation.PORTRAIT);DuoPrefs.write(context,l,DuoOrientation.LANDSCAPE)
        assertEquals(p,DuoPrefs.read(context,DuoOrientation.PORTRAIT));assertEquals(l,DuoPrefs.read(context,DuoOrientation.LANDSCAPE))
        val c=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(DuoSettingsProvider.rowFor(p,8,l));moveToFirst()}
        val first=DuoSettingsClient.fromCursor(c,Configuration.ORIENTATION_PORTRAIT);val second=DuoSettingsClient.fromCursor(c,Configuration.ORIENTATION_LANDSCAPE)
        assertFalse(first.showNotifications);assertEquals(75,first.notificationSize);assertEquals(185,first.quickSettingsSize)
        assertTrue(second.showNotifications);assertFalse(second.showQuickSettings);assertEquals(140,second.notificationSize);assertEquals(55,second.quickSettingsSize);assertEquals(120,second.sizePercent)
    }
    @Test fun `old provider columns retain working defaults and invalid new sizes are clamped`() {
        val newCols=setOf(DuoPrefs.COL_SHOW_NOTIFICATIONS,DuoPrefs.COL_NOTIFICATION_SIZE,DuoPrefs.COL_SHOW_QS,DuoPrefs.COL_QS_SIZE)
        val cols=DuoPrefs.PORTRAIT_COLUMNS.filterNot {it in newCols}.toTypedArray()
        val full=DuoSettingsProvider.rowFor(DuoSettings(enabled=true),1)
        val values=cols.map {full[DuoPrefs.PORTRAIT_COLUMNS.indexOf(it)]}
        val c=MatrixCursor(cols).apply {addRow(values);moveToFirst()};val old=DuoSettingsClient.fromCursor(c,Configuration.ORIENTATION_PORTRAIT)
        assertTrue(old.showNotifications);assertTrue(old.showQuickSettings);assertEquals(100,old.notificationSize);assertEquals(100,old.quickSettingsSize)
        DuoPrefs.write(context,DuoSettings(notificationSize=-100,quickSettingsSize=5000));val saved=DuoPrefs.read(context)
        assertEquals(50,saved.notificationSize);assertEquals(200,saved.quickSettingsSize)
    }
    @Test fun `restoring one optional panel does not restore another panels stock icons`() {
        val one=StockIconHider();val two=StockIconHider();val a=BatteryMeterView(context);val b=BatteryMeterView(context)
        a.layoutParams=LinearLayout.LayoutParams(30,40);b.layoutParams=LinearLayout.LayoutParams(32,42)
        one.hide(a);two.hide(b);one.restore()
        assertEquals(View.VISIBLE,a.visibility);assertEquals(30,a.layoutParams.width);assertEquals(View.GONE,b.visibility)
        two.restore();assertEquals(View.VISIBLE,b.visibility);assertEquals(32,b.layoutParams.width)
    }
}
