package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import io.github.kvmy666.duostatusbar.BuildConfig
import java.util.UUID

/** Global backend selection. No orientation setting or root permission is migrated here. */
internal object RuntimeBackend {
    const val MODULE = "module"
    const val ROOT = "root"
    private fun prefs(context: Context) = context.getSharedPreferences("duo_runtime", Context.MODE_PRIVATE)
    fun mode(context: Context): String = prefs(context).getString("mode", MODULE).let { if (it == ROOT) ROOT else MODULE }
    fun generation(context: Context): String = prefs(context).getString("generation", "").orEmpty()
    @Synchronized fun begin(context: Context): String {
        val generation = UUID.randomUUID().toString().replace("-", "")
        check(prefs(context).edit().putString("mode", ROOT).putString("generation", generation)
            .putString("state", "starting").putLong("elapsed", SystemClock.elapsedRealtime()).commit())
        SettingsBridge.push(context)
        return generation
    }
    @Synchronized fun stop(context: Context) {
        prefs(context).edit().putString("mode", MODULE).putString("state", "stopping").commit()
        SettingsBridge.push(context)
    }
    fun requestDiagnostics(context: Context) { prefs(context).edit().putLong("dumpRequest", SystemClock.elapsedRealtime()).apply() }
    fun snapshot(context: Context) = Bundle().apply {
        putString("mode", mode(context)); putString("generation", generation(context))
        putInt("versionCode", BuildConfig.VERSION_CODE)
        putLong("revision", DuoPrefs.revision(context))
        putLong("dumpRequest", prefs(context).getLong("dumpRequest", 0))
    }
    fun health(context: Context) = Bundle().apply {
        val p = prefs(context)
        putString("generation", p.getString("health_generation", ""))
        putString("state", p.getString("state", "stopped"))
        putLong("elapsed", p.getLong("elapsed", 0))
        putInt("versionCode", p.getInt("versionCode", 0))
        putBoolean("stockRestored", p.getBoolean("stockRestored", false) && p.getString("stock_generation", "").isNullOrEmpty())
        putBoolean("hideRequestedSuccess", p.getBoolean("hideRequestedSuccess", false))
        putBoolean("panelTracking", false); putBoolean("stockVisualVerified", false)
    }
    fun ready(context: Context): Boolean {
        val h = health(context)
        val age = SystemClock.elapsedRealtime() - h.getLong("elapsed")
        return mode(context) == ROOT && h.getString("generation") == generation(context) &&
            h.getString("state") == "ready" && h.getInt("versionCode") == BuildConfig.VERSION_CODE && age in 0..30_000
    }
    @Synchronized fun report(context: Context, generation: String?, data: Bundle?): Boolean {
        if (generation.isNullOrEmpty() || generation != generation(context) || data == null) return false
        val state = data.getString("state") ?: return false
        if (state !in setOf("starting", "ready", "inactive", "stopped", "failed")) return false
        return prefs(context).edit().putString("health_generation", generation).putString("state", state)
            .putLong("elapsed", SystemClock.elapsedRealtime()).putInt("versionCode", data.getInt("versionCode"))
            .putBoolean("stockRestored", data.getBoolean("stockRestored"))
            .putBoolean("hideRequestedSuccess", data.getBoolean("hideRequestedSuccess")).commit()
    }
    /** Only the provider's UID-0 branch can read/write this restoration journal. */
    @Synchronized fun stockJournal(context: Context, generation: String?, data: Bundle?): Bundle {
        val p = prefs(context)
        val owner = p.getString("stock_generation", "").orEmpty()
        if (data != null && !generation.isNullOrEmpty()) {
            if (data.getBoolean("clear") && owner == generation) {
                p.edit().remove("stock_generation").remove("stock_added").commit()
            } else if (generation == generation(context) && (owner.isEmpty() || owner == generation)) {
                val added = data.getString("added").orEmpty().split(',').filter { it in IconBlacklist.KEYS }.distinct().joinToString(",")
                p.edit().putString("stock_generation", generation).putString("stock_added", added).commit()
            }
        }
        return Bundle().apply {
            putString("generation", p.getString("stock_generation", ""))
            putString("added", p.getString("stock_added", ""))
        }
    }
}
