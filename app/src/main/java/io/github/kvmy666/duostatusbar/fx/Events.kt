package io.github.kvmy666.duostatusbar.fx

/** Local module diagnostics requested in section 10; no network destination. */
internal object Events {
    const val MARKER = "== Eventos do módulo (mais recentes por último) =="
    private val lines = ArrayDeque<String>()
    private const val MAX_CHARS = 96 * 1024
    private var retainedChars = 0
    @Volatile var changed: (() -> Unit)? = null
    fun record(at: Long, message: String) {
        try {
            synchronized(lines) {
                val line = "$at ${message.replace('\n', ' ').replace('\r', ' ').take(2000)}"
                lines.addLast(line); retainedChars += line.length + 1
                while (lines.size > 400 || retainedChars > MAX_CHARS) retainedChars -= lines.removeFirst().length + 1
            }
            changed?.invoke()
        } catch (_: Throwable) { }
    }
    fun snapshot(): String = synchronized(lines) { lines.joinToString("\n") }
    fun wrap(base: String?): String = (base ?: "").substringBefore(MARKER).trimEnd() + "\n\n$MARKER\n" + snapshot()
    internal fun clear() = synchronized(lines) { lines.clear(); retainedChars = 0 }
}
