package io.github.kvmy666.duostatusbar

/**
 * Classifies how the phone is rooted and which capture route can reach the evidence.
 *
 * This is a **pure** decision function on purpose. Every input is a fact the caller has already measured
 * — a candidate `su` path that exists, a marker directory under `/data/adb` that was readable, the text
 * of `/proc/modules`, whether Sui responded, whether Shizuku is running and its uid. Nothing here touches
 * the device, so the whole decision table is exercised by a JVM unit test rather than only on a phone.
 *
 * It also keeps the project's rule — **never guess** — in code: a root method we have not measured is
 * reported as [RootKind.UNKNOWN], never silently assumed to be one we know. The kind is written into every
 * diagnostic report, so the next report from an unfamiliar ROM teaches us instead of leaving us blind.
 *
 * Background (measured from the projects' own docs, not invented):
 *  - Magisk / KernelSU (GKI or LKM) / KernelSU Next / APatch all expose a `su` command; the paths differ.
 *  - KernelSU LKM loads the root driver as a kernel module, so `/proc/modules` names it; GKI builds it in.
 *  - Sui adds **no binary to `PATH`**; it can only be reached through the Shizuku API (`Sui.init()`).
 *  - Shizuku runs as shell (uid 2000, adb/wireless) or root (uid 0).
 *  - A locked-bootloader soft-root may expose neither a runnable `su` nor a readable `/data/adb`, which is
 *    exactly the case the module-pushed log and the Shizuku binder are meant to cover.
 */
internal object RootProbe {

    enum class RootKind { MAGISK, KERNELSU, KERNELSU_LKM, APATCH, SUI, SUPERSU, SHIZUKU, NONE, UNKNOWN }

    /** How a report will be collected. Ordered best → weakest. */
    enum class Route { ROOT, SHIZUKU, MODULE_LOG }

    data class Facts(
        /** The command [RootLogs] will run (an absolute path, or the `su` PATH fallback). */
        val suCommand: String,
        /** True when [suCommand] is a real absolute path that exists (not the PATH fallback). */
        val suExistsAtAbsolutePath: Boolean,
        /** Directory names read under `/data/adb` (`magisk`, `ksu`, `ap`, `sui`, `lspd` …); empty if unreadable. */
        val adbMarkers: Set<String>,
        /** Raw `/proc/modules` text, used to tell KernelSU LKM from a built-in GKI kernel. */
        val kernelModules: String,
        /** Whether the Sui API answered (Sui ships no `su`). */
        val suiAvailable: Boolean,
        val shizukuRunning: Boolean,
        /** Shizuku's uid: 0 root, 2000 shell, -1 when unknown/not running. */
        val shizukuUid: Int,
        /** Whether the user granted this app root, i.e. an explicit `su -c id` succeeded. */
        val rootAllowed: Boolean
    )

    data class Result(val kind: RootKind, val route: Route, val detail: String)

    fun classify(f: Facts): Result {
        val kind = kind(f)
        val route = when {
            f.rootAllowed && f.suExistsAtAbsolutePath -> Route.ROOT
            f.shizukuRunning -> Route.SHIZUKU
            else -> Route.MODULE_LOG
        }
        return Result(kind, route, describe(kind, route, f))
    }

    /** The one line every report carries, so a future report names the root method it came from. */
    fun describe(kind: RootKind, route: Route, f: Facts): String = buildString {
        append("rootKind=").append(kind.name.lowercase())
        append(" · route=").append(route.name.lowercase())
        append(" · su=").append(f.suCommand)
        append(" · suAbs=").append(f.suExistsAtAbsolutePath)
        append(" · shizuku=").append(if (f.shizukuRunning) "running" else "off")
        if (f.shizukuUid >= 0) append("(uid=").append(f.shizukuUid).append(')')
        append(" · sui=").append(f.suiAvailable)
        append(" · rootGranted=").append(f.rootAllowed)

    }

    private fun kind(f: Facts): RootKind = when {
        // Sui has no `su` binary, so it must be checked before any path-based guess.
        f.suiAvailable -> RootKind.SUI
        "magisk" in f.adbMarkers -> RootKind.MAGISK
        "ksu" in f.adbMarkers && isKernelModule(f.kernelModules) -> RootKind.KERNELSU_LKM
        "ksu" in f.adbMarkers -> RootKind.KERNELSU
        "ap" in f.adbMarkers -> RootKind.APATCH
        f.shizukuRunning -> RootKind.SHIZUKU
        // A legacy `/system/xbin/su` with no `/data/adb` marker is the SuperSU-era layout.
        f.suExistsAtAbsolutePath && f.suCommand.contains("xbin") -> RootKind.SUPERSU
        f.suExistsAtAbsolutePath -> RootKind.UNKNOWN
        else -> RootKind.NONE
    }

    /**
     * Whether the `/proc/modules` text names a KernelSU driver. The module is spelled `kernelsu` (or
     * `kernelsu_next`) — it does **not** contain the substring `ksu`, which is why the check is two
     * separate tokens rather than one. A built-in (GKI) kernel loads no module, so this stays false.
     */
    private fun isKernelModule(modules: String): Boolean =
        modules.contains("kernelsu", ignoreCase = true) || modules.contains("ksu", ignoreCase = true)
}
