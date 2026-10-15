package io.github.kvmy666.duostatusbar.fx

import android.graphics.*
import org.json.JSONObject
import kotlin.math.*

internal data class IslandAppearance(val opacity:Int=96,val coverOpacity:Int=100,val coverEffect:Int=0,val waveProgress:Boolean=true) {
    fun encode()=JSONObject().put("opacity",opacity.coerceIn(20,100)).put("coverOpacity",coverOpacity.coerceIn(20,100)).put("effect",coverEffect.coerceIn(0,20)).put("wave",waveProgress)
    companion object { fun decode(j:JSONObject?)=if(j==null)IslandAppearance()else IslandAppearance(j.optInt("opacity",96).coerceIn(20,100),j.optInt("coverOpacity",100).coerceIn(20,100),j.optInt("effect",0).coerceIn(0,20),j.optBoolean("wave",true)) }
}

/** Decorative motion, not an audio amplitude measurement. No microphone or audio capture. */
internal object CoverEffects {
    val names=listOf(
        arrayOf("Sem efeito","No effect","Sin efecto"),arrayOf("Halo suave","Soft halo","Halo suave"),
        arrayOf("Halo respirando","Breathing halo","Halo respirando"),arrayOf("Órbita simples","Single orbit","Órbita simple"),
        arrayOf("Órbita dupla","Double orbit","Órbita doble"),arrayOf("Quatro satélites","Four satellites","Cuatro satélites"),
        arrayOf("Arco de progresso","Progress arc","Arco de progreso"),arrayOf("Arcos alternados","Alternating arcs","Arcos alternados"),
        arrayOf("Ondas concêntricas","Concentric waves","Ondas concéntricas"),arrayOf("Borda pulsante","Pulsing border","Borde pulsante"),
        arrayOf("Quadrantes iluminados","Lit quadrants","Cuadrantes iluminados"),arrayOf("Trilha de pontos","Dotted trail","Rastro de puntos"),
        arrayOf("Equalizador circular","Circular equalizer","Ecualizador circular"),arrayOf("Pulso radial","Radial pulse","Pulso radial"),
        arrayOf("Onda orbital","Orbital wave","Onda orbital"),arrayOf("Onda circular","Circular wave","Onda circular"),
        arrayOf("Cometa","Comet","Cometa"),arrayOf("Brilho alternado","Alternating glow","Brillo alternado"),
        arrayOf("Anel segmentado","Segmented ring","Anillo segmentado"),arrayOf("Órbita elíptica","Elliptical orbit","Órbita elíptica"),
        arrayOf("Duplo contorno","Double outline","Doble contorno"))
    fun label(id:Int)=names[id.coerceIn(0,20)].let {AuroraText.t(it[0],it[1],it[2])}
}

internal class CoverEffectPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect=RectF()
    private val path=Path()
    fun draw(c:Canvas,b:RectF,id:Int,time:Long,color:Int,progress:Float=-1f,motion:Boolean=true) {
        if(id !in 1..20)return
        val t=if(motion)(time%6000)/6000f else 0f
        val a=t*2*PI.toFloat();val pulse=.5f+.5f*sin(a*2)
        val u=min(b.width(),b.height());val x=b.centerX();val y=b.centerY();val radius=u*.63f
        paint.shader=null;paint.color=if(color==0)0xFF93D9FF.toInt()else color or 0xFF000000.toInt()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1f,u*.035f);paint.strokeCap=Paint.Cap.ROUND
        rect.set(x-radius,y-radius,x+radius,y+radius)
        fun circle(r:Float,alpha:Int=180){paint.alpha=alpha;c.drawCircle(x,y,r,paint)}
        fun dot(angle:Float,r:Float=radius,size:Float=u*.045f){paint.style=Paint.Style.FILL;paint.alpha=230;c.drawCircle(x+cos(angle)*r,y+sin(angle)*r,size,paint);paint.style=Paint.Style.STROKE}
        when(id) {
            1,2->{for(i in 1..4){paint.strokeWidth=u*.04f;circle(radius+i*u*.022f,((if(id==2)35+35*pulse else 48f)/i).toInt())}}
            3->{circle(radius,60);dot(a)}
            4->{circle(radius,45);dot(a);dot(-a+PI.toFloat(),radius+u*.06f,u*.032f)}
            5->{repeat(4){dot(a+it*PI.toFloat()/2)}}
            6->{paint.alpha=220;c.drawArc(rect,-90f,360f*progress.coerceIn(0f,1f),false,paint)}
            7->{paint.alpha=210;c.drawArc(rect,t*360,72f,false,paint);c.drawArc(rect,t*360+180,72f,false,paint)}
            8->{repeat(3){val phase=(t*2+it/3f)%1f;circle(radius+phase*u*.18f,((1-phase)*120).toInt())}}
            9->{paint.alpha=(90+150*pulse).toInt();circle(radius,paint.alpha)}
            10->{paint.alpha=220;repeat(4){c.drawArc(rect,it*90f+12f+t*36,38f,false,paint)}}
            11->{repeat(16){paint.alpha=80;dot(it*PI.toFloat()/8+a,size=u*(.018f+.012f*((it+1)/16f)))}}
            12,13->{repeat(32){val angle=it*PI.toFloat()/16;val v=(.035f+.10f*(.5f+.5f*sin(a*3+it*.8f)))*u
                val r=radius+if(id==13).025f*u*sin(a*2)else 0f
                c.drawLine(x+cos(angle)*r,y+sin(angle)*r,x+cos(angle)*(r+v),y+sin(angle)*(r+v),paint)}}
            14->{path.reset();repeat(97){val angle=it*PI.toFloat()/48;val rr=radius+sin(angle*4-a*3)*u*.065f;val xx=x+cos(angle)*rr;val yy=y+sin(angle)*rr;if(it==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)};c.drawPath(path,paint)}
            15->{path.reset();repeat(65){val angle=it*PI.toFloat()/32;val rr=radius+sin(angle*6+a*3)*u*.035f;val xx=x+cos(angle)*rr;val yy=y+sin(angle)*rr;if(it==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)};c.drawPath(path,paint)}
            16->{repeat(8){dot(a-it*.08f,size=u*(.045f-it*.004f))}}
            17->{paint.alpha=(80+120*pulse).toInt();c.drawArc(rect,-60f,120f,false,paint);paint.alpha=(200-120*pulse).toInt();c.drawArc(rect,120f,120f,false,paint)}
            18->{repeat(12){paint.alpha=if(it/12f<=progress)230 else 70;c.drawArc(rect,it*30f-90,18f,false,paint)}}
            19->{val save=c.save();c.rotate(t*360,x,y);rect.set(x-radius*1.12f,y-radius*.96f,x+radius*1.12f,y+radius*.96f);c.drawOval(rect,paint);c.restoreToCount(save)}
            20->{circle(radius,210);circle(radius+u*.075f,80)}
        }
        paint.alpha=255
    }
    fun progress(c:Canvas,left:Float,right:Float,y:Float,value:Float,time:Long,motion:Boolean,density:Float) {
        paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=2*density;paint.color=0x36FFFFFF
        val end=left+(right-left)*value.coerceIn(0f,1f)
        if(end<right)c.drawLine(end,y,right,y,paint)
        if(value<=0f)return
        paint.color=0xFF93D9FF.toInt();path.reset()
        val count=((end-left)/(2*density)).toInt().coerceIn(1,240)
        for(i in 0..count){
            val xx=left+(end-left)*i/count
            val edge=minOf(1f,(xx-left)/(8*density),(end-xx)/(8*density)).coerceAtLeast(0f)
            val yy=y+if(motion)sin((xx-left)/(24*density)*2*PI-(time%1600)/1600.0*2*PI).toFloat()*4*density*edge else 0f
            if(i==0)path.moveTo(xx,yy)else path.lineTo(xx,yy)
        }
        c.drawPath(path,paint)
        paint.style=Paint.Style.FILL;c.drawCircle(end,y,2.5f*density,paint)

    }
}
