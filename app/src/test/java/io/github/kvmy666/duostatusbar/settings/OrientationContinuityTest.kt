package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.res.Configuration
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],manifest=Config.NONE)
class OrientationContinuityTest {
    private val c=ApplicationProvider.getApplicationContext<Context>()
    private val portrait=DuoSettings(enabled=true,sizePercent=133,notificationSize=200,quickSettingsSize=180,
        longPressAction="no_action",experienceJson=ExperienceOptions.ALL.copy(dwellMs=17000).encode())
    private val obsolete=portrait.copy(sizePercent=100,notificationSize=100,quickSettingsSize=100,
        experienceJson=ExperienceOptions().encode())
    @Before fun clear() {c.getSharedPreferences("duo_settings",Context.MODE_PRIVATE).edit().clear().commit()}
    private fun existingProfiles() {
        DuoPrefs.write(c,portrait,DuoOrientation.PORTRAIT,independent=true)
        DuoPrefs.write(c,obsolete,DuoOrientation.LANDSCAPE,independent=true)
    }
    @Test fun `upgrade ignores an obsolete horizontal profile while retaining its backup`() {
        existingProfiles()
        assertTrue(DuoPrefs.linkOrientations(c))
        assertEquals(portrait,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
        DuoPrefs.writeLinkOrientations(c,false)
        assertEquals(obsolete,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
        assertEquals(portrait,DuoPrefs.read(c,DuoOrientation.PORTRAIT))
    }
    @Test fun `edits while horizontal update the common profile without overwriting the backup`() {
        existingProfiles();val changed=portrait.copy(sizePercent=155,notificationSize=160)
        DuoPrefs.write(c,changed,DuoOrientation.LANDSCAPE)
        assertEquals(changed,DuoPrefs.read(c,DuoOrientation.PORTRAIT))
        assertEquals(changed,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
        DuoPrefs.writeLinkOrientations(c,false)
        assertEquals(obsolete,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
    }
    @Test fun `provider publishes every effect gesture icon and size identically during rotation`() {
        existingProfiles()
        val p=DuoPrefs.read(c,DuoOrientation.PORTRAIT);val l=DuoPrefs.read(c,DuoOrientation.LANDSCAPE)
        val cursor=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(DuoSettingsProvider.rowFor(p,DuoPrefs.revision(c),l));moveToFirst()}
        assertEquals(DuoSettingsClient.fromCursor(cursor,Configuration.ORIENTATION_PORTRAIT),
            DuoSettingsClient.fromCursor(cursor,Configuration.ORIENTATION_LANDSCAPE))
    }
    @Test fun `opting out saves truly independent profiles and each switch bumps the revision`() {
        val revision=DuoPrefs.revision(c)
        assertTrue(DuoPrefs.writeLinkOrientations(c,false)>revision)
        DuoPrefs.write(c,portrait,DuoOrientation.PORTRAIT);DuoPrefs.write(c,obsolete,DuoOrientation.LANDSCAPE)
        assertEquals(portrait,DuoPrefs.read(c,DuoOrientation.PORTRAIT))
        assertEquals(obsolete,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
        val before=DuoPrefs.revision(c);assertTrue(DuoPrefs.writeLinkOrientations(c,true)>before)
        assertEquals(portrait,DuoPrefs.read(c,DuoOrientation.LANDSCAPE))
    }
}
