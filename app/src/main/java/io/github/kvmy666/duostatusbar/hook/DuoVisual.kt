package io.github.kvmy666.duostatusbar.hook


/**
 * Everything the drawing needs, derived from system state.
 *
 * Pure data + pure maths on purpose: this is the half that can go wrong silently, so it is kept
 * free of Android types and unit-tested (`DuoMappingTest`) instead of being debugged on a phone.
 */
data class DuoVisual(
    val trimLeftEnd: Float,
    val trimRightEnd: Float,
    /** The two halves' track arc lengths (trim fractions); the right one is 0 when the gap closes. */
    val leftArc: Float,
    val rightArc: Float,
    val trackOpacity: Float,
    val percentText: String,
    val percentOpacity: Float,
    val percentFontSize: Float,
    /**
     * The cellular generation shown in the middle slot when Wi-Fi is off — "5G"/"4G"/"3G"/"2G", or
     * empty when there is no service. Empty unless the slot is actually holding it, so a hidden
     * occupant never carries stale text (FR-06).
     */
    val networkText: String,
    val boltOpacity: Float,
    val wifiOuterOpacity: Float,
    val wifiMidOpacity: Float,
    val cell1Opacity: Float,
    val cell2Opacity: Float,
    val cell3Opacity: Float,
    val cell4Opacity: Float,
    val tint: Int,
    val fgColor: Int,
    /**
     * Which occupant the middle slot holds: 0 off, 1 Wi-Fi, 2 airplane, 3 DND (FR-06/FR-16).
     *
     * The slot's whole hand-over - the arcs collapsing, the dot fading, the plane or crescent growing
     * out of it - is one Rive state machine layer, so this is the only thing the host says about it.
     */
    val middleMode: Int,
    /**
     * The two signal axes the Rive blend layers read: Wi-Fi 0-3, cellular 0-4. One number each rather
     * than six opacities, because Rive blends between pose animations and gets the per-sphere cascade
     * for free as the eased axis sweeps past each sphere's threshold.
     */
    val wifiLevel: Int,
    val cellLevel: Int,
    /**
     * Charging drives the bolt's journey - it is born in the middle slot and travels up into the ring's
     * gap - so that whole sequence is one Rive layer, and this is the only thing the host says about it.
     */
    val charging: Boolean,
    /** False plays the departure (screen off); true brings the element back. */
    val visible: Boolean = true,
    /**
     * Whether the charging *journey* plays. False shows the bolt instantly, which is the user's
     * "charging animation" switch (FR-25).
     */
    val animateCharge: Boolean = true
) {
    /**
     * Interpolates between two snapshots, for the FR-09 setting demos that morph one setting between
     * its off and on states.
     *
     * Every drawn quantity is a Float, so those interpolate; the things that cannot be half-way
     * (colours, the text, the boolean that drives the state machine) snap at the mid-point. Pure and
     * unit-tested, because a demo that quietly shows the wrong thing is worse than no demo.
     */
    fun lerp(other: DuoVisual, t: Float): DuoVisual {
        val f = t.coerceIn(0f, 1f)
        fun at(a: Float, b: Float) = a + (b - a) * f
        val past = f >= 0.5f
        return copy(
            trimLeftEnd = at(trimLeftEnd, other.trimLeftEnd),
            trimRightEnd = at(trimRightEnd, other.trimRightEnd),
            leftArc = at(leftArc, other.leftArc),
            rightArc = at(rightArc, other.rightArc),
            trackOpacity = at(trackOpacity, other.trackOpacity),
            percentOpacity = at(percentOpacity, other.percentOpacity),
            percentFontSize = at(percentFontSize, other.percentFontSize),
            boltOpacity = at(boltOpacity, other.boltOpacity),
            wifiOuterOpacity = at(wifiOuterOpacity, other.wifiOuterOpacity),
            wifiMidOpacity = at(wifiMidOpacity, other.wifiMidOpacity),
            cell1Opacity = at(cell1Opacity, other.cell1Opacity),
            cell2Opacity = at(cell2Opacity, other.cell2Opacity),
            cell3Opacity = at(cell3Opacity, other.cell3Opacity),
            cell4Opacity = at(cell4Opacity, other.cell4Opacity),
            percentText = if (past) other.percentText else percentText,
            networkText = if (past) other.networkText else networkText,
            tint = if (past) other.tint else tint,
            fgColor = if (past) other.fgColor else fgColor,
            // The middle slot's hand-over is a Rive layer, not a tween: this only picks which occupant.
            middleMode = if (past) other.middleMode else middleMode,
            wifiLevel = if (past) other.wifiLevel else wifiLevel,
            cellLevel = if (past) other.cellLevel else cellLevel,
            charging = if (past) other.charging else charging,
            visible = if (past) other.visible else visible,
            animateCharge = if (past) other.animateCharge else animateCharge
        )
    }
}
