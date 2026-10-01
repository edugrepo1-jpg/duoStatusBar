package io.github.kvmy666.duostatusbar.hook

/**
 * Which part of the one element a view draws.
 *
 * [ALL] is the original element, ring and icons together. [RING] is the battery ring plus the
 * percentage, bolt and signal dots that belong to it. [INDICATORS] is the Wi-Fi and middle-slot
 * cluster that normally sits inside the ring. Both parts are the same drawing; this only chooses which
 * group is visible, so a split layout is two views of one file rather than a second element.
 */
internal enum class DuoPart {
    ALL,
    RING,
    INDICATORS;

    /** Forces the group opacities this part draws. Other fields of [visual] are left as they are. */
    fun apply(visual: DuoVisual): DuoVisual = when (this) {
        ALL -> visual.copy(ringOpacity = 1f, indicatorsOpacity = 1f)
        RING -> visual.copy(ringOpacity = 1f, indicatorsOpacity = 0f)
        INDICATORS -> visual.copy(ringOpacity = 0f, indicatorsOpacity = 1f)
    }
}
