package io.github.kvmy666.duostatusbar.hook

/**
 * The Wi-Fi/cellular level mapping and the middle-slot occupant choice.
 *
 * Pure and unit-tested (`DuoMappingTest`), so a change here is caught off-device.
 */
object SignalMapping {

    /**
     * Maps the phone's Wi-Fi bar count onto Duo's three levels, so the ring agrees with the stock icon.
     *
     * The stock icon has four bars (1..4); Duo draws two arcs plus the dot (1..3). The user's mapping,
     * measured against the real icon: 1 and 2 bars -> 1, 3 bars -> 2, 4 bars -> 3. 0 is "no signal".
     */
    fun wifiBars(stockLevel: Int): Int = when {
        stockLevel <= 0 -> 0
        stockLevel <= 2 -> 1
        stockLevel == 3 -> 2
        else -> 3
    }

    /**
     * Wi-Fi layers, bottom-up: nothing connected dims everything, then the dot, the middle arc and
     * finally the outer arc appear as the signal improves (FR-25).
     */
    fun wifiOpacities(level: Int): Triple<Float, Float, Float> = when (level.coerceIn(0, 3)) {
        0 -> Triple(0.3f, 0.3f, 0.3f)
        1 -> Triple(0.3f, 0.3f, 1f)
        2 -> Triple(0.3f, 1f, 1f)
        else -> Triple(1f, 1f, 1f)
    }

    /** Four cellular spheres; `level` 0..4 spheres lit. */
    fun cellOpacities(level: Int): List<Float> {
        val n = level.coerceIn(0, 4)
        return (1..4).map { if (it <= n) 1f else 0.3f }
    }

    /** The middle slot's occupants, in the order the state machine expects (see `scene.rml`). */
    const val MIDDLE_OFF = 0
    const val MIDDLE_WIFI = 1
    const val MIDDLE_AIRPLANE = 2
    const val MIDDLE_DND = 3

    /** The cellular generation shown when Wi-Fi is off (FR-06): "5G"/"4G"/"3G"/"2G". */
    const val MIDDLE_NETWORK = 4

    /**
     * FR-06/FR-16: airplane wins the slot, then DND, then a **connected** Wi-Fi, then the cellular
     * generation. [wifiConnected] is false when the radio is on but there is no network through it, in
     * which case the phone is on mobile data and the generation is the honest thing to show.
     */
    fun middleMode(
        airplane: Boolean,
        dnd: Boolean,
        wifiOn: Boolean = true,
        hasNetwork: Boolean = false,
        wifiConnected: Boolean = true,
        networkOnly: Boolean = false
    ): Int = when {
        // The "network icons only" switch: the slot is about the connection, so DND and airplane are
        // deliberately ignored and neither can take it.
        networkOnly && wifiOn && wifiConnected -> MIDDLE_WIFI
        networkOnly && hasNetwork -> MIDDLE_NETWORK
        networkOnly && wifiOn -> MIDDLE_WIFI
        networkOnly -> MIDDLE_OFF
        airplane -> MIDDLE_AIRPLANE
        dnd -> MIDDLE_DND
        wifiOn && wifiConnected -> MIDDLE_WIFI
        hasNetwork -> MIDDLE_NETWORK
        // Wi-Fi on but not connected, and no generation to name: keep the dim glyph rather than empty.
        wifiOn -> MIDDLE_WIFI
        else -> MIDDLE_OFF
    }

    /**
     * The cellular generation label for a `TelephonyManager.NETWORK_TYPE_*` value.
     *
     * Android-free on purpose: the type constants are spelled out as literals so this stays in the
     * pure, unit-tested half. Unknown or Wi-Fi-calling types map to empty, which leaves the slot off
     * rather than showing a label that means nothing.
     */
    fun networkGeneration(type: Int, nrConnected: Boolean = false): String {
        // The phone's radio reports NR as connected. On 5G NSA the data network type is still LTE, so
        // this is the only honest 5G signal - it is what the stock OxygenOS bar reads (see
        // `OplusMobileSignalExImpl`: `ServiceState.getNrState()` 2 or 3 means 5G).
        if (nrConnected) return "5G"
        return when (type) {
            // NETWORK_TYPE_NR
            20 -> "5G"
            // NETWORK_TYPE_LTE, NETWORK_TYPE_LTE_CA
            13, 19 -> "4G"
            // UMTS, EVDO_0/A, HSDPA, HSUPA, HSPA, EVDO_B, EHRPD, HSPAP, TD_SCDMA
            3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
            // GPRS, EDGE, CDMA, 1xRTT, IDEN, GSM
            1, 2, 4, 7, 11, 16 -> "2G"
            else -> ""
        }
    }
}
