package io.github.kvmy666.duostatusbar.hook

/**
 * The single entry point the drawing and the monitor have always used, kept as a thin facade so every
 * existing caller and test keeps compiling unchanged.
 *
 * The maths itself now lives next to what it belongs to: [RingGeometry] (arc/trim/gap), [SignalMapping]
 * (Wi-Fi/cellular levels and the middle-slot occupant) and [Colors] (palette and battery tint).
 */
object DuoMapping {

    // Ring geometry - see RingGeometry.
    const val LEFT_START = RingGeometry.LEFT_START
    const val RIGHT_FULL = RingGeometry.RIGHT_FULL
    const val GAP_PERCENT_DEG = RingGeometry.GAP_PERCENT_DEG
    const val GAP_CHARGING_DEG = RingGeometry.GAP_CHARGING_DEG
    val RIGHT_START = RingGeometry.RIGHT_START
    val LEFT_FULL = RingGeometry.LEFT_FULL
    const val DRAWN_ARC = RingGeometry.DRAWN_ARC

    fun gapClosed(charging: Boolean, showPercent: Boolean): Boolean =
        RingGeometry.gapClosed(charging, showPercent)

    fun halfArc(charging: Boolean): Float = RingGeometry.halfArc(charging)

    fun leftArc(charging: Boolean, showPercent: Boolean): Float =
        RingGeometry.leftArc(charging, showPercent)

    fun rightArc(charging: Boolean, showPercent: Boolean): Float =
        RingGeometry.rightArc(charging, showPercent)

    fun trimLeft(level: Int, charging: Boolean, closed: Boolean): Float =
        RingGeometry.trimLeft(level, charging, closed)

    fun trimRight(level: Int, charging: Boolean, closed: Boolean): Float =
        RingGeometry.trimRight(level, charging, closed)

    fun percentFontSize(text: String): Float = RingGeometry.percentFontSize(text)

    // Palette - see Colors.
    const val GREEN_CHARGING = Colors.GREEN_CHARGING
    const val YELLOW_SAVER = Colors.YELLOW_SAVER
    const val RED_CRITICAL = Colors.RED_CRITICAL
    const val WHITE = Colors.WHITE
    const val BLACK = Colors.BLACK

    fun tint(level: Int, charging: Boolean, saver: Boolean, base: Int = WHITE): Int =
        Colors.tint(level, charging, saver, base)

    // Signal levels and the middle slot - see SignalMapping.
    const val MIDDLE_OFF = SignalMapping.MIDDLE_OFF
    const val MIDDLE_WIFI = SignalMapping.MIDDLE_WIFI
    const val MIDDLE_AIRPLANE = SignalMapping.MIDDLE_AIRPLANE
    const val MIDDLE_DND = SignalMapping.MIDDLE_DND
    const val MIDDLE_NETWORK = SignalMapping.MIDDLE_NETWORK

    fun wifiBars(stockLevel: Int): Int = SignalMapping.wifiBars(stockLevel)

    fun wifiOpacities(level: Int): Triple<Float, Float, Float> = SignalMapping.wifiOpacities(level)

    fun cellOpacities(level: Int): List<Float> = SignalMapping.cellOpacities(level)

    fun middleMode(
        airplane: Boolean,
        dnd: Boolean,
        wifiOn: Boolean = true,
        hasNetwork: Boolean = false,
        wifiConnected: Boolean = true,
        networkOnly: Boolean = false
    ): Int = SignalMapping.middleMode(airplane, dnd, wifiOn, hasNetwork, wifiConnected, networkOnly)

    fun networkGeneration(type: Int, nrConnected: Boolean = false): String =
        SignalMapping.networkGeneration(type, nrConnected)

    fun visual(
        level: Int,
        charging: Boolean,
        saver: Boolean,
        showPercent: Boolean,
        wifiLevel: Int,
        cellLevel: Int,
        airplane: Boolean,
        dnd: Boolean = false,
        fgColor: Int = WHITE,
        visible: Boolean = true,
        /** Whether the Wi-Fi radio is on. Off hands the middle slot to the cellular generation. */
        wifiOn: Boolean = true,
        /**
         * Whether Wi-Fi is the network actually carrying data. False (connected but no internet, or
         * radio off) hands the slot to the cellular generation, so the element follows the active path
         * rather than showing a Wi-Fi glyph while the user is on mobile data.
         */
        wifiConnected: Boolean = wifiLevel > 0,
        /** The cellular generation, e.g. "5G"; ignored unless the slot is actually holding it. */
        networkText: String = "",
        /** Whether the charging journey plays; false shows the bolt instantly. */
        animateCharge: Boolean = true,
        /**
         * The "network icons only" switch: true keeps the middle slot to Wi-Fi + 5G/4G and never lets
         * airplane or DND occupy it (the user's toggle, FR-06).
         */
        networkOnly: Boolean = false
    ): DuoVisual {
        // The middle slot holds exactly one occupant (FR-06/FR-16): airplane wins, then DND, then
        // Wi-Fi; with Wi-Fi off the slot shows the cellular generation instead. The hand-over itself
        // - the arcs collapsing, the dot fading, the new occupant growing out of it - is the
        // MiddleSlot layer's job, so all the host says is which occupant it should be.
        // Wi-Fi only owns the slot when it is the active data path: a connected-but-internet-less AP
        // still leaves the phone on mobile data, so the slot shows the cellular generation instead of a
        // Wi-Fi glyph while the user is plainly on 4G/5G (user-reported).
        val mode = SignalMapping.middleMode(airplane, dnd, wifiOn, networkText.isNotEmpty(), wifiConnected, networkOnly)
        val (outer, middle, dot) = SignalMapping.wifiOpacities(wifiLevel)
        val cells = SignalMapping.cellOpacities(if (airplane) 0 else cellLevel)
        val percent = if (showPercent && !charging) level.toString() else ""
        val closed = RingGeometry.gapClosed(charging, showPercent)
        return DuoVisual(
            trimLeftEnd = RingGeometry.trimLeft(level, charging, closed),
            trimRightEnd = RingGeometry.trimRight(level, charging, closed),
            leftArc = RingGeometry.leftArc(charging, showPercent),
            rightArc = RingGeometry.rightArc(charging, showPercent),
            trackOpacity = 0.22f,
            percentText = percent.ifEmpty { " " },
            percentOpacity = if (percent.isEmpty()) 0f else 1f,
            percentFontSize = RingGeometry.percentFontSize(percent.ifEmpty { "50" }),
            // Only the slot's actual occupant carries text: a hidden one never shows a stale label.
            networkText = if (mode == SignalMapping.MIDDLE_NETWORK) networkText else "",
            boltOpacity = if (charging) 1f else 0f,
            wifiOuterOpacity = outer,
            wifiMidOpacity = middle,
            cell1Opacity = cells[0],
            cell2Opacity = cells[1],
            cell3Opacity = cells[2],
            cell4Opacity = cells[3],
            tint = Colors.tint(level, charging, saver, fgColor),
            fgColor = fgColor,
            middleMode = mode,
            wifiLevel = wifiLevel,
            cellLevel = cellLevel,
            charging = charging,
            visible = visible,
            animateCharge = animateCharge
        )
    }
}
