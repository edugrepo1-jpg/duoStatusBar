package io.github.kvmy666.duostatusbar.ui

/** A remote/recomposed value cannot pull the thumb back while the finger owns it. */
internal data class ControlDraft(val value: Float, val editing: Boolean = false) {
    fun external(next: Float) = if (editing) this else copy(value = next)
    fun move(next: Float, range: ClosedFloatingPointRange<Float>) =
        ControlDraft(next.coerceIn(range.start, range.endInclusive), true)
    fun finish() = copy(editing = false)
}
