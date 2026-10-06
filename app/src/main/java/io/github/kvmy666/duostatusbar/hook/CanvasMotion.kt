package io.github.kvmy666.duostatusbar.hook

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure animation maths, shared by the live renderer and offline tests. */
internal object CanvasMotion {
    fun step(elapsed: Long, tau: Float): Float =
        (1.0 - exp(-elapsed.coerceAtLeast(0).toDouble() / tau)).toFloat()

    fun overshoot(value: Float): Float {
        val x = value.coerceIn(0f, 1f) - 1f
        return x * x * (3.2f * x + 2.2f) + 1f
    }

    fun blend(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        if (t == 0f) return start
        if (t == 1f) return end
        fun channel(shift: Int): Int {
            val a = ((start ushr shift) and 255) / 255.0
            val b = ((end ushr shift) and 255) / 255.0
            val v = if (shift == 24) a + (b - a) * t
                else (a.pow(2.2) + (b.pow(2.2) - a.pow(2.2)) * t).pow(1.0 / 2.2)
            return (v * 255).roundToInt().coerceIn(0, 255) shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }

    fun fillDuration(delta: Int, revealMs: Int): Long {
        val maximum = (revealMs * 2.4f).toLong().coerceAtLeast(200)
        return (maximum * kotlin.math.abs(delta) / 100).coerceIn(200, maximum)
    }
}
