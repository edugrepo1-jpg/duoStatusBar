package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import java.io.File

/**
 * Draws the status-bar clock in the phone's own system font, matching the element's digits (FR-16/28).
 *
 * The module never hides or moves the clock - this only swaps its `Typeface`, remembers the original so
 * switching off restores it exactly, and does nothing when the setting is off or the clock cannot be
 * found. The same OEM font the Rive digits bundle (`SysSans`) is preferred, falling back to the platform
 * `sans-serif` so the feature still works on a ROM that spells its font differently.
 */
internal class ClockFontController(private val context: Context, private val rom: RomAdapter) {

    private var clockView: TextView? = null
    private var clockOriginalTypeface: Typeface? = null

    /** The system typeface, built once: assigning a fresh one every pass forced a relayout loop. */
    private var cachedTypeface: Typeface? = null

    /**
     * Applies the setting: the system font when [enabled], otherwise the remembered original. Safe to
     * call repeatedly (every layout pass); it only logs when the clock view itself changes.
     */
    fun apply(root: View?, enabled: Boolean) {
        if (!enabled) {
            restore()
            return
        }
        try {
            val view = root ?: return
            val id = context.resources.getIdentifier(rom.clockId, "id", rom.systemUiPackage)
            if (id == 0) return
            // HyperOS 3 nests several views with the same `clock` id; `findViewById` returns the first,
            // which can be a 0x0 duplicate. The real clock is the visible one, so prefer it.
            val matches = ArrayList<TextView>()
            collectClocks(view, id, matches)
            val clock = matches.firstOrNull { it.isShown && it.width > 0 }
                ?: matches.firstOrNull() ?: return
            if (clockView !== clock) {
                clockView = clock
                clockOriginalTypeface = clock.typeface
                L.i("clock font -> system (${clock.javaClass.simpleName} id=${rom.clockId})")
            }
            // Only touch the typeface when it actually changes. Building a new Typeface and assigning it
            // on every layout pass forced a re-layout each time, which re-fired the layout listener and
            // kept the status bar in a continuous relayout loop - a real battery drain.
            val tf = systemTypeface()
            if (clock.typeface !== tf) {
                clock.typeface = tf
            }
        } catch (t: Throwable) {
            L.w("clock font: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Every `TextView` in the subtree carrying [id], in tree order. */
    private fun collectClocks(view: View, id: Int, out: MutableList<TextView>) {
        if (view.id == id && view is TextView) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectClocks(view.getChildAt(i), id, out)
        }
    }

    /** Puts the clock's original typeface back. */
    fun restore() {
        val clock = clockView ?: return
        try {
            clock.typeface = clockOriginalTypeface
            L.i("clock font restored")
        } catch (t: Throwable) {
            L.w("clock restore: ${t.message}")
        }
        clockView = null
        clockOriginalTypeface = null
    }

    private fun systemTypeface(): Typeface {
        cachedTypeface?.let { return it }
        val tf = try {
            val file = File("/system/fonts/SysSans-En-Regular.ttf")
            if (file.exists()) Typeface.createFromFile(file)
            else Typeface.create("sans-serif", Typeface.NORMAL)
        } catch (t: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
        cachedTypeface = tf
        return tf
    }
}
