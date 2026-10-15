package io.github.kvmy666.duostatusbar.fx

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.sin

/**
 * Original, transparent header particle field. It does not detect NFC or confirm a payment.
 * The host owns the clock, event eligibility and cancellation. No callbacks, services, bitmaps,
 * shaders or temporary collections are created by draw(). All randomness is fixed at construction.
 */
internal class NfcParticlePainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL}
    private val x=FloatArray(COUNT)
    private val y=FloatArray(COUNT)
    private val radius=FloatArray(COUNT)
    private val phase=IntArray(COUNT)
    private val opacity=FloatArray(COUNT)
    private val waves=FloatArray(1024) {sin(it*2.0*PI/1024.0).toFloat()}
    init {
        var seed=0x44554F31
        fun random():Float {
            seed=seed*1664525+1013904223
            return (seed ushr 8)/16777216f
        }
        for(i in 0 until COUNT) {
            x[i]=random();y[i]=random();radius[i]=.38f+random()*.53f
            phase[i]=(random()*1024).toInt();opacity[i]=.35f+random()*.65f
        }
    }

    /**
     * Draw in a full-width transparent window. heightPx includes the permitted spill below the
     * status bar; statusBarHeightPx attenuates particles behind clock/system icons for legibility.
     * Returns true only while this finite effect has time remaining. Reduced motion keeps every
     * point spatially stationary and removes twinkle; only a gentle opacity envelope remains.
     */
    fun draw(canvas:Canvas,widthPx:Float,heightPx:Float,elapsedMs:Long,alpha:Float=1f,
        reducedMotion:Boolean=false,statusBarHeightPx:Float=0f,tint:Int=0):Boolean {
        if(elapsedMs<0L||elapsedMs>=DURATION_MS||!widthPx.isFinite()||!heightPx.isFinite()||widthPx<=0f||heightPx<=0f||!alpha.isFinite()||alpha<=0f)return false
        val inTime=if(reducedMotion)200f else 340f
        val outTime=if(reducedMotion)400f else 650f
        val enter=smooth((elapsedMs/inTime).coerceIn(0f,1f))
        val leave=smooth(((DURATION_MS-elapsedMs)/outTime).coerceIn(0f,1f))
        val envelope=enter*leave*alpha.coerceAtMost(1f)
        if(envelope<=0f)return true
        val tick=if(reducedMotion)0 else (elapsedMs*1024/5400).toInt()
        val density=(heightPx/116f).coerceIn(.5f,4f)
        val barHeight=if(statusBarHeightPx.isFinite())statusBarHeightPx.coerceIn(0f,heightPx)else 0f
        val saved=canvas.save()
        canvas.clipRect(0f,0f,widthPx,heightPx)
        for(i in 0 until COUNT) {
            val p=phase[i]
            val horizontal=if(reducedMotion)0f else .026f*(waves[(p+tick) and 1023]-waves[p])+.017f*waves[((y[i]*1024).toInt()+tick*2) and 1023]
            var px=x[i]+horizontal
            if(px<0f)px+=1f else if(px>1f)px-=1f
            // Broad waves carry the field; smaller out-of-phase eddies keep it from reading as rain.
            val vertical=if(reducedMotion)0f else .062f*waves[((x[i]*2048).toInt()+tick+p/4) and 1023]+.028f*waves[(p-tick*2) and 1023]
            var py=y[i]+vertical
            if(py<0f)py=-py else if(py>1f)py=2f-py
            val falloff=1f-smooth(((py-.60f)/.40f).coerceIn(0f,1f))
            val topFade=.42f+.58f*smooth((py/.12f).coerceIn(0f,1f))
            val pixelY=py*heightPx
            val textProtection=if(pixelY<barHeight).35f else 1f
            val twinkle=if(reducedMotion)1f else .72f+.28f*waves[(p+tick*3) and 1023]
            val strength=(envelope*opacity[i]*falloff*topFade*textProtection*twinkle).coerceIn(0f,1f)
            if(strength<.015f)continue
            val r=radius[i]*density
            val pixelX=px*widthPx
            if(i%7==0) {
                paint.color=if(tint!=0)tint else 0xFF237CFF.toInt();paint.alpha=(strength*28).toInt()
                canvas.drawCircle(pixelX,pixelY,r*2.7f,paint)
            }
            paint.color=if(tint!=0)tint else if(i%5==0)0xFF8CDFFF.toInt()else if(i%3==0)0xFF358CFF.toInt()else 0xFF4DB8FF.toInt()
            paint.alpha=(strength*218).toInt()
            canvas.drawCircle(pixelX,pixelY,r,paint)
        }
        canvas.restoreToCount(saved)
        return true
    }
    private fun smooth(t:Float)=t*t*(3f-2f*t)
    companion object {
        const val DURATION_MS=3000L
        const val FRAME_DELAY_MS=33L
        private const val COUNT=760
    }
}
