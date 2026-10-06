package io.github.kvmy666.duostatusbar.ui
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.DiagnosticTransport
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class PreferenceAuditTest {
    @Test fun `shared timing updates both orientations without replacing individual maps or features`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val landscape=ExperienceOptions(island=true,music=true,iconSeconds=mapOf("NFC" to 47),universalTiming=false)
        DuoPrefs.write(context,DuoSettings(sizePercent=175,experienceJson=landscape.encode()),DuoOrientation.LANDSCAPE)
        val before=DuoSettings(experienceJson=ExperienceOptions().encode())
        val next=before.copy(experienceJson=ExperienceOptions(dwellMs=60000).encode())
        TimingPreferences.publish(context,before,next,DuoOrientation.PORTRAIT)
        val saved=DuoPrefs.read(context,DuoOrientation.LANDSCAPE)
        val actual=ExperienceOptions.decode(saved.experienceJson)
        assertEquals(175,saved.sizePercent);assertTrue(actual.island);assertTrue(actual.music)
        assertEquals(mapOf("NFC" to 47),actual.iconSeconds);assertEquals(60000,actual.dwellMs);assertTrue(actual.universalTiming)
    }
    @Test fun `individual timing never overwrites another orientation`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val saved=DuoSettings(experienceJson=ExperienceOptions(dwellMs=30000).encode())
        DuoPrefs.write(context,saved,DuoOrientation.LANDSCAPE)
        TimingPreferences.publish(context,DuoSettings(),DuoSettings(experienceJson=ExperienceOptions(dwellMs=1000,universalTiming=false).encode()),DuoOrientation.PORTRAIT)
        assertEquals(saved.experienceJson,DuoPrefs.read(context,DuoOrientation.LANDSCAPE).experienceJson)
    }
    @Test fun `unsafe update metadata cannot become a local APK filename or bypass verification`() {
        for(version in listOf("../../other", "1/2", "", "a".repeat(81), "1\\2"))assertFalse(UpdateDownloadPolicy.validVersion(version))
        assertTrue(UpdateDownloadPolicy.validVersion("1.4.2-canvas-audit"))
        assertFalse(UpdateDownloadPolicy.validDigest(""));assertFalse(UpdateDownloadPolicy.validDigest("f".repeat(63)))
        assertTrue(UpdateDownloadPolicy.validDigest("Af".repeat(32)))
        assertFalse(UpdateDownloadPolicy.compatibleChannel("1.4.2-canvas-audit","1.5.0"))
        assertTrue(UpdateDownloadPolicy.compatibleChannel("1.4.2-canvas-audit","1.5.0-canvas-audit"))
    }
    @Test fun `large technical report fits Binder and retains header and most recent event without personal values`() {
        val raw="device=Android 16\nssid=PrivateNetwork\n"+"old trace line\n".repeat(50000)+"\nmost-recent-event=attached\nemail=person@example.com\n"
        val prepared=DiagnosticTransport.prepare(raw)
        assertTrue(prepared.length<=128000);assertTrue(prepared.startsWith("device=Android 16"))
        assertTrue(prepared.contains("most-recent-event=attached"));assertFalse(prepared.contains("PrivateNetwork"));assertFalse(prepared.contains("person@example.com"))
    }
    @Test fun `recording owns the center while its dot and timer alternate without exposing WiFi`() {
        val cycle=SlotCycle();cycle.configure(ExperienceOptions(dwellMs=1000,recordingTime=true),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.RECORD,SlotIcon.RECORD_TIME),0);cycle.hold(SlotIcon.RECORD,0,true)
        assertEquals(SlotIcon.RECORD,cycle.frame(400).icon)
        assertEquals(SlotIcon.RECORD,cycle.frame(940).icon);assertEquals(.5f,cycle.frame(940).opacity,.001f)
        assertEquals(SlotIcon.RECORD_TIME,cycle.frame(1080).icon)
        assertEquals(SlotIcon.RECORD_TIME,cycle.frame(1600).icon)
        assertEquals(SlotIcon.RECORD,cycle.frame(2300).icon)
        for(time in 3000L..30000L step 50)assertTrue(cycle.frame(time).icon in setOf(SlotIcon.RECORD,SlotIcon.RECORD_TIME))
        cycle.hold(null,31000);cycle.frame(31280);assertEquals(SlotIcon.WIFI,cycle.frame(31500).icon)
    }
}
