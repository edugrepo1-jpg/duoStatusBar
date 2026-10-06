package io.github.kvmy666.duostatusbar.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.DuoCanvasPreview
import io.github.kvmy666.duostatusbar.hook.DuoVisual

/** Static Canvas chip: sliders remain readable while the dedicated effects demo animates. */
@Composable
fun DuoSettingPreview(
    off: DuoVisual,
    on: DuoVisual,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    periodMs: Long = DEMO_PERIOD_MS,
    /** For the size setting: the element's scale at the off end, eased to 1 at the on end. */
    scaleFrom: Float = 1f,
    /** For the position setting: how far the element slides, in dp, at the on end. */
    slideDp: Float = 0f
) {
    io.github.kvmy666.duostatusbar.DuoCanvasStill(
        visual = on,
        modifier = modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Color(0xFF101014))
    )
}

/**
 * Where the demo is in its off -> on -> off cycle, given the fraction [t] through one period.
 *
 * A plain triangle wave would spend half the loop mid-morph, which reads as a blur rather than as two
 * states. So each end is held still for a moment and the move between them is eased.
 */
internal fun demoPhase(t: Float): Float = when {
    t < HOLD -> 0f
    t < HOLD + MOVE -> smooth((t - HOLD) / MOVE)
    t < HOLD + MOVE + HOLD -> 1f
    t < HOLD + MOVE + HOLD + MOVE -> 1f - smooth((t - HOLD - MOVE - HOLD) / MOVE)
    else -> 0f
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** Fraction of the loop each end is held, and spent moving between them. */
private const val HOLD = 0.16f
private const val MOVE = 0.30f

/** Long enough to read, short enough that several rows are not all in step. */
private const val DEMO_PERIOD_MS = 2400L
