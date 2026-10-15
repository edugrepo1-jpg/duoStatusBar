package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.*
import android.media.MediaMetadata
import androidx.test.core.app.ApplicationProvider
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
class IslandArtworkTest {
    private fun metadata(bitmap:Bitmap)=MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,bitmap).build()
    @Test fun `cover is bounded cached and removed when next track has none`() {
        val original=Bitmap.createBitmap(1500,900,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.CYAN)}
        val cache=MediaArtworkCache();val data=metadata(original);val first=requireNotNull(cache.read(data))
        assertTrue(MediaArtwork.valid(first));assertEquals(128,first.width);assertTrue(first.allocationByteCount<=65536)
        assertNotSame(original,first);assertSame(first,cache.read(data));assertFalse(original.isRecycled)
        assertNull(cache.read(null));assertFalse(original.isRecycled)
        original.recycle()
    }
    @Test fun `invalid relay cover falls back instead of being drawn`() {
        val oversized=Bitmap.createBitmap(129,128,Bitmap.Config.ARGB_8888)
        assertFalse(MediaArtwork.valid(oversized));assertFalse(AlbumCoverPainter().draw(Canvas(Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)),oversized,RectF(10f,10f,110f,110f),0))
        oversized.recycle();assertFalse(MediaArtwork.valid(oversized));assertFalse(MediaArtwork.valid(null))
    }
    @Test fun `artwork renders with all three controls and nfc dispatches separately`() {
        val context=ApplicationProvider.getApplicationContext<Context>();val d=context.resources.displayMetrics.density
        val art=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        val c=Canvas(art);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {shader=LinearGradient(0f,0f,128f,128f,0xFF6926AD.toInt(),0xFF23B7BF.toInt(),Shader.TileMode.CLAMP)}
        c.drawRect(0f,0f,128f,128f,p);p.shader=null;p.color=0xFFEDC980.toInt();c.drawCircle(72f,52f,28f,p)
        val actions=mutableListOf<String>()
        val state=IslandState(72,items=listOf(IslandItem(SlotIcon.MEDIA,"Música"),IslandItem(SlotIcon.WIFI,"Wi-Fi")),playback=PlaybackSnapshot(playing=true,title="Horizontes · demonstração",positionMs=30000,durationMs=180000,artwork=art,color=0xFF23B7BF.toInt()),aurora=AuroraOptions.ALL)
        for((name,w,h) in listOf(Triple("ilha-glass-capa",352,420),Triple("ilha-glass-capa-horizontal",500,240))) {
            val v=IslandSummary.SummaryCanvas(context,state,animate=false,expandedBodyHeight=h*d,action={actions.add(it)}){}.apply {layout(0,0,(w*d).toInt(),(h*d).toInt())}
            val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
            for(key in listOf("previous","play","next","wifi","nfc"))assertNotNull(key,v.actionBounds(key))
            assertNull(v.actionBounds("volume"));assertTrue(v.performAccessibilityAction(0x01000005,null));assertEquals("nfc",actions.last())
            assertTrue(v.performAccessibilityAction(0x01000000,null));assertEquals("previous",actions.last())
            File("build/ui-gallery/$name.png").also {it.parentFile.mkdirs()}.outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
            b.recycle();v.dispose()
        }
        art.recycle()
    }
}
