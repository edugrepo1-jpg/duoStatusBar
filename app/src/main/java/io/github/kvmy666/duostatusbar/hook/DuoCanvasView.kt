package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.fx.*
import kotlin.math.abs

/** The only drawing backend: ordinary Android Canvas, also used by every app preview. */
internal class DuoCanvasView(context: Context, part: DuoPart = DuoPart.ALL) : View(context), DuoElement {
    override var part: DuoPart = part
        set(value) { field = value; safe("parte") { invalidate() } }
    override val ui: View get() = this
    override val rendererName: String get() = "Canvas"
    override val isReady: Boolean get() = !failed
    var animationsEnabled = true
        set(value) {
            if (field == value) return
            field = value
            safe("animações") {
                if (!value) {
                    revealAt = -1L
                    target?.let { snap(it) }
                    removeCallbacks(tick)
                    pending = false
                }
                invalidate()
            }
        }
    var thickPercent = 100
    var globalPercent = 100
    var arrivalEnabled = true
    var effects = EffectFrame()
        set(value) { val changed=field.hideCells!=value.hideCells;field = value; safe("efeitos") { if(changed){changedAt=SystemClock.uptimeMillis();advance()} else invalidate() } }
    var continuumX = 0f
    var continuumY = 0f
    var lastAlignment = Float.NaN
    private val effectPainter = EffectPainter()
    private val glass = GlassBackdrop(this)
    private var failed = false
    private var active = true
    private var target: DuoVisual? = null
    private var shown: DuoVisual? = null
    private var lastFrame = 0L
    private var changedAt = 0L
    private var revealAt = -1L
    private var revealDuration = 0
    private var visibilityAmount = 1f
    private var boltAmount = 0f
    private var cellVisibility=1f
    private var pending = false
    private val middleSwap=SequentialSwap()
    private val numberBounds=android.graphics.Rect()
    private val middleWeights = FloatArray(5)
    private var oldNetworkText = ""
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bounds = RectF()
    private val roundedBolt = CornerPathEffect(1.4f)
    private val tick = Runnable { pending = false; safe("quadro") { advance() } }

    private inline fun safe(where: String, action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            try { L.w("Canvas $where: ${error.javaClass.simpleName}: ${error.message}") } catch (_: Throwable) { }
        }
    }

    override fun start(): Boolean = !failed
    override fun onReady(action: () -> Unit) { safe("pronto") { if (!failed) action() } }
    override fun onFailed(action: () -> Unit) = Unit
    override fun render(v: DuoVisual) = safe("estado") {
        if (target == v && shown != null) return@safe
        middleSwap.update(v.middleMode,SystemClock.uptimeMillis(),animationsEnabled&&active)
        target = v
        changedAt = SystemClock.uptimeMillis()
        if (v.networkText.isNotBlank()) oldNetworkText = v.networkText
        if (shown == null || !animationsEnabled || !active) snap(v)
        advance()
    }

    private fun snap(v: DuoVisual) {
        shown = v
        visibilityAmount = if (v.visible) 1f else 0f
        boltAmount = if (v.charging) 1f else 0f
        cellVisibility=if(effects.hideCells)0f else 1f
        for (i in middleWeights.indices) middleWeights[i] = if (v.middleMode == i) 1f else 0f
    }

    override fun reveal(ms: Int) = safe("chegada") {
        if (ms <= 0 || !animationsEnabled || !arrivalEnabled || !active) return@safe
        revealAt = SystemClock.uptimeMillis()
        revealDuration = ms.coerceAtLeast(1)
        advance()
    }

    override fun setRenderActive(active: Boolean) = safe("atividade") {
        this.active = active
        if (!active) {
            removeCallbacks(tick); pending = false; revealAt = -1L
        } else {
            lastFrame = 0L
            advance()
        }
    }

    override fun teardown() = safe("encerramento") {
        glass.release()
        active = false; removeCallbacks(tick); pending = false; revealAt = -1L
        target = null; shown = null
    }

    override fun onDetachedFromWindow() {
        glass.release()
        safe("desanexar") { removeCallbacks(tick); pending = false; lastFrame = 0L }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        safe("anexar") { lastFrame = 0L; advance() }
    }

    private fun advance() {
        val v = target ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(0, 64)
        lastFrame = now
        val old = shown ?: v
        val settled = !animationsEnabled || now - changedAt > 1600L
        val colorStep = if (settled) 1f else CanvasMotion.step(dt, 130f)
        val levelStep = if (settled) 1f else CanvasMotion.step(dt, 70f)
        val geometryStep = if (settled) 1f else CanvasMotion.step(dt, 190f)
        fun move(a: Float, b: Float, f: Float) = a + (b - a) * f
        shown = if (settled) v else old.lerp(v, colorStep).copy(
            trimLeftEnd = move(old.trimLeftEnd, v.trimLeftEnd, levelStep),
            trimRightEnd = move(old.trimRightEnd, v.trimRightEnd, levelStep),
            leftArc = move(old.leftArc, v.leftArc, geometryStep),
            rightArc = move(old.rightArc, v.rightArc, geometryStep),
            percentY = move(old.percentY, v.percentY, geometryStep),
            tint = CanvasMotion.blend(old.tint, v.tint, colorStep),
            fgColor = CanvasMotion.blend(old.fgColor, v.fgColor, colorStep),
            charging = v.charging,
            animateCharge = v.animateCharge,
            wifiLevel = v.wifiLevel,
            cellLevel = v.cellLevel,
            visible = v.visible,
            // Disabling the number must clear it in this frame, without a stale tweened label.
            percentText = v.percentText,
            percentOpacity = if (v.percentText.isBlank()) 0f else move(old.percentOpacity, v.percentOpacity, colorStep)
        )
        visibilityAmount = move(visibilityAmount, if (v.visible) 1f else 0f, geometryStep)
        boltAmount = move(boltAmount, if (v.charging) 1f else 0f,
            if (!v.animateCharge) 1f else geometryStep)
        cellVisibility=move(cellVisibility,if(effects.hideCells)0f else 1f,geometryStep)
        val slotPose=middleSwap.frame(now)
        for(i in middleWeights.indices)middleWeights[i]=if(i==slotPose.key)slotPose.opacity else 0f

        if (revealAt >= 0 && now - revealAt >= revealDuration) revealAt = -1
        invalidate()
        // Stop at convergence, and always stop while hidden: no lifetime idle render loop.
        val moving = middleSwap.moving(now) || abs(cellVisibility-if(effects.hideCells)0f else 1f)>.001f || (!settled && shown != v) || revealAt >= 0 ||
            abs(visibilityAmount - if (v.visible) 1f else 0f) > 0.001f
        if (active && moving && !pending && isAttachedToWindow) {
            pending = true; postOnAnimation(tick)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (failed) return
        val checkpoint = canvas.save()
        try {
            drawElement(canvas)
        } catch (error: Throwable) {
            failed = true
            safe("falha no desenho") { L.w("Canvas: ${error.javaClass.simpleName}: ${error.message}") }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun drawElement(canvas: Canvas) {
        val current = shown ?: return
        if (width <= 0 || height <= 0 || visibilityAmount < 0.001f) return
        val mapped = part.apply(current)
        val v = mapped.copy(tint = effectPainter.tint(mapped.tint, effects))
        val scale = minOf(width / 120f, height / 136f)
        canvas.translate(continuumX, continuumY)
        canvas.translate((width - 120f * scale) / 2f, (height - 136f * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.translate(60f, 77.5f)
        val globalScale = globalPercent.coerceIn(0, 100) / 100f
        canvas.scale(globalScale, globalScale)
        val r = if (revealAt < 0) 1f else
            ((SystemClock.uptimeMillis() - revealAt).toFloat() / revealDuration).coerceIn(0f, 1f)
        val visibilityScale = 0.72f + 0.28f * CanvasMotion.overshoot(visibilityAmount)
        val arrivalScale = if (revealAt < 0) 1f else 0.82f + 0.18f * (if (effects.spring) EffectTimeline.spring(r) else CanvasMotion.overshoot(r))
        canvas.scale(visibilityScale * arrivalScale, visibilityScale * arrivalScale)
        val opacity = visibilityAmount * (r * 4).coerceIn(0f, 1f)
        val contraction = 1f - .05f * EffectTimeline.smooth(effects.checkMs / 80f) * (1f - EffectTimeline.smooth((effects.checkMs - 120) / 280f))
        val audioExpansion = if (effects.audioMs < 0) 0f else minOf(EffectTimeline.smooth(effects.audioMs / 200f), EffectTimeline.smooth((4000 - effects.audioMs) / 250f))
        canvas.scale(contraction * (1f + .05f * audioExpansion), contraction)
        if (v.ringOpacity > 0f) {
            glass.draw(canvas, effects.glass, scale * globalScale, opacity)
            effectPainter.background(canvas, v.fgColor, effects, .5f * resources.displayMetrics.density / (scale * globalScale).coerceAtLeast(.01f))
        }
        val f = thickPercent.coerceIn(1, 300) / 100f
        val radius = (111f - 8f * f) / 2f
        bounds.set(-radius, -radius, radius, radius)
        stroke.strokeWidth = 8f * f
        if (v.ringOpacity > 0f) {
            stroke.color = alpha(v.fgColor, opacity * v.trackOpacity * v.ringOpacity)
            arc(canvas, v.leftArc, 270f + RingGeometry.LEFT_START * 360f)
            arc(canvas, v.rightArc, 270f + RingGeometry.RIGHT_START * 360f)
            stroke.color = alpha(v.tint, opacity * v.ringOpacity)
            arc(canvas, v.trimLeftEnd, 270f + RingGeometry.LEFT_START * 360f)
            arc(canvas, v.trimRightEnd, 270f + RingGeometry.RIGHT_START * 360f)
        }
        val slotAlpha = opacity * v.indicatorsOpacity * EffectTimeline.checkNormal(effects.checkMs) * EffectTimeline.audioNormal(effects.audioMs)
        val cycleIcon = effects.slot.icon
        if (slotAlpha > 0f && cycleIcon != null) {
            val saved = canvas.save(); canvas.scale(effects.slot.scale, effects.slot.scale)
            when (cycleIcon) {
                SlotIcon.WIFI, SlotIcon.WIFI_OFFLINE -> drawWifi(canvas, v, slotAlpha * effects.slot.opacity)
                SlotIcon.AIRPLANE -> Unit
                else -> Unit
            }
            canvas.restoreToCount(saved)
        } else if (slotAlpha > 0f && !effects.managedSlots) {
            drawWifi(canvas, v, slotAlpha * middleWeights[DuoMapping.MIDDLE_WIFI])
            effectPainter.icon(canvas,SlotIcon.AIRPLANE,v.fgColor,slotAlpha * middleWeights[DuoMapping.MIDDLE_AIRPLANE],effects)
            glyph(canvas, CanvasPaths.moon, -.5f, .81f, 1f, v.fgColor,
                slotAlpha * middleWeights[DuoMapping.MIDDLE_DND])
            label(canvas, oldNetworkText, -.5f, 1f, 30f, 2f, v.fgColor,
                slotAlpha * middleWeights[DuoMapping.MIDDLE_NETWORK])
        }
        effectPainter.foreground(canvas, if (cycleIcon == null) effects.copy(slot = SlotFrame(null)) else effects,
            v.fgColor, thickPercent, opacity, v.ringOpacity > 0f, v.indicatorsOpacity > 0f,v.leftArc,v.rightArc)
        if (v.ringOpacity <= 0f) return
        val ringAlpha = opacity * v.ringOpacity
        val cells = floatArrayOf(v.cell1Opacity, v.cell2Opacity, v.cell3Opacity, v.cell4Opacity)
        val moons = floatArrayOf(v.moon1Opacity, v.moon2Opacity, v.moon3Opacity, v.moon4Opacity)
        for (i in 0..3) if (cellVisibility>.001f) {
            fill.color = alpha(v.fgColor, ringAlpha * cells[i]*cellVisibility)
            canvas.drawCircle(CELL_X[i], CELL_Y[i], 5.5f*cellVisibility, fill)
            glyph(canvas, CanvasPaths.moon, CELL_X[i], CELL_Y[i], DotMoonMotion.DOT_SCALE * moons[i]*cellVisibility,
                v.fgColor, ringAlpha * moons[i]*cellVisibility)
        }
        glyph(canvas, CanvasPaths.moon, DotMoonMotion.CENTER_X, DotMoonMotion.CENTER_Y,
            DotMoonMotion.DOT_SCALE * v.centerMoonOpacity, v.fgColor, ringAlpha * v.centerMoonOpacity)
        if (boltAmount > .001f) {
            fill.pathEffect = roundedBolt
            glyph(canvas, CanvasPaths.bolt, 0f, -49.5f * boltAmount,
                if (current.animateCharge) CanvasMotion.overshoot(boltAmount) else 1f,
                v.tint, ringAlpha * boltAmount)
            fill.pathEffect = null
        }
        label(canvas, v.percentText.trim(), 0f, v.percentY + RingGeometry.PERCENT_BOX_HALF,
            v.percentFontSize, 2.4f, v.fgColor, ringAlpha * v.percentOpacity)
    }

    private fun arc(canvas: Canvas, fraction: Float, start: Float) {
        if (fraction > 0.00001f) canvas.drawArc(bounds, start, fraction * 360f, false, stroke)
    }

    private fun drawWifi(canvas: Canvas, v: DuoVisual, opacity: Float) {
        if (opacity < .001f) return
        fun wifiArc(d: Float, width: Float, offset: Float, sweep: Float, strength: Float) {
            val r = d / 2
            bounds.set(-.5f - r, 17f - r, -.5f + r, 17f + r)
            stroke.strokeWidth = width
            stroke.color = alpha(v.fgColor, opacity * strength)
            canvas.drawArc(bounds, 270f + offset * 360f, sweep * 360f, false, stroke)
        }
        wifiArc(62.2f, 7.1f, .88f, .239f, v.wifiOuterOpacity)
        wifiArc(36.3f, 7f, .882f, .237f, v.wifiMidOpacity)
        glyph(canvas, CanvasPaths.wifiDot, -.5f, 17f, 1f, v.fgColor,
            opacity * if (v.wifiLevel > 0) 1f else .3f)
    }

    private fun glyph(canvas: Canvas, path: Path, x: Float, y: Float, scale: Float, color: Int, opacity: Float) {
        if (opacity < .001f || scale <= 0) return
        val saved = canvas.save()
        try {
            canvas.translate(x, y); canvas.scale(scale, scale)
            fill.color = alpha(color, opacity); canvas.drawPath(path, fill)
        } finally { canvas.restoreToCount(saved) }
    }

    private fun label(canvas: Canvas, value: String, x: Float, y: Float, size: Float,
                      weight: Float, color: Int, opacity: Float) {
        if (value.isBlank() || opacity < .001f) return
        text.textSize = size; text.color = alpha(color, opacity)
        text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = weight
        text.getTextBounds(value,0,value.length,numberBounds)
        canvas.drawText(value, x, y - (numberBounds.top + numberBounds.bottom) / 2f, text)
    }

    private fun alpha(color: Int, opacity: Float): Int = (color and 0x00ffffff) or
        ((((color ushr 24) * opacity.coerceIn(0f, 1f)).toInt()) shl 24)

    private companion object {
        val CELL_X = floatArrayOf(-27f, -9.5f, 8.5f, 26f)
        val CELL_Y = floatArrayOf(42.7f, 49.7f, 50.2f, 44.3f)
    }
}
