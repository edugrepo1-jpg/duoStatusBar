package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/**
 * Reads the user's settings out of the app's provider, from inside System UI (FR-03/16/17).
 *
 * Only the *shared contract constants* come from [DuoPrefs] — its storage methods are never called from
 * here, because inside this process `context.getSharedPreferences` would be System UI's own storage, not the
 * app's. The values arrive over the provider, which is the only channel that crosses the uid boundary.
 *
 * Every read is guarded and falls back to [DEFAULT]: a status bar must not care whether the settings app is
 * installed, running, or being upgraded.
 */
internal data class ModuleSettings(
    val enabled: Boolean,
    val useRive: Boolean,
    val showPercent: Boolean,
    val sizePercent: Int,
    val offsetX: Int,
    val liveApply: Boolean,
    val systemClockFont: Boolean,
    val revealMs: Int,
    val revision: Long,
    val tapAction: String,
    val doubleTapAction: String,
    val longPressAction: String,
    val animationsEnabled: Boolean,
    val arrivalEnabled: Boolean,
    val departureEnabled: Boolean,
    val chargingEnabled: Boolean,
    /** "auto" follows the bar's own icon colour; "black"/"white" are manual overrides. */
    val iconColor: String,
    /** False leaves the icons Duo does not replace (silent, vibrate, alarm…) visible. */
    val hideOtherIcons: Boolean,
    /** True keeps the middle slot to Wi-Fi + 5G/4G and never shows DND or airplane there. */
    val networkOnly: Boolean,
    /** Which cellular line the spheres show on a dual-SIM phone: "auto", "sim1" or "sim2". */
    val simChoice: String
) {
    companion object {
        val DEFAULT = ModuleSettings(
            enabled = false,
            useRive = true,
            showPercent = true,
            sizePercent = 100,
            offsetX = 0,
            liveApply = true,
            systemClockFont = true,
            revealMs = DuoPrefs.DEFAULT_REVEAL_MS,
            revision = 0L,
            tapAction = "no_action",
            doubleTapAction = "no_action",
            longPressAction = "no_action",
            animationsEnabled = true,
            arrivalEnabled = true,
            departureEnabled = true,
            chargingEnabled = true,
            iconColor = "auto",
            hideOtherIcons = false,
            networkOnly = false,
            simChoice = "auto"
        )
    }
}

internal object DuoSettingsClient {

    private const val TAG = "DuoSB"

    /** Sent by the app after a write, so the module re-reads without polling (NFR-2). */
    const val ACTION_SETTINGS_CHANGED = "io.github.kvmy666.duostatusbar.SETTINGS_CHANGED"

    private val uri: Uri get() = Uri.parse("content://${DuoPrefs.AUTHORITY}")

    /**
     * Whether the *last* [read] could not reach the app's provider at all, as opposed to the provider
     * answering "the module is off". The two are indistinguishable in [ModuleSettings.DEFAULT] (both are
     * `enabled=false`) but need opposite handling: "off" is the user's wish, "unreachable" must not be
     * mistaken for it. ColorOS/realme showed exactly that failure — the module loaded, read nothing and
     * fell back to `stage 0`, so the element never drew even though the app's master switch was on.
     */
    @Volatile
    var providerUnreachable: Boolean = false
        private set

    fun read(context: Context): ModuleSettings = try {
        val started = android.os.SystemClock.elapsedRealtime()
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor == null) {
            // A provider that answers always returns a row (see DuoSettingsProvider), so a null cursor is
            // "could not be reached", never "the module is off".
            providerUnreachable = true
            L.w("settings provider returned no cursor - treating as unreachable, not as off")
            ModuleSettings.DEFAULT
        } else {
            // The provider always adds a row when it answers (see DuoSettingsProvider.rowFor), so an empty
            // cursor means the authority was answered by something else - also "unreachable", not "off".
            var rowRead = false
            val result = cursor.use { c ->
                if (!c.moveToFirst()) {
                    ModuleSettings.DEFAULT
                } else {
                    rowRead = true
                    ModuleSettings(
                        enabled = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_ENABLED)) == 1,
                        useRive = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_USE_RIVE)) == 1,
                        showPercent = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_SHOW_PERCENT)) == 1,
                        sizePercent = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_SIZE_PERCENT)),
                        offsetX = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_OFFSET_X)),
                        liveApply = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_LIVE_APPLY)) == 1,
                        systemClockFont = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_CLOCK_FONT)) == 1,
                        revealMs = DuoPrefs.nearestReveal(c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_REVEAL_MS))),
                        revision = c.getLong(c.getColumnIndexOrThrow(DuoPrefs.COL_REVISION)),
                        tapAction = c.getString(c.getColumnIndexOrThrow(DuoPrefs.COL_TAP)) ?: "no_action",
                        doubleTapAction = c.getString(c.getColumnIndexOrThrow(DuoPrefs.COL_DOUBLE_TAP)) ?: "no_action",
                        longPressAction = c.getString(c.getColumnIndexOrThrow(DuoPrefs.COL_LONG_PRESS)) ?: "no_action",
                        animationsEnabled = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_ANIMATIONS)) == 1,
                        arrivalEnabled = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_ARRIVAL)) == 1,
                        departureEnabled = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_DEPARTURE)) == 1,
                        chargingEnabled = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_CHARGING)) == 1,
                        iconColor = c.getString(c.getColumnIndexOrThrow(DuoPrefs.COL_ICON_COLOR)) ?: "auto",
                        hideOtherIcons = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_HIDE_OTHER_ICONS)) == 1,
                        networkOnly = c.getInt(c.getColumnIndexOrThrow(DuoPrefs.COL_NETWORK_ONLY)) == 1,
                        simChoice = c.getString(c.getColumnIndexOrThrow(DuoPrefs.COL_SIM_CHOICE)) ?: "auto"
                    )
                }
            }
            providerUnreachable = !rowRead
            if (!rowRead) {
                L.w("settings provider returned no row - treating as unreachable, not as off")
            } else {
                // Logged because this is a synchronous binder call from System UI's boot path: if it is
                // ever slow, it is slow there, and that deserves a number rather than a guess.
                L.i("settings read in ${android.os.SystemClock.elapsedRealtime() - started} ms (rev ${result.revision})")
            }
            result
        }
    } catch (t: Throwable) {
        providerUnreachable = true
        L.w("settings unreadable (${t.javaClass.simpleName}: ${t.message}) - using defaults")
        ModuleSettings.DEFAULT
    }

    /** Tells the app what the module actually did, for the diagnostics screen. Never throws. */
    fun report(context: Context, status: String) {
        try {
            val extras = Bundle().apply { putString("status", status) }
            context.contentResolver.call(uri, "status", null, extras)
        } catch (t: Throwable) {
            L.w("status report failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Sends the debug diagnostic dump to the app so its "Save status to a file" button can ship it.
     * Separate from [report] because the dump is large and debug-only: the compact status line is the
     * release path. Never throws.
     */
    fun reportDump(context: Context, dump: String) {
        try {
            val extras = Bundle().apply { putString("dump", dump) }
            context.contentResolver.call(uri, "dump", null, extras)
        } catch (t: Throwable) {
            L.w("dump report failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Tells the app the module had to fall back — Rive could not draw, so the simple Canvas element is
     * being used — so the app can ask the user to send the log. An empty [reason] clears the alert (sent
     * at each module load). Never throws.
     */
    fun reportFallback(context: Context, reason: String) {
        try {
            val extras = Bundle().apply { putString("fallback", reason) }
            context.contentResolver.call(uri, "fallback", null, extras)
        } catch (t: Throwable) {
            L.w("fallback report failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
