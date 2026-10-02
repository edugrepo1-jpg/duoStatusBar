package io.github.kvmy666.duostatusbar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FR-03/NFR-06: a root shell that is named but does not exist is why locked-bootloader reports came
 * back empty. The discovery must always yield something executable-looking, or the legacy `"su"`
 * lookup as the last resort.
 */
class RootLogsTest {

    @Test
    fun `the discovered root shell is non-blank and exists when it is an absolute path`() {
        val su = RootLogs.findSuBinary()
        assertTrue("must always name a command", su.isNotBlank())
        assertTrue("a chosen path must be real, 'su' is the PATH fallback", su == "su" || File(su).exists())
    }
}
