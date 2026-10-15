package io.github.kvmy666.duostatusbar.fx

import android.graphics.*

/** Static optical layers. The compositor supplies backdrop blur; no screenshot or idle animation. */
internal class IslandGlassPainter(private val density:Float) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix=Matrix()
    private val inset=RectF()
    private val base=LinearGradient(0f,0f,.35f,1f,
        intArrayOf(0xFF303743.toInt(),0xFF1A202B.toInt(),0xFF0D1119.toInt()),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
    private val rim=LinearGradient(0f,0f,1f,1f,
        intArrayOf(0x95FFFFFF.toInt(),0x12FFFFFF,0x04FFFFFF,0x42C4DAFF),floatArrayOf(0f,.28f,.62f,1f),Shader.TileMode.CLAMP)
    private val glow=RadialGradient(.16f,0f,.9f,intArrayOf(0x285BAAFF,0x006CAEFF),null,Shader.TileMode.CLAMP)
    fun draw(canvas:Canvas,rect:RectF,radius:Float,opacity:Int=96) {
        matrix.setScale(rect.width().coerceAtLeast(1f),rect.height().coerceAtLeast(1f))
        matrix.postTranslate(rect.left,rect.top)
        paint.style=Paint.Style.FILL;paint.alpha=(opacity.coerceIn(20,100)*255/100)
        base.setLocalMatrix(matrix);paint.shader=base;canvas.drawRoundRect(rect,radius,radius,paint)
        paint.alpha=255;glow.setLocalMatrix(matrix);paint.shader=glow;canvas.drawRoundRect(rect,radius,radius,paint)
        inset.set(rect);inset.inset(.65f*density,.65f*density)
        rim.setLocalMatrix(matrix);paint.shader=rim;paint.style=Paint.Style.STROKE;paint.strokeWidth=.85f*density
        canvas.drawRoundRect(inset,(radius-.65f*density).coerceAtLeast(0f),(radius-.65f*density).coerceAtLeast(0f),paint)
        paint.shader=null;paint.style=Paint.Style.FILL
    }
    fun surface(canvas:Canvas,rect:RectF,radius:Float,selected:Boolean=false,pressed:Boolean=false) {
        paint.shader=null;paint.style=Paint.Style.FILL
        paint.color=when {pressed->0x387DBAFF;selected->0xDA1877D4.toInt();else->0x12FFFFFF}
        canvas.drawRoundRect(rect,radius,radius,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.65f*density;paint.color=if(selected)0x558BD5FF else 0x20FFFFFF
        inset.set(rect);inset.inset(.4f*density,.4f*density)
        canvas.drawRoundRect(inset,radius,radius,paint);paint.style=Paint.Style.FILL
    }
}
