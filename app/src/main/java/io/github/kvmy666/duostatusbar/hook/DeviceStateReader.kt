package io.github.kvmy666.duostatusbar.hook

import android.app.NotificationManager
import android.content.Context
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
     * Do Not Disturb, read through the notification policy (not a settings string), so it covers every
     * zen mode (priority, alarms, total silence, bedtime).
     *
     * A silenced **ringer** is deliberately *not* DND. The two are independent on stock Android: a user
     * can silence the ringer without enabling DND, and the moon inside the ring means "DND is on". The
     * old code OR-ed `RINGER_MODE_SILENT` into this, so a merely-silenced phone drew the DND crescent
     * with DND switched off (user-reported). The ringer is still observed elsewhere if a distinct
     * silent indicator is ever wanted; it must not masquerade as DND here.
     */
    fun isDndOn(context: Context): Boolean = try {
        val filter = (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.currentInterruptionFilter
        interruptionFilterIsDnd(filter)
    } catch (t: Throwable) {
        L.w("isDndOn: ${t.message}")
        false
    }

    /**
     * The pure decision behind [isDndOn]: DND is on when an interruption filter is set and it is not
     * `INTERRUPTION_FILTER_ALL`. A null filter (service unavailable, policy access denied) reads as off,
     * never as on. Pure, so the silent-vs-DND boundary is unit-tested off the phone.
     */
    fun interruptionFilterIsDnd(filter: Int?): Boolean =
        filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL
}
