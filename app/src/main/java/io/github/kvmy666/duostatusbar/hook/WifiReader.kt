package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.github.kvmy666.duostatusbar.L

/**
 * The Wi-Fi platform reads behind the Duo element.
 *
 * Every reader returns the caller's current value when it cannot read — a status bar that shows a
 * slightly stale number is far better than a status bar that throws (FR-21).
 */
internal object WifiReader {

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
}
