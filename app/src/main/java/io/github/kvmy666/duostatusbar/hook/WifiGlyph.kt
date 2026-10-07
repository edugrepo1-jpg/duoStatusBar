package io.github.kvmy666.duostatusbar.hook

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** One geometry for connected Wi-Fi, offline Wi-Fi, preview and island. */
internal class WifiGlyph {
    private val bounds=RectF()
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND}
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas:Canvas,color:Int,opacity:Float,outer:Float=1f,middle:Float=1f,dot:Float=1f,reveal:Float=1f) {
        fun tint(amount:Float)=(color and 0xffffff) or ((((color ushr 24)*opacity*amount).toInt().coerceIn(0,255)) shl 24)
        fun arc(diameter:Float,width:Float,offset:Float,sweep:Float,strength:Float) {
            val r=diameter/2f;bounds.set(-.5f-r,17f-r,-.5f+r,17f+r)
            stroke.strokeWidth=width;stroke.color=tint(strength)
            canvas.drawArc(bounds,270f+offset*360f,sweep*360f*reveal.coerceIn(0f,1f),false,stroke)
        }
        arc(62.2f,7.1f,.88f,.239f,outer)
        arc(36.3f,7f,.882f,.237f,middle)
        val saved=canvas.save();canvas.translate(-.5f,17f);fill.color=tint(dot)
        canvas.drawPath(CanvasPaths.wifiDot,fill);canvas.restoreToCount(saved)
    }
}
