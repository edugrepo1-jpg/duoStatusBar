package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FR-28: where the support button goes. */
internal const val DONATE_URL = "https://paypal.me/kroomfahd"

/** Where a fallback report goes: the developer's Telegram, and the issue tracker. */
internal const val TELEGRAM_CHAT_URL = "https://t.me/kvmy1"
internal const val GITHUB_NEW_ISSUE = "https://github.com/kvmy666/duoStatusBar/issues/new"
internal val TELEGRAM_PACKAGES = listOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.telegram.messenger.beta"
)

/**
 * Saves the diagnostics file and hands it to Telegram, so one press gets the log on its way. When
 * Telegram is not installed the chat is opened instead, and the file can be attached from
 * "Save status to a file".
 */
internal fun sendLogOnTelegram(context: Context, report: String) {
    val file = writeDiagnostics(context, report)
    // Preferred: the bot uploads it straight to the developer's chat, no user step.
    if (file != null &&
        TelegramLog.send(context, file, "Duo Status Bar fallback report") == TelegramLog.Result.SENT
    ) return
    // Bot not configured or offline: hand the file to Telegram if installed, else open the chat.
    val uri = file?.let { fileUri(context, it) }
    val telegram = TELEGRAM_PACKAGES.firstOrNull { pkg ->
        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
    }
    if (uri != null && telegram != null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Duo Status Bar log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(telegram)
        }
        if (runCatching { context.startActivity(send) }.isSuccess) return
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHAT_URL))) }
        .onFailure { L.w("telegram open: ${it.message}") }
}

/** Opens a new GitHub issue with the device and status pre-filled; the report file can be attached there. */
internal fun openGitHubIssue(context: Context, fallback: String, status: String) {
    val body = buildString {
        appendLine("Duo Status Bar fallback report")
        appendLine()
        appendLine("fallback: $fallback")
        appendLine("status: $status")
        appendLine(
            "device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
        )
        append("Please attach the diagnostics file shared from the app's About section.")
    }
    val url = "$GITHUB_NEW_ISSUE?body=${Uri.encode(body)}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { L.w("github open: ${it.message}") }
}

/** The report the About buttons send — one text, shared or written to a file. */
internal fun buildDiagnostics(
    settings: DuoSettings,
    status: String,
    history: List<String>,
    dump: String,
    moduleLoadAt: Long
): String =
    buildString {
        appendLine("Duo Status Bar diagnostics")
        appendLine("settings: $settings")
        appendLine(
            "module load: " + if (moduleLoadAt <= 0L) {
                "never (LSPosed has not injected the module into System UI)"
            } else {
                formatTimestamp(moduleLoadAt)
            }
        )
        appendLine("module: ").append(status.ifEmpty { "no report yet" })
        if (history.isNotEmpty()) {
            appendLine("history:")
            history.forEach { appendLine("  $it") }
        }
        // The debug build's full dump (build identity, id probes, view tree, readers). Empty in release.
        if (dump.isNotEmpty()) {
            appendLine()
            appendLine(dump)
        }
    }

/** The complete bug report: the user's description, the diagnostics, then the device capture. */
internal fun buildFullReport(
    problem: String,
    settings: DuoSettings,
    status: String,
    history: List<String>,
    dump: String,
    moduleLoadAt: Long,
    logs: String
): String = buildString {
    val message = problem.trim()
    if (message.isNotEmpty()) {
        appendLine("user message:")
        appendLine(message)
        appendLine()
    }
    append(buildDiagnostics(settings, status, history, dump, moduleLoadAt))
    append("\n\n===== device capture =====\n")
    append(logs)
}

internal fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

internal fun shareText(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(send, "Share diagnostics"))
}

/**
 * Hands the collected log to a share sheet, where the user picks Telegram (the developer, @kvmy1),
 * another app, or "Save to Files" — one button, "send it" or "keep it", as the user prefers.
 */
internal fun shareLog(context: Context, file: Uri) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, file)
        putExtra(Intent.EXTRA_TEXT, "Duo Status Bar log — @kvmy1")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(share, context.getString(R.string.settings_log_share_title))
    )
}

/**
 * FR-28: writes the report to the app's own diagnostics directory and returns a shareable Uri.
 *
 * A bug report needs the whole log, and a shared string gets truncated by chat apps. The file lives in
 * the app's external files dir and is handed out through a FileProvider, so nothing else is exposed.
 * Returns null (and the caller shares nothing) if the directory is unavailable.
 */
internal fun writeDiagnostics(context: Context, report: String): File? = try {
    val dir = context.getExternalFilesDir("diagnostics") ?: return null
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    File(dir, "duo-diagnostics-$stamp.txt").apply { writeText(report) }
} catch (t: Throwable) {
    L.w("diagnostics file: ${t.javaClass.simpleName}: ${t.message}")
    null
}

/** A shareable Uri for a written diagnostics [file], or null if the FileProvider cannot serve it. */
internal fun fileUri(context: Context, file: File): Uri? = try {
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
} catch (t: Throwable) {
    L.w("diagnostics uri: ${t.javaClass.simpleName}: ${t.message}")
    null
}
