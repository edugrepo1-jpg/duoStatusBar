package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], manifest=Config.NONE)
class NfcFieldReceiverTest {
    private lateinit var context: Context
    private val sent = mutableListOf<Intent>()
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("duo_runtime", Context.MODE_PRIVATE).edit().clear().commit()
        context = object : ContextWrapper(app) {
            override fun sendBroadcast(intent: Intent) { sent.add(intent) }
        }
        configure(false, false)
    }
    private fun configure(enabled: Boolean, particles: Boolean) {
        DuoPrefs.write(context, DuoSettings(enabled=enabled,
            experienceJson=ExperienceOptions(nfcParticles=particles).encode()))
    }
    @Test fun `disabled feature or module never relays RF events`() {
        val receiver = NfcFieldReceiver()
        receiver.onReceive(context, Intent(NfcFieldReceiver.FIELD_ON))
        configure(true, false); receiver.onReceive(context, Intent(NfcFieldReceiver.FIELD_ON))
        configure(false, true); receiver.onReceive(context, Intent(NfcFieldReceiver.FIELD_ON))
        DuoPrefs.write(context, DuoSettings(enabled=true,
            experienceJson=ExperienceOptions(nfcParticles=true,networkOnly=true).encode()))
        receiver.onReceive(context, Intent(NfcFieldReceiver.FIELD_ON))
        assertTrue(sent.isEmpty())
    }
    @Test fun `RF relay strips foreign data and uses own timestamp and target`() {
        configure(true, true)
        val before = SystemClock.elapsedRealtime()
        NfcFieldReceiver().onReceive(context, Intent(NfcFieldReceiver.FIELD_ON)
            .putExtra("nfcFieldAt", Long.MAX_VALUE).putExtra("aid", byteArrayOf(1,2))
            .putExtra("amount", "private"))
        val result = sent.single()
        assertEquals(RuntimeExperience.ACTION, result.action)
        assertEquals(SettingsBridge.SYSTEMUI, result.`package`)
        assertTrue(result.getBooleanExtra("nfcField", false))
        assertEquals(setOf("nfcField", "nfcFieldAt"), result.extras!!.keySet())
        assertTrue(result.getLongExtra("nfcFieldAt", -1) in before..SystemClock.elapsedRealtime())
    }
    @Test fun `root mode gets RF off without any financial or adapter event accepted`() {
        configure(true, true)
        context.getSharedPreferences("duo_runtime", Context.MODE_PRIVATE).edit().putString("mode","root").commit()
        val receiver = NfcFieldReceiver()
        receiver.onReceive(context, Intent("android.nfc.action.ADAPTER_STATE_CHANGED"))
        receiver.onReceive(context, Intent("android.nfc.action.TRANSACTION_DETECTED"))
        assertTrue(sent.isEmpty())
        receiver.onReceive(context, Intent(NfcFieldReceiver.FIELD_OFF))
        assertEquals(BuildConfig.APPLICATION_ID, sent.single().`package`)
        assertFalse(sent.single().getBooleanExtra("nfcField", true))
    }
    @Test fun `manifest requires privileged sender and declares only RF action filters`() {
        val file = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }.newDocumentBuilder().parse(file)
        val ns="http://schemas.android.com/apk/res/android"
        val receivers=doc.getElementsByTagName("receiver")
        val receiver=(0 until receivers.length).map { receivers.item(it) as org.w3c.dom.Element }
            .single {it.getAttributeNS(ns,"name").endsWith(".NfcFieldReceiver")}
        assertEquals("true",receiver.getAttributeNS(ns,"exported"))
        assertEquals("android.permission.WRITE_SECURE_SETTINGS",receiver.getAttributeNS(ns,"permission"))
        val actions=receiver.getElementsByTagName("action")
        assertEquals(setOf(NfcFieldReceiver.FIELD_ON,NfcFieldReceiver.FIELD_OFF),
            (0 until actions.length).map {(actions.item(it) as org.w3c.dom.Element).getAttributeNS(ns,"name")}.toSet())
    }
}
