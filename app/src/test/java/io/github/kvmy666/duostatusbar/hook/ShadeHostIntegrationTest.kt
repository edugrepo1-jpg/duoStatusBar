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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w800dp-h360dp-land-xxhdpi")
class ShadeHostIntegrationTest {
    private class BatteryMeterView(c:Context):View(c)
    private class StatusIconContainer(c:Context):View(c)
    private class NotificationPanelHeader(c:Context):FrameLayout(c)
    private class SecQuickStatusBarHeader(c:Context):FrameLayout(c)
    @Suppress("DEPRECATION")
    private class HeaderResources(c:Context):android.content.res.Resources(c.resources.assets,c.resources.displayMetrics,c.resources.configuration) {
        override fun getResourceEntryName(id:Int)=when(id) {
            0x70100001 -> "shade_header_system_icons"
            0x70100002 -> "split_shade_status_bar"
            else -> super.getResourceEntryName(id)
        }
        override fun getIdentifier(name:String?,type:String?,pkg:String?):Int =
            if(name=="shade_header_system_icons"&&type=="id")0x70100001 else super.getIdentifier(name,type,pkg)
    }
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
    @Test fun `observed nested Samsung strip has one ring grows past 34px and receives the latest effect when recreated`() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val c=object:ContextWrapper(app) {private val res=HeaderResources(app);override fun getResources()=res}
        val provider=Store(DuoSettings(enabled=true,useRive=false,notificationSize=50,quickSettingsSize=200))
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY,provider)
        Settings.Global.putInt(c.contentResolver,"duo_statusbar_stage",1)
        val ordinary=FrameLayout(c).apply {layout(0,0,1000,150)}
        val main=LinearLayout(c).apply {addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));layout(0,0,100,90)}
        ordinary.addView(main);main.getChildAt(0).layout(0,0,63,42)
        fun header():Pair<FrameLayout,FrameLayout> {
            val header=FrameLayout(c).apply {id=0x70100002;layout(0,0,1962,160)}
            val outer=FrameLayout(c).apply {id=0x70100001;layout(1492,77,1522,111)}
            val inner=LinearLayout(c).apply {layout(0,0,30,34)}
            val battery=BatteryMeterView(c).apply {layout(0,0,63,42)}
            inner.addView(battery,LinearLayout.LayoutParams(63,42));outer.addView(inner);header.addView(outer)
            return header to outer
        }
        fun canvas(outer:FrameLayout)=(0 until outer.childCount).map {outer.getChildAt(it)}.filterIsInstance<DuoCanvasView>().single()
        val host=DuoIconHost(c)
        val frame=io.github.kvmy666.duostatusbar.fx.EffectFrame(checkMs=1000,chargeMs=1500,
            slot=io.github.kvmy666.duostatusbar.fx.SlotFrame(io.github.kvmy666.duostatusbar.fx.SlotIcon.NFC,.7f),managedSlots=true)
        try {
            assertTrue(host.attach(ordinary));host.applyEffects(frame,false)
            host.render(DuoMapping.visual(level=33,charging=true,saver=false,showPercent=true,wifiLevel=3,cellLevel=4,airplane=false,dnd=false))
            val (first,outer)=header()
            assertTrue(host.attachShadeHeader(first));host.attachShadePanels(first,true)
            val ring=canvas(outer);assertEquals(31,ring.layoutParams.width)
            val inner=outer.getChildAt(0) as LinearLayout
            assertFalse((0 until inner.childCount).any {inner.getChildAt(it) is DuoCanvasView})
            assertEquals(frame,ring.effects)
            host.setShadeExpanded(true);assertEquals(126,ring.layoutParams.width)
            assertTrue(ring.layoutParams.height<=first.height)
            host.setShadeExpanded(false);provider.saved=provider.saved.copy(notificationSize=200)
            host.refreshSettings();assertEquals(126,ring.layoutParams.width)
            val (second,replacement)=header()
            assertTrue(host.attachShadeHeader(second));host.attachShadePanels(second,true)
            assertFalse((0 until outer.childCount).any {outer.getChildAt(it) is DuoCanvasView})
            assertEquals(126,canvas(replacement).layoutParams.width);assertEquals(frame,canvas(replacement).effects)
        } finally {host.teardown();Settings.Global.putString(c.contentResolver,"duo_statusbar_stage",null)}
    }
    @Test fun `rotation with identical preferences reapplies bounds and replacing the main window preserves effects`() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val provider=Store(DuoSettings(enabled=true,useRive=false,sizePercent=150))
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY,provider)
        Settings.Global.putInt(c.contentResolver,"duo_statusbar_stage",1)
        fun window():Pair<FrameLayout,LinearLayout> {
            val root=FrameLayout(c).apply {layout(0,0,1000,150)}
            val row=LinearLayout(c).apply {layout(0,0,100,100)}
            row.addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));root.addView(row)
            return root to row
        }
        val (first,row)=window();val original=row.getChildAt(0);val host=DuoIconHost(c)
        val effect=io.github.kvmy666.duostatusbar.fx.EffectFrame(checkMs=1200,charging=true)
        try {
            assertTrue(host.attach(first));host.applyEffects(effect,false)
            val old=host.duo!!.ui;val before=old.layoutParams.width
            first.layout(0,0,2400,90);host.refreshSettings(applySavedSize=true)
            assertTrue(old.layoutParams.width<before)
            val (second,replacement)=window();assertTrue(host.attach(second))
            assertNull(old.parent);assertEquals(View.VISIBLE,original.visibility)
            assertSame(replacement,host.duo!!.ui.parent)
            assertEquals(effect,(host.duo as DuoCanvasView).effects)
        } finally {host.teardown();Settings.Global.putString(c.contentResolver,"duo_statusbar_stage",null)}
    }
    @Test @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
    fun `long press on a visible notification ring opens the island and closing restores both rings`() {
        val controller=org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val c=controller.get();val provider=Store(DuoSettings(enabled=true,useRive=false,
            experienceJson=io.github.kvmy666.duostatusbar.fx.ExperienceOptions.ALL.encode()))
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY,provider)
        Settings.Global.putInt(c.contentResolver,"duo_statusbar_stage",1)
        val root=FrameLayout(c);c.setContentView(root)
        val ordinary=FrameLayout(c);val shade=NotificationPanelHeader(c);root.addView(ordinary);root.addView(shade)
        fun strip(parent:FrameLayout):LinearLayout=LinearLayout(c).also {
            it.addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));parent.addView(it)
            it.layout(0,0,100,80);it.getChildAt(0).layout(0,0,63,42)
        }
        strip(ordinary);val panel=strip(shade)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        ordinary.layout(0,0,2400,90);shade.layout(0,200,2400,350);panel.layout(1800,10,1900,90)
        val host=DuoIconHost(c)
        try {
            assertTrue(host.attach(ordinary));host.attachShadePanels(shade,true)
            host.updateSummary(io.github.kvmy666.duostatusbar.fx.IslandState(72))
            val ring=(0 until panel.childCount).map {panel.getChildAt(it)}.filterIsInstance<DuoCanvasView>().single()
            ring.layout(0,0,63,71);assertTrue(ring.isShown);assertTrue(ring.isClickable)
            val position=IntArray(2);ring.getLocationOnScreen(position)
            val now=android.os.SystemClock.uptimeMillis()
            val down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,position[0]+31f,position[1]+35f,0)
            assertTrue(host.handleElementTouch(down));down.recycle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(650))
            val summary=DuoIconHost::class.java.getDeclaredField("summary").apply {isAccessible=true}.get(host) as io.github.kvmy666.duostatusbar.fx.IslandSummary
            assertNotNull(summary.windowSize);assertEquals(0f,ring.alpha,0f)
            host.dismissSummary();assertNull(summary.windowSize);assertEquals(1f,ring.alpha,0f)
        } finally {
            host.teardown();Settings.Global.putString(c.contentResolver,"duo_statusbar_stage",null)
            controller.pause().stop().destroy()
        }
    }
}
