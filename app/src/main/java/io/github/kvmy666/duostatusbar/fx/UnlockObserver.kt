package io.github.kvmy666.duostatusbar.fx

import android.os.Handler
import android.os.SystemClock

/** Main-thread observer of actual Keyguard dismissal. No OEM callback or privileged listener needed. */
internal class UnlockObserver(
    private val handler: Handler,
    private val readLocked: () -> Boolean?,
    private val log: (String) -> Unit,
    private val onUnlock: (String) -> Unit
) {
    private var running = false
    private var screen = true
    private var lastLocked: Boolean? = null
    private var reported = false
    private var authenticationUntil = 0L
    private val poll = Runnable { sample("estado da tela de bloqueio") }

    fun start(screenOn: Boolean) {
        stop()
        running = true
        screen = screenOn
        lastLocked = readLocked()
        log("Desbloqueio: observador iniciado; bloqueio=$lastLocked")
        schedule()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
        lastLocked = null
        reported = false
        authenticationUntil = 0L
    }

    fun screenChanged(on: Boolean) {
        if (!running) return
        screen = on
        if (!on) {
            reported = false
            authenticationUntil = 0L
        }
        sample(if (on) "SCREEN_ON" else "SCREEN_OFF")
        // Keyguard can become showing just after SCREEN_OFF. Sample once after the transition,
        // then sleep until wake; never run a continuous watcher while the display is off.
        if (!on) handler.postDelayed(poll, 250L)
    }

    fun authenticated() {
        if (!running) return
        authenticationUntil = SystemClock.uptimeMillis() + 5000L
        log("Desbloqueio: autenticação recebida; aguardando saída do bloqueio")
        sample("biometria confirmada")
    }

    fun userPresent() {
        // A confirmed broadcast also works if KeyguardManager is unavailable on a ROM.
        authenticationUntil = 0L
        lastLocked = false
        if (!reported) {
            reported = true
            onUnlock("USER_PRESENT")
        }
        if (running) schedule()
    }

    private fun sample(source: String) {
        handler.removeCallbacks(poll)
        if (!running) return
        val locked = readLocked()
        if (locked != null) {
            val wasLocked = lastLocked
            if (locked != wasLocked) log("Desbloqueio: bloqueio=$locked; origem=$source; tela=$screen")
            if (locked) reported = false
            val authenticated = SystemClock.uptimeMillis() < authenticationUntil
            if (screen && !locked && !reported && (wasLocked == true || authenticated)) {
                reported = true
                authenticationUntil = 0L
                onUnlock(source)
            }
            // Preserve an off-screen dismissal until SCREEN_ON can actually show the effect.
            if (screen || locked || wasLocked != true) lastLocked = locked
        }
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(poll)
        if (running && screen) {
            val authenticated = SystemClock.uptimeMillis() < authenticationUntil
            handler.postDelayed(poll, if (authenticated) 100L else if (lastLocked == true) 250L else 2000L)
        }
    }
}
