package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.view.Surface
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.L

/** Magnetic north in current display coordinates; UI-rate sensor, shortest-arc smoothing. */
internal class HeadingObserver(private val context:Context,private val handler:Handler,private val changed:(Float)->Unit):SensorEventListener {
    private val manager by lazy { context.getSystemService(SensorManager::class.java) }
    private var running=false
    private var failed=false
    private var heading=Float.NaN
    private val rotation=FloatArray(9)
    private val remapped=FloatArray(9)
    private val orientation=FloatArray(3)
    fun start() {
        if(running)return
        try {
            val sensor=manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            running=sensor!=null&&manager?.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI,handler)==true
            if(!running&&!failed){failed=true;L.w("Bússola: sensor de orientação indisponível; ícone mantém posição fixa")}
        } catch(t:Throwable) { if(!failed){failed=true;L.w("Bússola: ${t.javaClass.simpleName}")};stop() }
    }
    fun stop(){if(running)runCatching {manager?.unregisterListener(this)};running=false}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun onSensorChanged(event:SensorEvent) {
        if(!running)return
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
            if(heading.isNaN()||kotlin.math.abs(next-heading)>.2f){heading=next;changed(next)}
        } catch(t:Throwable) { if(!failed){failed=true;L.w("Bússola: leitura indisponível: ${t.javaClass.simpleName}")} }
    }
    companion object { fun shortestDelta(from:Float,to:Float)=((to-(from%360)+540)%360)-180 }
}
