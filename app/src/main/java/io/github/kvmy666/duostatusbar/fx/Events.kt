package io.github.kvmy666.duostatusbar.fx

/** Local module diagnostics requested in section 10; no network destination. */
internal object Events {
    const val MARKER = "== Eventos do módulo (mais recentes por último) =="
    private val lines = ArrayDeque<String>()
    @Volatile var changed: (() -> Unit)? = null
    fun record(at: Long, message: String) {
        try {
            synchronized(lines) {
                lines.addLast("$at ${message.replace('\n', ' ').replace('\r', ' ').take(2000)}")
                while (lines.size > 400) lines.removeFirst()
            }
            changed?.invoke()
        } catch (_: Throwable) { }
    }
    fun snapshot(): String = synchronized(lines) { lines.joinToString("\n") }
    fun wrap(base: String?): String = (base ?: "").substringBefore(MARKER).trimEnd() + "\n\n$MARKER\n" + snapshot()
    internal fun clear() = synchronized(lines) { lines.clear() }
}
