package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Private challenge is delivered only through the signature-protected app -> SystemUI channel.
 * Never included in the provider row, telemetry, export or log. Works on Android 33 too. */
internal object ReportAuthentication {
    private const val STORE = "duo_private_report_channel"
    private const val KEY = "challenge"
    @Synchronized fun token(context: Context): String {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { valid(it) }?.let { return it }
        val value = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        check(prefs.edit().putString(KEY, value).commit())
        return value
    }
    fun valid(value: String?): Boolean = value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    fun accepts(context: Context, supplied: String?): Boolean {
        if (!valid(supplied)) return false
        val expected = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
        return valid(expected) && MessageDigest.isEqual(expected!!.toByteArray(Charsets.US_ASCII), supplied!!.toByteArray(Charsets.US_ASCII))
    }
}

/** At most the newest three reports wait for the authenticated settings handshake. No timer. */
internal class BridgeReportSession {
    private var token: String? = null
    private data class Pending(val key: String, val value: String)
    private val pending = linkedMapOf<String, Pending>()
    @Synchronized fun establish(value: String?, dispatch: (String, String, String, String) -> Unit) {
        if (!ReportAuthentication.valid(value)) return
        token = value
        val waiting = pending.toMap(); pending.clear()
        waiting.forEach { (action, report) -> dispatch(action, report.key, report.value, value!!) }
    }
    @Synchronized fun send(action: String, key: String, value: String, dispatch: (String, String, String, String) -> Unit): Boolean {
        val bounded = value.take(if (key == SettingsBridge.EXTRA_DUMP) 128_000 else 4_096)
        token?.let { dispatch(action, key, bounded, it); return true }
        pending[action] = Pending(key, bounded)
        while (pending.size > 3) pending.remove(pending.keys.first())
        return false
    }
}
