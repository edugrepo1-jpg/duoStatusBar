package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.*
import io.github.kvmy666.duostatusbar.i18n.*
import io.github.kvmy666.duostatusbar.settings.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioBetaTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @After fun resetLanguage() { UiText.initialize(context,"pt-BR") }

    @Test fun `individual times and minimal mode survive settings migration`() {
        val options=ExperienceOptions.ALL.copy(iconSeconds=mapOf("WIFI" to 0,"BLUETOOTH" to 90,"BOLT" to 15,"UNKNOWN" to 5),networkOnly=true,fadeEnabled=false,language="es")
        val actual=ExperienceOptions.decode(options.encode())
        assertEquals(mapOf("WIFI" to 1,"BLUETOOTH" to 60,"BOLT" to 15),actual.iconSeconds)
        assertTrue(actual.networkOnly);assertFalse(actual.fadeEnabled);assertEquals("es",actual.language)
        assertTrue(actual.music&&actual.island&&actual.screenshot&&actual.compass)
        assertEquals("pt-BR",ExperienceOptions.decode("{\"language\":\"invalid\"}").language)
        assertEquals(ExperienceOptions(),ExperienceOptions.decode("{}"))
    }
    @Test fun `without fades one second and sixty second slots keep exact boundaries`() {
        val cycle=SlotCycle()
        cycle.configure(ExperienceOptions(fadeEnabled=false,universalTiming=false,iconSeconds=mapOf("WIFI" to 1,"BLUETOOTH" to 60)),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        assertEquals(SlotIcon.WIFI,cycle.frame(999).icon)
        assertEquals(1L,cycle.nextDelay(999))
        assertEquals(SlotIcon.BLUETOOTH,cycle.frame(1000).icon)
        assertEquals(SlotIcon.BLUETOOTH,cycle.frame(60999).icon)
        assertEquals(SlotIcon.WIFI,cycle.frame(61000).icon)
        assertEquals(1f,cycle.frame(61000).opacity,0f)
    }
    @Test fun `sequential fades finish the outgoing icon before drawing the next one`() {
        val c=SlotCycle();c.configure(ExperienceOptions(dwellMs=1000,exitMs=120,entryMs=160),0)
        c.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        assertEquals(SlotIcon.WIFI,c.frame(879).icon)
        assertEquals(SlotIcon.WIFI,c.frame(940).icon);assertTrue(c.frame(940).opacity in .1f.. .9f)
        assertEquals(SlotIcon.BLUETOOTH,c.frame(1000).icon);assertEquals(0f,c.frame(1000).opacity,0f)
        assertEquals(SlotIcon.BLUETOOTH,c.frame(1160).icon);assertEquals(1f,c.frame(1160).opacity,0f)
    }
    @Test fun `periodic state polls do not starve any of twenty six active icons`() {
        val c=SlotCycle();val icons=SlotIcon.entries.toList();val seen=mutableSetOf<SlotIcon>()
        c.configure(ExperienceOptions(dwellMs=1000),0);c.update(icons,0)
        for(t in 0L..27000L step 20L){if(t%2000L==0L)c.update(icons,t);c.frame(t).takeIf { it.opacity>.95f }?.icon?.let {seen.add(it)}}
        assertEquals(icons.toSet(),seen)
    }
    @Test fun `rootless collection includes device facts and sanitized app evidence`() {
        DuoPrefs.writeRootAllowed(context,false)
        L.i("studio-test permission denied; email=someone@example.com ssid=PrivateWifi")
        val report=RootLogs.collect(context)
        for(key in listOf("manufacturer=","brand=","model=","android=","sdk=","securityPatch=","kernelRelease=","abi=","density=","renderer=Canvas","studio-test"))assertTrue(key,report.contains(key))
        assertFalse(report.contains("someone@example.com"));assertFalse(report.contains("PrivateWifi"))
        assertFalse(report.contains("androidId="));assertFalse(report.contains("nodename="))
        assertTrue(report.contains("root capture skipped"))
    }
    @Test fun `privacy removes personal identifiers while preserving useful fault evidence`() {
        val safe=DiagnosticPrivacy.clean("SDK=35 model=SM-S918B kernel=6.1.75 Density=3.0 FAILED IOException\nemail=a@b.com MAC=12:34:56:78:9a:bc IP=192.168.1.2\nssid=MinhaRede\nimei=123456789\nphone=+55 11 99999-1111\nlatitude=-12.123\nhttps://example.com/private?token=abc\n/storage/emulated/0/DCIM/photo.jpg")
        for(value in listOf("a@b.com","12:34:56:78:9a:bc","192.168.1.2","MinhaRede","123456789","99999-1111","-12.123","example.com","photo.jpg"))assertFalse(value,safe.contains(value))
        assertTrue(safe.contains("SDK=35 model=SM-S918B kernel=6.1.75 Density=3.0 FAILED IOException"))
    }
    @Test fun `all three catalogues contain the same keys and substitution arguments`() {
        val catalogs=AppLanguage.supported.map { tag->JSONObject(context.assets.open("translations/$tag.json").bufferedReader().use {it.readText()}) }
        val keys=catalogs.first().keys().asSequence().toSet()
        assertTrue(keys.size>=270)
        for(catalog in catalogs){assertEquals(keys,catalog.keys().asSequence().toSet());for(key in keys)assertEquals(key,Regex("\\{[0-9]+}").findAll(catalogs.first().getString(key)).map {it.value}.toSet(),Regex("\\{[0-9]+}").findAll(catalog.getString(key)).map {it.value}.toSet())}
    }
    @Test fun `language switch translates dynamic labels and persists both orientations`() {
        val existing=ExperienceOptions.ALL.copy(iconSeconds=mapOf("BOLT" to 60),iconPercent=180)
        for(o in listOf(DuoOrientation.PORTRAIT,DuoOrientation.LANDSCAPE))DuoPrefs.write(context,DuoSettings(experienceJson=existing.encode()),o)
        AppLanguage.choose(context,"en")
        assertTrue(AppLanguage.chosen(context));assertEquals("en",AppLanguage.selected(context))
        assertEquals("Airplane mode",iconLabel(SlotIcon.AIRPLANE));assertEquals("Estimating",estimateLabel(-1))
        for(o in listOf(DuoOrientation.PORTRAIT,DuoOrientation.LANDSCAPE)){val saved=ExperienceOptions.decode(DuoPrefs.read(context,o).experienceJson);assertEquals(existing.copy(language="en"),saved)}
        AppLanguage.choose(context,"es");assertEquals("Modo avión",iconLabel(SlotIcon.AIRPLANE));assertEquals("Calculando",estimateLabel(-1))
    }
    private fun summary(height:Int=1600,actions:MutableList<String> = mutableListOf()):IslandSummary.SummaryCanvas {
        UiText.initialize(context,"pt-BR")
        return IslandSummary.SummaryCanvas(context,IslandState(72,true,SlotIcon.entries.map {IslandItem(it,iconLabel(it),if(it==SlotIcon.NETWORK)"4G" else io.github.kvmy666.duostatusbar.i18n.UiText.t("Ativo"))},PlaybackSnapshot(true,30000,120000,0,color=0xFF526CBB.toInt(),title="Faixa de demonstração"),36*60000L,65),animate=false,action={actions.add(it)}) {}.apply {layout(0,0,1080,height)}
    }
    private fun draw(view:IslandSummary.SummaryCanvas):Bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
    private fun tap(view:IslandSummary.SummaryCanvas,x:Float,y:Float) {for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(0,10,action,x,y,0);view.onTouchEvent(e);e.recycle()}}
    @Test fun `expanded island buttons dispatch media and all four shortcuts separately`() {
        val actions=mutableListOf<String>();val view=summary(actions=actions);draw(view).recycle();val d=context.resources.displayMetrics.density
        for(key in listOf("previous","play","next","wifi","bluetooth","volume","torch")){val rect=view.actionBounds(key)!!;tap(view,rect.centerX(),rect.centerY())}
        assertEquals(listOf("previous","play","next","wifi","bluetooth","volume","torch"),actions)
        val info=AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info)
        assertTrue(info.actionList.size>=8)
        assertTrue(view.performAccessibilityAction(0x01000000+6,null));assertEquals("torch",actions.last())
    }
    @Test fun `compact island retains shortcuts and its complete scrollable state list`() {
        val actions=mutableListOf<String>();val view=summary(740,actions);val before=draw(view);val d=context.resources.displayMetrics.density
        val rect=view.actionBounds("wifi")!!;tap(view,rect.centerX(),rect.centerY());assertEquals(listOf("wifi"),actions)
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null))
        val after=draw(view);assertFalse(before.sameAs(after));assertEquals(listOf("wifi"),actions)
        before.recycle();after.recycle()
    }
    @Test fun `expanded island renders its new music battery and shortcuts layout`() {
        val view=summary();val b=draw(view);assertEquals(0xFF050505.toInt(),b.getPixel(540,45))
        val out=File("build/experience-captures").apply {mkdirs()};File(out,"island-studio.png").outputStream().use {assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it))};b.recycle()
    }
}
