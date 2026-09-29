package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomResources

/**
 * Resolves the status-bar icon strip and the container the element is injected into from the view tree.
 *
 * Stateless: it only reads the tree and the ROM adapter. All attach/hide/overlay state stays in
 * [DuoIconHost]; this only answers "which view" questions.
 */
internal class ContainerFinder(
    private val context: Context,
    private val rom: RomAdapter,
    private val logOnce: LogOnce
) {

    /**
     * The keyguard's status bar itself - `com.android.systemui.statusbar.phone.KeyguardStatusBarView`,
     * read out of the device's SystemUI (`reverse/SystemUI-device.apk`, `layout/keyguard_status_bar.xml`),
     * not guessed. Walking up from the strip rather than looking the id up keeps it working on a ROM
     * that spells the id differently, and gives the right height to centre the element in.
     */
    fun findBar(strip: View): View? {
        var view: View? = strip
        while (view != null) {
            val name = view.javaClass.simpleName
            if (name.contains("KeyguardStatusBar") || name.contains("ShadeHeader")) return view
            view = view.parent as? View
        }
        return null
    }

    /**
     * Finds the strip the element goes into.
     *
     * First the ROM adapter's ids are probed in order. When none resolves — an unmeasured ROM such as
     * ColorOS 14/16 or One UI — the strip is found by what it must *contain*: every ROM's icon strip holds
     * the battery and/or the status-icons container, so walking up from those finds the parent without
     * knowing its id. That fallback is what lets a device we have never seen attach instead of doing
     * nothing, and the diagnostic dump records exactly which path was taken.
     */
    fun findStatusIconsHost(root: View): ViewGroup? {
        logOnce.once("rom") {
            L.i("ROM adapter: ${rom.id} (${rom.label}) - ${rom.notes}")
        }
        for (name in rom.containerIds) {
            val id = RomResources.id(context, rom, name)
            if (id == 0) continue
            val found = root.findViewById<View>(id)
            L.d("container $name -> ${found?.javaClass?.simpleName ?: "null"}")
            if (found is ViewGroup) {
                // A GONE strip is a legacy leftover, not the real one. Android 17 AOSP keeps a
                // `system_icons` LinearLayout that is GONE forever while the icons are drawn by Compose,
                // so injecting there produced a 0x0 element and nothing appeared (Pixel 9 report).
                if (found.visibility == View.GONE) {
                    L.i("container $name is GONE - skipping it (likely a legacy/Compose strip)")
                    continue
                }
                return found
            }
        }
        val anchor = stripAroundAnchors(root)
        if (anchor != null) {
            L.i("container id not found (tried ${rom.containerIds}) - using the strip around " +
                    "battery/status icons: ${anchor.javaClass.simpleName}")
            return anchor
        }
        L.w("no container id resolved (tried ${rom.containerIds}) - status bar left untouched")
        return null
    }

    /**
     * The last-resort strip: the direct parent of the battery view, or of the status-icons container. Its
     * parent is what the ROM put the strip's children in, whatever the ROM calls that container.
     */
    fun stripAroundAnchors(root: View): ViewGroup? {
        for (anchorName in listOf(rom.batteryId, "statusIcons", "status_icons", "system_icons")) {
            val id = RomResources.id(context, rom, anchorName)
            if (id == 0) continue
            val anchor = root.findViewById<View>(id) ?: continue
            val parent = anchor.parent as? ViewGroup ?: continue
            // Never hand back the status-bar window itself: injecting there would fight the bar's layout.
            if (parent === root) continue
            // A GONE parent is a legacy/Compose leftover, not a strip we can draw in.
            if (parent.visibility == View.GONE) continue
            return parent
        }
        return null
    }

    /**
     * Where the element view should be added.
     *
     * The strip itself when it is a plain Android layout. When it is a custom container — HyperOS 3's
     * `MiuiStatusBatteryContainer` is the measured one — it lays out only its own children and left the
     * injected view at 0×0, so the icons disappeared and nothing replaced them. In that case the element
     * goes into the nearest plain `FrameLayout` ancestor (the bar's own icon layer) and is anchored over
     * the battery by [anchorOnBattery]; the strip is still what gets hidden, by [hideStrip].
     */
    fun chooseElementParent(strip: ViewGroup, barRoot: View): ViewGroup {
        if (isStandardLayout(strip)) return strip
        var view: View? = strip.parent as? View
        while (view != null) {
            if (view.javaClass.name == "android.widget.FrameLayout" && view is ViewGroup) return view
            view = view.parent as? View
        }
        return (barRoot as? ViewGroup)?.takeIf { it !== strip } ?: strip
    }

    /** A layout whose measurement the module trusts to honour a child's requested size. */
    fun isStandardLayout(view: ViewGroup): Boolean = when (view.javaClass.name) {
        "android.widget.FrameLayout",
        "android.widget.LinearLayout",
        "android.widget.RelativeLayout",
        "android.widget.GridLayout" -> true
        else -> false
    }

    /**
     * The Compose view that draws the Wi-Fi/cellular/battery cluster, on a status bar with no View strip
     * (Android 17 AOSP). Only the end-side area is searched, so the centre clock's Compose view is never
     * mistaken for the icon cluster. The outer `ComposeView` is preferred over the inner
     * `AndroidComposeView`: hiding the outer one hides everything it draws.
     */
    fun findComposeIconView(root: View): View? {
        for (name in listOf("status_bar_end_side_content", "status_bar_end_side_container")) {
            val id = RomResources.id(context, rom, name)
            if (id == 0) continue
            val area = root.findViewById<View>(id) ?: continue
            findComposeDescendant(area)?.let { return it }
        }
        return null
    }

    /** First `*ComposeView` in the subtree (top-down), or null. */
    fun findComposeDescendant(view: View): View? {
        if (view.javaClass.name.endsWith("ComposeView")) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeDescendant(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** The battery view inside [strip], the anchor for the overlay placement. */
    fun findBattery(strip: ViewGroup): View? {
        val id = RomResources.id(context, rom, rom.batteryId)
        return if (id != 0) strip.findViewById(id) else null
    }

    fun findShadeIconsArea(header: View): ViewGroup? {
        val id = RomResources.id(context, rom, "shade_header_system_icons")
        (if (id != 0) header.findViewById<View>(id) as? ViewGroup else null)?.let { return it }
        // Fall back to the parent of the icon container the controller binds to.
        val icons = RomResources.id(context, rom, "statusIcons")
        val container = if (icons != 0) header.findViewById<View>(icons) else null
        return container?.parent as? ViewGroup
    }
}
