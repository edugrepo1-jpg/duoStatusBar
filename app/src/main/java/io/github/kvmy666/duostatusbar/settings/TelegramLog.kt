package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONObject

/**
 * One-tap bug-report delivery.
 *
 * Preferred route: a Cloudflare Worker relay ([BuildConfig.TELEGRAM_RELAY_URL]) holds the Telegram bot
 * token as a server secret, so the APK carries nothing secret; the app POSTs the report there and the
 * relay forwards it to the developer's chat. Fallback route: a directory bot token compiled from `.env`
 * ([BuildConfig.TELEGRAM_BOT_TOKEN]), used only for local/private builds. With neither configured the
 * caller falls back to the system share sheet.
 *
 * Everything is best-effort: no config, no network, or a rejection returns a non-SENT result. Never throws.
 */
internal object TelegramLog {

    /** What happened when a send was attempted. */
    enum class Result { SENT, RATE_LIMITED, FAILED }

    private const val API = "https://api.telegram.org"

    /**
     * Hidden cooldown between successful sends. Repeated taps (or a stuck finger) cannot spam the bot,
     * and it survives an app restart because the last-sent time is persisted.
     */
    private const val COOLDOWN_MS = 60_000L

    /** Whether a relay URL or a bot token was compiled in. False means the share sheet is used. */
    fun configured(): Boolean =
        BuildConfig.TELEGRAM_RELAY_URL.isNotBlank() || BuildConfig.TELEGRAM_BOT_TOKEN.isNotBlank()

    /** Uploads [file] to the developer's chat, honouring the hidden client-side cooldown. */
    fun send(context: Context, file: File, caption: String): Result {
        val relay = BuildConfig.TELEGRAM_RELAY_URL
        val token = BuildConfig.TELEGRAM_BOT_TOKEN
        if (relay.isBlank() && token.isBlank()) return Result.FAILED

        val now = System.currentTimeMillis()
        val since = now - DuoPrefs.logSentAt(context)
        if (since in 0 until COOLDOWN_MS) {
            L.i("telegram: rate limited (${(COOLDOWN_MS - since) / 1000}s left of the cooldown)")
            return Result.RATE_LIMITED
        }

        val delivered = if (relay.isNotBlank()) {
            // Preferred: the Worker holds the token, the app holds nothing secret. The Worker also
            // enforces its own per-IP cooldown, which a modified app cannot bypass.
            postMultipart(relay, file, caption, extraFields = emptyMap())
        } else {
            val chatId = BuildConfig.TELEGRAM_CHAT_ID.ifBlank {
                discoverChatId(token) ?: return Result.FAILED
            }
            postMultipart(
                "$API/bot$token/sendDocument",
                file,
                caption,
                extraFields = mapOf("chat_id" to chatId)
            )
        }
        return if (delivered) {
            DuoPrefs.writeLogSentAt(context, now)
            Result.SENT
        } else {
            Result.FAILED
        }
    }

    /** The chat id of the most recent message sent to the bot, or null. See the class note. */
    private fun discoverChatId(token: String): String? = try {
        val result = JSONObject(httpGet("$API/bot$token/getUpdates")).optJSONArray("result")
        var id: String? = null
        if (result != null) {
            for (i in 0 until result.length()) {
                val chat = result.getJSONObject(i).optJSONObject("message")?.optJSONObject("chat")
                if (chat != null && chat.has("id")) id = chat.getLong("id").toString()
            }
        }
        id
    } catch (t: Throwable) {
        L.w("telegram getUpdates: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    /**
     * POSTs the log as `multipart/form-data`. [extraFields] are added as form fields (the direct bot path
     * sends `chat_id`; the relay needs none). Returns whether the endpoint accepted it.
     */
    private fun postMultipart(
        url: String,
        file: File,
        caption: String,
        extraFields: Map<String, String>
    ): Boolean = try {
        val boundary = "----duo" + UUID.randomUUID().toString().replace("-", "")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            for ((name, value) in extraFields) field(out, boundary, name, value)
            if (caption.isNotBlank()) field(out, boundary, "caption", caption)
            filePart(out, boundary, "document", file)
            out.write("--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
            L.w("log upload HTTP $code: $body")
        } else {
            L.i("log delivered (${file.length()} bytes)")
        }
        conn.disconnect()
        code in 200..299
    } catch (t: Throwable) {
        L.w("telegram send: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private fun field(out: OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        out.write(value.toByteArray())
        out.write("\r\n".toByteArray())
    }

    private fun filePart(out: OutputStream, boundary: String, name: String, file: File) {
        out.write("--$boundary\r\n".toByteArray())
        out.write(
            "Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray()
        )
        out.write("Content-Type: text/plain\r\n\r\n".toByteArray())
        file.inputStream().use { it.copyTo(out) }
        out.write("\r\n".toByteArray())
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
