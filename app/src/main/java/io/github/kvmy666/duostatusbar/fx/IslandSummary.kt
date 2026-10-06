package io.github.kvmy666.duostatusbar.fx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.PopupWindow
import io.github.kvmy666.duostatusbar.L

internal data class IslandItem(val icon:SlotIcon,val title:String,val detail:String="Ativo")
internal data class IslandState(val battery:Int=-1,val charging:Boolean=false,val items:List<IslandItem> = emptyList())

/** Attached subwindow of the existing status bar: no app overlay permission or replacement window. */
internal class IslandSummary(private val context:Context,private val visibility:(Boolean)->Unit) {
    private var popup:PopupWindow?=null
    private var content:SummaryCanvas?=null
    private var state=IslandState()
    fun update(next:IslandState) { if(state!=next){state=next;content?.update(next)} }
    fun open(anchor:View,animate:Boolean):Boolean {
        if(popup!=null){content?.close();return true}
        if(!anchor.isAttachedToWindow||!anchor.isShown)return false
        try {
            val density=context.resources.displayMetrics.density
            val metrics=context.resources.displayMetrics
            val width=minOf((360*density).toInt(),metrics.widthPixels-(24*density).toInt())
            val rows=maxOf(1,(state.items.size+1)/2)
            val height=minOf(((104+rows*54)*density).toInt(),metrics.heightPixels-(130*density).toInt()).coerceAtLeast((170*density).toInt())
            val top=maxOf(anchor.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())?.top ?: 0,(24*density).toInt())+(8*density).toInt()
            val location=IntArray(2);anchor.getLocationOnScreen(location)
            val left=(metrics.widthPixels-width)/2
            val initial=RectF((location[0]-left).toFloat(),location[1].toFloat(),(location[0]-left+anchor.width).toFloat(),(location[1]+anchor.height).toFloat())
            val view=SummaryCanvas(context,state,initial,animate,top.toFloat()) { dismiss() }
            val window=PopupWindow(view,width,height+top,true).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true
                isClippingEnabled=false;setIsLaidOutInScreen(true);elevation=12*density
                setOnDismissListener { content?.dispose();content=null;popup=null;visibility(false) }
            }
            content=view;popup=window
            window.showAtLocation(anchor,Gravity.TOP or Gravity.CENTER_HORIZONTAL,0,0)
            visibility(true)
            L.i("Resumo: expandido; estados=${state.items.size}")
            return true
        } catch(t:Throwable) { L.w("Resumo: ${t.javaClass.simpleName}: ${t.message}");dismiss();return false }
    }
    fun dismiss() { popup?.dismiss();content?.dispose();content=null;popup=null;visibility(false) }

    internal class SummaryCanvas(context:Context,private var state:IslandState,
        private val initial:RectF=RectF(0f,0f,48f,48f),private val animate:Boolean=true,private val insetTop:Float=0f,private val dismissed:()->Unit):View(context) {
        private val d=resources.displayMetrics.density
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        private val iconPainter=StatusIconPainter()
        private var amount=if(animate)0f else 1f
        private var animator:ValueAnimator?=null
        private var scroll=0f
        private var lastY=0f
        private var downY=0f
        private var downX=0f
        init { isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;update(state) }
        fun update(next:IslandState) {
            state=next
            contentDescription="Estados ativos. Bateria ${state.battery} por cento. "+state.items.joinToString { "${it.title}: ${it.detail}" }+". Toque em fechar para recolher."
            invalidate()
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow();if(animate)move(1f) }
        private fun move(to:Float) {
            animator?.cancel()
            animator=ValueAnimator.ofFloat(amount,to).apply {
                duration=if(to==1f)320 else 220
                interpolator=android.view.animation.DecelerateInterpolator()
                addUpdateListener { amount=it.animatedValue as Float;invalidate() }
                if(to==0f)addListener(object:android.animation.AnimatorListenerAdapter(){ override fun onAnimationEnd(animation:android.animation.Animator){dismissed()} })
                start()
            }
        }
        fun close() { if(animate)move(0f) else dismissed() }
        fun dispose() { animator?.removeAllListeners();animator?.cancel();animator=null }
        override fun onDetachedFromWindow() { dispose();super.onDetachedFromWindow() }
        override fun performClick():Boolean { super.performClick();close();return true }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{lastY=event.y;downY=event.y;downX=event.x;return true}
                MotionEvent.ACTION_MOVE->{scroll=(scroll+lastY-event.y).coerceIn(0f,maxScroll());lastY=event.y;invalidate();return true}
                MotionEvent.ACTION_UP->{if(kotlin.math.abs(event.y-downY)<8*d&&downX>width-60*d&&downY>=insetTop&&downY<insetTop+65*d)performClick();return true}
                MotionEvent.ACTION_CANCEL->return true
            }
            return false
        }
        private fun maxScroll()=maxOf(0f,((state.items.size+1)/2)*54*d-(height-insetTop-94*d))
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val a=EffectTimeline.smooth(amount)
            fun lerp(x:Float,y:Float)=x+(y-x)*a
            val rect=RectF(lerp(initial.left,1*d),lerp(initial.top,insetTop+1*d),lerp(initial.right,width-1*d),lerp(initial.bottom,height-1*d))
            val radius=lerp(minOf(initial.width(),initial.height())/2,30*d)
            paint.style=Paint.Style.FILL;paint.color=0xF510141B.toInt();canvas.drawRoundRect(rect,radius,radius,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=d;paint.color=0x354AD4B4;canvas.drawRoundRect(rect,radius,radius,paint)
            val save=canvas.save();val clip=Path().apply { addRoundRect(rect,radius,radius,Path.Direction.CW) };canvas.clipPath(clip)
            canvas.translate(0f,insetTop)
            val bodyHeight=height-insetTop
            val alpha=((a-.35f)/.65f).coerceIn(0f,1f)
            text.color=Color.WHITE;text.alpha=(255*alpha).toInt();text.textSize=17*d
            canvas.drawText("DUO",22*d,31*d,text)
            text.textSize=12*d;text.color=0xFF71E3B1.toInt()
            canvas.drawText(if(state.battery<0)"Bateria" else "${state.battery}%${if(state.charging) " · carregando" else ""}",22*d,52*d,text)
            text.color=Color.WHITE;text.textSize=23*d;canvas.drawText("×",width-39*d,36*d,text)
            text.textSize=11*d;text.color=0xFF9EAABD.toInt();canvas.drawText("${state.items.size} estados ativos",22*d,77*d,text)
            canvas.clipRect(12*d,88*d,width-12*d,bodyHeight-10*d)
            if(state.items.isEmpty())canvas.drawText("Nenhum indicador ativo",22*d,115*d,text)
            val cell=(width-32*d)/2
            state.items.forEachIndexed { index,item ->
                val x=16*d+(index%2)*cell;val y=90*d+(index/2)*54*d-scroll
                if(y+50*d>=88*d&&y<bodyHeight) {
                    paint.style=Paint.Style.FILL;paint.color=0xFF1C2531.toInt();paint.alpha=(255*alpha).toInt()
                    canvas.drawRoundRect(RectF(x,y,x+cell-6*d,y+47*d),14*d,14*d,paint)
                    val iconSave=canvas.save();canvas.translate(x+22*d,y+23*d);canvas.scale(.55f*d,.55f*d)
                    iconPainter.draw(canvas,item.icon,Color.WHITE,alpha,EffectFrame(networkText="5G",motionMs=android.os.SystemClock.uptimeMillis()))
                    canvas.restoreToCount(iconSave)
                    text.textSize=11*d;text.color=Color.WHITE;text.alpha=(255*alpha).toInt()
                    canvas.drawText(shorten(item.title,cell-51*d),x+43*d,y+20*d,text)
                    text.textSize=9*d;text.color=0xFF9EAABD.toInt();canvas.drawText(shorten(item.detail,cell-51*d),x+43*d,y+35*d,text)
                }
            }
            canvas.restoreToCount(save)
        }
        private fun shorten(value:String,width:Float):String {
            if(text.measureText(value)<=width)return value
            var end=value.length
            while(end>0&&text.measureText(value.substring(0,end)+"…")>width)end--
            return value.substring(0,end)+"…"
        }
    }
}
