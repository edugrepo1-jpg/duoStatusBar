package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.provider.Settings

/**
 * Reads the module's adb/developer stage override (see the module's `DuoGuard`).
 *
 * `duo_statusbar_stage` is a `Settings.Global` entry that wins over the app's own settings, so a
 * leftover `1` (icons only, no native drawing) pins the module to the Canvas fallback forever with no
 * visible reason — the exact "wifi icon not show" report. A normal app can read `Settings.Global`
 * without a permission, so the app can detect and explain it; clearing it needs root (the app has no
 * `WRITE_SECURE_SETTINGS`), which is why [clearCommand] is shown to the user as a fallback.
 */
internal object StageOverride {

    const val KEY = "duo_statusbar_stage"

    /** The override in force (0/1/2), or null when it has not been set. Never throws. */
    fun read(context: Context): Int? = try {
        Settings.Global.getInt(context.contentResolver, KEY, ABSENT).let {
            if (it in OFF..RIVE) it else null
        }
    } catch (_: Throwable) {
        null
    }

    /** What to tell the user when the app cannot clear it itself. */
    const val clearCommand = "adb shell settings delete global duo_statusbar_stage"

    private const val ABSENT = -1
    private const val OFF = 0
    private const val RIVE = 2
}
