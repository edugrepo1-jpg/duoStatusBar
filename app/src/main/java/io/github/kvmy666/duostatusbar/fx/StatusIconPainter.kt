package io.github.kvmy666.duostatusbar.fx
import io.github.kvmy666.duostatusbar.i18n.UiText
import android.graphics.*
import kotlin.math.*
/** Original Canvas artwork with filled, rounded system-icon proportions. No Apple assets/font. */
internal class StatusIconPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    private var contrast=false
    private val measure=PathMeasure()
    private val trace=Path()
    private val entrance=Path()
    private val entranceBounds=RectF()
    private val clear=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val warningBounds=RectF(-30f,-30f,30f,30f)
    private val wifiGlyph=io.github.kvmy666.duostatusbar.hook.WifiGlyph()
    private val headphonePads=Path().apply {
        moveTo(-18.75f,3f);lineTo(-15f,3f);quadTo(-10f,3f,-10f,8f)
        lineTo(-10f,22f);lineTo(-18.75f,22f);close()
        moveTo(18.75f,3f);lineTo(15f,3f);quadTo(10f,3f,10f,8f)
        lineTo(10f,22f);lineTo(18.75f,22f);close()
    }
    private val headbandLight=LinearGradient(0f,-25f,0f,22f,0xFF999999.toInt(),0xFFE0E0E0.toInt(),Shader.TileMode.CLAMP)
    private val headbandDark=LinearGradient(0f,-25f,0f,22f,0xFF303030.toInt(),0xFF777777.toInt(),Shader.TileMode.CLAMP)
    private val leftPad=LinearGradient(-18.75f,0f,-10f,0f,0xFF777777.toInt(),0xFFA0A0A0.toInt(),Shader.TileMode.CLAMP)
    private val rightPad=LinearGradient(10f,0f,18.75f,0f,0xFFA0A0A0.toInt(),0xFF777777.toInt(),Shader.TileMode.CLAMP)
    private val speaker=Path().apply { moveTo(-18f,-6f);lineTo(-10f,-6f);lineTo(0f,-15f);lineTo(0f,15f);lineTo(-10f,6f);lineTo(-18f,6f);close() }
    private val soundWaves=Path().apply {
        moveTo(7f,-7f);cubicTo(12f,-3f,12f,3f,7f,7f)
        moveTo(13f,-13f);cubicTo(23f,-6f,23f,6f,13f,13f)
    }
    private val pausedBars=floatArrayOf(5f,10f,15f,10f,5f)
    private val networkLabels=setOf("2G","3G","4G","4G+","5G","5G+","LTE","H","H+","E","G")
    // create(AIRPODS) uses the cached pads; initialize the catalogue after its dependencies.
    private val paths=SlotIcon.entries.associateWith { create(it) }
    /** Shortcut artwork differs from an ephemeral volume-percentage readout in the ring. */
    fun drawShortcut(canvas:Canvas,icon:SlotIcon,fg:Int,opacity:Float,frame:EffectFrame) {
        if(icon!=SlotIcon.VOLUME){draw(canvas,icon,fg,opacity,frame);return}
        paint.xfermode=null;paint.color=fg;paint.alpha=((fg ushr 24)*opacity).toInt().coerceIn(0,255)
        paint.style=Paint.Style.FILL;canvas.drawPath(speaker,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2.6f;canvas.drawPath(soundWaves,paint)
    }
    private val lengths=paths.mapValues { (_,path) ->
        val m=PathMeasure(path,false);var total=0f
        do { total+=m.length } while(m.nextContour())
        total
    }
    private val outlined=setOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.SHARE,SlotIcon.WIRELESS,SlotIcon.MICROPHONE,SlotIcon.ALARM,SlotIcon.SILENT,SlotIcon.VIBRATE,SlotIcon.TASK_DONE)
    fun draw(canvas:Canvas,icon:SlotIcon,fg:Int,opacity:Float,frame:EffectFrame) {
        if(opacity<=.001f)return
        contrast=frame.aurora.adaptiveContrast
        val save=canvas.save()
        val requested=frame.iconPercent.coerceIn(60,200)/100f
        val maxRadius=when(icon){SlotIcon.AIRPODS,SlotIcon.VOLUME,SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH->35f;SlotIcon.WIFI_OFFLINE->28f;SlotIcon.WIFI->28f;SlotIcon.AIRPLANE->31f;SlotIcon.LOCATION->28f;else->30f}
        val factor=AuroraRender.iconScale(requested,frame.iconRadius,maxRadius,frame.aurora.safeGeometry)
        canvas.scale(factor,factor)
        if(icon==SlotIcon.AIRPODS)canvas.translate(0f,1.5f)
        if(icon==SlotIcon.LOCATION&&frame.compass)canvas.rotate(-frame.compassDegrees-45f)
        val confirming=icon==SlotIcon.AIRPODS&&AuroraRender.headphone(frame)
        val color=when {confirming->0xFF71E3B1.toInt();icon==SlotIcon.TASK_DONE->0xFF30D158.toInt();icon==SlotIcon.RECORD->0xFFFF453A.toInt();icon==SlotIcon.BOLT->0xFF3DDC84.toInt();else->fg }
        // A painter instance is reused by all grid cells: never inherit another icon's blend mode.
        paint.xfermode=null
        paint.shader=null
        paint.color=color;paint.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        paint.style=if(icon in outlined)Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=2.6f
        // A single finite contour reveal covers pictograms AND live readouts. Wi-Fi uses its
        // strength-aware path reveal below. No new timers or redraw loops are introduced.
        if(frame.drawIcons&&frame.motionEnabled&&frame.slot.reveal<.999f&&icon!=SlotIcon.WIFI&&icon!=SlotIcon.WIFI_OFFLINE) {
            entrancePath(icon,frame,entrance)
            val progress=frame.slot.reveal.coerceIn(0f,1f)
            val fill=EffectTimeline.smooth((progress-.65f)/.35f)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=2.6f
            paint.alpha=(paint.alpha*(1f-fill)).toInt()
            tracePath(canvas,entrance,progress)
            // Crossfade the drawn outline to its ordinary filled form, preserving semantic colors.
            canvas.restoreToCount(save)
            if(fill>0f)draw(canvas,icon,fg,opacity*fill,frame.copy(drawIcons=false))
            return
        }
        if(frame.aurora.adaptiveContrast&&icon!=SlotIcon.CALL&&icon!=SlotIcon.WIFI&&
            (icon!=SlotIcon.TASK_DONE||!frame.motionEnabled||frame.taskCompleteMs<0||frame.taskCompleteMs>=300)&&
            (!frame.drawIcons||frame.slot.reveal>=.999f)&&paths.getValue(icon).isEmpty.not()) {
            val style=paint.style;paint.style=Paint.Style.STROKE;paint.strokeWidth=if(style==Paint.Style.STROKE)4.6f else 2f
            paint.color=AuroraRender.opposite(fg);paint.alpha=(180*opacity).toInt().coerceIn(0,255)
            canvas.drawPath(paths.getValue(icon),paint)
            paint.style=style;paint.strokeWidth=2.6f;paint.color=color;paint.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        }
        when(icon) {
            SlotIcon.WIFI->{wifiOutline(canvas,fg,opacity,frame);wifiGlyph.draw(canvas,fg,opacity,reveal=if(frame.drawIcons)frame.slot.reveal else 1f)}
            SlotIcon.NETWORK->{
                if(frame.networkText in networkLabels)label(canvas,frame.networkText,0f,1f,28f,fg,opacity)
                else { paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
                    for(i in 0..3)canvas.drawLine(-12f+i*8f,14f,-12f+i*8f,10f-i*8f,paint)
                }
            }
            SlotIcon.CHARGE_TIME->{label(canvas,estimateLabel(frame.chargeRemainingMs),0f,-3f,estimateTextSize(estimateLabel(frame.chargeRemainingMs)),0xFF71E3B1.toInt(),opacity);label(canvas,UiText.t("até 100%"),0f,19f,10f,fg,opacity)}
            SlotIcon.RECORD_TIME->{label(canvas,durationLabel(frame.recordElapsedMs),0f,3f,19f,fg,opacity);paint.style=Paint.Style.FILL;paint.color=0xFFFF453A.toInt();paint.alpha=(255*opacity).toInt();canvas.drawCircle(0f,-17f,3f,paint)}
            SlotIcon.VOLUME->{paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;canvas.drawArc(RectF(-34f,-34f,34f,34f),145f,250f*(frame.volumePercent.coerceIn(0,100)/100f),false,paint);label(canvas,"${frame.volumePercent.coerceIn(0,100)}%",0f,0f,22f,fg,opacity)}
            SlotIcon.SCREENSHOT->{paint.style=Paint.Style.STROKE;paint.strokeWidth=2.8f;canvas.drawCircle(0f,0f,20f,paint);val s=canvas.save();canvas.rotate((1-frame.slot.reveal)*65f)
                if(frame.aurora.shutter&&frame.motionEnabled) {
                    val opening=3f+10f*abs(cos(frame.slot.reveal.coerceIn(0f,1f)*PI)).toFloat()
                    for(i in 0..5){canvas.rotate(60f);canvas.drawLine(0f,-19f,opening,-opening*.35f,paint)}
                } else for(i in 0..5){canvas.rotate(60f);canvas.drawLine(0f,-19f,11f,0f,paint)}
                canvas.restoreToCount(s)}
            SlotIcon.CALL->{val s=canvas.save();canvas.translate(0f,-9f);canvas.scale(.6f,.6f);canvas.drawPath(paths.getValue(icon),paint);canvas.restoreToCount(s);label(canvas,durationLabel(frame.callElapsedMs),0f,16f,15f,fg,opacity)}
            SlotIcon.TIMER,SlotIcon.STOPWATCH->{
                val elapsed=if(icon==SlotIcon.TIMER)frame.timerElapsedMs.coerceAtLeast(0) else frame.stopwatchElapsedMs.coerceAtLeast(0)
                val remaining=frame.timerRemainingMs.coerceAtLeast(0)
                val progress=if(icon==SlotIcon.TIMER&&(elapsed+remaining)>0)remaining.toFloat()/(elapsed+remaining) else 1f
                paint.style=Paint.Style.STROKE;paint.strokeWidth=2.2f;canvas.drawArc(RectF(-28f,-28f,28f,28f),-90f,360f*progress,false,paint)
                canvas.drawCircle(0f,-13f,5f,paint);canvas.drawLine(0f,-13f,0f,-16f,paint);canvas.drawLine(-3f,-21f,3f,-21f,paint)
                label(canvas,durationLabel(if(icon==SlotIcon.TIMER)remaining else elapsed),0f,8f,16f,fg,opacity)
            }
            SlotIcon.NOTIFICATION->canvas.drawPath(paths.getValue(icon),paint)
            SlotIcon.WIFI_OFFLINE->{
                // The very same connected-Wi-Fi geometry; only a red diagonal is added.
                val pulse=OfflineWifiPulse.opacity(frame.motionMs,frame.motionEnabled)
                val layer=canvas.saveLayer(warningBounds,null)
                val slash=if(frame.drawIcons&&frame.motionEnabled)frame.slot.reveal.coerceIn(0f,1f) else 1f
                wifiOutline(canvas,fg,opacity*pulse,frame)
                wifiGlyph.draw(canvas,fg,opacity*pulse,reveal=if(frame.drawIcons)frame.slot.reveal else 1f)
                // Transparent separation follows the diagonal across every wave, including on light backgrounds.
                paint.style=Paint.Style.STROKE;paint.strokeWidth=9.8f;paint.xfermode=clear;paint.alpha=255
                canvas.drawLine(-16f,-18f,-16f+32f*slash,-18f+36f*slash,paint);paint.xfermode=null
                paint.strokeWidth=7f;paint.color=0xFFFF2020.toInt();paint.alpha=(255*opacity*pulse).toInt()
                canvas.drawLine(-16f,-18f,-16f+32f*slash,-18f+36f*slash,paint)
                canvas.restoreToCount(layer)
            }
            SlotIcon.VPN->{paint.style=Paint.Style.STROKE;canvas.drawRoundRect(RectF(-24f,-12f,24f,12f),4f,4f,paint);label(canvas,"VPN",0f,1f,17f,fg,opacity)}
            SlotIcon.RECORD->{val pulse=if(frame.motionEnabled).55f+.45f*(.5f+.5f*sin(frame.motionMs/260f))else 1f;paint.alpha=(255*opacity*pulse).toInt();canvas.drawCircle(0f,0f,8f,paint)}
            SlotIcon.MEDIA->{
                paint.style=Paint.Style.STROKE;paint.strokeWidth=3.8f
                for(i in 0..4) {
                    val h=if(frame.musicPlaying&&frame.motionEnabled)4f+11f*abs(sin(frame.motionMs/210f+i*.8f)) else pausedBars[i]
                    canvas.drawLine(-16f+i*8f,-h,-16f+i*8f,h,paint)
                }
            }
            else->{
                val path=paths.getValue(icon)
                val progress=if(icon==SlotIcon.TASK_DONE&&frame.motionEnabled&&frame.taskCompleteMs>=0)EffectTimeline.smooth(frame.taskCompleteMs/300f) else if(frame.drawIcons)frame.slot.reveal else 1f
                if(progress>=.999f)canvas.drawPath(path,paint)
                else {
                    val normalStyle=paint.style
                    var remaining=lengths.getValue(icon)*progress
                    measure.setPath(path,false);paint.style=Paint.Style.STROKE
                    do {
                        trace.reset();measure.getSegment(0f,minOf(remaining,measure.length).coerceAtLeast(0f),trace,true)
                        canvas.drawPath(trace,paint);remaining-=measure.length
                    } while(remaining>0&&measure.nextContour())
                    if(normalStyle==Paint.Style.FILL&&progress>.7f) {
                        paint.style=normalStyle;paint.alpha=(paint.alpha*(progress-.7f)/.3f).toInt();canvas.drawPath(path,paint)
                    }
                }
            }
        }
        if(icon==SlotIcon.AIRPODS&&!confirming&&(!frame.drawIcons||frame.slot.reveal>=.999f)) {
            val light=Color.red(fg)+Color.green(fg)+Color.blue(fg)>380
            paint.style=Paint.Style.FILL;paint.shader=if(light)headbandLight else headbandDark
            paint.alpha=((fg ushr 24)*opacity).toInt().coerceIn(0,255)
            canvas.drawPath(paths.getValue(icon),paint)
            val pads=canvas.save()
            canvas.clipRect(-25f,-25f,0f,25f);paint.shader=if(light)leftPad else headbandDark
            canvas.drawPath(headphonePads,paint);canvas.restoreToCount(pads)
            val right=canvas.save()
            canvas.clipRect(0f,-25f,25f,25f);paint.shader=if(light)rightPad else headbandDark
            canvas.drawPath(headphonePads,paint);canvas.restoreToCount(right)
            paint.shader=null
        }
        canvas.restoreToCount(save)
    }
    private fun tracePath(canvas:Canvas,path:Path,progress:Float) {
        measure.setPath(path,false);var total=0f
        do {total+=measure.length} while(measure.nextContour())
        var remaining=total*progress;measure.setPath(path,false)
        do {
            trace.reset();measure.getSegment(0f,minOf(remaining,measure.length).coerceAtLeast(0f),trace,true)
            canvas.drawPath(trace,paint);remaining-=measure.length
        } while(remaining>0&&measure.nextContour())
    }
    private fun entrancePath(icon:SlotIcon,frame:EffectFrame,out:Path) {
        out.reset()
        fun line(x1:Float,y1:Float,x2:Float,y2:Float){out.moveTo(x1,y1);out.lineTo(x2,y2)}
        fun caption(value:String,y:Float,size:Float) {
            text.textSize=size
            trace.reset();text.getTextPath(value,0,value.length,0f,y-(text.ascent()+text.descent())/2f,trace)
            out.addPath(trace);trace.reset()
        }
        fun arc(radius:Float,start:Float,sweep:Float) {
            entranceBounds.set(-radius,-radius,radius,radius);out.addArc(entranceBounds,start,sweep)
        }
        when(icon) {
            SlotIcon.NETWORK->if(frame.networkText in networkLabels)caption(frame.networkText,1f,28f)
                else for(i in 0..3)line(-12f+i*8f,14f,-12f+i*8f,10f-i*8f)
            SlotIcon.CHARGE_TIME->{caption(estimateLabel(frame.chargeRemainingMs),-3f,estimateTextSize(estimateLabel(frame.chargeRemainingMs)));caption(UiText.t("até 100%"),19f,10f)}
            SlotIcon.RECORD_TIME->{caption(durationLabel(frame.recordElapsedMs),3f,19f);out.addCircle(0f,-17f,3f,Path.Direction.CW)}
            SlotIcon.VOLUME->{arc(34f,145f,250f*frame.volumePercent.coerceIn(0,100)/100f);caption("${frame.volumePercent.coerceIn(0,100)}%",0f,22f)}
            SlotIcon.SCREENSHOT->{out.addCircle(0f,0f,20f,Path.Direction.CW);for(i in 0..5){val a=i*PI/3;line((19*sin(a)).toFloat(),(-19*cos(a)).toFloat(),(11*cos(a)).toFloat(),(11*sin(a)).toFloat())}}
            SlotIcon.VPN->{entranceBounds.set(-24f,-12f,24f,12f);out.addRoundRect(entranceBounds,4f,4f,Path.Direction.CW);caption("VPN",1f,17f)}
            SlotIcon.RECORD->out.addCircle(0f,0f,8f,Path.Direction.CW)
            SlotIcon.MEDIA->for(i in 0..4){val h=pausedBars[i];line(-16f+i*8f,-h,-16f+i*8f,h)}
            SlotIcon.TIMER,SlotIcon.STOPWATCH->{arc(28f,-90f,360f);out.addCircle(0f,-13f,5f,Path.Direction.CW);line(0f,-13f,0f,-16f);line(-3f,-21f,3f,-21f);caption(durationLabel(if(icon==SlotIcon.TIMER)frame.timerRemainingMs.coerceAtLeast(0) else frame.stopwatchElapsedMs.coerceAtLeast(0)),8f,16f)}
            SlotIcon.CALL->{out.addPath(paths.getValue(icon),Matrix().apply {setValues(floatArrayOf(.6f,0f,0f,0f,.6f,-9f,0f,0f,1f))});caption(durationLabel(frame.callElapsedMs),16f,15f)}
            else->out.addPath(paths.getValue(icon))
        }
    }
    private fun estimateTextSize(value:String):Float {
        text.textSize=22f
        return minOf(22f,22f*54f/text.measureText(value).coerceAtLeast(1f))
    }
    private fun label(canvas:Canvas,value:String,x:Float,y:Float,size:Float,color:Int,opacity:Float){
        text.style=Paint.Style.FILL;text.textSize=size;text.color=color;text.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        if(contrast) {
            text.style=Paint.Style.STROKE;text.strokeWidth=2f;text.color=AuroraRender.opposite(color);text.alpha=(180*opacity).toInt().coerceIn(0,255)
            canvas.drawText(value,x,y-(text.ascent()+text.descent())/2,text)
            text.style=Paint.Style.FILL;text.color=color;text.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        }
        canvas.drawText(value,x,y-(text.ascent()+text.descent())/2,text)
    }
    private fun wifiOutline(canvas:Canvas,fg:Int,opacity:Float,frame:EffectFrame) {
        if(!frame.aurora.adaptiveContrast)return
        val save=canvas.save();canvas.scale(1.06f,1.06f)
        wifiGlyph.draw(canvas,AuroraRender.opposite(fg),opacity*.7f,reveal=if(frame.drawIcons)frame.slot.reveal else 1f)
        canvas.restoreToCount(save)
    }
    private fun create(icon:SlotIcon)=Path().apply {
        fun line(vararg p:Float){moveTo(p[0],p[1]);for(i in 2 until p.size step 2)lineTo(p[i],p[i+1])}
        fun polygon(vararg p:Float){line(*p);close()}
        when(icon){
            SlotIcon.AIRPLANE->{
                // Horizontal plane: exact optical centre from its symmetric bounds, no inherited offset.
                moveTo(24f,0f);cubicTo(24f,-3f,19f,-3.5f,15f,-3.5f);lineTo(4f,-3.5f);lineTo(-8f,-19f);lineTo(-13f,-19f);lineTo(-7f,-3.5f);lineTo(-18f,-3.5f);lineTo(-22f,-9f);lineTo(-25f,-9f);lineTo(-23f,0f);lineTo(-25f,9f);lineTo(-22f,9f);lineTo(-18f,3.5f);lineTo(-7f,3.5f);lineTo(-13f,19f);lineTo(-8f,19f);lineTo(4f,3.5f);lineTo(15f,3.5f);cubicTo(19f,3.5f,24f,3f,24f,0f);close()
            }
            SlotIcon.DND->{moveTo(8f,-17f);cubicTo(-15f,-18f,-23f,6f,-8f,17f);cubicTo(4f,25f,21f,13f,18f,5f);cubicTo(-1f,10f,-9f,-4f,8f,-17f);close()}
            SlotIcon.BLUETOOTH->{line(0f,-17f,10f,-8f,-10f,9f);line(-10f,-9f,10f,8f,0f,17f,0f,-17f)}
            SlotIcon.SHARE->{
                // Personal hotspot is represented by connected links, not file-transfer arrows.
                addRoundRect(RectF(-21f,-9f,2f,9f),8f,8f,Path.Direction.CW)
                addRoundRect(RectF(-2f,-9f,21f,9f),8f,8f,Path.Direction.CW)
                transform(Matrix().apply { setRotate(-35f) })
            }
            SlotIcon.NOTIFICATION->{
                fillType=Path.FillType.EVEN_ODD;addRoundRect(RectF(-20f,-14f,20f,12f),7f,7f,Path.Direction.CW)
                for(x in floatArrayOf(-9f,0f,9f))addCircle(x,-1f,2f,Path.Direction.CCW)
            }
            SlotIcon.NFC->{
                // Google Material NFC, Apache-2.0; scaled from the official 24dp path.
                addPath(androidx.core.graphics.PathParser.createPathFromPathData("M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 18H4V4h16v16zM18 6h-5c-1.1 0-2 .9-2 2v2.28c-.6.35-1 .98-1 1.72 0 1.1.9 2 2 2s2-.9 2-2c0-.74-.4-1.38-1-1.72V8h3v8H8V8h2V6H6v12h12V6z")!!)
                transform(Matrix().apply { setValues(floatArrayOf(2f,0f,-24f,0f,2f,-24f,0f,0f,1f)) })
            }
            SlotIcon.AIRPODS->{
                // Symmetric over-ear arch: open centre, vertical sides, rounded lower corners.
                moveTo(-25f,0f);cubicTo(-25f,-13.8f,-13.8f,-25f,0f,-25f)
                cubicTo(13.8f,-25f,25f,-13.8f,25f,0f);lineTo(25f,17f)
                quadTo(25f,22f,20f,22f);lineTo(18.75f,22f);lineTo(18.75f,0f)
                cubicTo(18.75f,-10.35f,10.35f,-18.75f,0f,-18.75f)
                cubicTo(-10.35f,-18.75f,-18.75f,-10.35f,-18.75f,0f)
                lineTo(-18.75f,22f);lineTo(-20f,22f);quadTo(-25f,22f,-25f,17f);close()
                addPath(headphonePads)
            }
            SlotIcon.BOLT->polygon(4f,-19f,-12f,3f,-2f,3f,-5f,20f,13f,-5f,3f,-5f)
            SlotIcon.CAMERA->{fillType=Path.FillType.EVEN_ODD;moveTo(-17f,-12f);lineTo(-10f,-12f);lineTo(-7f,-17f);lineTo(7f,-17f);lineTo(10f,-12f);lineTo(17f,-12f);cubicTo(21f,-12f,21f,-10f,21f,-8f);lineTo(21f,11f);cubicTo(21f,15f,19f,15f,17f,15f);lineTo(-17f,15f);cubicTo(-21f,15f,-21f,13f,-21f,11f);lineTo(-21f,-8f);cubicTo(-21f,-12f,-19f,-12f,-17f,-12f);close();addCircle(0f,1f,8f,Path.Direction.CCW)}
            SlotIcon.MICROPHONE->{addRoundRect(RectF(-5f,-18f,5f,5f),5f,5f,Path.Direction.CW);moveTo(-10f,-1f);cubicTo(-10f,15f,10f,15f,10f,-1f);line(0f,12f,0f,19f);line(-7f,19f,7f,19f)}
            SlotIcon.ALARM->{addCircle(0f,2f,15f,Path.Direction.CW);line(0f,-7f,0f,2f,7f,6f);moveTo(-19f,-10f);cubicTo(-18f,-17f,-10f,-20f,-7f,-15f);moveTo(7f,-15f);cubicTo(10f,-20f,18f,-17f,19f,-10f);line(-11f,13f,-15f,18f);line(11f,13f,15f,18f)}
            SlotIcon.LOCATION->polygon(-19f,-3f,20f,-20f,3f,19f,-2f,3f)
            SlotIcon.SILENT->{moveTo(-12f,0f);cubicTo(-12f,-17f,12f,-17f,12f,0f);line(12f,0f,12f,8f,16f,12f,-16f,12f,-12f,8f,-12f,0f);moveTo(-4f,17f);cubicTo(-2f,21f,2f,21f,4f,17f);line(-19f,-19f,19f,19f)}
            SlotIcon.VIBRATE->{addRoundRect(RectF(-9f,-19f,9f,19f),3f,3f,Path.Direction.CW);line(-17f,-10f,-20f,-5f,-16f,0f,-20f,5f,-17f,10f);line(17f,-10f,20f,-5f,16f,0f,20f,5f,17f,10f)}
            SlotIcon.WIRELESS->{addCircle(0f,0f,20f,Path.Direction.CW);moveTo(-26f,-11f);cubicTo(-30f,-5f,-30f,5f,-26f,11f);moveTo(26f,-11f);cubicTo(30f,-5f,30f,5f,26f,11f);line(3f,-12f,-7f,2f,0f,2f,-3f,12f,8f,-3f,1f,-3f)}
            SlotIcon.TORCH->{addRoundRect(RectF(-7f,-3f,7f,22f),3f,3f,Path.Direction.CW);polygon(-13f,-20f,13f,-20f,12f,-10f,7f,-3f,-7f,-3f,-12f,-10f)}
            SlotIcon.WIFI->{moveTo(-22f,-4f);cubicTo(-9f,-18f,9f,-18f,22f,-4f);moveTo(-14f,5f);cubicTo(-6f,-4f,6f,-4f,14f,5f);moveTo(-6f,13f);cubicTo(-2f,9f,2f,9f,6f,13f)}
            SlotIcon.CALL->{moveTo(-18f,-19f);quadTo(-23f,-14f,-16f,-1f);quadTo(-6f,17f,11f,22f);quadTo(18f,24f,22f,17f);lineTo(13f,8f);quadTo(10f,6f,6f,11f);quadTo(-3f,6f,-8f,-4f);quadTo(-3f,-8f,-6f,-12f);lineTo(-13f,-21f);close()}
            SlotIcon.TASK_DONE->line(-18f,0f,-5f,14f,20f,-15f)
            else->Unit
        }
    }
}
