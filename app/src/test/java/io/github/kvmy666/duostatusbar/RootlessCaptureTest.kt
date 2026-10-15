package io.github.kvmy666.duostatusbar

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.fx.ExperienceOptions
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.ui.buildFullReport
import io.github.kvmy666.duostatusbar.ui.telegramDiagnosticIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],packageName=BuildConfig.APPLICATION_ID)
class RootlessCaptureTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun `rootless capture contains useful technical sections and both profiles with root disabled`() {
        DuoPrefs.writeRootAllowed(context,false)
        DuoPrefs.write(context,DuoSettings(experienceJson=ExperienceOptions(dwellMs=19000,music=true).encode()),DuoOrientation.PORTRAIT)
        val report=RootlessCapture.collect(context)
        for(marker in listOf("rootless app capture","--- power ---","--- memory ---","--- our permissions and capabilities ---",
            "settings.portrait=","settings.landscape=","rootAllowed=false","batteryAttribution=whole-device snapshot","appProcessCpuElapsedMs="))
            assertTrue("missing $marker",report.contains(marker))
        assertTrue(report.length<30000)
        assertTrue(report.contains("19000"));assertFalse(report.contains("ssId="))
    }
    @Test fun `unrecognised imported preference text and metadata never enters a technical configuration`() {
        val settings=DuoSettings(tapAction="A private person's name",iconColor="PRIVATE_METADATA",
            experienceJson="""{"music":true,"privateTitle":"secret track","albumArtist":"private artist","unknown":{"serialNumber":"hidden"}}""")
        val report=DiagnosticConfiguration.render(settings)
        assertTrue(report.contains("tapAction=unknown"));assertTrue(report.contains("\"music\":true"))
        for(secret in listOf("private person's name","PRIVATE_METADATA","secret track","private artist","serialNumber","hidden"))assertFalse(report.contains(secret))
    }
    @Test fun `long problem description cannot displace module evidence from the complete report`() {
        val report=buildFullReport("x".repeat(600000),DuoSettings(),"status",emptyList(),"MODULE_RUNTIME_EVIDENCE",0,"ROOTLESS_DEVICE_FACTS")
        assertTrue(report.contains("MODULE_RUNTIME_EVIDENCE"));assertTrue(report.contains("ROOTLESS_DEVICE_FACTS"))
        assertTrue(report.length<30000)
    }
    @Test fun `Telegram share intent grants read-only access and leaves recipient confirmation to user`() {
        val pkg=PackageInfo().apply {packageName="org.telegram.messenger";applicationInfo=ApplicationInfo().apply {packageName="org.telegram.messenger"}}
        shadowOf(context.packageManager).installPackage(pkg)
        // Test our Intent contract, not AndroidX's Unix-path canonicalisation on the Windows host.
        // FileProvider integration is a device check; its production paths remain restricted.
        val uri=Uri.parse("content://${BuildConfig.APPLICATION_ID}.files/diagnostics/report.txt")
        val send=telegramDiagnosticIntent(context,uri,"unused")
        assertEquals(Intent.ACTION_SEND,send.action);assertEquals("org.telegram.messenger",send.`package`)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("@GMnevz"))
        assertEquals(uri,send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        assertEquals(0,send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(send.data)
    }
    @Test fun `diagnostic attachment is written inside app diagnostic directory with private fields removed`() {
        val file=requireNotNull(io.github.kvmy666.duostatusbar.ui.writeDiagnostics(context,"technical evidence\nemail=person@example.com ssid=PrivateWiFi"))
        assertEquals(context.getExternalFilesDir("diagnostics")!!.canonicalFile,file.parentFile!!.canonicalFile)
        val content=file.readText();assertTrue(content.contains("technical evidence"))
        assertFalse(content.contains("person@example.com"));assertFalse(content.contains("PrivateWiFi"))
    }
    @Test fun `Telegram absent uses standard Android share and text fallback is sanitised`() {
        val send=telegramDiagnosticIntent(context,null,"email=person@example.com ssid=PrivateWiFi")
        assertNull(send.`package`);assertEquals(Intent.ACTION_SEND,send.action)
        val text=send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text.contains("@GMnevz"));assertFalse(text.contains("person@example.com"));assertFalse(text.contains("PrivateWiFi"))
    }
    @Test fun `Telegram workflow has complete Portuguese English and Spanish labels`() {
        val previous=UiText.language
        try {
            for((language,label) in listOf("pt-BR" to "Enviar no Telegram · @GMnevz","en" to "Send on Telegram · @GMnevz","es" to "Enviar por Telegram · @GMnevz")) {
                UiText.initialize(context,language)
                assertEquals(label,UiText.t("Enviar no Telegram · @GMnevz"))
                assertFalse(UiText.format("Escolha {0} no Telegram e confirme o envio","@GMnevz").contains("{0}"))
            }
        } finally {UiText.language=previous}
    }
}
