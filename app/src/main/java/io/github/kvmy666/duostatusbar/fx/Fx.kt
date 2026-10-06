package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import io.github.kvmy666.duostatusbar.hook.ModuleSettings

internal object Fx {
    var flags = 0x3FDF
        private set
    var experience = ExperienceOptions()
        private set
    var level = -1
    private var context: Context? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var baseDump = ""
    private var pending = false
    private var sending = false
    fun enabled(bit: Int) = flags and BuildConfig.FEATURE_MASK and bit != 0
    fun sync(settings: ModuleSettings, ctx: Context) {
        flags = settings.featFlags
        experience = ExperienceOptions.decode(settings.experienceJson)
        io.github.kvmy666.duostatusbar.i18n.UiText.initialize(ctx,experience.language)
        context = ctx.applicationContext ?: ctx
        Events.changed = ::scheduleLocalLog
    }
    fun wrapDump(raw: String?): String {
        baseDump = (raw ?: "").substringBefore(Events.MARKER).trimEnd()
        return Events.wrap(baseDump)
    }
    /** Explicitly requested local IPC (spec section 10). Existing provider writes last_dump;
     * fallback broadcast is package-scoped. No upload, external service or log collection here. */
    @Synchronized private fun scheduleLocalLog() {
        if (pending || sending || context == null) return
        pending = true
        handler.postDelayed({
            synchronized(this) { pending = false; sending = true }
            try { context?.let { DuoSettingsClient.reportDump(it, baseDump) } }
            catch (_: Throwable) { }
            finally { synchronized(this) { sending = false } }
        }, 2500L)
    }
}
