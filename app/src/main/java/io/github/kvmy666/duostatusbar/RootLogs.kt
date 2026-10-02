package io.github.kvmy666.duostatusbar

/**
 * Root-assisted diagnostics for a module that is not (yet) running.
 *
 * The in-module dump only exists once LSPosed has injected the module into SystemUI. When it has not — the
 * exact case a "the module does nothing" report describes — there is nothing on the app side to read, so
 * this shells out to `su` and pulls the evidence that decides why:
 *
 *  - logcat filtered to `DuoSB` (works on ROMs that keep SystemUI's logcat),
 *  - LSPosed's own module log, which is where `L` writes when the ROM filters logcat and is the only place
 *    that records "MainHook loaded into com.android.systemui" — present or absent, it answers the question,
 *  - the build props and the installed module version, to pin the device/ROM and prove which APK is on.
 *
 * Every call is best-effort: a missing `su`, a denied prompt, or a ROM that keeps none of these files yields
 * a line saying so, never an exception.
 */
internal object RootLogs {

    /**
     * Where a root shell can live. `su` is not guaranteed to be on the app's `PATH`: locked-bootloader
     * root frameworks (KernelSU, APatch, some Samsung "no unlock" roots) keep it somewhere the app
     * process does not search, and the old code then failed with "Cannot run program su: No such file
     * or directory" — every report from those users came back empty. Each absolute path is tried in
     * turn, then a plain `su` so an ordinary PATH lookup is still the last resort.
     */
    private val SU_CANDIDATES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/data/adb/magisk/su",
        "su"
    )

    /** How long a root capture may take before it is abandoned, so a hung prompt cannot freeze the app. */
    private const val ROOT_TIMEOUT_SECONDS = 25L

    /**
     * The first root shell that actually exists, preferring known absolute paths over a `PATH` lookup.
     * Returns `"su"` when none exists so the legacy behaviour (and its error message) is unchanged.
     */
    internal fun findSuBinary(): String =
        SU_CANDIDATES.firstOrNull { it != "su" && java.io.File(it).exists() } ?: "su"

    /**
     * Runs one root script (a single root prompt) and returns everything it printed. The compound script
     * is deliberate: one prompt for the whole capture is far less annoying than one per command.
     *
     * When no root shell can be run at all, this still returns the facts the app can read without root,
     * so a report is never empty — see [nonRootFacts].
     */
    fun collect(): String {
        val script = """
            echo '=== logcat (DuoSB) ==='
            logcat -d -t 3000 -s DuoSB 2>&1
            echo '=== LSPosed newest modules log ==='
            LOG=$(ls -t /data/adb/lspd/log/modules_*.log 2>/dev/null | head -1)
            echo "file=${'$'}LOG"
            if [ -n "${'$'}LOG" ]; then
              echo '-- matching DuoSB --'
              grep -a -i 'DuoSB\|duostatusbar' "${'$'}LOG" | tail -n 400
              echo '-- tail --'
              tail -n 120 "${'$'}LOG"
            fi
            echo '=== LSPosed newest verbose log ==='
            VLOG=$(ls -t /data/adb/lspd/log/verbose_*.log 2>/dev/null | head -1)
            echo "file=${'$'}VLOG"
            if [ -n "${'$'}VLOG" ]; then
              echo '-- matching DuoSB --'
              grep -a -i 'duostatusbar\|DuoSB' "${'$'}VLOG" | tail -n 400
              echo '-- tail --'
              tail -n 150 "${'$'}VLOG"
            fi
            echo '=== LSPosed config (enabled / scope) ==='
            tr -c '[:print:]' '\n' < /data/adb/lspd/config/modules_config.db 2>/dev/null | grep -a -i -B3 -A8 duostatusbar
            echo '=== magisk / lsposed modules ==='
            ls -1 /data/adb/modules 2>/dev/null
            for m in /data/adb/modules/*/module.prop; do echo "--- ${'$'}m"; cat "${'$'}m" 2>/dev/null; done
            echo '=== installed module package ==='
            pm path io.github.kvmy666.duostatusbar 2>&1
            dumpsys package io.github.kvmy666.duostatusbar 2>/dev/null | head -n 40
            echo '=== build props ==='
            getprop ro.product.manufacturer
            getprop ro.product.brand
            getprop ro.product.model
            getprop ro.build.version.sdk
            getprop ro.build.version.release
            getprop ro.build.display.id
            echo '=== root framework ==='
            ls /data/adb 2>/dev/null
            ls /data/adb/lspd/log 2>/dev/null
        """.trimIndent()
        val rooted = runRoot(script)
        // A failed capture must still carry the device identity: on a locked-bootloader root the missing
        // root shell is itself the answer, and the build tells us which ROM to fix next.
        return if (rooted.startsWith(FAILURE_PREFIX)) {
            buildString {
                appendLine(rooted)
                appendLine()
                nonRootFacts()
            }
        } else {
            rooted
        }
    }

    /**
     * The evidence available with no root at all. Weaker than the root capture, but it records the
     * device/ROM and the installed module so a "nothing happens" report still identifies the target.
     */
    private fun nonRootFacts(): String = buildString {
        appendLine("=== app-side facts (no root) ===")
        appendLine("MANUFACTURER=${android.os.Build.MANUFACTURER} BRAND=${android.os.Build.BRAND} MODEL=${android.os.Build.MODEL}")
        appendLine("DEVICE=${android.os.Build.DEVICE} PRODUCT=${android.os.Build.PRODUCT}")
        appendLine("SDK=${android.os.Build.VERSION.SDK_INT} RELEASE=${android.os.Build.VERSION.RELEASE} DISPLAY=${android.os.Build.DISPLAY}")
        appendLine("selinux=${DeviceFacts.selinux()} abi=${DeviceFacts.abi()}")
        appendLine("rootShell=${findSuBinary()}")
        append("note=root capture unavailable; ask the user to grant root to Duo Status Bar in their root manager, then reopen the About screen.")
    }

    /**
     * Restarts SystemUI through root. Used only when the module is not running: the module's own path (kill
     * its process from inside SystemUI) needs no root, so this is the fallback that still works before
     * LSPosed has injected anything — and it is the root prompt users of other modules expect.
     */
    fun restartSystemUi(): Boolean = try {
        // Different ROMs keep different tools, so every spelling is tried in turn: `pkill -f` (AOSP),
        // `killall` (some vendors) and `pidof` + `kill -9` (the one that survives SELinux-restricted
        // pkill). `true` makes the script succeed even when the process was already gone.
        val script = "pkill -f com.android.systemui; " +
            "killall com.android.systemui 2>/dev/null; " +
            "kill -9 ${'$'}(pidof com.android.systemui) 2>/dev/null; true"
        val finished = ProcessBuilder(findSuBinary(), "-c", script).redirectErrorStream(true).start()
            .waitFor(ROOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) L.w("root restart timed out after ${ROOT_TIMEOUT_SECONDS}s")
        finished
    } catch (t: Throwable) {
        L.w("root restart failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    /**
     * Removes a leftover `duo_statusbar_stage` adb override and clears the Rive crash counter, so a
     * device that was once pinned to the simple drawing (stage 1) goes back to the app's own setting.
     * The override is a developer kill switch the app cannot write; this is the user-facing way back.
     * Returns true only when the root shell finished (the delete itself is best-effort — `settings`
     * reports nothing useful, so the caller re-reads the value).
     */
    fun clearStageOverride(): Boolean = try {
        val script = "settings delete global duo_statusbar_stage; " +
            "settings put global duo_statusbar_rive_attempts 0; true"
        val finished = ProcessBuilder(findSuBinary(), "-c", script).redirectErrorStream(true).start()
            .waitFor(ROOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) L.w("clear override timed out after ${ROOT_TIMEOUT_SECONDS}s")
        finished
    } catch (t: Throwable) {
        L.w("clear override failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private const val FAILURE_PREFIX = "root log collection failed"

    /**
     * Runs [script] through the discovered root shell. stderr is merged into stdout and read in one pass
     * (the old two-stream read could deadlock on a large log), and the process is bounded by a timeout so
     * a stalled root prompt cannot hang the app. Never throws.
     */
    private fun runRoot(script: String): String {
        val su = findSuBinary()
        // The read itself can block forever if the root prompt never answers, so the whole
        // start-and-read runs on a worker that the caller abandons after the timeout.
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            val future = executor.submit<String> {
                val process = ProcessBuilder(su, "-c", script)
                    .redirectErrorStream(true)
                    .start()
                val out = process.inputStream.bufferedReader().readText()
                process.waitFor()
                out.ifBlank { "(root shell returned no output - root denied?)" }
            }
            future.get(ROOT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            "$FAILURE_PREFIX: root shell ($su) timed out after ${ROOT_TIMEOUT_SECONDS}s"
        } catch (t: Throwable) {
            "$FAILURE_PREFIX: ${t.javaClass.simpleName}: ${t.message} (tried $su)"
        } finally {
            executor.shutdownNow()
        }
    }
}
