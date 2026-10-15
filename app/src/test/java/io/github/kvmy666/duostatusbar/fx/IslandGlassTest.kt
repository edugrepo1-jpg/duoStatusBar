package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.i18n.UiText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE,qualifiers="w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandGlassTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val d=context.resources.displayMetrics.density
    private val options=AuroraOptions(setOf("threeLevels","contextActions","landscapeLayout","fingerExpansion"))
    private fun state()=IslandState(72,foreground=SlotIcon.MEDIA,items=listOf(IslandItem(SlotIcon.MEDIA,"Música"),IslandItem(SlotIcon.WIFI,"Wi-Fi"),IslandItem(SlotIcon.BLUETOOTH,"Bluetooth"),IslandItem(SlotIcon.AIRPODS,"Fones")),playback=PlaybackSnapshot(playing=true,title="Faixa de demonstração",positionMs=30000,durationMs=180000),aurora=options)
    private fun bitmap(v:IslandSummary.SummaryCanvas)=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also {v.draw(Canvas(it))}
    private fun capture(v:IslandSummary.SummaryCanvas,name:String) {val image=bitmap(v);File("build/ui-gallery/$name.png").also {it.parentFile.mkdirs()}.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
    @Test fun `glass quick controls fit and touch regions never overlap`() {
        UiText.initialize(context,"pt-BR")
        val v=IslandSummary.SummaryCanvas(context,state(),animate=false,startCompact=true,expandedBodyHeight=420*d){}.apply {layout(0,0,(352*d).toInt(),(420*d).toInt())}
        capture(v,"ilha-glass-compacta");v.setLevel(1);capture(v,"ilha-glass-musica")
        val rects=listOf("close","previous","play","next","wifi","bluetooth","nfc","torch").map {requireNotNull(v.actionBounds(it))}
        rects.forEach {assertTrue(it.height()>=48*d-1);assertTrue(v.displayedBounds().contains(it))}
        rects.forEachIndexed {i,a->rects.drop(i+1).forEach {assertFalse(RectF.intersects(a,it))}}
        v.setLevel(2);capture(v,"ilha-glass-detalhes");v.dispose()
    }
    @Test fun `horizontal glass keeps all media actions within visible bounds`() {
        val v=IslandSummary.SummaryCanvas(context,state(),animate=false,expandedBodyHeight=240*d){}.apply {layout(0,0,(500*d).toInt(),(240*d).toInt())}
        capture(v,"ilha-glass-horizontal")
        for(key in listOf("close","previous","play","next","wifi","bluetooth","nfc","torch"))assertTrue(v.displayedBounds().contains(requireNotNull(v.actionBounds(key))))
        v.dispose()
    }
    @Test fun `static glass has no time dependent shimmer or duplicated primary status`() {
        val s=IslandState(72,items=listOf(IslandItem(SlotIcon.WIFI,"Wi-Fi")),aurora=options)
        val v=IslandSummary.SummaryCanvas(context,s,animate=false,expandedBodyHeight=280*d){}.apply {layout(0,0,(352*d).toInt(),(280*d).toInt())}
        val first=bitmap(v);val second=bitmap(v)
        assertTrue(first.sameAs(second));assertTrue(s.secondaryItems().isEmpty());assertNull(v.actionBounds("play"))
        capture(v,"ilha-glass-rede");first.recycle();second.recycle();v.dispose()
    }
}
