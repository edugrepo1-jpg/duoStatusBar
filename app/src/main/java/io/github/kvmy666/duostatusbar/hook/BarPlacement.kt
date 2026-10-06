package io.github.kvmy666.duostatusbar.hook

import android.view.View

/** Keep the entire artboard inside a measured bar, never a full-screen shade window. */
internal object BarPlacement {
    fun keepInside(view: View, bar: View?) {
        if (bar == null || bar.height <= 0 || view.width <= 0 || view.height <= 0 ||
            bar.height > view.resources.displayMetrics.heightPixels / 3) return
        val origin = IntArray(2); bar.getLocationInWindow(origin)
        val parent = view.parent as? View ?: return
        val position = IntArray(2); parent.getLocationInWindow(position)
        // Read the unshifted frame directly: integer location rounding must not accumulate on relayout.
        if (bar.width > 0) view.translationX = boundedTranslation(
            (position[0] + view.left).toFloat(), view.translationX, view.width, origin[0], bar.width)
        view.translationY = boundedTranslation(
            (position[1] + view.top).toFloat(), view.translationY, view.height, origin[1], bar.height)
    }
    fun boundedTranslation(base: Float, shift: Float, size: Int, origin: Int, extent: Int): Float {
        val end = (origin + extent - size).coerceAtLeast(origin).toFloat()
        return (base + shift).coerceIn(origin.toFloat(), end) - base
    }
}
