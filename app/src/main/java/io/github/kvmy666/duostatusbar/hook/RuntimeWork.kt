package io.github.kvmy666.duostatusbar.hook

import android.os.Handler
import android.view.View
import io.github.kvmy666.duostatusbar.L

/** One pending operation, owned by the current window. Replacement and cancellation invalidate it. */
internal class RootBoundWork(private val handler: Handler, private val currentRoot: () -> View?) {
    private var pending: Runnable? = null

    fun post(root: View, delayMs: Long, action: () -> Unit) {
        if (currentRoot() !== root) return
        cancel()
        val task = object : Runnable {
            override fun run() {
                if (pending !== this) return
                pending = null
                if (currentRoot() === root) action()
            }
        }
        pending = task
        handler.postDelayed(task, delayMs)
    }

    fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }
}

/** Stop registrations and timers before removing the drawing and restoring the stock bar. */
internal fun stopDuoRuntime(monitor: DuoStateMonitor?, host: DuoIconHost?) {
    L.guard("stop disabled runtime") { monitor?.stop() }
    L.guard("restore disabled bar") { host?.teardown() }
}
