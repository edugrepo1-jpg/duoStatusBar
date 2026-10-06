package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.fx.SensorRetryGate
import org.junit.Assert.*
import org.junit.Test

class TelemetryAuditTest {
    private var time=0L
    private fun ledger()=TelemetryLedger { time }
    private fun line(ledger:TelemetryLedger,feature:String)=ledger.report().lines().single { it.startsWith("feature.$feature ") }
    private fun battery(at:Long,level:Int?,charging:Boolean?)=TelemetryLedger.BatteryReading(at,level,charging,320,4000,null)

    @Test fun `enabled without observed evidence remains unverified`() {
        val ledger=ledger();ledger.configure(TelemetryFeature.CAMERA,true)
        assertTrue(line(ledger,"camera").contains("health=unverified"))
        assertTrue(line(ledger,"camera").contains("detected=unknown"))
        ledger.detect(TelemetryFeature.CAMERA,false)
        assertTrue("fallback false without a successful reader is not certification",line(ledger,"camera").contains("health=unverified"))
    }
    @Test fun `reader success and detection do not fabricate a successful draw`() {
        val ledger=ledger();ledger.configure(TelemetryFeature.BLUETOOTH,true)
        ledger.probe(TelemetryFeature.BLUETOOTH,true);ledger.detect(TelemetryFeature.BLUETOOTH,true)
        assertTrue(line(ledger,"bluetooth").contains("health=unverified"))
        ledger.executed(TelemetryFeature.BLUETOOTH)
        assertTrue(line(ledger,"bluetooth").contains("health=execution_observed"))
        ledger.detect(TelemetryFeature.BLUETOOTH,false)
        assertTrue(line(ledger,"bluetooth").contains("health=inactive"))
        ledger.configure(TelemetryFeature.BLUETOOTH,false)
        assertTrue(line(ledger,"bluetooth").contains("health=disabled"))
    }
    @Test fun `reader failure is explicit and a later reader recovery clears current failure health`() {
        val ledger=ledger();ledger.configure(TelemetryFeature.RECORD,true)
        ledger.probe(TelemetryFeature.RECORD,false);ledger.detect(TelemetryFeature.RECORD,false)
        assertTrue(line(ledger,"record").contains("health=failed_reader"))
        ledger.probe(TelemetryFeature.RECORD,true)
        assertTrue(line(ledger,"record").contains("health=inactive"))
        assertTrue("history remains",line(ledger,"record").contains("failures=1"))
    }
    @Test fun `lifecycle accounts visible hidden and stopped time without a wakeup timer`() {
        val ledger=ledger();time=100;ledger.lifecycle(true,true,false)
        time=1100;ledger.lifecycle(true,false,false)
        time=3100;ledger.lifecycle(false,false,false)
        time=3600
        assertTrue(ledger.report().contains("visibleMs=1000 pausedMs=2000 stoppedMs=600"))
    }
    @Test fun `coarse battery estimate waits ten minutes and does not attribute device drain to module`() {
        val ledger=ledger();ledger.battery(battery(0,80,false));ledger.battery(battery(120000,79,false))
        assertTrue(ledger.report().contains("observedDeviceDrainPercentPerHour=unverified"))
        ledger.battery(battery(600000,78,false));time=600000
        assertTrue(ledger.report().contains("observedDeviceDrainPercentPerHour=12.0"))
        assertTrue(ledger.report().contains("module-specific mAh/CPU attribution=unavailable"))
        assertTrue(ledger.report().contains("scope=whole device observation"))
    }
    @Test fun `plug transition resets battery segment even inside sample throttle`() {
        val ledger=ledger();ledger.battery(battery(0,80,false));ledger.battery(battery(600000,78,false))
        ledger.battery(battery(600001,78,true));time=600001
        assertTrue(ledger.report().contains("battery.samples=3 segments=2"))
        assertTrue(ledger.report().contains("battery.segmentMs=0"))
        assertTrue(ledger.report().contains("observedDeviceDrainPercentPerHour=unverified"))
    }
    @Test fun `battery reversal or clock reset cannot fabricate negative drain`() {
        val ledger=ledger();ledger.battery(battery(100000,50,false));ledger.battery(battery(800000,51,false));time=800000
        assertTrue(ledger.report().contains("battery.segmentMs=0"))
        ledger.battery(battery(0,50,true));time=0
        assertFalse(ledger.report().contains("sampleAgeMs=-"))
        assertTrue(ledger.report().contains("observedDeviceDrainPercentPerHour=unverified"))
    }
    @Test fun `one minute broadcast throttle bounds battery records without losing plug changes`() {
        val ledger=ledger()
        for(at in 0L until 60000 step 1000)ledger.battery(battery(at,80,false))
        assertTrue(ledger.report().contains("battery.samples=1 segments=1"))
        ledger.battery(battery(60000,80,false))
        assertTrue(ledger.report().contains("battery.samples=2 segments=1"))
    }
    @Test fun `unknown battery measurements stay unknown`() {
        val ledger=ledger();ledger.battery(TelemetryLedger.BatteryReading(0,null,null,null,null,null))
        assertTrue(ledger.report().contains("battery.level=unknown charging=unknown temperatureDeciC=unknown voltageMv=unknown"))
        assertTrue(ledger.report().contains("battery.segmentDeltaPercent=unknown"))
    }
    @Test fun `fixed counters stay atomic under simultaneous render and reader threads`() {
        val ledger=ledger()
        val threads=List(6) { Thread {repeat(10000){ledger.increment(TelemetryCounter.CANVAS_DRAW)}} }
        threads.forEach {it.start()};threads.forEach {it.join()}
        assertEquals(60000L,ledger.count(TelemetryCounter.CANVAS_DRAW))
        assertTrue(ledger.report().length<16000)
    }
    @Test fun `replaceable diagnostic block preserves raw failure evidence without duplication`() {
        val ledger=ledger()
        var report="header\nFAILED reader\nfooter"
        repeat(20){report=ledger.report()+"\n"+TelemetryLedger.strip(report)}
        assertEquals(1,report.split(TelemetryLedger.MARKER).size-1)
        assertEquals("header\nFAILED reader\nfooter",TelemetryLedger.strip(report))
    }
    @Test fun `missing compass never retries at animation frequency and can recover after cooldown`() {
        val gate=SensorRetryGate();assertTrue(gate.begin(0));gate.complete(false)
        for(at in 1L until 30000 step 16)assertFalse(gate.begin(at))
        assertTrue(gate.begin(30000));gate.complete(true)
        assertTrue("successful sensor can be selected again after normal stop",gate.begin(30001))
        gate.failedAt(30002);assertFalse(gate.begin(60001));assertTrue(gate.begin(60002))
    }
}
