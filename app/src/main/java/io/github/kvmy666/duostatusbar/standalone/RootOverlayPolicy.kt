package io.github.kvmy666.duostatusbar.standalone

import io.github.kvmy666.duostatusbar.hook.RingGeometry

/** Bounds and authorization decisions shared by the root helper and its regression tests. */
internal object RootOverlayPolicy {
    const val SOCKET = "io.github.RECREATE.statusbar.root"
    const val STARTUP_TIMEOUT_MS = 8000L
    const val VISIBLE_POLL_MS = 2000L
    const val HIDDEN_POLL_MS = 8000L
    const val MAX_READ_FAILURES = 3
    fun validGeneration(value: String) = value.length in 16..128 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
    fun authorized(uid: Int, expected: String, mode: String?, generation: String?) =
        uid == 0 && validGeneration(expected) && mode == "root" && generation == expected

    data class Box(val width: Int, val height: Int, val right: Int, val top: Int, val side: Int)
    fun box(screenWidth: Int, screenHeight: Int, density: Float, barHeight: Int,
            sizePercent: Int, offsetDp: Int, split: Boolean, indicatorsOffsetDp: Int): Box {
        val sw = screenWidth.coerceAtLeast(1); val sh = screenHeight.coerceAtLeast(1)
        val d = density.takeIf { it.isFinite() && it > 0 }?.coerceIn(.1f,10f) ?: 1f
        val bar = barHeight.coerceIn(1, sh)
        // Preserve the normal slot baseline while keeping even extreme saved settings on screen.
        val wanted = (27.4f * d * sizePercent.coerceIn(1, 300) / 100).toInt().coerceAtLeast(1)
        val side = minOf(wanted, sw / if (split) 2 else 1,
            (minOf(sh.toLong(),bar.toLong()*2).toFloat() * RingGeometry.ARTBOARD_SIZE / RingGeometry.ARTBOARD_HEIGHT).toInt().coerceAtLeast(1)).coerceAtLeast(1)
        val separation = if (split) (indicatorsOffsetDp.coerceIn(-300,300) * d).toInt() else 0
        val width = (if (split) (side * 2 + kotlin.math.abs(separation)).coerceAtMost(sw) else side).coerceAtLeast(1)
        val height = RingGeometry.elementHeightPx(side).coerceAtMost(sh)
        val inset = (8 * d - offsetDp.coerceIn(-300,300) * d).toInt().coerceIn(0, (sw - width).coerceAtLeast(0))
        return Box(width, height, inset, ((bar-height)/2).coerceIn(0,(sh-height).coerceAtLeast(0)),side)
    }
}
