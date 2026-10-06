package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import kotlin.math.*

/** Original Canvas artwork with filled, rounded system-icon proportions. No Apple assets/font. */
internal class StatusIconPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
    private val paths=SlotIcon.entries.associateWith { create(it) }
    private val outlined=setOf(SlotIcon.BLUETOOTH,SlotIcon.SHARE,SlotIcon.NFC,SlotIcon.WIRELESS,SlotIcon.MICROPHONE,SlotIcon.ALARM,SlotIcon.SILENT,SlotIcon.VIBRATE)
    fun draw(canvas:Canvas,icon:SlotIcon,fg:Int,opacity:Float,frame:EffectFrame) {
        if(opacity<=.001f)return
        val color=when(icon) { SlotIcon.RECORD->0xFFFF453A.toInt();SlotIcon.BOLT->0xFF3DDC84.toInt();else->fg }
        paint.color=color;paint.alpha=((color ushr 24)*opacity).toInt().coerceIn(0,255)
        paint.style=if(icon in outlined)Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=2.6f
        when(icon) {
            SlotIcon.NETWORK->label(canvas,frame.networkText,0f,1f,28f,fg,opacity)
            SlotIcon.WIFI_OFFLINE->label(canvas,"!",29f,9f,18f,fg,opacity)
            SlotIcon.VPN->{paint.style=Paint.Style.STROKE;canvas.drawRoundRect(RectF(-24f,-12f,24f,12f),4f,4f,paint);label(canvas,"VPN",0f,1f,17f,fg,opacity)}
            SlotIcon.RECORD->{val pulse=.55f+.45f*(.5f+.5f*sin(frame.motionMs/260f));paint.alpha=(255*opacity*pulse).toInt();canvas.drawCircle(0f,0f,8f,paint)}
            SlotIcon.MEDIA->{paint.style=Paint.Style.STROKE;paint.strokeWidth=3.2f;for(i in 0..4){val h=4f+11f*abs(sin(frame.motionMs/170f+i*.8f));canvas.drawLine(-16f+i*8f,-h,-16f+i*8f,h,paint)}}
            else->canvas.drawPath(paths.getValue(icon),paint)
        }
        if(icon==SlotIcon.AIRPODS&&frame.headphoneBattery in 0..100)
            label(canvas,"${frame.headphoneBattery}%",0f,27.5f,12.5f,if(frame.headphoneBattery>15)0xFF30D158.toInt() else 0xFFFF453A.toInt(),opacity)
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
                fillType=Path.FillType.EVEN_ODD
                for(side in listOf(-1f,1f)){
                    moveTo(side*8f,-8f);cubicTo(side*5f,-18f,side*17f,-22f,side*23f,-14f)
                    cubicTo(side*29f,-7f,side*22f,-1f,side*16f,-4f)
                    lineTo(side*16f,16f);cubicTo(side*16f,19f,side*10f,19f,side*10f,16f);lineTo(side*10f,-3f);cubicTo(side*9f,-4f,side*8f,-6f,side*8f,-8f);close()
                    moveTo(side*17f,-14f);cubicTo(side*19f,-16f,side*23f,-13f,side*22f,-11f);cubicTo(side*21f,-9f,side*17f,-11f,side*17f,-14f);close()
                }
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
            else->Unit
        }
    }
}
