package io.github.kvmy666.duostatusbar.hook

import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Method
import java.util.WeakHashMap

/** UI-thread registry. Recreated status/shade windows receive gestures without retaining old roots.
 * A dispatch override that calls super is routed only once, through its registered method. */
internal class TouchWindows {
    private val windows = WeakHashMap<View, Method>()
    private val hooked = HashSet<Method>()
    fun register(root: View): Method? {
        if (windows.containsKey(root)) return null
        val method = root.javaClass.getMethod("dispatchTouchEvent", MotionEvent::class.java)
        windows[root] = method
        return method.takeIf { hooked.add(it) }
    }
    fun accepts(method: Method, receiver: Any?): Boolean = windows[receiver] == method
}
