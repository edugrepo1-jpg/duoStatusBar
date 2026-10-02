package io.github.kvmy666.duostatusbar

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NFR-06: the SELinux and ABI fields must always be populated, on any device, without throwing —
 * they are what tells a report whether native Rive could even load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceFactsTest {

    @Test
    fun `selinux is one of the three known values`() {
        assertTrue(
            DeviceFacts.selinux() in setOf("enforcing", "permissive", "unknown")
        )
    }

    @Test
    fun `abi is never blank`() {
        assertTrue(DeviceFacts.abi().isNotBlank())
    }
}
