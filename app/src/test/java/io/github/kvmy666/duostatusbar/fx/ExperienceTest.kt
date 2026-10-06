package io.github.kvmy666.duostatusbar.fx

import android.content.*
import android.content.res.Configuration
import android.database.MatrixCursor
import android.media.AudioManager
import android.os.*
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import io.github.kvmy666.duostatusbar.settings.*
import io.github.kvmy666.duostatusbar.ui.SimulationClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ExperienceTest {
    @Test fun `missing malformed and partial settings preserve defaults`() {
        for(raw in listOf("", "broken", "[]"))assertEquals(ExperienceOptions(),ExperienceOptions.decode(raw))
        assertEquals(ExperienceOptions(compass=true),ExperienceOptions.decode("{\"compass\":true}"))
        assertEquals(ExperienceOptions.ALL.copy(iconPercent=200),ExperienceOptions.decode(ExperienceOptions.ALL.copy(iconPercent=200).encode()))
    }
    @Test fun `rhythm and internal icon size are bounded`() {
        val options=ExperienceOptions.decode(ExperienceOptions(dwellMs=0,exitMs=999,entryMs=-1,iconPercent=300).encode())
        assertEquals(1000,options.dwellMs);assertEquals(600,options.exitMs);assertEquals(60,options.entryMs);assertEquals(200,options.iconPercent)
    }
    @Test fun `new settings round trip to module in both orientations and legacy columns remain readable`() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val p=DuoSettings(experienceJson=ExperienceOptions.ALL.copy(iconPercent=200).encode())
        val l=p.copy(experienceJson=ExperienceOptions(compass=true,iconPercent=75).encode())
        DuoPrefs.write(ctx,p);assertEquals(p.experienceJson,DuoPrefs.read(ctx).experienceJson)
        val row=DuoSettingsProvider.rowFor(p,3L,l)
        val cursor=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(row);moveToFirst()}
        assertEquals(p.experienceJson,DuoSettingsClient.fromCursor(cursor,Configuration.ORIENTATION_PORTRAIT).experienceJson)
        assertEquals(l.experienceJson,DuoSettingsClient.fromCursor(cursor,Configuration.ORIENTATION_LANDSCAPE).experienceJson)
        val legacy=DuoPrefs.COLUMNS.filterNot { it.contains(DuoPrefs.COL_EXPERIENCE) }.toTypedArray()
        val old=MatrixCursor(legacy).apply {addRow(legacy.map {row[DuoPrefs.COLUMNS.indexOf(it)]});moveToFirst()}
        assertEquals("",DuoSettingsClient.fromCursor(old,Configuration.ORIENTATION_PORTRAIT).experienceJson)
    }
    @Test fun `every active icon including charging participates after custom sequential fades`() {
        val cycle=SlotCycle();cycle.configure(ExperienceOptions(dwellMs=1600,exitMs=80,entryMs=120),0)
        cycle.update(SlotIcon.entries,0)
        val seen=mutableSetOf<SlotIcon>()
        for(t in 0L..(SlotIcon.entries.size*1600L) step 10) {
            cycle.update(SlotIcon.entries,t)
            val f=cycle.frame(t);assertTrue(f.opacity in 0f..1f);assertTrue(f.reveal in 0f..1f)
            if(f.opacity>.99f)f.icon?.let {seen.add(it)}
        }
        assertEquals(SlotIcon.entries.toSet(),seen)
        assertEquals(SlotIcon.WIFI,cycle.frame(SlotIcon.entries.size*1600L+200).icon)
    }
    @Test fun `custom fade out completes before next icon starts drawing`() {
        val swap=SequentialSwap();swap.configure(240,400);swap.update(1,0,false);swap.update(2,100,true)
        assertEquals(1,swap.frame(339).key);assertEquals(1f,swap.frame(339).reveal,0f)
        assertEquals(SwapFrame(2,0f,0f),swap.frame(340))
        assertEquals(SwapFrame(2,.5f,.5f),swap.frame(540))
        assertEquals(SwapFrame(2,1f,1f),swap.frame(740))
    }
    @Test fun `transient event exits and returns to a changing active list`() {
        val c=SlotCycle();c.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0);c.transient(SlotIcon.VOLUME,500)
        assertEquals(SlotIcon.WIFI,c.frame(600).icon);assertEquals(SlotIcon.VOLUME,c.frame(780).icon)
        c.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.BOLT),900)
        assertEquals(SlotIcon.VOLUME,c.frame(1000).icon)
        c.transient(null,2000);assertEquals(SlotIcon.VOLUME,c.frame(2100).icon)
        assertEquals(SlotIcon.WIFI,c.frame(2280).icon)
    }
    @Test fun `charge estimate waits for evidence then resets on unplug or reversal`() {
        val c=ChargeEstimator();c.observe(50,true,0);assertEquals(-1L,c.remaining(0))
        c.observe(51,true,60000);assertEquals(-1L,c.remaining(60000))
        c.observe(52,true,120000);assertEquals(48*60000L,c.remaining(120000))
        assertEquals(35*60000L,c.remaining(120000,35*60000L))
        c.observe(40,true,130000);assertEquals(-1L,c.remaining(130000))
        c.observe(100,true,140000);assertEquals(0L,c.remaining(140000))
        c.observe(100,false,150000);assertEquals(-1L,c.remaining(150000))
    }
    @Test fun `playback progress extrapolates only while playing and clamps endpoints`() {
        val s=PlaybackSnapshot(true,30000,120000,10000,speed=2f)
        assertEquals(.5f,s.progress(25000),.0001f)
        assertEquals(.25f,s.copy(playing=false).progress(90000),.0001f)
        assertEquals(1f,s.progress(200000),0f)
        assertEquals(-1f,s.copy(durationMs=0).progress(200000),0f)
    }
    @Test fun `screenshot identification accepts saved capture names and rejects camera photographs`() {
        assertTrue(ScreenshotObserver.isScreenshot("Screenshot_20261006.png","Pictures/"))
        assertTrue(ScreenshotObserver.isScreenshot("20261006.jpg","Pictures/Screenshots/"))
        assertTrue(ScreenshotObserver.isScreenshot("Captura de tela 2026.png",""))
        assertFalse(ScreenshotObserver.isScreenshot("IMG_20261006.jpg","DCIM/Camera/"))
    }
    @Test fun `compass takes shortest arc across north and multiple revolutions`() {
        assertEquals(2f,HeadingObserver.shortestDelta(359f,1f),0f)
        assertEquals(-2f,HeadingObserver.shortestDelta(1f,359f),0f)
        assertEquals(2f,HeadingObserver.shortestDelta(719f,1f),0f)
        assertEquals(90f,HeadingObserver.shortestDelta(0f,90f),0f)
    }
    @Test fun `volume observes actual audio levels without startup or duplicate notifications`() {
        val ctx=ApplicationProvider.getApplicationContext<Context>();val audio=ctx.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,2,0)
        val values=mutableListOf<Int>();val observer=VolumeObserver(ctx,Handler(Looper.getMainLooper())) {values.add(it)}
        observer.start();assertTrue(values.isEmpty())
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,4,0)
        val intent=Intent(VolumeObserver.ACTION).putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE",AudioManager.STREAM_MUSIC)
        ctx.sendBroadcast(intent);Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf((400f/audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt()),values)
        ctx.sendBroadcast(intent);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,values.size)
        observer.stop();audio.setStreamVolume(AudioManager.STREAM_MUSIC,5,0);ctx.sendBroadcast(intent)
        Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,values.size)
    }
    @Test fun `preview clock freezes all effects through pause and resume`() {
        var actual=0L;val clock=SimulationClock {actual};actual=1000;clock.pause();actual=50000
        assertEquals(1000L,clock.now());clock.pause();clock.resume();assertEquals(1000L,clock.now())
        actual=51000;assertEquals(2000L,clock.now())
    }
    @Test fun `media controller supplies position duration title and album color`() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val session=android.media.session.MediaSession(ctx,"Duo test")
        val art=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(0xFF4060CC.toInt())}
        try {
            // Robolectric's controller does not mirror its MediaSession; supply Android values at the controller boundary.
            val controller=Shadows.shadowOf(session.controller)
            controller.setMetadata(android.media.MediaMetadata.Builder().putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,120000)
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Faixa teste").putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART,art).build())
            controller.setPlaybackState(android.media.session.PlaybackState.Builder().setState(android.media.session.PlaybackState.STATE_PLAYING,30000,1f,SystemClock.elapsedRealtime()).build())
            val snapshot=RuntimeExperience.snapshot(session.controller)
            assertTrue(snapshot.playing);assertEquals("Faixa teste",snapshot.title);assertEquals(120000L,snapshot.durationMs)
            assertEquals(.25f,snapshot.progress(SystemClock.elapsedRealtime()),.0001f);assertNotEquals(0,snapshot.color)
            assertTrue(android.graphics.Color.blue(snapshot.color)>android.graphics.Color.red(snapshot.color))
        } finally {session.release();art.recycle()}
    }
}
