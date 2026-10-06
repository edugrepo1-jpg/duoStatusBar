package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportAuthenticationTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    @Test fun `private challenge persists but missing and forged reports cannot overwrite actual status`() {
        val token = ReportAuthentication.token(app)
        assertEquals(token, ReportAuthentication.token(app))
        DuoPrefs.writeStatus(app, "actual")
        val receiver = StatusBridgeReceiver()
        val fake = Intent(SettingsBridge.ACTION_STATUS_PUSH).putExtra(SettingsBridge.EXTRA_STATUS, "forged")
        receiver.onReceive(app, fake)
        receiver.onReceive(app, fake.putExtra(SettingsBridge.EXTRA_REPORT_TOKEN, "0".repeat(64)))
        assertEquals("actual", DuoPrefs.status(app))
        receiver.onReceive(app, fake.putExtra(SettingsBridge.EXTRA_REPORT_TOKEN, token).putExtra(SettingsBridge.EXTRA_STATUS, "confirmed"))
        assertEquals("confirmed", DuoPrefs.status(app))
    }
    @Test fun `authenticated handshake flushes only latest status and bounded dump and rejects malformed token`() {
        val session = BridgeReportSession()
        val sent = mutableListOf<List<String>>()
        val emit: (String,String,String,String)->Unit = { a,k,v,t -> sent.add(listOf(a,k,v,t)) }
        assertFalse(session.send("status", "status", "old", emit))
        assertFalse(session.send("status", "status", "new", emit))
        assertFalse(session.send("dump", "dump", "x".repeat(200_000), emit))
        session.establish("bad", emit); assertTrue(sent.isEmpty())
        val token = ReportAuthentication.token(app)
        session.establish(token, emit)
        assertEquals(2, sent.size); assertEquals("new", sent[0][2]); assertEquals(128_000, sent[1][2].length)
        assertTrue(sent.all { it[3] == token })
        assertTrue(session.send("status", "status", "next", emit)); assertEquals(3, sent.size)
        session.establish(token, emit); assertEquals(3, sent.size)
    }
}
