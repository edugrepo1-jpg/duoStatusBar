package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class NfcParticleOptionsTest {
    @Test fun `old preferences and enable all do not silently enable experimental nfc`() {
        assertFalse(ExperienceOptions().nfcParticles)
        assertFalse(ExperienceOptions.decode("{\"v\":5,\"music\":true}").nfcParticles)
        assertFalse(ExperienceOptions.ALL.nfcParticles)
        assertFalse(ExperienceOptions.decode("invalid").nfcParticles)
    }
    @Test fun `nfc option survives roundtrip without changing existing preferences`() {
        val original=ExperienceOptions(music=true,island=true,iconPercent=170,language="es",dwellMs=41000,
            customBatteryColors=true,batteryMid=0xFFABCDEF.toInt(),aurora=AuroraOptions.ALL,nfcParticles=true)
        assertEquals(original,ExperienceOptions.decode(original.encode()))
        assertEquals(original.copy(nfcParticles=false),ExperienceOptions.decode(original.copy(nfcParticles=false).encode()))
    }
}
