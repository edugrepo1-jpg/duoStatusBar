package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import io.github.kvmy666.duostatusbar.hook.CanvasMotion
import kotlin.math.*

/** Paints only in the ring's local coordinate system; never changes the window or stock icons. */
internal class EffectPainter {
    private val icons = StatusIconPainter()
    fun icon(canvas:Canvas,icon:SlotIcon,fg:Int,opacity:Float,frame:EffectFrame)=icons.draw(canvas,icon,fg,opacity,frame)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val trace = Path()
    private val measure = PathMeasure()
    private val clip = Path()
    private val bounds = RectF()
    private val gradient = SweepGradient(0f, 0f, intArrayOf(0x003DDC84, 0xFFD2FFE2.toInt(), 0x003DDC84), floatArrayOf(0f, .65f, 1f))
    private val rotation = Matrix()
    private val energyGradient=LinearGradient(-55f,0f,55f,0f,0xFF30D158.toInt(),0xFFD2FFE2.toInt(),Shader.TileMode.CLAMP)
    fun tint(base: Int, frame: EffectFrame): Int {
        var color = CanvasMotion.blend(base, 0xFF3DDC84.toInt(), EffectTimeline.unlockColor(frame.checkMs))
        if(frame.charging)color=CanvasMotion.blend(color,0xFF8CFFBC.toInt(),if(frame.motionEnabled).16f+.14f*sin(frame.motionMs/500f) else .16f)
        color = CanvasMotion.blend(color, if (frame.pulseRed) 0xFFFF8A80.toInt() else 0xFFFFB300.toInt(), EffectTimeline.pulse(frame.pulseMs))
        return color
    }
    fun foreground(canvas: Canvas, frame: EffectFrame, fg: Int, thickPercent: Int, opacity: Float, ring: Boolean, indicators: Boolean, leftArc:Float, rightArc:Float) {
        val save = canvas.save()
        clip.reset(); clip.addCircle(0f, 0f, 55.5f, Path.Direction.CW); canvas.clipPath(clip)
        try {
            if(ring&&frame.musicPlaying&&frame.musicProgress>=0) {
                paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.8f
                paint.color=fg
                paint.alpha=(200*opacity).toInt();val musicRadius=minOf(43f,55.5f-8f*thickPercent.coerceIn(1,300)/100f-3f)
                bounds.set(-musicRadius,-musicRadius,musicRadius,musicRadius)
                canvas.drawArc(bounds,150f,240f*frame.musicProgress,false,paint)
            }
            if (ring && (frame.chargeMs >= 0 || frame.charging)) {
                val thick = 8f * thickPercent.coerceIn(1,300) / 100f
                if(frame.aurora.chargeStyles) {
                    drawChargeStyle(canvas,frame,opacity,leftArc,rightArc,thick)
                } else {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = minOf(4f, thick * .85f)
                rotation.setRotate(if(frame.motionEnabled)frame.motionMs * .18f else 0f); gradient.setLocalMatrix(rotation); paint.shader = gradient
                paint.alpha = ((if (frame.chargeMs >= 0) 220f * minOf(1f, frame.chargeMs / 180f, (3000 - frame.chargeMs) / 300f) else 210f) * opacity).toInt().coerceIn(0, 255)
                val radius = (111f - thick) / 2f
                bounds.set(-radius,-radius,radius,radius)
                val geometry = io.github.kvmy666.duostatusbar.hook.RingGeometry
                // Exactly the same arc windows as the visible ring, including percent/no-percent modes.
                canvas.drawArc(bounds,270f + geometry.LEFT_START * 360f,leftArc * 360f,false,paint)
                canvas.drawArc(bounds,270f + geometry.RIGHT_START * 360f,rightArc * 360f,false,paint)
                paint.shader = null
                }
            }
            if (ring && frame.criticalDot) { paint.style = Paint.Style.FILL; paint.color = 0xFFFFB300.toInt(); paint.alpha = (230 * opacity).toInt(); canvas.drawCircle(0f, 32f, 2f, paint) }
            if (ring && frame.checkMs >= 0) drawCheck(canvas, frame.checkMs, thickPercent, opacity)
            if(indicators&&AuroraRender.headphone(frame)&&frame.checkMs<0) {
                val age=frame.headphoneConnectMs
                icons.draw(canvas,SlotIcon.AIRPODS,fg,opacity*AuroraRender.headphoneOpacity(frame),
                    frame.copy(drawIcons=true,slot=SlotFrame(SlotIcon.AIRPODS,reveal=EffectTimeline.smooth(age/450f))))
            } else {
            val returnOpacity=AuroraRender.headphoneReturnOpacity(frame)
            if (indicators && frame.audioMs >= 0) drawAudio(canvas, frame, fg, opacity * EffectTimeline.checkNormal(frame.checkMs)*returnOpacity)
            if (indicators && frame.slot.icon != null) drawSlot(canvas, frame.slot, fg,
                opacity * EffectTimeline.checkNormal(frame.checkMs) * EffectTimeline.audioNormal(frame.audioMs)*returnOpacity, frame)
            }
        } finally { canvas.restoreToCount(save); paint.shader = null; paint.maskFilter = null }
    }
    private fun drawChargeStyle(canvas:Canvas,frame:EffectFrame,opacity:Float,left:Float,right:Float,thick:Float) {
        paint.shader=null;paint.style=Paint.Style.STROKE;paint.strokeWidth=minOf(3f,thick*.75f)
        paint.color=0xFF71E3B1.toInt()
        if(frame.wirelessCharging) {
            // Concentric waves contract toward the centre, wholly inside the battery geometry.
            for(i in 0..2) {
                val phase=if(frame.motionEnabled)((frame.motionMs%1200)/1200f+i/3f)%1f else (i+1)/4f
                val radius=(55.5f-thick-4f)*(1f-phase)
                paint.alpha=(opacity*160f*(1f-phase)).toInt().coerceIn(0,255)
                canvas.drawCircle(0f,0f,radius.coerceAtLeast(1f),paint)
            }
        } else {
            // One bright packet traverses the visible left/right arc windows without closing the top gap.
            val total=left+right
            if(total<=0f)return
            val phase=if(frame.motionEnabled)(frame.motionMs%1400)/1400f else .5f
            val start=total*phase;val extent=minOf(.055f,total)
            val radius=(111f-thick)/2f;bounds.set(-radius,-radius,radius,radius)
            paint.shader=energyGradient;paint.alpha=(230*opacity).toInt().coerceIn(0,255)
            val geometry=io.github.kvmy666.duostatusbar.hook.RingGeometry
            if(start<left)chargePacket(canvas,270f+(geometry.LEFT_START+start)*360f,minOf(extent,left-start)*360f,opacity)
            val rightStart=(start-left).coerceAtLeast(0f)
            val rightExtent=if(start<left)(extent-(left-start)).coerceAtLeast(0f) else extent
            if(rightExtent>0&&rightStart<right)chargePacket(canvas,270f+(geometry.RIGHT_START+rightStart)*360f,minOf(rightExtent,right-rightStart)*360f,opacity)
            paint.shader=null
        }
    }
    private fun chargePacket(canvas:Canvas,start:Float,sweep:Float,opacity:Float) {
        val width=paint.strokeWidth
        for(layer in 2 downTo 1) {
            paint.strokeWidth=width+layer*2f;paint.alpha=(opacity*22f/layer).toInt().coerceIn(0,255)
            canvas.drawArc(bounds,start,sweep,false,paint)
        }
        paint.strokeWidth=width;paint.alpha=(230*opacity).toInt().coerceIn(0,255)
        canvas.drawArc(bounds,start,sweep,false,paint)
    }
    private fun drawCheck(canvas: Canvas, age: Long, thick: Int, opacity: Float) {
        val amount = EffectTimeline.check(age)
        if (amount <= 0) return
        val inner = 55.5f - 8f * thick.coerceIn(1, 300) / 100f
        val k = minOf(1f, inner * 1.25f / 55.2f)
        val size = 55.2f * k
        val scale = (.8f + .2f * EffectTimeline.smooth((age - 120) / 160f)) * (1 - .15f * EffectTimeline.smooth((age - EffectTimeline.CHECK_FADE_OUT_AT) / (EffectTimeline.CHECK_MS-EffectTimeline.CHECK_FADE_OUT_AT).toFloat()))
        val save = canvas.save(); canvas.translate(0f, 1.5f); canvas.scale(scale, scale)
        path.reset(); path.moveTo(-.42f * size, .04f * size); path.lineTo(-.12f * size, .34f * size); path.lineTo(.44f * size, -.34f * size)
        measure.setPath(path, false); trace.reset()
        val t = ((age - 120) / 160f).coerceIn(0f, 1f); measure.getSegment(0f, measure.length * (1 - (1 - t).pow(3)), trace, true)
        paint.style = Paint.Style.STROKE
        // Concentric soft strokes remain inside the clipping circle; hardware Canvas supports these on every target.
        for (layer in 3 downTo 0) {
            paint.strokeWidth = 6.5f * k * (1f + .25f * layer)
            paint.color = 0xFF3DDC84.toInt(); paint.alpha = ((if (layer == 0) 255 else 12) * amount * opacity).toInt()
            canvas.drawPath(trace, paint)
        }
        paint.strokeWidth = 6.5f * k * .3f; paint.color = 0xFFB9F6CA.toInt(); paint.alpha = (150 * amount * opacity).toInt(); canvas.drawPath(trace, paint)
        canvas.restoreToCount(save)
    }
    private fun drawSlot(canvas: Canvas, slot: SlotFrame, fg: Int, opacity: Float, frame: EffectFrame) {
        val icon=slot.icon ?: return
        if(icon==SlotIcon.WIFI)return // Strength-aware Wi-Fi is owned by DuoCanvasView.
        val save=canvas.save()
        val scale=if(frame.spring) .8f+.2f*EffectTimeline.spring((slot.scale-.8f)/.2f) else slot.scale
        canvas.scale(scale,scale)
        icons.draw(canvas,icon,fg,opacity*slot.opacity,frame)
        canvas.restoreToCount(save)
    }
    private fun drawAudio(canvas: Canvas, frame: EffectFrame, fg: Int, opacity: Float) {
        icons.draw(canvas,SlotIcon.AIRPODS,fg,EffectTimeline.audio(frame.audioMs)*opacity,frame)
    }
}
