package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The health decision is what tells apart the three "no report" lives a user can be in: never injected,
 * updated-but-not-restarted, and loaded-but-unable-to-report (the One UI 8 provider case). It is a pure
 * function so the rule is pinned off the phone — a wrong answer here hides the recovery button.
 */
class ModuleHealthTest {

    @Test
    fun `never loaded when there is no load stamp`() {
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(0L, 1_000L, true))
        assertEquals(ModuleState.NEVER_LOADED, ModuleHealthCheck.decide(0L, 0L, false))
    }

    @Test
    fun `needs a restart when the apk is newer than the last load`() {
        assertEquals(ModuleState.NEEDS_RESTART, ModuleHealthCheck.decide(100L, 200L, false))
        assertEquals(ModuleState.NEEDS_RESTART, ModuleHealthCheck.decide(100L, 200L, true))
    }

    @Test
    fun `not reporting when loaded after the update but no status arrived`() {
        assertEquals(ModuleState.NOT_REPORTING, ModuleHealthCheck.decide(200L, 100L, true))
    }

    @Test
    fun `ok when loaded after the update and reporting`() {
        assertEquals(ModuleState.OK, ModuleHealthCheck.decide(200L, 100L, false))
    }
}
