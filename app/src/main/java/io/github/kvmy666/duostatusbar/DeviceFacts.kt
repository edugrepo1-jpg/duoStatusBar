package io.github.kvmy666.duostatusbar

import android.os.Build
import java.io.File

/**
 * Facts that make a report conclusive without a round-trip (NFR-06).
 *
 * SELinux mode and the ABI are the two that decide whether native Rive can load at all, and both are
 * readable without root: `/sys/fs/selinux/enforce` is world-readable, and `Build.SUPPORTED_ABIS` is
 * static. Keeping them in one place means the in-app capture and the module dump always agree.
 */
internal object DeviceFacts {

    private const val SELINUX_ENFORCE = "/sys/fs/selinux/enforce"

    /** `"enforcing"`, `"permissive"`, or `"unknown"` — never throws. */
    fun selinux(): String = try {
        when (File(SELINUX_ENFORCE).readText().trim()) {
            "1" -> "enforcing"
            "0" -> "permissive"
            else -> "unknown"
        }
    } catch (_: Throwable) {
        "unknown"
    }

    /** The device's ABIs, most preferred first. */
    fun abi(): String = Build.SUPPORTED_ABIS.joinToString(",")
}
