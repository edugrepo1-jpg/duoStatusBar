package io.github.kvmy666.duostatusbar.hook

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import io.github.kvmy666.duostatusbar.L

/**
 * The device-state platform reads behind the Duo element.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object DeviceStateReader {

    fun isAirplaneOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (_: Throwable) {
        false
    }

    fun isPowerSaveOn(context: Context): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false
    } catch (_: Throwable) {
        false
    }

    /**
     * Do Not Disturb / silent, as one state (the design groups them: DESIGN-duo.md §4 "DND / silent").
     *
     * DND is read through the notification policy, not a settings string, so it covers every zen mode
     * (priority, alarms, total silence, bedtime). "Silent" is the ringer truly silenced; vibrate is not
     * silent, so it is deliberately excluded - the moon means "this will not make a sound", and a phone
     * that still vibrates has not said that.
     */
    fun isDndOn(context: Context): Boolean = try {
        val filter = (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.currentInterruptionFilter
        val zenActive = filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL
        val ringer = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
        val silent = ringer == AudioManager.RINGER_MODE_SILENT
        zenActive || silent
    } catch (t: Throwable) {
        L.w("isDndOn: ${t.message}")
        false
    }
}
