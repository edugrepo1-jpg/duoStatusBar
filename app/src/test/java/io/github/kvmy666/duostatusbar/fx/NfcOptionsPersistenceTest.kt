package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class NfcOptionsPersistenceTest {
    @Test fun `old configurations and enable all do not activate experimental particles`() {
        assertFalse(ExperienceOptions.decode("{}").nfcParticles)
        assertFalse(ExperienceOptions.decode("{invalid").nfcParticles)
        assertFalse(ExperienceOptions.ALL.nfcParticles)
    }
    @Test fun `particle toggle round trip preserves existing effects timing and battery palette`() {
        val before=ExperienceOptions.ALL.copy(dwellMs=17000,exitMs=210,entryMs=240,iconPercent=160,
            iconSeconds=mapOf(SlotIcon.WIFI.name to 11),customBatteryColors=true,
            batteryLow=0xFF123456.toInt(),batteryMid=0xFF345678.toInt(),batteryHigh=0xFF56789A.toInt(),
            language="es",aurora=AuroraOptions.ALL.copy(timerMinutes=23))
        val activated=ExperienceOptions.decode(before.copy(nfcParticles=true).encode())
        assertTrue(activated.nfcParticles)
        assertEquals(before,activated.copy(nfcParticles=false))
        assertEquals(before,ExperienceOptions.decode(activated.copy(nfcParticles=false).encode()))
    }
}
