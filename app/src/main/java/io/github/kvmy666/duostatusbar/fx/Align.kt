package io.github.kvmy666.duostatusbar.fx

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import kotlin.math.abs

internal object Align {
    fun adjust(view: View, root: View?, offsetDp: Int) {
        if (!Fx.enabled(2) || view !is DuoCanvasView || view.height <= 0 || view.width <= 0) return
        try {
            val time = clock(root) ?: return
            val cp = IntArray(2); time.getLocationInWindow(cp)
            val rp = IntArray(2); view.getLocationInWindow(rp)
            val b = Rect(); time.paint.getTextBounds("0", 0, 1, b)
            val scale = minOf(view.width / 120f, view.height / 136f)
            val digit = cp[1] + time.baseline + (b.top + b.bottom) / 2f
            val base = rp[1] - view.translationY + (view.height - 136 * scale) / 2f + 77.5f * scale
            val shift = digit - base
            if (shift.isFinite() && abs(shift) <= 40 * view.resources.displayMetrics.density) {
                view.translationY = shift
                if (!view.lastAlignment.isFinite() || abs(view.lastAlignment - shift) > 1) {
                    view.lastAlignment = shift; L.i("align: shift=${shift}px digitCenter=$digit ringCenter=${base + shift}")
                }
            }
            if (view.part == io.github.kvmy666.duostatusbar.hook.DuoPart.INDICATORS) return
            val target = calculateDynamicIslandOffsets(view) ?: return
            val desired = target.first + offsetDp * view.resources.displayMetrics.density
            val radius = 55.5f * scale * view.globalPercent / 100f
            val ring = Rect((desired - radius).toInt(), (digit - radius).toInt(), (desired + radius).toInt(), (digit + radius).toInt())
            if (overlapsNative(root, view, ring)) return
            view.translationX = desired - (rp[0] - view.translationX + view.width / 2f)
        } catch (t: Throwable) { L.w("Posição automática: ${t.message}") }
    }
    private fun overlapsNative(node: View?, ours: View, ring: Rect): Boolean {
        if (node == null || node === ours || node is DuoCanvasView || !node.isShown) return false
        if (node is ViewGroup) return (0 until node.childCount).any { overlapsNative(node.getChildAt(it), ours, ring) }
        if (node !is TextView && node !is android.widget.ImageView) return false
        val pos = IntArray(2); node.getLocationInWindow(pos)
        return Rect.intersects(ring, Rect(pos[0],pos[1],pos[0]+node.width,pos[1]+node.height))
    }
    fun clock(root: View?): TextView? {
        if (root == null) return null
        if (root is TextView && root.isShown && root.height > 0 &&
            (root.javaClass.simpleName.contains("Clock") || runCatching { root.resources.getResourceEntryName(root.id) == "clock" }.getOrDefault(false))) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) clock(root.getChildAt(i))?.let { return it }
        return null
    }
    fun shift(container: ViewGroup, root: View?): Float {
        if (!Fx.enabled(2)) return Float.NaN
        return try {
            val clock = clock(root) ?: return Float.NaN
            val ring = (0 until container.childCount).map { container.getChildAt(it) }.filterIsInstance<DuoCanvasView>().firstOrNull() ?: return Float.NaN
            if (ring.height == 0) return Float.NaN
            val bounds = Rect(); clock.paint.getTextBounds("0", 0, 1, bounds)
            val clockPos = IntArray(2); clock.getLocationInWindow(clockPos)
            val ringPos = IntArray(2); ring.getLocationInWindow(ringPos)
            val scale = minOf(ring.width / 120f, ring.height / 136f)
            val digit = clockPos[1] + clock.baseline + (bounds.top + bounds.bottom) / 2f
            val center = ringPos[1] - ring.translationY + (ring.height - 136 * scale) / 2f + 77.5f * scale
            val shift = digit - center
            if (!shift.isFinite() || abs(shift) > 40 * ring.resources.displayMetrics.density) return Float.NaN
            if (abs(shift - ring.lastAlignment) > 1f || !ring.lastAlignment.isFinite()) {
                ring.lastAlignment = shift
                L.i("align: shift=${shift}px digitCenter=$digit ringCenter=${center + shift}")
            }
            shift - io.github.kvmy666.duostatusbar.hook.RingGeometry.ringAnchorShiftY(ring.width)
        } catch (t: Throwable) { L.w("Alinhamento: ${t.message}"); Float.NaN }
    }
    /** Actual cutout bounds, never an assumed camera coordinate. The user's chosen x stays authoritative. */
    fun calculateDynamicIslandOffsets(view: View): Pair<Float, Float>? {
        val cutout = view.rootWindowInsets?.displayCutout ?: return null
        val top = cutout.boundingRects.filter { it.top <= cutout.safeInsetTop && !it.isEmpty }.minByOrNull { it.top } ?: return null
        return top.exactCenterX() to top.exactCenterY()
    }
}
