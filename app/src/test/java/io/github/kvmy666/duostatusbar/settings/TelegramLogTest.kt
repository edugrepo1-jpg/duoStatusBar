package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.BuildConfig
import java.io.File
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Send the bug to the developer" button must work on any device the APK is installed on, including
 * a build made by CI. The failure this pins down actually shipped once: the release workflow has no
 * `.env`, so the relay URL was not compiled in, `configured()` was false and the button silently fell back
 * to the share sheet on every device. The URL is public, so it is now baked in by default
 * (see `app/build.gradle.kts`) and this test makes the omission a CI failure rather than a user report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TelegramLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a relay url is always baked into the build`() {
        assertTrue(
            "TELEGRAM_RELAY_URL must be compiled in, or the report button falls back to the share sheet",
            BuildConfig.TELEGRAM_RELAY_URL.isNotBlank()
        )
        assertTrue(
            "the relay must be https",
            BuildConfig.TELEGRAM_RELAY_URL.startsWith("https://")
        )
        // A malformed URL would make every upload throw and fall back; parse it here.
        val parsed = URL(BuildConfig.TELEGRAM_RELAY_URL)
        assertTrue("the relay needs a host", parsed.host.isNotBlank())
    }

    @Test
    fun `the report button is configured on a normal build`() {
        assertTrue(TelegramLog.configured())
    }

    @Test
    fun `the cooldown window is one minute`() {
        assertEquals(60_000L, TelegramLog.COOLDOWN_MS)
    }

    @Test
    fun `the cooldown opens exactly after a minute`() {
        val now = 2_000_000L
        assertTrue(TelegramLog.withinCooldown(lastSentAt = now, now = now))
        assertTrue(TelegramLog.withinCooldown(lastSentAt = now - 1, now = now))
        assertTrue(TelegramLog.withinCooldown(lastSentAt = now - 59_999, now = now))
        // One millisecond past the window is allowed again.
        assertFalse(TelegramLog.withinCooldown(lastSentAt = now - 60_000, now = now))
        // A device that has never sent is never "inside" the cooldown.
        assertFalse(TelegramLog.withinCooldown(lastSentAt = 0L, now = now))
    }

    @Test
    fun `a second send inside the cooldown is refused without touching the network`() {
        DuoPrefs.writeLogSentAt(context, System.currentTimeMillis())
        val file = File.createTempFile("duo-log", ".txt").apply { writeText("duo") }
        try {
            // No network is reachable in a unit test, so a SENT/FAILED result would mean the guard was
            // skipped; RATE_LIMITED proves the cooldown returned before the upload.
            assertEquals(TelegramLog.Result.RATE_LIMITED, TelegramLog.send(context, file, "test"))
        } finally {
            file.delete()
        }
    }
}
