package io.github.kvmy666.duostatusbar.hook

import android.view.View
import android.view.ViewGroup
import java.util.ArrayDeque

/** An anchor must be a small icon strip inside a header, never the full shade or a QS tile. */
internal object ShadePanels {
    enum class Kind { NOTIFICATIONS, QUICK_SETTINGS, SHARED }
    data class Anchor(val strip:ViewGroup,val header:View,val kind:Kind)
    fun kind(view:View):Kind? {
        var current:View?=view
        repeat(8) {
            val v=current?:return null
            val name=(v.javaClass.simpleName+" "+idName(v)).lowercase()
            if(name.contains("keyguard"))return null
            if(idName(v)=="split_shade_status_bar")return Kind.SHARED
            if(name.contains("combined_qs_header"))return Kind.SHARED
            if(name.contains("quickstatusbarheader")||name.contains("quick_status_bar_header")||name.contains("qs_header"))return Kind.QUICK_SETTINGS
            if(name.contains("notification")&&name.contains("header"))return Kind.NOTIFICATIONS
            if(name.contains("shadeheader")||name.contains("shade_header")||name.contains("combined_qs_header"))return Kind.SHARED
            current=v.parent as? View
        }
        return null
    }
    fun find(root:View):List<Anchor> {
        val queue=ArrayDeque<View>();queue.add(root);val results=ArrayList<Anchor>();var visited=0
        while(queue.isNotEmpty()&&visited++<512) {
            val v=queue.removeFirst();val name=(v.javaClass.simpleName+" "+idName(v)).lowercase()
            // A meter or CombinedStatusView is a reliable stock anchor; Wi-Fi tiles are not.
            if(name.contains("batterymeter")||name.contains("combinedstatusview")||idName(v) in setOf("battery","battery_icon","battery_view")) {
                val parent=v.parent as? ViewGroup
                val kind=kind(v)
                if(parent!=null&&kind!=null) {
                    val strip=canonicalStrip(parent)
                    if(results.none {it.strip===strip}) results.add(Anchor(strip,headerFor(strip),kind))
                }
            }
            if(v is ViewGroup)for(i in 0 until v.childCount)queue.add(v.getChildAt(i))
        }
        return results
    }
    /** The Samsung controller binds the outer frame, while the battery is inside its hover row.
     * Both discovery paths must own the same slot. This id is present in the device diagnostic. */
    private fun canonicalStrip(parent:ViewGroup):ViewGroup {
        var current:View?=parent
        repeat(8) {
            val v=current?:return parent
            if(idName(v)=="shade_header_system_icons"&&v is ViewGroup)return v
            current=v.parent as? View
        }
        return parent
    }
    /** Keep the anchor in its icon row, but use the small header's vertical room for sizing.
     * A 34px icon strip is not a cap for a 160px header. Never use the entire shade window. */
    fun headerFor(strip:View):View {
        var current=strip.parent as? View
        repeat(8) {
            val v=current?:return strip
            val id=idName(v)
            val name=v.javaClass.simpleName.lowercase()
            if(id in setOf("split_shade_status_bar","combined_qs_header","qs_header","quick_status_bar_header")||
                name.contains("quickstatusbarheader")||name.contains("shadeheader")||
                (name.contains("notification")&&name.contains("header")))return v
            current=v.parent as? View
        }
        return strip
    }
    /** Capture the original meter before stock hiding collapses its layout params. */
    fun baseWidth(strip:ViewGroup):Int {
        val queue=ArrayDeque<View>();queue.add(strip);var visited=0
        while(queue.isNotEmpty()&&visited++<128) {
            val v=queue.removeFirst()
            val name=v.javaClass.simpleName.lowercase()
            if(name.contains("batterymeter")||name.contains("combinedstatusview")||
                idName(v) in setOf("battery","battery_icon","battery_view","batteryRemainingIcon")) {
                val width=v.width.takeIf {it>0}?:v.layoutParams?.width?.takeIf {it>0}
                if(width!=null)return width
            }
            if(v is ViewGroup)for(i in 0 until v.childCount)queue.add(v.getChildAt(i))
        }
        return 0
    }
    private fun idName(v:View)=runCatching {v.resources.getResourceEntryName(v.id)}.getOrDefault("")
    fun enabled(s:ModuleSettings,kind:Kind,expanded:Boolean)=s.enabled&&when(kind) {
        Kind.NOTIFICATIONS->s.showNotifications
        Kind.QUICK_SETTINGS->s.showQuickSettings
        Kind.SHARED->if(expanded)s.showQuickSettings else s.showNotifications
    }
    fun size(s:ModuleSettings,kind:Kind,expanded:Boolean)=when(kind) {
        Kind.NOTIFICATIONS->s.notificationSize
        Kind.QUICK_SETTINGS->s.quickSettingsSize
        Kind.SHARED->if(expanded)s.quickSettingsSize else s.notificationSize
    }.coerceIn(50,200)
}
