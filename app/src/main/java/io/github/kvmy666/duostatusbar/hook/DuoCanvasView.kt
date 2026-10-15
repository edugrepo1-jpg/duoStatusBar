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
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.TelemetryCounter
import io.github.kvmy666.duostatusbar.TelemetryFeature
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
        set(value) {
            if(field==value)return
            val changed=field.hideCells!=value.hideCells;field=value
            // Keep the newest state while an island/window hides us. Resuming consumes it once;
            // hidden status icons do not need an invalidation on every controller animation tick.
            if(!active||!isShown||windowVisibility!=VISIBLE)return
            safe("efeitos") {if(changed){changedAt=SystemClock.uptimeMillis();advance()} else invalidate()}
        }
    var continuumX = 0f
    var continuumY = 0f
    var lastAlignment = Float.NaN
    private val effectPainter = EffectPainter()
    private var failed = false
    private var failureAction:(()->Unit)?=null
    private var failureNotified=false
    private var lastEvidenceFeature: TelemetryFeature? = null
    private val effectEvidence=BooleanArray(TelemetryFeature.entries.size)
    private fun effectObserved(feature:TelemetryFeature,active:Boolean) {
        val index=feature.ordinal
        if(active&&!effectEvidence[index])RuntimeTelemetry.executed(feature)
        effectEvidence[index]=active
    }
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
    private var touchDown=false
    private var touchReleasedAt=-1L
    private var visibilityReady = false
    private val middleSwap=SequentialSwap()
    private val batterySourceSwap=BatterySourceSwap()
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
    private val outline=Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val roundedBolt = CornerPathEffect(1.4f)
    private val tick = Runnable { pending = false; safe("quadro") { advance() } }
    init { visibilityReady = true }

    private inline fun safe(where: String, action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            try { L.w("Canvas $where: ${error.javaClass.simpleName}: ${error.message}") } catch (_: Throwable) { }
        }
    }

    override fun start(): Boolean = !failed
    override fun onReady(action: () -> Unit) { safe("pronto") { if (!failed) action() } }
    override fun onFailed(action: () -> Unit) { failureAction=action;notifyFailure() }
    private fun notifyFailure() {
        if(!failed||failureNotified)return
        val action=failureAction ?: return
        failureNotified=true;failureAction=null
        // Restoration removes Views: run after the current parent/Canvas draw has returned.
        android.os.Handler(android.os.Looper.getMainLooper()).post {safe("recuperação") {action()}}
    }
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
        if(this.active==active)return@safe
        this.active = active
        if (!active) {
            removeCallbacks(tick); pending = false; revealAt = -1L;touchDown=false;touchReleasedAt=-1L
        } else {
            lastFrame = 0L
            advance()
        }
    }

    override fun teardown() = safe("encerramento") {
        active = false; removeCallbacks(tick); pending = false; revealAt = -1L
        touchDown=false;touchReleasedAt=-1L
        target = null; shown = null
        batterySourceSwap.clear()
        failureAction=null
    }
    fun touchResponse(pressed:Boolean) {
        if(!effects.aurora.touchResponse||!effects.motionEnabled||!animationsEnabled||!active)return
        if(!pressed&&!touchDown)return
        touchDown=pressed;touchReleasedAt=if(pressed)-1L else SystemClock.uptimeMillis()
        advance()
    }

    override fun onDetachedFromWindow() {
        safe("desanexar") { removeCallbacks(tick); pending = false; lastFrame = 0L }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        safe("anexar") { lastFrame = 0L; advance() }
    }

    override fun onVisibilityChanged(changedView:View,visibility:Int) {
        super.onVisibilityChanged(changedView,visibility)
        if(visibilityReady)safe("visibilidade") { updateVisibility() }
    }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(visibilityReady)safe("janela") { updateVisibility() }
    }
    private fun updateVisibility() {
        if(isShown&&windowVisibility==VISIBLE) { lastFrame=0L;advance() }
        else {removeCallbacks(tick);pending=false;lastFrame=0L}
    }

    private fun advance() {
        val v = target ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(0, 64)
        lastFrame = now
        val old = shown ?: v
        val settled = !animationsEnabled || !active || !isShown || windowVisibility!=VISIBLE || now - changedAt > 1600L
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
            batteryLevel = v.batteryLevel,
            percentEnabled = v.percentEnabled,
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
        val touchMoving=effects.aurora.touchResponse&&touchReleasedAt>=0&&now-touchReleasedAt<180
        val moving = touchMoving||middleSwap.moving(now) || abs(cellVisibility-if(effects.hideCells)0f else 1f)>.001f || (!settled && shown != v) || revealAt >= 0 ||
            abs(visibilityAmount - if (v.visible) 1f else 0f) > 0.001f
        if (active && moving && !pending && isAttachedToWindow && isShown && windowVisibility==VISIBLE) {
            pending = true; postOnAnimation(tick)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (failed) return
        val checkpoint = canvas.save()
        val drawAt = System.nanoTime()
        try {
            drawElement(canvas)
            if(active && isAttachedToWindow && isShown && shown!=null && width>0 && height>0 && visibilityAmount>=.001f) {
                RuntimeTelemetry.increment(TelemetryCounter.CANVAS_DRAW)
                val feature=when {
                    part!=DuoPart.INDICATORS&&effects.checkMs>=120&&EffectTimeline.check(effects.checkMs)>.01f -> TelemetryFeature.UNLOCK_CHECK
                    part!=DuoPart.RING&&AuroraRender.headphone(effects)&&effects.checkMs<0&&effects.headphoneConnectMs>=120 -> TelemetryFeature.AIRPODS
                    part!=DuoPart.RING&&effects.audioMs>=120&&EffectTimeline.audio(effects.audioMs)>.01f -> TelemetryFeature.AIRPODS
                    part!=DuoPart.RING&&effects.slot.icon!=null&&effects.slot.opacity>.01f&&effects.checkMs<0&&effects.audioMs<0 -> TelemetryFeature.valueOf(effects.slot.icon!!.name)
                    else -> null
                }
                if(feature!=lastEvidenceFeature) { feature?.let { RuntimeTelemetry.executed(it) };lastEvidenceFeature=feature }
                effectObserved(TelemetryFeature.CHARGE_EFFECT,effects.charging)
                effectObserved(TelemetryFeature.CRITICAL_PULSE,effects.pulseMs>=0||effects.criticalDot)
                effectObserved(TelemetryFeature.FADE,effects.slot.icon!=null&&effects.slot.opacity in .001f.. .999f)
                effectObserved(TelemetryFeature.DRAW_ICONS,effects.drawIcons&&effects.slot.icon!=null&&effects.slot.reveal in .001f.. .999f)
                effectObserved(TelemetryFeature.GLASS,false)
                effectObserved(TelemetryFeature.SPRING,effects.spring&&effects.slot.icon!=null&&effects.slot.scale<.999f)
                effectObserved(TelemetryFeature.ALBUM_COLORS,false)
            }
        } catch (error: Throwable) {
            failed = true
            removeCallbacks(tick);pending=false;notifyFailure()
            RuntimeTelemetry.increment(TelemetryCounter.CANVAS_FAILURE)
            lastEvidenceFeature?.let { RuntimeTelemetry.probe(it,false) }
            safe("falha no desenho") { L.w("Canvas: ${error.javaClass.simpleName}: ${error.message}") }
        } finally {
            RuntimeTelemetry.increment(TelemetryCounter.CANVAS_DRAW_NS,(System.nanoTime()-drawAt).coerceAtLeast(0))
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun drawElement(canvas: Canvas) {
        val current = shown ?: return
        if (width <= 0 || height <= 0 || visibilityAmount < 0.001f) return
        val confirmingHeadphones=AuroraRender.headphone(effects)&&effects.checkMs<0
        val incomingHeadset=if(confirmingHeadphones)effects.headphoneBattery.takeIf {it in 0..100} else BatteryPresentation.headphoneLevel(effects)
        val incomingFrame=BatteryPresentation.frameForSource(current,effects,incomingHeadset)
        val selectedSource=batterySourceSwap.present(BatteryPresentation.visual(current,incomingFrame,incomingHeadset),incomingHeadset,effects)
        val headsetLevel=selectedSource.headphoneLevel
        // Custom discharge bands cannot replace the charging colour/effect. The headset is a
        // separate, noncharging source and retains its chosen bands even while the phone charges.
        val batteryFrame=BatteryPresentation.frameForSource(current,effects,headsetLevel)
        val displayed=if(headsetLevel==null&&(current.charging||effects.charging))selectedSource.visual.copy(tint=current.tint) else selectedSource.visual
        val mapped = part.apply(displayed)
        // Phone charging/critical overlays must never suggest that a headset is charging.
        val batteryEffects=if(headsetLevel!=null)batteryFrame.copy(charging=false,chargeMs=-1,pulseMs=-1,criticalDot=false,albumColor=0,musicProgress=-1f) else batteryFrame
        val glow=maxOf(AuroraRender.glow(effects),if(confirmingHeadphones).65f*AuroraRender.headphoneOpacity(effects) else 0f)
        val presentationTint=CanvasMotion.blend(if(headsetLevel!=null||batteryFrame.batteryColors!=null)mapped.tint else effectPainter.tint(mapped.tint,batteryEffects),0xFF71E3B1.toInt(),glow)
        val v = mapped.copy(tint=CanvasMotion.blend(presentationTint,0xFF71E3B1.toInt(),AuroraRender.taskGlow(effects)),
            fgColor=CanvasMotion.blend(mapped.fgColor,0xFF71E3B1.toInt(),glow))
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
        if(effects.aurora.touchResponse&&effects.motionEnabled&&animationsEnabled) {
            val compression=if(touchDown).96f else if(touchReleasedAt>=0) .96f+.04f*EffectTimeline.smooth((SystemClock.uptimeMillis()-touchReleasedAt)/180f) else 1f
            canvas.scale(compression,compression)
        }
        val opacity = visibilityAmount * (r * 4).coerceIn(0f, 1f)
        val contraction = 1f - .05f * EffectTimeline.smooth(effects.checkMs / 80f) * (1f - EffectTimeline.smooth((effects.checkMs - 120) / 280f))
        val audioExpansion = if (effects.audioMs < 0) 0f else minOf(EffectTimeline.smooth(effects.audioMs / 200f), EffectTimeline.smooth((4000 - effects.audioMs) / 250f))
        canvas.scale(contraction * (1f + .05f * audioExpansion), contraction)
        val f = AuroraRender.thickness(thickPercent,effects.aurora.safeGeometry)
        val drawingFrame=if(effects.aurora.safeGeometry)batteryEffects.copy(iconRadius=minOf(batteryEffects.iconRadius,55.5f-8f*f-4f)) else batteryEffects
        val buildRing=AuroraRender.build(effects,0)
        val buildNumber=AuroraRender.build(effects,1)
        val buildCells=AuroraRender.build(effects,2)
        val sourceOpacity=AuroraRender.sourceOpacity(effects)
        val radius = (111f - 8f * f) / 2f
        bounds.set(-radius, -radius, radius, radius)
        stroke.strokeWidth = 8f * f
        if (v.ringOpacity > 0f) {
            val ringOpacity=opacity*v.ringOpacity*sourceOpacity
            if(effects.aurora.adaptiveContrast) {
                outline.color=alpha(AuroraRender.opposite(mapped.fgColor),ringOpacity*.7f);outline.strokeWidth=stroke.strokeWidth+2f
                if(v.leftArc*buildRing>0)canvas.drawArc(bounds,270f+RingGeometry.LEFT_START*360f,v.leftArc*buildRing*360f,false,outline)
                if(v.rightArc*buildRing>0)canvas.drawArc(bounds,270f+RingGeometry.RIGHT_START*360f,v.rightArc*buildRing*360f,false,outline)
            }
            stroke.color = alpha(v.fgColor, ringOpacity * v.trackOpacity)
            arc(canvas, v.leftArc*buildRing, 270f + RingGeometry.LEFT_START * 360f)
            arc(canvas, v.rightArc*buildRing, 270f + RingGeometry.RIGHT_START * 360f)
            stroke.color = alpha(v.tint, ringOpacity)
            arc(canvas, v.trimLeftEnd*buildRing, 270f + RingGeometry.LEFT_START * 360f)
            arc(canvas, v.trimRightEnd*buildRing, 270f + RingGeometry.RIGHT_START * 360f)
        }
        val slotAlpha = opacity * v.indicatorsOpacity * EffectTimeline.checkNormal(effects.checkMs) * EffectTimeline.audioNormal(effects.audioMs)*(if(confirmingHeadphones)0f else AuroraRender.headphoneReturnOpacity(effects))
        val cycleIcon = effects.slot.icon
        if (slotAlpha > 0f && cycleIcon != null) {
            val saved = canvas.save(); canvas.scale(effects.slot.scale, effects.slot.scale)
            when (cycleIcon) {
                SlotIcon.WIFI -> {
                    val requested=effects.iconPercent.coerceIn(60,200)/100f
                    val factor=AuroraRender.iconScale(requested,55.5f-8f*f-2f,28f,effects.aurora.safeGeometry)
                    canvas.scale(factor,factor)
                    drawWifi(canvas,v,slotAlpha*effects.slot.opacity)
                }
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
        effectPainter.foreground(canvas, if (cycleIcon == null) drawingFrame.copy(slot = SlotFrame(null)) else drawingFrame,
            v.fgColor, if(effects.aurora.safeGeometry)thickPercent.coerceIn(1,160) else thickPercent, opacity, v.ringOpacity > 0f, v.indicatorsOpacity > 0f,v.leftArc*buildRing,v.rightArc*buildRing)
        if (v.ringOpacity <= 0f) return
        val ringAlpha = opacity * v.ringOpacity
        val cells = floatArrayOf(v.cell1Opacity, v.cell2Opacity, v.cell3Opacity, v.cell4Opacity)
        val moons = floatArrayOf(v.moon1Opacity, v.moon2Opacity, v.moon3Opacity, v.moon4Opacity)
        val cellColor=CanvasMotion.blend(v.fgColor,0xFF71E3B1.toInt(),AuroraRender.glow(effects))
        for (i in 0..3) if (cellVisibility>.001f) {
            val cellAlpha=ringAlpha*cells[i]*cellVisibility*buildCells
            if(effects.aurora.adaptiveContrast&&cellAlpha>.001f) {
                fill.color=alpha(AuroraRender.opposite(mapped.fgColor),cellAlpha*.7f);canvas.drawCircle(CELL_X[i],CELL_Y[i],6.5f*cellVisibility*buildCells,fill)
            }
            fill.color = alpha(cellColor, cellAlpha)
            canvas.drawCircle(CELL_X[i], CELL_Y[i], 5.5f*cellVisibility*buildCells, fill)
            glyph(canvas, CanvasPaths.moon, CELL_X[i], CELL_Y[i], DotMoonMotion.DOT_SCALE * moons[i]*cellVisibility,
                cellColor, ringAlpha * moons[i]*cellVisibility*buildCells)
        }
        glyph(canvas, CanvasPaths.moon, DotMoonMotion.CENTER_X, DotMoonMotion.CENTER_Y,
            DotMoonMotion.DOT_SCALE * v.centerMoonOpacity, cellColor, ringAlpha * v.centerMoonOpacity*buildCells)
        if (headsetLevel==null && boltAmount > .001f) {
            fill.pathEffect = roundedBolt
            glyph(canvas, CanvasPaths.bolt, 0f, -49.5f * boltAmount,
                if (current.animateCharge) CanvasMotion.overshoot(boltAmount) else 1f,
                v.tint, ringAlpha * boltAmount)
            fill.pathEffect = null
        }
        val numberColor=CanvasMotion.blend(if(headsetLevel!=null||batteryFrame.batteryColors!=null)presentationTint else v.fgColor,0xFF71E3B1.toInt(),AuroraRender.glow(effects))
        label(canvas, v.percentText.trim(), 0f, v.percentY + RingGeometry.PERCENT_BOX_HALF,
            v.percentFontSize, 2.4f, numberColor, ringAlpha * v.percentOpacity*buildNumber*sourceOpacity)
    }

    private fun arc(canvas: Canvas, fraction: Float, start: Float) {
        if (fraction > 0.00001f) canvas.drawArc(bounds, start, fraction * 360f, false, stroke)
    }

    private val wifiGlyph=WifiGlyph()
    private fun drawWifi(canvas: Canvas, v: DuoVisual, opacity: Float) {
        if(opacity<.001f)return
        if(effects.aurora.adaptiveContrast) {
            val save=canvas.save();canvas.scale(1.06f,1.06f)
            wifiGlyph.draw(canvas,AuroraRender.opposite(v.fgColor),opacity*.7f,v.wifiOuterOpacity,v.wifiMidOpacity,
                if(v.wifiLevel>0)1f else .3f,if(effects.drawIcons)effects.slot.reveal else 1f)
            canvas.restoreToCount(save)
        }
        wifiGlyph.draw(canvas,v.fgColor,opacity,v.wifiOuterOpacity,v.wifiMidOpacity,
            if(v.wifiLevel>0)1f else .3f,if(effects.drawIcons)effects.slot.reveal else 1f)
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
        if(effects.aurora.adaptiveContrast) {
            outline.textSize=size;outline.typeface=text.typeface;outline.textAlign=Paint.Align.CENTER;outline.strokeWidth=weight+2f
            outline.color=alpha(AuroraRender.opposite(color),opacity*.7f)
            canvas.drawText(value,x,y-(numberBounds.top+numberBounds.bottom)/2f,outline)
        }
        canvas.drawText(value, x, y - (numberBounds.top + numberBounds.bottom) / 2f, text)
    }

    private fun alpha(color: Int, opacity: Float): Int = (color and 0x00ffffff) or
        ((((color ushr 24) * opacity.coerceIn(0f, 1f)).toInt()) shl 24)

    private companion object {
        val CELL_X = floatArrayOf(-27f, -9.5f, 8.5f, 26f)
        val CELL_Y = floatArrayOf(42.7f, 49.7f, 50.2f, 44.3f)
    }
}
