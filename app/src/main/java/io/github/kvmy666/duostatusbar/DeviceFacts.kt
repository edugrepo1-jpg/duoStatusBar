package io.github.kvmy666.duostatusbar

import android.os.Build
import android.content.Context
import android.system.Os
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

    /** Same allowlisted facts for every capture route. Deliberately excludes uname nodename and host. */
    fun header(context: Context): String = DiagnosticPrivacy.clean(buildString {
        appendLine("=== device facts (no privilege, schema=2) ===")
        appendLine("moduleVersion=${BuildConfig.VERSION_NAME} moduleCode=${BuildConfig.VERSION_CODE} base=1.4.2-beta.2 renderer=Canvas")
        appendLine("manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE} product=${Build.PRODUCT} hardware=${Build.HARDWARE}")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT} securityPatch=${Build.VERSION.SECURITY_PATCH}")
        appendLine("romBuild=${Build.DISPLAY} incremental=${Build.VERSION.INCREMENTAL}")
        val kernel=runCatching { Os.uname().release }.getOrDefault("unknown")
        appendLine("kernelRelease=$kernel abi=${abi()} selinux=${selinux()}")
        val metrics=context.resources.displayMetrics
        val config=context.resources.configuration
        appendLine("screen=${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.density} densityDpi=${metrics.densityDpi}")
        appendLine("orientation=${config.orientation} fontScale=${config.fontScale} nightMode=${config.uiMode and 48}")
        appendLine("processUid=${android.os.Process.myUid()} processPid=${android.os.Process.myPid()}")
        appendLine("privacy=technical allowlist; no identifiers, account, network names, addresses, user content, or other-module inventory")
    })
}
