package io.github.kvmy666.duostatusbar.hook.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-01: a measured profile can be added from data, but it must never override an unverified guess
 * or a device it does not match. That rule is what keeps a data edit from silently changing behaviour
 * on a phone we have not read, so it is pinned here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RomProfilesTest {

    private val json = """
        {
          "version": 1,
          "profiles": [
            {
              "id": "oxygenos",
              "label": "OxygenOS / OnePlus",
              "systemUiPackage": "com.android.systemui",
              "containerIds": ["system_icons", "status_bar_end_side_content"],
              "batteryId": "battery",
              "clockId": "clock",
              "match": ["oneplus", "oxygen"],
              "measured": true,
              "notes": "measured on OxygenOS 16"
            },
            {
              "id": "broken",
              "containerIds": [],
              "match": ["nobody"],
              "measured": true,
              "notes": "must be skipped"
            }
          ]
        }
    """.trimIndent()

    private fun measuredSamsung() = RomAdapter(
        id = "samsung",
        label = "Samsung One UI (measured)",
        systemUiPackage = "com.android.systemui",
        containerIds = listOf("samsung_status_icons"),
        batteryId = "battery",
        clockId = "clock",
        notes = "measured on SM-S938B",
        match = listOf("samsung"),
        measured = true
    )

    @Test
    fun `parses a profile and skips an entry with no container ids`() {
        val profiles = RomProfiles.parse(json)
        assertEquals(1, profiles.size)
        val p = profiles.single()
        assertEquals("oxygenos", p.id)
        assertEquals(listOf("system_icons", "status_bar_end_side_content"), p.containerIds)
        assertTrue(p.measured)
        assertEquals(listOf("oneplus", "oxygen"), p.match)
    }

    @Test
    fun `a malformed body yields no profiles instead of throwing`() {
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse("{not json"))
        assertEquals(emptyList<RomAdapter>(), RomProfiles.parse(""))
    }

    @Test
    fun `a measured profile overrides the unverified built-in for its match`() {
        val samsung = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q:14", listOf(measuredSamsung())
        )
        assertEquals("samsung_status_icons", samsung.containerIds.first())
        assertTrue(samsung.measured)
    }

    @Test
    fun `an unmeasured profile never overrides the built-in`() {
        val samsung = RomDetection.forThisRom(
            "samsung", "samsung", "dm1q", "samsung/dm1q:14",
            listOf(measuredSamsung().copy(measured = false))
        )
        assertEquals("system_icons", samsung.containerIds.first())
        assertFalse(samsung.measured)
    }

    @Test
    fun `a profile that does not match leaves the built-in choice alone`() {
        val google = RomDetection.forThisRom(
            "Google", "google", "husky", "Google/husky:16", listOf(measuredSamsung())
        )
        assertEquals("aosp", google.id)
    }
}
