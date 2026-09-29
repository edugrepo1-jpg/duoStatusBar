package io.github.kvmy666.duostatusbar.hook

/**
 * The ring's arc/trim/gap maths, straight out of docs/DESIGN-duo.md.
 *
 * Pure and unit-tested (`DuoMappingTest`): this is the half that can go wrong silently.
 */
object RingGeometry {

    // The fill runs from the bottom-left endpoint clockwise: left segment covers 0-50 %, right segment
    // covers 50-100 %, and the top gap between them holds the number (or the bolt). The gap is a
    // *variable*: 71.3 deg with the digits, 55.6 deg with the bolt, and 0 (the ring closes) when the
    // number is off - DESIGN §4.
    const val LEFT_START = 0.6631f
    const val RIGHT_FULL = 0.3369f
    const val GAP_PERCENT_DEG = 71.3f
    const val GAP_CHARGING_DEG = 55.6f

    /** The percent-on gap edges; the "closed" case is 0 and 1. */
    val RIGHT_START = GAP_PERCENT_DEG / 720f
    val LEFT_FULL = 1f - RIGHT_START

    /**
     * The two halves' arc lengths, as trim fractions. Each half is one trimmed window anchored by
     * `offset` and sized by `end`, so these are *lengths*, not positions.
     *
     * With the gap open each half covers [LEFT_START]..[LEFT_FULL] / [RIGHT_START]..[RIGHT_FULL].
     * With it closed the left half grows past 12 o'clock to cover the whole [DRAWN_ARC] on its own
     * and the right half goes to zero - there is deliberately no pair of windows meeting at 12
     * o'clock, because each would end in a round cap and the two caps would overlap: the left body
     * painted over the right body (user-reported bug), and the track's two 0.22 caps stacked to 0.46
     * and showed as a bright blob.
     */
    /** The whole drawn arc once the gap is closed: bottom-left endpoint, through 12 o'clock, round. */
    const val DRAWN_ARC = (1f - LEFT_START) + RIGHT_FULL

    /** The top gap is closed exactly when neither the digits nor the bolt occupy it. */
    fun gapClosed(charging: Boolean, showPercent: Boolean): Boolean = !charging && !showPercent

    /** One half's arc length for the current gap: the bolt's narrower gap makes the halves longer. */
    fun halfArc(charging: Boolean): Float =
        1f - (if (charging) GAP_CHARGING_DEG else GAP_PERCENT_DEG) / 720f - LEFT_START

    /** Left half's arc length: half the drawn arc, or all of it once the gap closes. */
    fun leftArc(charging: Boolean, showPercent: Boolean): Float =
        if (gapClosed(charging, showPercent)) DRAWN_ARC else halfArc(charging)

    /** Right half's arc length: half the drawn arc, or nothing once the gap closes. */
    fun rightArc(charging: Boolean, showPercent: Boolean): Float =
        if (gapClosed(charging, showPercent)) 0f else halfArc(charging)

    /**
     * Left half's filled arc: 0 % -> 50 % of [halfArc] normally. With the gap closed it owns the
     * whole [DRAWN_ARC] instead, so 0 % -> 100 % of that.
     */
    fun trimLeft(level: Int, charging: Boolean, closed: Boolean): Float =
        if (closed) DRAWN_ARC * (level.coerceIn(0, 100) / 100f)
        else halfArc(charging) * (level.coerceIn(0, 50) / 50f)

    /** Right half's filled arc: 50 % -> 100 % of [halfArc], and nothing while the gap is closed. */
    fun trimRight(level: Int, charging: Boolean, closed: Boolean): Float =
        if (closed) 0f else halfArc(charging) * ((level.coerceIn(50, 100) - 50) / 50f)

    /**
     * Font size, measured against the reference: the digits' cap height is 0.218 of the ring diameter
     * (`tools/measure-element.py` on `duo-reference-02`: 43 px digits / 197 px ring), so ~22.5 units in
     * the 103-unit ring - a font of 32 for Montserrat (cap height ~0.7 em). Three digits shrink further
     * so `100` still fits the gap.
     */
    fun percentFontSize(text: String): Float = if (text.length >= 3) 26f else 32f
}
