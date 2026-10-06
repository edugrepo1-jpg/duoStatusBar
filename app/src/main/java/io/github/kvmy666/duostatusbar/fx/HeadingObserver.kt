package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.TelemetryCounter
import io.github.kvmy666.duostatusbar.TelemetryFeature

/** Magnetic north in current display coordinates; UI-rate sensor, shortest-arc smoothing. */
internal class HeadingObserver(private val context:Context,private val handler:Handler,private val changed:(Float)->Unit):SensorEventListener {
    private val manager by lazy { context.getSystemService(SensorManager::class.java) }
    private var running=false
    private var failed=false
    private var heading=Float.NaN
    private var lastSample=-1000L
    private val retryGate=SensorRetryGate()
    private val rotation=FloatArray(9)
    private val remapped=FloatArray(9)
    private val orientation=FloatArray(3)
    fun start() {
        if(running)return
        if(!retryGate.begin(SystemClock.uptimeMillis()))return
        try {
            // Magnetic heading does not need the gyroscope when a low-power vector exists.
            val sensor=manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
                ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            lastSample=-1000L
            running=sensor!=null&&manager?.registerListener(this,sensor,SAMPLE_MS.toInt()*1000,handler)==true
            RuntimeTelemetry.probe(TelemetryFeature.COMPASS,running)
            retryGate.complete(running)
            if(running)RuntimeTelemetry.increment(TelemetryCounter.SENSOR_START)
            if(!running&&!failed){failed=true;L.w("Bússola: sensor de orientação indisponível; ícone mantém posição fixa")}
        } catch(t:Throwable) { RuntimeTelemetry.probe(TelemetryFeature.COMPASS,false);retryGate.complete(false);if(!failed){failed=true;L.w("Bússola: ${t.javaClass.simpleName}")};stop() }
    }
    fun stop(){if(running){RuntimeTelemetry.increment(TelemetryCounter.SENSOR_STOP);runCatching {manager?.unregisterListener(this)}};running=false;lastSample=-1000L}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun onSensorChanged(event:SensorEvent) {
        if(!running)return
        val now=SystemClock.uptimeMillis()
        if(!sampleDue(lastSample,now))return
        lastSample=now
        RuntimeTelemetry.increment(TelemetryCounter.SENSOR_SAMPLE)
        try {
            SensorManager.getRotationMatrixFromVector(rotation,event.values)
            val display=runCatching { context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation }.getOrNull() ?: Surface.ROTATION_0
            val axes=when(display) {
                Surface.ROTATION_90->SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180->SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270->SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else->SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(rotation,axes.first,axes.second,remapped)
            SensorManager.getOrientation(remapped,orientation)
            val target=((Math.toDegrees(orientation[0].toDouble()).toFloat()+360)%360)
            if(!target.isFinite())return
            val next=if(heading.isNaN())target else heading+shortestDelta(heading,target)*.22f
            if(heading.isNaN()||kotlin.math.abs(shortestDelta(heading,next))>.5f){heading=(next+360f)%360f;changed(heading);RuntimeTelemetry.executed(TelemetryFeature.COMPASS)}
        } catch(t:Throwable) { RuntimeTelemetry.probe(TelemetryFeature.COMPASS,false);retryGate.failedAt(now);if(!failed){failed=true;L.w("Bússola: leitura indisponível: ${t.javaClass.simpleName}")};stop() }
    }
    companion object {
        const val SAMPLE_MS=100L
        fun sampleDue(previous:Long,now:Long)=now-previous>=SAMPLE_MS
        fun shortestDelta(from:Float,to:Float)=((to-(from%360)+540)%360)-180
    }
}

/** Missing sensors may recover; do not retry a Binder registration on every animation frame. */
internal class SensorRetryGate {
    private var attemptedAt:Long?=null
    private var failed=false
    fun begin(now:Long):Boolean {
        val at=attemptedAt
        if(failed && at!=null && now-at in 0 until RETRY_MS)return false
        attemptedAt=now
        return true
    }
    fun complete(success:Boolean){failed=!success}
    fun failedAt(now:Long){attemptedAt=now;failed=true}
    companion object { const val RETRY_MS=30_000L }
}
