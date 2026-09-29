package io.github.kvmy666.duostatusbar.hook

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.github.kvmy666.duostatusbar.L

/**
 * The platform reads behind the Duo element, kept apart from the monitor so each one can be tested
 * and swapped per ROM (Phase 7) without touching the wiring.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object SystemReaders {

    private const val TAG = "DuoSB"

    /**
     * Wi-Fi 0..3, or the previous value when Wi-Fi cannot be queried.
     *
     * The level comes from `WifiManager.calculateSignalLevel`, the platform's own RSSI-to-bars mapping,
     * rather than fixed dBm thresholds: the local thresholds were strict enough that a genuinely strong
     * link read as 2 of 3 (user-reported). The RSSI is taken from the *active* network's `WifiInfo` where
     * possible, so it matches the icon the ROM itself draws, and `connectionInfo` is only the fallback.
     */
    fun wifiLevel(context: Context, current: Int): Int = try {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        when {
            wifi == null -> current
            !wifi.isWifiEnabled -> 0
            else -> {
                val rssi = activeWifiRssi(context) ?: wifi.connectionInfo?.rssi ?: current
                if (rssi == -127) {
                    0
                } else {
                    // The stock icon draws four bars; Duo draws three. `calculateSignalLevel` uses the
                    // AOSP thresholds, which on OxygenOS report one bar fewer than the icon shows
                    // (measured: stock 3 bars read as 2). Shift to the stock count, then compress.
                    val stockBars = (WifiManager.calculateSignalLevel(rssi, 5) + 1).coerceIn(0, 4)
                    DuoMapping.wifiBars(stockBars)
                }
            }
        }
    } catch (t: Throwable) {
        L.w("wifiLevel: ${t.message}")
        current
    }

    /** The active Wi-Fi network's RSSI when Wi-Fi is the default network, else null. */
    private fun activeWifiRssi(context: Context): Int? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        (caps?.transportInfo as? android.net.wifi.WifiInfo)?.rssi
    } catch (_: Throwable) {
        null
    }

    /** Cellular spheres 0..4. */
    fun cellLevel(context: Context, airplane: Boolean, current: Int): Int = try {
        if (airplane) 0
        else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.signalStrength?.level?.coerceIn(0, 4) ?: current
        }
    } catch (t: Throwable) {
        L.w("cellLevel: ${t.message}")
        current
    }

    /**
     * Per-SIM cellular levels (0..4), in SIM-slot order, or an empty list when there is only one active
     * subscription. The dual-SIM display splits the four spheres into two pairs, one per line, so it needs
     * both levels at once (the user's request; iOS shows both carriers' signal strength).
     */
    @SuppressLint("MissingPermission")
    fun cellLevels(context: Context, airplane: Boolean): List<Int> = try {
        if (airplane) emptyList()
        else {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subs = sm?.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
            if (subs.size < 2) emptyList() else subs.map { levelForSubscription(context, it.subscriptionId) }
        }
    } catch (t: Throwable) {
        L.w("cellLevels: ${t.message}")
        emptyList()
    }

    /** One subscription's signal level (0..4); 0 when it cannot be read. */
    private fun levelForSubscription(context: Context, subId: Int): Int = try {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val perSub = base?.let { tm ->
            TelephonyManager::class.java
                .getMethod("createForSubscriptionId", Int::class.javaPrimitiveType)
                .invoke(tm, subId) as? TelephonyManager
        }
        ((perSub ?: base)?.signalStrength?.level ?: 0).coerceIn(0, 4)
    } catch (t: Throwable) {
        L.w("cellLevel[$subId]: ${t.message}")
        0
    }

    /** Whether the Wi-Fi radio is on at all — distinct from "connected", which is a signal level. */
    fun isWifiEnabled(context: Context, current: Boolean): Boolean = try {
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled ?: current
    } catch (t: Throwable) {
        L.w("isWifiEnabled: ${t.message}")
        current
    }

    /**
     * Whether Wi-Fi is the network the phone is actually using for data.
     *
     * This is the difference between "Wi-Fi is connected" and "Wi-Fi is carrying traffic": a captive
     * portal or an internet-less AP leaves Wi-Fi connected while Android routes everything over mobile
     * data, and the stock bar shows the cellular icon. The element must follow the active path, or it
     * shows a Wi-Fi glyph while the user is plainly on 4G/5G (user-reported bug). Read from the default
     * network's transport; unreadable keeps the caller's value.
     */
    fun isWifiActive(context: Context, current: Boolean): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: current
    } catch (t: Throwable) {
        L.w("isWifiActive: ${t.message}")
        current
    }

    /**
     * Whether the active Wi-Fi network actually has internet (validated), not merely a link.
     *
     * A captive portal or an internet-less AP leaves Wi-Fi connected; Android routes over cellular and the
     * stock bar shows the cellular icon. The element follows the validated state so it never shows a live
     * Wi-Fi glyph while the phone is really on mobile data.
     */
    fun isWifiValidated(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (t: Throwable) {
        L.w("isWifiValidated: ${t.message}")
        false
    }

    /**
     * The cellular generation the phone is on ("5G"/"4G"/"3G"/"2G"), or empty when there is no
     * service. Empty is a real answer here (unknown type), so it is returned as-is rather than
     * masked by the last value; only a failed read keeps the caller's value (FR-21).
     */
    @SuppressLint("MissingPermission")
    fun networkGeneration(context: Context, airplane: Boolean, current: String): String = try {
        if (airplane) ""
        else {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val data = tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            val type = if (data != TelephonyManager.NETWORK_TYPE_UNKNOWN) data
            else tm?.voiceNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
            DuoMapping.networkGeneration(type, nrConnected = isNrConnected(tm))
        }
    } catch (t: Throwable) {
        L.w("networkGeneration: ${t.message}")
        current
    }

    /**
     * Whether the radio is actually on NR (5G).
     *
     * On 5G NSA - which is what most carriers run - `getDataNetworkType()` reports LTE even while the
     * stock bar shows 5G, which is why the element said 4G on a 5G phone. `ServiceState.getNrState()`
     * is what the stock OxygenOS bar itself reads (`OplusMobileSignalExImpl`), so it is read here the
     * same way. Both calls are hidden/reflected and guarded: unreadable means "not NR", which only
     * costs a 5G label, never the status bar.
     */
    private fun isNrConnected(tm: TelephonyManager?): Boolean = try {
        if (tm == null) false
        else {
            val serviceState = TelephonyManager::class.java
                .getMethod("getServiceState")
                .invoke(tm)
            val nrState = serviceState?.let {
                it.javaClass.getMethod("getNrState").invoke(it) as? Int
            }
            // ServiceState.NR_STATE_CONNECTED (2) / NR_STATE_NOT_RESTRICTED (3).
            nrState == 2 || nrState == 3
        }
    } catch (t: Throwable) {
        false
    }

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
