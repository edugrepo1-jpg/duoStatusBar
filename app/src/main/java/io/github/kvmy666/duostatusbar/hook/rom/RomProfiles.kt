package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import io.github.kvmy666.duostatusbar.L
import org.json.JSONArray
import org.json.JSONObject

/**
 * Measured ROM profiles loaded from `assets/rom-profiles.json` (FR-01).
 *
 * The built-in adapters in [RomDetection] stay the source of truth for anything we have not measured.
 * This only supplies profiles that were read off a real device, and [RomDetection.forThisRom] accepts
 * them only when `measured` is true — so a data edit can add a new device without an APK, but it can
 * never silently override an unverified guess.
 *
 * Parsing is defensive: a missing or malformed asset yields an empty list, never an exception, and the
 * module falls back to the code defaults exactly as before.
 */
internal object RomProfiles {

    private const val ASSET = "rom-profiles.json"

    @Volatile
    private var cache: List<RomAdapter>? = null

    /** Reads and caches the bundled profiles. Safe to call from any thread. */
    fun load(context: Context): List<RomAdapter> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val parsed = try {
                val module = if (context.packageName == io.github.kvmy666.duostatusbar.BuildConfig.APPLICATION_ID) context
                    else context.createPackageContext(io.github.kvmy666.duostatusbar.BuildConfig.APPLICATION_ID, 0)
                module.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
            } catch (t: Throwable) {
                L.w("rom profiles unreadable: ${t.javaClass.simpleName}: ${t.message}")
                emptyList()
            }
            cache = parsed
        }
        return cache ?: emptyList()
    }

    /** Parses the JSON body. A blank id or an empty container list skips that entry. */
    fun parse(json: String): List<RomAdapter> = try {
        val array = JSONObject(json).optJSONArray("profiles") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val containerIds = o.optJSONArray("containerIds").toStringList()
                if (id.isBlank() || containerIds.isEmpty()) continue
                add(
                    RomAdapter(
                        id = id,
                        label = o.optString("label", id),
                        systemUiPackage = o.optString("systemUiPackage", "com.android.systemui"),
                        containerIds = containerIds,
                        batteryId = o.optString("batteryId", "battery"),
                        clockId = o.optString("clockId", "clock"),
                        notes = o.optString("notes"),
                        match = o.optJSONArray("match").toStringList(),
                        measured = o.optBoolean("measured", false)
                    )
                )
            }
        }
    } catch (t: Throwable) {
        L.w("rom profiles malformed: ${t.javaClass.simpleName}: ${t.message}")
        emptyList()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
    }
}
