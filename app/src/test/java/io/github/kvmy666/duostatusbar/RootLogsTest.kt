package io.github.kvmy666.duostatusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FR-03/NFR-06: a root shell that is named but does not exist is why locked-bootloader reports came
 * back empty. The discovery must always yield something executable-looking, or the legacy `"su"`
 * lookup as the last resort — and the grant/parse rules must be decidable without a device.
 */
class RootLogsTest {
    @Test fun `request shares one deadline across candidates`() {
        var clock = 0L
        val budgets = mutableListOf<Long>()
        val result = RootLogs.probeCandidates(listOf("a", "b", "c"), 12_000, { clock }) { _, budget ->
            budgets += budget
            clock += 6_000
            "root log collection failed"
        }
        assertEquals(listOf(12_000L, 6_000L), budgets)
        assertEquals(RootLogs.Access.TIMEOUT, result.access)
    }
    @Test fun `restricted uid profile does not try another route to bypass it`() {
        var calls = 0
        val result = RootLogs.probeCandidates(listOf("a", "b"), 12_000, { 0 }) { _, _ ->
            calls++; "uid=2000(shell) gid=2000(shell)"
        }
        assertEquals(1, calls)
        assertEquals(RootLogs.Access.NON_ROOT_PROFILE, result.access)
    }
    @Test fun `successful virtual route is retained after a missing executable`() {
        val result = RootLogs.probeCandidates(listOf("missing", "virtual"), 12_000, { 0 }) { name, _ ->
            if (name == "virtual") "uid=0(root) gid=0(root)" else "root log collection failed"
        }
        assertEquals(RootLogs.Access.GRANTED, result.access)
        assertEquals("virtual", result.candidate)
    }
    @Test fun `timeout does not open another pending authorization`() {
        var calls = 0
        val result = RootLogs.probeCandidates(listOf("a", "b"), 12_000, { 0 }) { _, _ ->
            calls++; "root log collection failed: timed out"
        }
        assertEquals(1, calls)
        assertEquals(RootLogs.Access.TIMEOUT, result.access)
    }
    @Test fun `virtual su is attempted even when file metadata cannot see it`() {
        assertEquals(listOf("/system/bin/su","su"),RootLogs.orderedCandidates(null,emptyList()))
    }
    @Test fun `verified root route wins and duplicate candidates are removed`() {
        assertEquals(listOf("su","/sbin/su","/system/bin/su"),RootLogs.orderedCandidates("su",listOf("/sbin/su","/system/bin/su")))
    }


    @Test
    fun `the discovered root shell is non-blank and exists when it is an absolute path`() {
        val su = RootLogs.findSuBinary()
        assertTrue("must always name a command", su.isNotBlank())
        assertTrue("a chosen path must be real, 'su' is the PATH fallback", su == "su" || File(su).exists())
    }

    @Test
    fun `an existing absolute path is preferred over the PATH fallback`() {
        val picked = RootLogs.firstExisting(listOf("/a/su", "/b/su", "su")) { it == "/b/su" }
        assertEquals("/b/su", picked)
    }

    @Test
    fun `the first existing absolute path in order wins`() {
        val picked = RootLogs.firstExisting(listOf("/a/su", "/b/su", "su")) { it == "/a/su" || it == "/b/su" }
        assertEquals("/a/su", picked)
    }

    @Test
    fun `no existing absolute path falls back to plain su`() {
        assertEquals("su", RootLogs.firstExisting(listOf("/a/su", "su")) { false })
    }

    @Test
    fun `the su sentinel is never treated as an absolute candidate`() {
        // Even if something claims "su" exists as a file, it must stay the PATH fallback, not a path.
        assertEquals("su", RootLogs.firstExisting(listOf("su")) { true })
    }

    @Test
    fun `looksRooted accepts the id output of a root shell`() {
        assertTrue(RootLogs.looksRooted("uid=0(root) gid=0(root) context=u:r:magisk:s0"))
    }

    @Test
    fun `looksRooted accepts a bare root uid`() {
        assertTrue(RootLogs.looksRooted("0"))
    }

    @Test
    fun `looksRooted rejects a shell uid`() {
        assertFalse(RootLogs.looksRooted("uid=2000(shell) gid=2000(shell)"))
    }

    @Test
    fun `looksRooted rejects a capture failure`() {
        assertFalse(RootLogs.looksRooted("root log collection failed: IOException: Cannot run program \"su\""))
    }

    @Test
    fun `section extracts only its own block`() {
        val text = "=== root framework ===\nmagisk\nksu\n=== kernel modules ===\nkernelsu 1\n"
        assertEquals("magisk\nksu", RootLogs.section(text, "root framework"))
        assertEquals("kernelsu 1", RootLogs.section(text, "kernel modules"))
    }

    @Test
    fun `section of a missing block is empty`() {
        assertEquals("", RootLogs.section("=== a ===\n1\n", "b"))
    }
}
