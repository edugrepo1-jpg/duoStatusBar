package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class ForkAuditTest {
    @Test fun `fork identity is distinct and every public contract agrees`() {
        assertEquals("io.github.RECREATE.statusbar",BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID+".settings",DuoPrefs.AUTHORITY)
        assertEquals(BuildConfig.APPLICATION_ID+".permission.SETTINGS",SettingsBridge.PERMISSION)
        assertTrue(UpdateChecker.RELEASES_PAGE.startsWith("https://github.com/edugrepo1-jpg/duoStatusBar/"))
        assertFalse(TelegramLog.configured())
    }
    @Test fun `update refuses another app signer and downgrades despite matching transfer hash`() {
        val cert=setOf("certificate")
        assertTrue(UpdateArtifactVerifier.validIdentity(BuildConfig.APPLICATION_ID,29,28,cert,cert))
        assertFalse(UpdateArtifactVerifier.validIdentity("io.github.kvmy666.duostatusbar",29,28,cert,cert))
        assertFalse(UpdateArtifactVerifier.validIdentity(BuildConfig.APPLICATION_ID,29,28,setOf("other"),cert))
        assertFalse(UpdateArtifactVerifier.validIdentity(BuildConfig.APPLICATION_ID,28,28,cert,cert))
        assertFalse(UpdateArtifactVerifier.validIdentity(BuildConfig.APPLICATION_ID,29,28,emptySet(),emptySet()))
        val path="https://github.com/edugrepo1-jpg/duoStatusBar/releases/download/v1/a.apk"
        assertTrue(UpdateDownloadPolicy.trustedAsset(path))
        for(url in listOf(path.replace("https","http"),path.replace("github.com/","github.com.evil/"),path.replace("edugrepo1-jpg","kvmy666"),path+"?override=true",path.replace("/a.apk","/../a.apk")))assertFalse(UpdateDownloadPolicy.trustedAsset(url))
    }
    @Test fun `legacy Canvas migration retains both orientations all effects and independent timing`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("duo_settings",Context.MODE_PRIVATE).edit().clear().commit()
        val portrait=DuoSettings(enabled=true,thickPercent=250,experienceJson=ExperienceOptions.ALL.copy(dwellMs=59000).encode())
        val landscape=portrait.copy(sizePercent=177,experienceJson=ExperienceOptions.ALL.copy(dwellMs=17000,universalTiming=false).encode())
        val cursor=MatrixCursor(DuoPrefs.COLUMNS).apply {addRow(DuoSettingsProvider.rowFor(portrait,55,landscape))}
        assertTrue(LegacySettingsMigration.importCursor(context,cursor))
        assertEquals(portrait,DuoPrefs.read(context,DuoOrientation.PORTRAIT))
        assertEquals(landscape,DuoPrefs.read(context,DuoOrientation.LANDSCAPE))
        assertFalse(LegacySettingsMigration.importCursor(context,cursor))
        assertFalse(context.getSharedPreferences("duo_settings",Context.MODE_PRIVATE).contains("root_allowed"))
    }
    @Test fun `theme choice persists and invalid stored choice falls back to system`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        for(choice in AppThemeMode.entries){ThemePreference.write(context,choice);assertEquals(choice,ThemePreference.read(context))}
        context.getSharedPreferences("duo_app_appearance",Context.MODE_PRIVATE).edit().putString("mode","invalid").commit()
        assertEquals(AppThemeMode.SYSTEM,ThemePreference.read(context))
    }
}
