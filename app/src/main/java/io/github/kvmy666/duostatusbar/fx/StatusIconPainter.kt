package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import kotlin.math.*

/** Original Canvas artwork with filled, rounded system-icon proportions. No Apple assets/font. */
internal class StatusIconPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    private val paths=SlotIcon.entries.associateWith { create(it) }
    private val measure=PathMeasure()
    private val trace=Path()
    private val lengths=paths.mapValues { (_,path) ->
        val m=PathMeasure(path,false);var total=0f
        do { total+=m.length } while(m.nextContour())
        total
    }
    private val outlined=setOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.SHARE,SlotIcon.NFC,SlotIcon.WIRELESS,SlotIcon.MICROPHONE,SlotIcon.ALARM,SlotIcon.SILENT,SlotIcon.VIBRATE,SlotIcon.AIRPODS)
    fun draw(canvas:Canvas,icon:SlotIcon,fg:Int,opacity:Float,frame:EffectFrame) {
        if(opacity<=.001f)return
        val save=canvas.save()
        val requested=frame.iconPercent.coerceIn(60,200)/100f
        val maxRadius=when(icon){SlotIcon.AIRPODS->29f;SlotIcon.AIRPLANE->31f;SlotIcon.LOCATION->28f;SlotIcon.VOLUME->35f;else->30f}
        val factor=if(requested<=1f)requested else minOf(requested,(frame.iconRadius/maxRadius).coerceAtLeast(1f))
        canvas.scale(factor,factor)
        if(icon==SlotIcon.AIRPODS){canvas.translate(0f,-5f/factor);canvas.scale(.82f,.82f)}
        if(icon==SlotIcon.LOCATION&&frame.compass)canvas.rotate(-frame.compassDegrees-45f)
        val color=when(icon) { SlotIcon.RECORD->0xFFFF453A.toInt();SlotIcon.BOLT->0xFF3DDC84.toInt();else->fg }
        paint.color=color;paint.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        paint.style=if(icon in outlined)Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=2.6f
        when(icon) {
            SlotIcon.NETWORK->label(canvas,frame.networkText,0f,1f,28f,fg,opacity)
            SlotIcon.CHARGE_TIME->{label(canvas,estimateLabel(frame.chargeRemainingMs),0f,0f,if(frame.chargeRemainingMs<0)10.5f else 15f,0xFF71E3B1.toInt(),opacity);label(canvas,"até 100%",0f,19f,8f,fg,opacity)}
            SlotIcon.RECORD_TIME->{label(canvas,durationLabel(frame.recordElapsedMs),0f,3f,19f,fg,opacity);paint.style=Paint.Style.FILL;paint.color=0xFFFF453A.toInt();canvas.drawCircle(0f,-17f,3f,paint)}
            SlotIcon.VOLUME->{paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;canvas.drawArc(RectF(-34f,-34f,34f,34f),145f,250f*(frame.volumePercent.coerceIn(0,100)/100f),false,paint);label(canvas,"${frame.volumePercent.coerceIn(0,100)}%",0f,1f,22f,fg,opacity)}
            SlotIcon.SCREENSHOT->{paint.style=Paint.Style.STROKE;paint.strokeWidth=2.8f;canvas.drawCircle(0f,0f,20f,paint);val s=canvas.save();canvas.rotate((1-frame.slot.reveal)*65f);for(i in 0..5){canvas.rotate(60f);canvas.drawLine(0f,-19f,11f,0f,paint)};canvas.restoreToCount(s)}
            SlotIcon.NOTIFICATION->{paint.style=Paint.Style.FILL;canvas.drawRoundRect(RectF(-20f,-14f,20f,12f),7f,7f,paint);paint.color=Color.BLACK;canvas.drawCircle(-9f,-1f,2f,paint);canvas.drawCircle(0f,-1f,2f,paint);canvas.drawCircle(9f,-1f,2f,paint)}
            SlotIcon.WIFI_OFFLINE->label(canvas,"!",0f,7f,20f,0xFFFF453A.toInt(),opacity)
            SlotIcon.VPN->{paint.style=Paint.Style.STROKE;canvas.drawRoundRect(RectF(-24f,-12f,24f,12f),4f,4f,paint);label(canvas,"VPN",0f,1f,17f,fg,opacity)}
            SlotIcon.RECORD->{val pulse=.55f+.45f*(.5f+.5f*sin(frame.motionMs/260f));paint.alpha=(255*opacity*pulse).toInt();canvas.drawCircle(0f,0f,8f,paint)}
            SlotIcon.MEDIA->{paint.style=Paint.Style.STROKE;paint.strokeWidth=3.2f;for(i in 0..4){val h=4f+11f*abs(sin(frame.motionMs/170f+i*.8f));canvas.drawLine(-16f+i*8f,-h,-16f+i*8f,h,paint)}}
            else->{
                val path=paths.getValue(icon)
                val progress=if(frame.drawIcons)frame.slot.reveal else 1f
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
        if(icon==SlotIcon.AIRPODS) {
            paint.style=Paint.Style.FILL;paint.color=0xFF57CFF4.toInt();paint.alpha=(255*opacity).toInt()
            canvas.drawRoundRect(RectF(-14f,-21f,-10f,-13f),2f,2f,paint)
            val vent=canvas.save();canvas.translate(14f,-5f);canvas.rotate(-34f)
            canvas.drawRoundRect(RectF(-4.5f,-1.8f,4.5f,1.8f),1.8f,1.8f,paint);canvas.restoreToCount(vent)
        }
        canvas.restoreToCount(save)
        if(icon==SlotIcon.AIRPODS&&frame.headphoneBattery in 0..100)
            label(canvas,"${frame.headphoneBattery}%",0f,minOf(37f,frame.iconRadius-3f),12.5f,if(frame.headphoneBattery>15)0xFF30D158.toInt() else 0xFFFF453A.toInt(),opacity)
    }
    private fun label(canvas:Canvas,value:String,x:Float,y:Float,size:Float,color:Int,opacity:Float){
        text.style=Paint.Style.FILL;text.textSize=size;text.color=color;text.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        canvas.drawText(value,x,y-(text.ascent()+text.descent())/2,text)
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
            SlotIcon.SHARE->{line(-17f,-8f,16f,-8f,9f,-15f);line(16f,-8f,9f,-1f);line(17f,8f,-16f,8f,-9f,1f);line(-16f,8f,-9f,15f)}
            SlotIcon.NFC->{
                // Contactless reader: radiating waves, not the previous stylised letter N.
                moveTo(-9f,-5f);cubicTo(-6f,-3f,-6f,3f,-9f,5f)
                moveTo(-3f,-11f);cubicTo(5f,-6f,5f,6f,-3f,11f)
                moveTo(3f,-17f);cubicTo(16f,-9f,16f,9f,3f,17f)
                moveTo(-14f,-1f);lineTo(-14f,1f)
            }
            SlotIcon.AIRPODS->{
                // Outline proportions traced from the user's earbud reference, without a bitmap backdrop.
                moveTo(-15f,-7f);cubicTo(-24f,-12f,-25f,-20f,-17f,-25f)
                cubicTo(-12f,-29f,-4f,-23f,0f,-19f);cubicTo(8f,-8f,-1f,-1f,-8f,-5f)
                moveTo(-17f,-15f);cubicTo(-15f,-10f,-16f,-8f,-16f,-5f)
                lineTo(-16f,10f);quadTo(-16f,12f,-14f,12f);lineTo(-10f,12f);quadTo(-8f,12f,-8f,10f)
                lineTo(-8f,-4f);cubicTo(-8f,-8f,-7f,-11f,-6f,-15f)
                moveTo(-2f,-17f);cubicTo(4f,-11f,3f,-3f,-6f,-2f)
                moveTo(2f,1f);cubicTo(7f,-8f,15f,-11f,21f,-5f)
                cubicTo(30f,7f,15f,17f,8f,13f);cubicTo(-4f,9f,-6f,3f,2f,1f);close()
                moveTo(-1f,3f);cubicTo(3f,1f,8f,3f,10f,7f);cubicTo(14f,15f,5f,15f,2f,11f)
                moveTo(10f,15f);lineTo(10f,24f);quadTo(10f,26f,12f,26f);lineTo(17f,26f)
                quadTo(19f,26f,19f,24f);lineTo(19f,11f)
                moveTo(-13f,9f);lineTo(-10f,9f);moveTo(13f,23f);lineTo(16f,23f)
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
            else->Unit
        }
    }
}
