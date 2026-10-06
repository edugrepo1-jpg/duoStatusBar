package io.github.kvmy666.duostatusbar.hook

/**
 * The element's palette and the battery-tint rule (FR-15/15b).
 *
 * Pure and unit-tested (`DuoMappingTest`).
 */
object Colors {

    const val GREEN_CHARGING = 0xFF34C759.toInt()
    const val YELLOW_SAVER = 0xFFF2B900.toInt()
    const val RED_CRITICAL = 0xFFFF3B30.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** The bar-matching foreground for a light bar (FR-15b). */
    const val BLACK = 0xFF000000.toInt()

    /** Charging wins, then the real 1–20% warning, then power saver.
     *  [base] is the bar-matching foreground (white or black) used when no rule applies, so the ring
     *  is black on a light bar and white on a dark one (FR-15b). */
    fun tint(level: Int, charging: Boolean, saver: Boolean, base: Int = WHITE): Int = when {
        charging -> GREEN_CHARGING
        level in 1..20 -> RED_CRITICAL
        saver -> YELLOW_SAVER
        else -> base
    }
}
