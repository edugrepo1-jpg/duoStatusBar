package io.github.kvmy666.duostatusbar.fx

import android.content.Context
import android.graphics.*
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoMapping

/** Preview only. Never attached to NFC receivers, wallets or payment notifications. */
internal class NfcPhoneSimulation(context:Context) {
    enum class Outcome { READ, DECLINED, MOVE_CLOSER }
    private val particles=NfcParticlePainter()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    private val icons=StatusIconPainter()
    private val still=EffectFrame(motionEnabled=false)
    private val ring=DuoCanvasView(context).apply {
        animationsEnabled=false;arrivalEnabled=false;layout(0,0,44,52)
        render(DuoMapping.visual(72,false,false,true,3,4,false,networkText="5G"))
    }
    fun dispose()=ring.teardown()
    fun stage(time:Long,outcome:Outcome)=when {
        time<0 || time>=DURATION_MS -> 0
        time<1200 || outcome==Outcome.MOVE_CLOSER -> 1
        time<3200 -> 2
        outcome==Outcome.DECLINED -> 4
        else -> 3
    }
    fun draw(c:Canvas,width:Float,height:Float,time:Long,outcome:Outcome) {
        if(width<=0||height<=0)return
        val save=c.save();val scale=minOf(width/360f,height/600f)
        c.translate((width-360*scale)/2,(height-600*scale)/2);c.scale(scale,scale)
        paint.style=Paint.Style.FILL;paint.color=0xFF090D18.toInt()
        c.drawRoundRect(0f,0f,360f,600f,30f,30f,paint)
        val clip=c.save();path.reset();path.addRoundRect(4f,4f,356f,596f,27f,27f,Path.Direction.CW);c.clipPath(path)
        paint.color=0xFF263957.toInt();c.drawRect(4f,4f,356f,596f,paint)
        paint.color=0xFF304862.toInt();c.drawCircle(320f,450f,260f,paint)
        text(c,"09:41",22f,34f,15f,Color.WHITE,true)
        paint.color=0xFF090D18.toInt();c.drawCircle(180f,23f,5f,paint)
        c.save();c.translate(296f,9f);ring.draw(c);c.restore()
        text(c,AuroraText.t("Sua barra, seu jeito","Your bar, your way","Tu barra, a tu manera"),22f,215f,23f,Color.WHITE,true)
        text(c,"DUO RECREATE",22f,240f,12f,0xFFC3D1E4.toInt())
        paint.color=0xFF1B2A40.toInt();c.drawRoundRect(20f,268f,340f,399f,22f,22f,paint)
        text(c,AuroraText.t("SIMULAÇÃO NFC","NFC SIMULATION","SIMULACIÓN NFC"),38f,300f,12f,0xFFB9D8FF.toInt(),true)
        val stage=stage(time,outcome)
        val label=when(stage){
            1->AuroraText.t("Aproxime mais · simulado","Move closer · simulated","Acerca más · simulado")
            2->AuroraText.t("Lendo · simulado","Reading · simulated","Leyendo · simulado")
            3->AuroraText.t("Leitura concluída · simulada","Read complete · simulated","Lectura completa · simulada")
            4->AuroraText.t("Recusa · simulada","Declined · simulated","Rechazo · simulado")
            else->AuroraText.t("Escolha um cenário abaixo","Choose a scenario below","Elige un escenario abajo")
        }
        text(c,label,38f,333f,16f,Color.WHITE,true)
        text(c,AuroraText.t("Nenhuma cobrança é realizada.","No payment is processed.","No se realiza ningún cobro."),38f,365f,12f,0xFFCDD9E8.toInt())
        if(stage!=0) {
            val color=when(stage){1->0xFFFFAC45.toInt();3->0xFF67E7A1.toInt();4->0xFFFF5967.toInt();else->0}
            val local=when(stage){1->time;2->time-1200;else->time-3200}
            val duration=if(stage==1&&outcome==Outcome.MOVE_CLOSER)DURATION_MS else if(stage==1)1200L else if(stage==2)2000L else 2800L
            val phase=(local*3000/duration).coerceIn(0,2999)
            particles.draw(c,360f,152f,phase,statusBarHeightPx=48f,tint=color)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=2.4f;paint.strokeCap=Paint.Cap.ROUND
            paint.color=if(color==0)0xFFD2EEFF.toInt()else color
            val alpha=minOf(1f,local/220f,(duration-local)/350f).coerceIn(0f,1f)
            paint.alpha=(255*alpha).toInt()
            c.drawCircle(180f,92f,21f,paint)
            if(stage==3) {
                val progress=(local/460f).coerceIn(0f,1f);path.reset();path.moveTo(168f,92f)
                if(progress<.36f)path.lineTo(168f+9f*progress/.36f,92f+8f*progress/.36f)
                else {path.lineTo(177f,100f);val q=(progress-.36f)/.64f;path.lineTo(177f+16f*q,100f-20f*q)}
                c.drawPath(path,paint)
            } else if(stage==4) {
                c.drawLine(173f,85f,187f,99f,paint);c.drawLine(187f,85f,173f,99f,paint)
            } else {
                val iconSave=c.save();c.translate(180f,92f);c.scale(.60f,.60f)
                icons.draw(c,SlotIcon.NFC,paint.color,alpha,still);c.restoreToCount(iconSave)
            }
            paint.style=Paint.Style.FILL;paint.alpha=255
        }
        paint.color=0xFFDAE4F1.toInt();c.drawRoundRect(136f,579f,224f,583f,3f,3f,paint)
        c.restoreToCount(clip);c.restoreToCount(save)
    }
    private fun text(c:Canvas,value:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false) {
        paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size
        paint.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(value,x,y,paint)
    }
    companion object { const val DURATION_MS=6000L }
}
