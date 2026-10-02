package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tells the app what the module is actually doing, so diagnostics shows facts, not intentions.
 *
 * One instance lives on the single [DuoHook] for the process, which keeps the one-shot diagnostic
 * dump per-process.
 */
internal class HookReporter(
    private val statusBarRoot: () -> View?,
    private val duo: () -> DuoElement?
) {

    /** The debug diagnostic dump is written to the log once per process, not on every settings change. */
    private val diagnosticsLogged = AtomicBoolean(false)

    fun report(ctx: Context, stage: Int, settings: ModuleSettings?) {
        L.guard("DuoHook status report") {
            val element = duo()
            val renderer = element?.rendererName ?: "none"
            val status = buildString {
                append("stage=").append(stage)
                append(" · renderer=").append(renderer)
                append(" · attached=").append(element != null)
                settings?.let {
                    append(" · size=").append(it.sizePercent).append('%')
                    append(" · offset=").append(it.offsetX).append("dp")
                    append(" · split=").append(it.splitIndicators)
                    append(" · icons=").append(it.indicatorsOffsetX).append("dp")
                    append(" · edge=").append(it.edgePadding).append('%')
                    append(" · percent=").append(it.showPercent)
                    append(" · rev=").append(it.revision)
                }
                append(" · riveAttempts=").append(DuoGuard(ctx).attempts())
            }
            L.i("status -> app: $status")
            DuoSettingsClient.report(ctx, status)
            reportDiagnostics(ctx, stage, settings, element)
        }
    }

    /**
     * Sends the full diagnostic dump to the app (so its "Save status to a file" button ships it) and
     * writes it to the log once per process. Always on — not debug-only — so any release user can pull a
     * complete bug report without a special build.
     */
    fun reportDiagnostics(ctx: Context, stage: Int, settings: ModuleSettings?, element: DuoElement?) {
        val dump = Diag.collect(ctx, statusBarRoot(), stage, settings, element)
        DuoSettingsClient.reportDump(ctx, dump)
        if (diagnosticsLogged.compareAndSet(false, true)) {
            L.i("--- diagnostic dump (debug build) ---")
            Diag.log(dump)
            L.i("--- end diagnostic dump ---")
        }
    }
}
