package io.github.kvmy666.duostatusbar

import android.content.Intent
import android.os.BatteryManager
import io.github.kvmy666.duostatusbar.fx.Events
import io.github.kvmy666.duostatusbar.fx.Fx
import io.github.kvmy666.duostatusbar.hook.DiagnosticTransport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class TelemetryBoundsTest {
    @Test fun `large repeated failures obey total memory bounds as well as line bounds`() {
        Events.changed=null;Events.clear()
        repeat(100){L.w("bound audit $it "+"x".repeat(5000))}
        assertTrue(L.recentText().length<=128*1024)
        assertTrue(Events.snapshot().length<=96*1024)
        assertTrue(L.recentText().contains("bound audit 99"))
        assertTrue(Events.snapshot().contains("bound audit 99"))
        Events.clear()
    }
    @Test fun `telemetry remains in rootless transport head even when older trees are omitted`() {
        val original="version: audit\n"+"technical tree\n".repeat(20000)
        var dump=Fx.wrapDump(original)
        repeat(3){dump=Fx.wrapDump(dump)}
        val transported=DiagnosticTransport.prepare(dump)
        assertEquals(1,transported.split(TelemetryLedger.MARKER).size-1)
        assertTrue(transported.contains("feature.unlock_check"))
        assertTrue(transported.contains("battery.scope=whole device observation"))
        assertTrue(transported.contains("older diagnostic lines omitted"))
        assertTrue(transported.length<=128000)
    }
    @Test fun `battery input consumes only the existing broadcast and invalid values remain unknown`() {
        TelemetrySampler.battery(Intent("unrelated"))
        TelemetrySampler.battery(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL,900).putExtra(BatteryManager.EXTRA_SCALE,100)
            .putExtra(BatteryManager.EXTRA_TEMPERATURE,9999).putExtra(BatteryManager.EXTRA_VOLTAGE,-2))
        assertTrue(RuntimeTelemetry.report().contains("battery.level=unknown"))
    }
}
