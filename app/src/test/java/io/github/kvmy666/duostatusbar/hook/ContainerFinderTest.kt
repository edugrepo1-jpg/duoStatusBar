package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-01: the strip must be found on a ROM whose ids we never measured, by the roles it contains.
 * A wrong guess here hides a user's icons and draws nothing, so the locator is pinned in tests
 * instead of only on a phone. Ids do not resolve under Robolectric, which is exactly the
 * "unmeasured ROM" state `roleScan` exists for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContainerFinderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rom = RomDetection.forThisRom("unknown", "unknown", "unknown", "unknown")

    private fun finder() = ContainerFinder(context, rom, LogOnce())

    /** A status-icons container named like the ones every ROM keeps. */
    private class FakeStatusIconContainer(context: Context) : View(context)

    /** A battery view named like the AOSP/OEM battery meters. */
    private class FakeBatteryMeterView(context: Context) : View(context)

    /** A battery *container* (HyperOS) whose name must not be mistaken for the battery view. */
    private class MiuiStatusBatteryContainer(context: Context) : LinearLayout(context)

    @Test
    fun `finds the strip as the common parent of the icons and the battery`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        root.addView(strip)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `picks the lowest ancestor that holds both, not an outer wrapper`() {
        val root = FrameLayout(context)
        val wrapper = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        wrapper.addView(strip)
        root.addView(wrapper)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `an icons-only strip still resolves`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        root.addView(strip)

        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `a battery container is not mistaken for the battery view`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        // Only a *container* named "…BatteryContainer" — no battery meter view at all.
        strip.addView(MiuiStatusBatteryContainer(context))
        root.addView(strip)

        // The icons alone still pin the strip, and the container name is ignored as a battery.
        assertSame(strip, finder().roleScan(root))
    }

    @Test
    fun `a gone strip is skipped`() {
        val root = FrameLayout(context)
        val strip = LinearLayout(context)
        strip.addView(FakeStatusIconContainer(context))
        strip.addView(FakeBatteryMeterView(context))
        strip.visibility = View.GONE
        root.addView(strip)

        assertNull(finder().roleScan(root))
    }

    @Test
    fun `a tree with neither icons nor battery is left untouched`() {
        val root = FrameLayout(context)
        val random = LinearLayout(context)
        random.addView(View(context))
        root.addView(random)

        assertNull(finder().roleScan(root))
    }
}
