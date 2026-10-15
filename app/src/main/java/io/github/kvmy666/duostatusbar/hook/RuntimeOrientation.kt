package io.github.kvmy666.duostatusbar.hook

import android.content.res.Configuration
import android.view.Surface

/** One selection rule for display events, configuration events, layouts and the initial read. */
internal object RuntimeOrientation {
    fun current(rotation: Int?, configured: Int,width:Int=0,height:Int=0): Int {
        // Real display dimensions already include rotation and do not assume a portrait-natural phone.
        if(width>0&&height>0&&width!=height)return if(width>height)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        return when (rotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> Configuration.ORIENTATION_LANDSCAPE
        Surface.ROTATION_0, Surface.ROTATION_180 -> Configuration.ORIENTATION_PORTRAIT
        else -> when (configured) {
            Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT -> configured
            else -> Configuration.ORIENTATION_UNDEFINED
        }
        }
    }
}
