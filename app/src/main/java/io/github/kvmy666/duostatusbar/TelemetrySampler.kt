package io.github.kvmy666.duostatusbar

import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock

/** Consume the existing monitor's broadcasts; never register a receiver or wake up the phone. */
internal object TelemetrySampler {
    fun battery(intent: Intent) {
        if(intent.action != Intent.ACTION_BATTERY_CHANGED)return
        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE,0)
        val percent = if(raw>=0 && scale>0 && raw<=scale)(100L*raw/scale).toInt() else null
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE).takeIf { it in -400..1000 }
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1).takeIf { it in 1..20000 }
        RuntimeTelemetry.battery(TelemetryLedger.BatteryReading(SystemClock.elapsedRealtime(),percent,
            plugged.takeIf { it>=0 }?.let { it!=0 },temperature,voltage,null))
    }
}
