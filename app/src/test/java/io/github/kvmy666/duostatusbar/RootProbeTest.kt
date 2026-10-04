package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.RootProbe.Facts
import io.github.kvmy666.duostatusbar.RootProbe.RootKind
import io.github.kvmy666.duostatusbar.RootProbe.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The root-method decision table, pinned on the JVM.
 *
 * Each test is one row of "how a phone is rooted" → "what the report will say and do". This is what makes
 * the claim "we cover every root method" checkable rather than a promise: a method we have not measured
 * must come out as [RootKind.UNKNOWN], never be mislabelled as one we know.
 */
class RootProbeTest {

    private fun facts(
        suCommand: String = "su",
        suExists: Boolean = false,
        markers: Set<String> = emptySet(),
        modules: String = "",
        sui: Boolean = false,
        shizuku: Boolean = false,
        uid: Int = -1,
        rootAllowed: Boolean = false
    ) = Facts(suCommand, suExists, markers, modules, sui, shizuku, uid, rootAllowed)

    @Test
    fun `sui wins over a magisk marker because it ships no su binary`() {
        assertEquals(RootKind.SUI, RootProbe.classify(facts(markers = setOf("magisk"), sui = true)).kind)
    }

    @Test
    fun `magisk marker`() {
        assertEquals(RootKind.MAGISK, RootProbe.classify(facts(markers = setOf("magisk"))).kind)
    }

    @Test
    fun `ksu with a loaded kernel module is lkm`() {
        val result = RootProbe.classify(
            facts(markers = setOf("ksu"), modules = "kernelsu 12345 0 - Live 0x0")
        )
        assertEquals(RootKind.KERNELSU_LKM, result.kind)
    }

    @Test
    fun `ksu without a kernel module is gki or next`() {
        assertEquals(RootKind.KERNELSU, RootProbe.classify(facts(markers = setOf("ksu"))).kind)
    }

    @Test
    fun `apatch marker`() {
        assertEquals(RootKind.APATCH, RootProbe.classify(facts(markers = setOf("ap"))).kind)
    }

    @Test
    fun `a legacy xbin su without adb markers is supersu`() {
        val result = RootProbe.classify(facts(suCommand = "/system/xbin/su", suExists = true))
        assertEquals(RootKind.SUPERSU, result.kind)
    }

    @Test
    fun `an absolute su we cannot name stays unknown rather than guessed`() {
        val result = RootProbe.classify(facts(suCommand = "/sbin/su", suExists = true))
        assertEquals(RootKind.UNKNOWN, result.kind)
    }

    @Test
    fun `shizuku only`() {
        assertEquals(RootKind.SHIZUKU, RootProbe.classify(facts(shizuku = true, uid = 2000)).kind)
    }

    @Test
    fun `nothing at all`() {
        assertEquals(RootKind.NONE, RootProbe.classify(facts()).kind)
    }

    @Test
    fun `route is root only when granted and an absolute su exists`() {
        val result = RootProbe.classify(
            facts(suCommand = "/data/adb/ksu/bin/su", suExists = true, rootAllowed = true)
        )
        assertEquals(Route.ROOT, result.route)
    }

    @Test
    fun `route is shizuku when su exists but root was not granted`() {
        val result = RootProbe.classify(
            facts(suCommand = "/sbin/su", suExists = true, rootAllowed = false, shizuku = true, uid = 0)
        )
        assertEquals(Route.SHIZUKU, result.route)
    }

    @Test
    fun `route is shizuku when only the shizuku binder is up`() {
        val result = RootProbe.classify(facts(suCommand = "su", suExists = false, shizuku = true, uid = 2000))
        assertEquals(Route.SHIZUKU, result.route)
    }

    @Test
    fun `route is the module log when nothing elevated is available`() {
        assertEquals(Route.MODULE_LOG, RootProbe.classify(facts()).route)
    }

    @Test
    fun `the detail line names the kind and the route`() {
        val detail = RootProbe.classify(
            facts(suCommand = "/data/adb/ap/bin/su", suExists = true, markers = setOf("ap"), rootAllowed = true)
        ).detail
        assertTrue(detail, detail.contains("apatch"))
        assertTrue(detail, detail.contains("route=root"))
    }
}
