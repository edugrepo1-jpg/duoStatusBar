package io.github.kvmy666.duostatusbar

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * Minimal logger used everywhere in the module.
 *
 * Two sinks on purpose:
 *  - logcat (tag `DuoSB`)  → works on stock ROMs, `adb logcat -s DuoSB` while developing (FR-26).
 *  - XposedBridge.log       → the sink that always works: LSPosed routes it to
 *                             `/data/adb/lspd/log/modules_<boot>.log` (tag `LSPosedFramework`).
 *
 * **Use this, never `android.util.Log` directly.** Measured on the target device (OnePlus 15 /
 * OxygenOS 16): `android.util.Log` calls made from inside SystemUI do not reach logcat at all - the ROM
 * runs a filtered logd, and the tag is nowhere in the buffer even though the code ran. During Phase 3
 * verification that made a *working* module look completely dead: every stage "failed" because the only
 * sink the checks could read was empty. `tools/route-module-logs.py` keeps the sources on this path.
 *
 * The XposedBridge sink is wrapped because it only exists inside a hooked process - in the app itself it
 * throws and logging is logcat-only. Every method swallows its own errors: logging must never be the
 * reason SystemUI dies (FR-21).
 */
object L {

    const val TAG = "DuoSB"

    /** How many of the most recent lines the module keeps to hand to the app. Bounded, so no leak. */
    private const val MAX_RECENT = 600

    private val ringLock = Any()
    private val ring = ArrayDeque<String>(MAX_RECENT)
    private const val MAX_RECENT_CHARS = 128 * 1024
    private var ringChars = 0

    /**
     * One entry point for every level. The XposedBridge sink carries no level, so nothing is lost by
     * sending `w`/`d`/`v`/`e` through the same path - and keeping them visible is the whole point: a
     * warning that never arrives is worse than no warning, because it looks like silence.
     */
    private fun both(msg: String) {
        // Bound bytes as well as line count: 600 large exceptions must not retain hundreds of MB.
        val safe = runCatching { DiagnosticPrivacy.clean(msg).take(4000) }
            .getOrDefault("diagnostic redaction unavailable")
        runCatching { io.github.kvmy666.duostatusbar.fx.Events.record(System.currentTimeMillis(), safe) }
        record(safe)
        sinks(safe)
    }

    /** Writes to both sinks without keeping the line (used for the bulky diagnostic dump). */
    fun diag(msg: String) = sinks(runCatching { DiagnosticPrivacy.clean(msg) }
        .getOrDefault("diagnostic redaction unavailable"))

    private fun sinks(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
        }
        try {
            XposedBridge.log("$TAG | $msg")
        } catch (_: Throwable) {
        }
    }

    /**
     * Keeps the last [MAX_RECENT] lines in memory so the module can push its own log to the app through
     * the settings channel. This is what makes a report self-contained on a device where the app cannot
     * get root: reading LSPosed's log file needs `su`, and on a locked-bootloader root `su` is often not
     * runnable from the app at all ("Cannot run program su: error=2"). The module already has every line;
     * it just hands them over. Never throws, never blocks on a lock for long.
     */
    private fun record(msg: String) {
        try {
            val line = System.currentTimeMillis().toString() + " " + msg
            synchronized(ringLock) {
                ring.addLast(line)
                ringChars += line.length + 1
                while (ring.size > MAX_RECENT || ringChars > MAX_RECENT_CHARS) {
                    ringChars -= ring.removeFirst().length + 1
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** The recent module log, oldest first, as one text block. Empty when nothing was logged yet. */
    fun recentText(): String = try {
        synchronized(ringLock) { ring.joinToString("\n") }
    } catch (_: Throwable) {
        ""
    }

    fun i(msg: String) = both(msg)

    fun w(msg: String) = both(msg)

    fun d(msg: String) = both(msg)

    fun v(msg: String) = both(msg)

    fun e(msg: String) = both(msg)

    fun e(where: String, t: Throwable?) {
        both("$where FAILED -> ${t?.javaClass?.name}: ${t?.message}")
    }

    /** Runs [block]; on any Throwable it logs and returns normally (fail-silent, FR-21). */
    inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            e(where, t)
        }
    }
}
