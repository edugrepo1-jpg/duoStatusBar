package io.github.kvmy666.duostatusbar.hook

import android.content.*
import android.database.*
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w800dp-h360dp-land-xxhdpi")
class HostDeepAuditTest {
    private class BatteryMeterView(c:Context):View(c)
    private class NotificationPanelHeader(c:Context):FrameLayout(c)
    private class Store(var saved:DuoSettings):ContentProvider() {
        override fun onCreate()=true
        override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(DuoSettingsProvider.rowFor(saved,1))}
        override fun getType(u:Uri):String?=null
        override fun insert(u:Uri,v:ContentValues?):Uri?=null
        override fun delete(u:Uri,s:String?,a:Array<out String>?)=0
        override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
    }
    private fun fixture(settings:DuoSettings=DuoSettings(enabled=true,useRive=false),onFailure:()->Unit={},block:(DuoIconHost,FrameLayout,LinearLayout,LinearLayout)->Unit) {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val store=Store(settings)
        ShadowContentResolver.registerProviderInternal(DuoPrefs.AUTHORITY,store)
        Settings.Global.putInt(c.contentResolver,"duo_statusbar_stage",1)
        fun strip(parent:FrameLayout)=LinearLayout(c).also {
            it.addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));parent.addView(it)
            it.layout(0,0,100,100);it.getChildAt(0).layout(0,0,63,42)
        }
        val root=FrameLayout(c).apply {layout(0,0,2400,150);tag=store};val main=strip(root)
        val shade=NotificationPanelHeader(c).apply {layout(0,0,2400,150)};val panel=strip(shade)
        val host=DuoIconHost(c,onFailure)
        try {assertTrue(host.attach(root));host.attachShadePanels(shade,true);block(host,root,main,panel)}
        finally {host.teardown();Settings.Global.putString(c.contentResolver,"duo_statusbar_stage",null)}
    }
    private fun canvas(row:LinearLayout,part:DuoPart?=null)=(0 until row.childCount).map {row.getChildAt(it)}.filterIsInstance<DuoCanvasView>().single {if(part==null)it.part!=DuoPart.INDICATORS else it.part==part}
    private fun assertInactive(view:DuoCanvasView) {
        assertEquals(View.GONE,view.visibility)
        assertFalse(view.javaClass.getDeclaredField("active").apply {isAccessible=true}.getBoolean(view))
    }
    private fun fault(view:DuoCanvasView) {
        view.layout(0,0,120,136)
        view.javaClass.getDeclaredField("effectPainter").apply {isAccessible=true}.set(view,null)
        val image=android.graphics.Bitmap.createBitmap(120,136,android.graphics.Bitmap.Config.ARGB_8888)
        try {view.draw(android.graphics.Canvas(image))} finally {image.recycle()}
    }
    private fun idle()=Shadows.shadowOf(Looper.getMainLooper()).idleFor(100,TimeUnit.MILLISECONDS)
    @Test fun `hidden screen stays hidden after extra layout settings and camera visibility refresh`() = fixture {host,_,main,panel ->
        host.setElementsVisible(false)
        host.reapplyHiding();host.refreshSettings(applySavedSize=true);host.effectsHidden(false);host.pauseEffects(false)
        assertEquals(View.GONE,canvas(main).visibility)
        assertEquals("Panel layout must preserve the owner's screen-off state",View.GONE,canvas(panel).visibility)
        for(row in listOf(main,panel)) {
            val view=canvas(row)
            assertFalse("Screen-off Canvas must remain inactive",view.javaClass.getDeclaredField("active").apply {isAccessible=true}.getBoolean(view))
        }
        host.setElementsVisible(true);assertEquals(View.VISIBLE,canvas(main).visibility);assertEquals(View.VISIBLE,canvas(panel).visibility)
    }
    @Test fun `a fatal Canvas draw restores originals once and cannot create a layout retry loop`() = fixture {host,root,main,panel ->
        val view=canvas(main);view.layout(0,0,120,136)
        host.render(DuoMapping.visual(72,false,false,true,3,4,false))
        assertEquals(View.GONE,main.getChildAt(0).visibility)
        // Controlled Java fault injection proves the safety path; it is not a device crash claim.
        fault(view);idle()
        assertFalse(view.isReady)
        assertEquals("Keep Android's status bar usable when rendering fails",View.VISIBLE,main.getChildAt(0).visibility)
        assertEquals(View.VISIBLE,panel.getChildAt(0).visibility)
        assertNull(host.duo)
        assertFalse("Automatic layout rediscovery cannot immediately repeat the same fatal draw",host.attach(root))
    }
    @Test fun `a header created while the screen is hidden inherits both visibility and cached effects`() = fixture {host,root,_,panel ->
        val effect=io.github.kvmy666.duostatusbar.fx.EffectFrame(checkMs=900,managedSlots=true)
        host.applyEffects(effect,false);host.render(DuoMapping.visual(37,false,false,true,3,4,false))
        host.setElementsVisible(false)
        val c=root.context
        val replacement=NotificationPanelHeader(c).apply {layout(0,0,2400,150)}
        val row=LinearLayout(c).apply {
            addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));layout(0,0,100,100)
            getChildAt(0).layout(0,0,63,42)
        }
        replacement.addView(row);host.attachShadePanels(replacement,true);host.setShadeExpanded(true);host.reapplyHiding()
        assertInactive(canvas(row));assertInactive(canvas(panel));assertEquals(effect,canvas(row).effects)
        host.setElementsVisible(true)
        assertEquals(View.VISIBLE,canvas(row).visibility)
        assertTrue(canvas(row).javaClass.getDeclaredField("active").apply {isAccessible=true}.getBoolean(canvas(row)))
    }
    @Test fun `new split indicators cannot wake the hidden main bar or panel`() = fixture {host,root,main,panel ->
        host.setElementsVisible(false)
        val store=root.tag as Store;store.saved=store.saved.copy(splitIndicators=true)
        host.refreshSettings(applySavedSize=true);host.reapplyHiding()
        for(row in listOf(main,panel)) {
            assertInactive(canvas(row,DuoPart.RING));assertInactive(canvas(row,DuoPart.INDICATORS))
        }
        host.setElementsVisible(true)
        for(row in listOf(main,panel))for(part in listOf(DuoPart.RING,DuoPart.INDICATORS))assertEquals(View.VISIBLE,canvas(row,part).visibility)
    }
    @Test fun `waking the screen cannot enable a notification panel disabled by the user`() = fixture(settings=DuoSettings(enabled=true,useRive=false,showNotifications=false,splitIndicators=true)) {host,_,main,panel ->
        host.setElementsVisible(false);host.setElementsVisible(true)
        for(part in listOf(DuoPart.RING,DuoPart.INDICATORS))assertEquals(View.VISIBLE,canvas(main,part).visibility)
        assertInactive(canvas(panel,DuoPart.RING))
        // The disabled panel owns no split cluster; waking cannot create one either.
        assertFalse((0 until panel.childCount).map {panel.getChildAt(it)}.filterIsInstance<DuoCanvasView>().any {it.part==DuoPart.INDICATORS})
        assertEquals("Disabled panels keep the original Android icons",View.VISIBLE,panel.getChildAt(0).visibility)
    }
    @Test fun `camera hide and summary close cannot unpause the pocket renderer`() = fixture {host,_,main,panel ->
        host.pauseEffects(true);host.effectsHidden(true);host.effectsHidden(false);host.dismissSummary()
        for(row in listOf(main,panel))assertFalse(canvas(row).javaClass.getDeclaredField("active").apply {isAccessible=true}.getBoolean(canvas(row)))
        host.pauseEffects(false)
        for(row in listOf(main,panel))assertTrue(canvas(row).javaClass.getDeclaredField("active").apply {isAccessible=true}.getBoolean(canvas(row)))
    }
    @Test fun `a fatal panel draw restores all slots and invokes the runtime shutdown only once`() {
        var stops=0
        fixture(onFailure={stops++}) {host,root,main,panel ->
            host.render(DuoMapping.visual(72,false,false,true,3,4,false));val view=canvas(panel)
            fault(view);idle();fault(view);idle();host.reapplyHiding()
            assertEquals(1,stops);assertTrue(host.rendererFailed);assertNull(host.duo)
            for(row in listOf(main,panel)) {
                assertEquals(View.VISIBLE,row.getChildAt(0).visibility)
                assertEquals(63,row.getChildAt(0).layoutParams.width)
                assertFalse((0 until row.childCount).any {row.getChildAt(it) is DuoCanvasView})
            }
            repeat(5){assertFalse(host.attach(root))};assertEquals(1,stops)
        }
    }
    @Test fun `a fatal split icon draw cannot spawn a replacement renderer`() {
        var stops=0
        fixture(settings=DuoSettings(enabled=true,useRive=false,splitIndicators=true),onFailure={stops++}) {host,root,main,panel ->
            host.render(DuoMapping.visual(72,false,false,true,3,4,false));fault(canvas(main,DuoPart.INDICATORS));idle()
            assertEquals(1,stops);assertTrue(host.rendererFailed);assertNull(host.duo)
            for(row in listOf(main,panel))assertEquals(View.VISIBLE,row.getChildAt(0).visibility)
            assertFalse(host.attach(root))
        }
    }
    @Test fun `a stale failed renderer callback cannot disable a healthy replacement window`() = fixture {host,root,main,_ ->
        host.render(DuoMapping.visual(72,false,false,true,3,4,false));fault(canvas(main))
        val c=root.context
        val replacement=FrameLayout(c).apply {layout(0,0,2400,150)}
        val row=LinearLayout(c).apply {addView(BatteryMeterView(c),LinearLayout.LayoutParams(63,42));layout(0,0,100,100);getChildAt(0).layout(0,0,63,42)}
        replacement.addView(row)
        assertTrue(host.attach(replacement));val healthy=host.duo;idle()
        assertFalse(host.rendererFailed);assertSame(healthy,host.duo);assertTrue(host.duo!!.isReady)
        assertEquals(View.GONE,row.getChildAt(0).visibility)
    }
}
