package io.github.kvmy666.duostatusbar

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.AppContextResolver
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.DuoSettingsProvider
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.ui.buildDiagnostics
import io.github.kvmy666.duostatusbar.ui.shareText
import io.github.kvmy666.duostatusbar.ui.writeDiagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class IntegrationAuditTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private class Bound(@JvmField val appContext: Context?)
    private class Initial(@JvmField val mInitialApplication: Context?, @JvmField val mBoundApplication: Any?)
    private class MissingInitial(@JvmField val mBoundApplication: Any?)

    @Test fun `missing optional AppBindData field is silent and does not poison retries`() {
        val before = L.recentText()
        repeat(3) { assertNull(AppContextResolver.contextFromThread(Initial(null, Any()))) }
        assertEquals(before, L.recentText())
        assertSame(context, AppContextResolver.contextFromThread(Initial(context, Any())))
    }

    @Test fun `independent bound fallback works even if initial application field is absent`() {
        assertSame(context, AppContextResolver.contextFromThread(MissingInitial(Bound(context))))
    }

    @Test fun `android system context cannot bootstrap provider even if a bound context exists`() {
        val unsafe = object : ContextWrapper(context) { override fun getPackageName() = "android" }
        assertNull(AppContextResolver.contextFromThread(Initial(unsafe, Bound(unsafe))))
        assertSame(context, AppContextResolver.contextFromThread(Initial(unsafe, Bound(context))))
    }

    @Test fun `diagnostic export sanitizes all entry points and preserves fault evidence`() {
        val report = buildDiagnostics(DuoSettings(), "FAILED email=person@example.com", listOf("ssid=PrivateWiFi"), "Exception=NoSuchFieldException /sdcard/private.jpg", 0)
        assertFalse(report.contains("person@example.com")); assertFalse(report.contains("PrivateWiFi"))
        assertFalse(report.contains("private.jpg")); assertTrue(report.contains("NoSuchFieldException"))
        val file = requireNotNull(writeDiagnostics(context, "SDK=35\nemail=person@example.com"))
        try { assertFalse(file.readText().contains("person@example.com")); assertTrue(file.readText().contains("SDK=35")) }
        finally { file.delete() }
    }

    @Test fun `two captures in the same second cannot overwrite an already shared diagnostic file`() {
        val a = requireNotNull(writeDiagnostics(context, "first capture"))
        val b = requireNotNull(writeDiagnostics(context, "second capture"))
        try {
            assertNotEquals(a.absolutePath, b.absolutePath)
            assertEquals("first capture", a.readText()); assertEquals("second capture", b.readText())
        } finally { a.delete(); b.delete() }
    }

    @Test fun `text share applies the same privacy boundary as file export`() {
        shareText(context, "SDK=35\nemail=person@example.com")
        val chooser = shadowOf(context as Application).nextStartedActivity
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        val shared = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertFalse(shared.contains("person@example.com")); assertTrue(shared.contains("SDK=35"))
    }

    @Test fun `capture truncation never publishes a half matched sensitive final token`() {
        val prefix = "technical\n" + "a".repeat(511_975) + "\n"
        val began = System.nanoTime()
        val safe = DiagnosticPrivacy.clean(prefix + "person@example.com" + "x".repeat(100))
        assertTrue("privacy processing must not monopolize CPU for long lines",System.nanoTime()-began<TimeUnit.SECONDS.toNanos(2))
        assertFalse(safe.contains("person@")); assertTrue(safe.startsWith("technical"))
    }

    @Test fun `report writer accepts installed SystemUI UID and app but rejects unrelated UID`() {
        val info = PackageInfo().apply {
            packageName = SettingsBridge.SYSTEMUI
            applicationInfo = ApplicationInfo().apply { packageName = SettingsBridge.SYSTEMUI; uid = 10123 }
        }
        shadowOf(context.packageManager).installPackage(info)
        assertTrue(DuoSettingsProvider.trustedReporter(context, context.applicationInfo.uid))
        assertTrue(DuoSettingsProvider.trustedReporter(context, 10123))
        assertFalse(DuoSettingsProvider.trustedReporter(context, 10999))
    }

    private class FakeProcess(private val source: InputStream, private val code: Int = 0) : Process() {
        @Volatile var destroyed = false
        override fun getInputStream() = source
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun waitFor(): Int = code
        override fun exitValue(): Int = code
        override fun destroy() { destroyed = true; source.close() }
        override fun isAlive(): Boolean = !destroyed
        override fun destroyForcibly(): Process { destroy(); return this }
    }
    private class BlockingPipe : InputStream() {
        val entered = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        override fun read(): Int {
            entered.countDown()
            try { closed.await() } catch (_: InterruptedException) { closed.await(1, TimeUnit.SECONDS) }
            return -1
        }
        override fun close() { closed.countDown() }
    }

    @Test fun `diagnostic timeout destroys process and closes blocked pipe instead of leaking a reader`() {
        val pipe = BlockingPipe(); val process = FakeProcess(pipe)
        val output = RootLogs.runRoot("fake-su", "id", 1, start = { process })
        assertTrue(pipe.entered.await(1, TimeUnit.SECONDS))
        assertTrue(output.contains("timed out")); assertTrue(process.destroyed)
    }

    @Test fun `completed root command failure remains a failure even when output contains uid text`() {
        val process = FakeProcess(ByteArrayInputStream("uid=0(root)".toByteArray()), 1)
        val output = RootLogs.runRoot("fake-su", "id", 1, start = { process })
        assertTrue(output.startsWith("root log collection failed: shell exit=1"))
        assertFalse(RootLogs.looksRooted(output)); assertTrue(process.destroyed)
    }

    @Test fun `successful commands with no stdout are accepted only for command mode`() {
        assertEquals("", RootLogs.runRoot("fake-su", "kill", 1, allowEmpty = true,
            start = { FakeProcess(ByteArrayInputStream(byteArrayOf())) }))
        assertTrue(RootLogs.runRoot("fake-su", "id", 1,
            start = { FakeProcess(ByteArrayInputStream(byteArrayOf())) }).startsWith("root log collection failed"))
        assertFalse(RootLogs.looksRooted("uid=000(shell)"))
        assertFalse(RootLogs.looksRooted("error uid=0(root)"))
    }
}
