package io.github.kvmy666.duostatusbar.fx

import io.github.kvmy666.duostatusbar.i18n.UiText
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.PopupWindow
import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import io.github.kvmy666.duostatusbar.L

internal data class IslandItem(val icon:SlotIcon,val title:String,val detail:String=UiText.t("Ativo"))
internal data class IslandState(val battery:Int=-1,val charging:Boolean=false,val items:List<IslandItem> = emptyList(),
    val playback:PlaybackSnapshot=PlaybackSnapshot(),val chargeRemainingMs:Long=-1,val headphoneBattery:Int=-1,val torch:Boolean=false)

/** Attached subwindow of the existing status bar: no app overlay permission or replacement window. */
internal class IslandSummary(private val context:Context,private val interactive:Boolean=true,private val visibility:(Boolean)->Unit) {
    private val actionInFlight=AtomicBoolean(false)
    private val main=Handler(Looper.getMainLooper())
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
            val top=maxOf(anchor.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())?.top ?: 0,(24*density).toInt())+(8*density).toInt()
            val height=minOf(((290+rows*54)*density).toInt(),metrics.heightPixels-top-(16*density).toInt()).coerceAtLeast(1)
            val location=IntArray(2);anchor.getLocationOnScreen(location)
            val left=(metrics.widthPixels-width)/2
            val initial=RectF((location[0]-left).toFloat(),location[1].toFloat(),(location[0]-left+anchor.width).toFloat(),(location[1]+anchor.height).toFloat())
            val view=SummaryCanvas(context,state,initial,animate,top.toFloat(),{ key -> if(interactive)performAction(key) }) { dismiss() }
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
    private fun performAction(key:String) {
        // Media/camera services can block in Binder. Never wait for them on SystemUI's draw thread,
        // and never enqueue an unbounded series of taps while a device service is unresponsive.
        if(key in listOf("play","previous","next","torch")) {
            if(!actionInFlight.compareAndSet(false,true))return
            try { serviceActions.execute { try { performServiceAction(key) } finally { actionInFlight.set(false) } } }
            catch(t:Throwable) { actionInFlight.set(false);L.w("Island action=$key unavailable=${t.javaClass.simpleName}") }
            return
        }
        performServiceAction(key)
    }
    private fun performServiceAction(key:String) {
        try {
            when(key) {
                "play","previous","next" -> {
                    val sessions=context.getSystemService(android.media.session.MediaSessionManager::class.java)?.getActiveSessions(null).orEmpty()
                    val controller=sessions.firstOrNull { it.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
                    if(controller==null){main.post { performServiceAction("volume") };return}
                    when(key){"previous"->controller.transportControls.skipToPrevious();"next"->controller.transportControls.skipToNext();else->if(controller.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING)controller.transportControls.pause() else controller.transportControls.play()}
                }
                "torch" -> {
                    val camera=context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    val id=camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
                    if(id!=null)camera.setTorchMode(id,!state.torch)
                }
                "wifi"->launch(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                "bluetooth"->launch(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                "volume"->launch(android.provider.Settings.Panel.ACTION_VOLUME)
            }
            L.i("Island action=$key result=requested")
        } catch(t:Throwable) { L.w("Island action=$key unavailable=${t.javaClass.simpleName}") }
    }
    private fun launch(action:String) { dismiss();context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun dismiss() {
        val window=popup
        if(window!=null) {
            // The listener owns cleanup; recover locally if the OEM has already removed its token.
            try { window.dismiss() } catch(t:Throwable) { L.w("Island dismiss unavailable=${t.javaClass.simpleName}") }
            if(popup===window){content?.dispose();content=null;popup=null;visibility(false)}
        }
        else { content?.dispose();content=null;visibility(false) }
    }
    private companion object {
        val serviceActions=ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,LinkedBlockingQueue<Runnable>(1),
            { task -> Thread(task,"DuoIslandActions").apply { isDaemon=true } })
    }

    internal class SummaryCanvas(context:Context,private var state:IslandState,
        private val initial:RectF=RectF(0f,0f,48f,48f),private val animate:Boolean=true,private val insetTop:Float=0f,private val action:(String)->Unit={},private val dismissed:()->Unit):View(context) {
        private val d=resources.displayMetrics.density
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val text=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        private val iconPainter=StatusIconPainter()
        private var amount=if(animate)0f else 1f
        private var animator:ValueAnimator?=null
        private var scroll=0f
        private var lastY=0f
        private var downY=0f
        private var downX=0f
        private var dragged=false
        private var gridGesture=false
        private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
        private var disposed=false
        private var motionPosted=false
        private var ready=false
        private var movingGlyphVisible=false
        private val motion=object:Runnable {
            override fun run() { motionPosted=false;if(needsMotion()){invalidate();scheduleMotion()} }
        }
        private val hits=linkedMapOf<String,RectF>()
        init { isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;ready=true;update(state) }
        fun update(next:IslandState) {
            state=next
            contentDescription=UiText.format("Estados ativos. Bateria {0} por cento. ", state.battery)+state.items.joinToString { "${it.title}: ${it.detail}" }+UiText.t(". Toque em fechar para recolher.")
            scroll=scroll.coerceIn(0f,maxScroll())
            invalidate()
            scheduleMotion()
        }
        private fun needsMotion()= !disposed&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&amount>.95f&&
            movingGlyphVisible&&(state.playback.playing||state.items.any { it.icon==SlotIcon.RECORD||it.icon==SlotIcon.RECORD_TIME })
        private fun scheduleMotion() {
            if(!needsMotion()){removeCallbacks(motion);motionPosted=false;return}
            if(!motionPosted){motionPosted=true;postDelayed(motion,100L)}
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow();disposed=false;if(animate)move(1f);scheduleMotion() }
        override fun onWindowVisibilityChanged(visibility:Int) { super.onWindowVisibilityChanged(visibility);if(ready)scheduleMotion() }
        override fun onVisibilityChanged(changedView:View,visibility:Int) { super.onVisibilityChanged(changedView,visibility);if(ready)scheduleMotion() }
        private fun move(to:Float) {
            animator?.cancel()
            animator=ValueAnimator.ofFloat(amount,to).apply {
                duration=if(to==1f)320 else 220
                interpolator=android.view.animation.DecelerateInterpolator()
                addUpdateListener { amount=it.animatedValue as Float;invalidate();scheduleMotion() }
                if(to==0f)addListener(object:android.animation.AnimatorListenerAdapter(){ override fun onAnimationEnd(animation:android.animation.Animator){dismissed()} })
                start()
            }
        }
        fun close() { disposed=true;removeCallbacks(motion);motionPosted=false;if(animate)move(0f) else dismissed() }
        fun dispose() { disposed=true;removeCallbacks(motion);motionPosted=false;animator?.removeAllListeners();animator?.cancel();animator=null }
        override fun onDetachedFromWindow() { dispose();super.onDetachedFromWindow() }
        override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,UiText.t("Fechar")))
            if(maxScroll()>0) { info.isScrollable=true;info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
            listOf("Anterior","Reproduzir ou pausar","Próxima","Abrir rede","Abrir Bluetooth","Abrir volume","Alternar lanterna").forEachIndexed { index,label ->
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000000+index,UiText.t(label)))
            }
        }
        override fun performAccessibilityAction(id:Int,args:android.os.Bundle?):Boolean {
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)return performClick()
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                val direction=if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1
                scroll=(scroll+direction*maxOf(54*d,height-insetTop-gridTop()-82*d)).coerceIn(0f,maxScroll());invalidate();return true
            }
            val index=id-0x01000000
            if(index in 0..6){action(listOf("previous","play","next","wifi","bluetooth","volume","torch")[index]);return true}
            return super.performAccessibilityAction(id,args)
        }
        override fun performClick():Boolean { super.performClick();close();return true }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{lastY=event.y;downY=event.y;downX=event.x;dragged=false;gridGesture=event.y-insetTop in gridTop()..(height-insetTop-72*d);return true}
                MotionEvent.ACTION_MOVE->{
                    if(kotlin.math.abs(event.y-downY)>touchSlop||kotlin.math.abs(event.x-downX)>touchSlop)dragged=true
                    if(gridGesture&&dragged){scroll=(scroll+lastY-event.y).coerceIn(0f,maxScroll());invalidate()};lastY=event.y;return true
                }
                MotionEvent.ACTION_UP->{
                    if(!dragged&&kotlin.math.abs(event.y-downY)<=touchSlop&&kotlin.math.abs(event.x-downX)<=touchSlop) {
                        if(downX>width-60*d&&downY>=insetTop&&downY<insetTop+65*d)performClick()
                        else if(amount>.95f)hits.entries.firstOrNull { it.value.contains(event.x,event.y-insetTop) }?.let { action(it.key) }
                    };return true
                }
                MotionEvent.ACTION_CANCEL->{dragged=true;return true}
            }
            return false
        }
        private fun gridTop()=if(height-insetTop>=310*d)205*d else if(height-insetTop>=190*d)128*d else 80*d
        internal fun actionBounds(key:String):RectF?=hits[key]?.let { RectF(it).apply { offset(0f,insetTop) } }
        private fun maxScroll()=maxOf(0f,((state.items.size+1)/2)*54*d-(height-insetTop-72*d-gridTop()))
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val a=EffectTimeline.smooth(amount)
            fun lerp(x:Float,y:Float)=x+(y-x)*a
            val rect=RectF(lerp(initial.left,1*d),lerp(initial.top,insetTop+1*d),lerp(initial.right,width-1*d),lerp(initial.bottom,height-1*d))
            val radius=lerp(minOf(initial.width(),initial.height())/2,38*d)
            paint.style=Paint.Style.FILL;paint.color=0xFF050505.toInt();canvas.drawRoundRect(rect,radius,radius,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=d;paint.color=0x22FFFFFF;canvas.drawRoundRect(rect,radius,radius,paint)
            val save=canvas.save();val clip=Path().apply { addRoundRect(rect,radius,radius,Path.Direction.CW) };canvas.clipPath(clip)
            canvas.translate(0f,insetTop)
            val bodyHeight=height-insetTop
            // A single opacity owner covers all card/text/icon paints during the morph.
            canvas.saveLayerAlpha(0f,0f,width.toFloat(),bodyHeight,(255*((a-.35f)/.65f).coerceIn(0f,1f)).toInt())
            val alpha=1f
            movingGlyphVisible=false
            hits.clear()
            text.color=Color.WHITE;text.alpha=(255*alpha).toInt();text.textSize=19*d
            canvas.drawText(UiText.t("Agora"),22*d,34*d,text)
            text.textSize=12*d;text.color=0xFF8D8D93.toInt()
            canvas.drawText(if(state.charging)UiText.t("Carregando") else UiText.t("Seu resumo"),22*d,55*d,text)
            text.color=if(state.battery in 0..15)0xFFFF453A.toInt() else 0xFF30D158.toInt();text.textSize=17*d
            canvas.drawText(if(state.battery<0)"—" else "${state.battery}%",width-108*d,34*d,text)
            text.textSize=9*d;text.color=0xFF8D8D93.toInt()
            if(state.charging)canvas.drawText(estimateLabel(state.chargeRemainingMs),width-108*d,53*d,text)
            paint.style=Paint.Style.FILL;paint.color=0xFF252528.toInt();paint.alpha=(255*alpha).toInt()
            canvas.drawCircle(width-34*d,33*d,14*d,paint)
            text.color=Color.WHITE;text.textSize=22*d;canvas.drawText(UiText.t("×"),width-40*d,40*d,text)
            // Compact landscape keeps the state list and shortcuts reachable.
            if(bodyHeight>=310*d) {
            movingGlyphVisible=state.playback.playing
            // Media card, progress and separate transport controls.
            paint.color=0xFF18181B.toInt();canvas.drawRoundRect(RectF(16*d,72*d,width-16*d,178*d),22*d,22*d,paint)
            paint.color=if(state.playback.color!=0)state.playback.color else 0xFF313139.toInt()
            canvas.drawRoundRect(RectF(28*d,84*d,72*d,128*d),11*d,11*d,paint)
            val iconSave=canvas.save();canvas.translate(50*d,106*d);canvas.scale(.7f*d,.7f*d)
            iconPainter.draw(canvas,SlotIcon.MEDIA,Color.WHITE,alpha,EffectFrame(motionMs=android.os.SystemClock.uptimeMillis(),musicPlaying=state.playback.playing))
            canvas.restoreToCount(iconSave)
            text.textSize=12*d;text.color=Color.WHITE;canvas.drawText(shorten(state.playback.title.ifBlank { UiText.t("Música") },width-132*d),84*d,101*d,text)
            text.textSize=10*d;text.color=0xFF8D8D93.toInt();canvas.drawText(if(state.playback.playing)UiText.t("Reproduzindo") else UiText.t("Pausado"),84*d,119*d,text)
            for((index,key) in listOf("previous","play","next").withIndex()) {
                val x=width-140*d+index*48*d
                val rectButton=RectF(x-24*d,124*d,x+24*d,172*d);hits[key]=rectButton
                paint.style=Paint.Style.FILL;paint.color=Color.WHITE;paint.alpha=(255*alpha).toInt()
                val path=Path()
                if(key=="play"&&state.playback.playing) {canvas.drawRoundRect(RectF(x-6*d,140*d,x-2*d,152*d),d,d,paint);canvas.drawRoundRect(RectF(x+2*d,140*d,x+6*d,152*d),d,d,paint)}
                else { val sign=if(key=="previous")-1 else 1;path.moveTo(x-sign*5*d,140*d);path.lineTo(x+sign*6*d,146*d);path.lineTo(x-sign*5*d,152*d);path.close();canvas.drawPath(path,paint);if(key!="play")canvas.drawRect(minOf(x+sign*7*d,x+sign*9*d),140*d,maxOf(x+sign*7*d,x+sign*9*d),152*d,paint) }
            }
            val progress=state.playback.progress(android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0f)
            paint.color=0xFF39393F.toInt();canvas.drawRoundRect(RectF(28*d,146*d,width-178*d,149*d),2*d,2*d,paint)
            paint.color=Color.WHITE;canvas.drawRoundRect(RectF(28*d,146*d,28*d+(width-206*d).coerceAtLeast(0f)*progress,149*d),2*d,2*d,paint)
            text.textSize=11*d;text.color=0xFF8D8D93.toInt();canvas.drawText(UiText.format("{0} estados ativos", state.items.size),22*d,193*d,text)
            if(state.headphoneBattery>=0) {text.color=if(state.headphoneBattery>15)0xFF30D158.toInt() else 0xFFFF453A.toInt();canvas.drawText(UiText.format("Fones: {0}%", state.headphoneBattery),width-120*d,193*d,text)}
            } else if(bodyHeight>=190*d) {
                paint.style=Paint.Style.FILL;paint.color=0xFF18181B.toInt()
                canvas.drawRoundRect(RectF(16*d,72*d,width-16*d,122*d),16*d,16*d,paint)
                text.textSize=11*d;text.color=Color.WHITE
                canvas.drawText(shorten(state.playback.title.ifBlank { UiText.t("Música") },width-193*d),28*d,102*d,text)
                for((index,key) in listOf("previous","play","next").withIndex()) {
                    val x=width-140*d+index*48*d;hits[key]=RectF(x-24*d,74*d,x+24*d,122*d)
                    paint.color=Color.WHITE;val path=Path()
                    if(key=="play"&&state.playback.playing) {canvas.drawRoundRect(RectF(x-6*d,92*d,x-2*d,104*d),d,d,paint);canvas.drawRoundRect(RectF(x+2*d,92*d,x+6*d,104*d),d,d,paint)}
                    else {val sign=if(key=="previous")-1 else 1;path.moveTo(x-sign*5*d,92*d);path.lineTo(x+sign*6*d,98*d);path.lineTo(x-sign*5*d,104*d);path.close();canvas.drawPath(path,paint);if(key!="play")canvas.drawRect(minOf(x+sign*7*d,x+sign*9*d),92*d,maxOf(x+sign*7*d,x+sign*9*d),104*d,paint)}
                }
            }
            // Fixed quick actions sit below the scrollable state grid.
            val actionY=bodyHeight-62*d;val unit=(width-32*d)/4
            for((index,key) in listOf("wifi","bluetooth","volume","torch").withIndex()) {
                val x=16*d+index*unit;val rectAction=RectF(x,actionY,x+unit-7*d,bodyHeight-12*d);hits[key]=rectAction
                paint.style=Paint.Style.FILL;paint.color=if(key=="torch"&&state.torch)0xFF294C35.toInt() else 0xFF202023.toInt();paint.alpha=(255*alpha).toInt();canvas.drawRoundRect(rectAction,16*d,16*d,paint)
                val mini=canvas.save();canvas.translate(x+(unit-7*d)/2,actionY+17*d);canvas.scale(.45f*d,.45f*d)
                iconPainter.drawShortcut(canvas,when(key){"wifi"->SlotIcon.WIFI;"bluetooth"->SlotIcon.BLUETOOTH;"volume"->SlotIcon.VOLUME;else->SlotIcon.TORCH},Color.WHITE,alpha,EffectFrame())
                canvas.restoreToCount(mini);text.textSize=8*d;text.color=Color.WHITE;text.textAlign=Paint.Align.CENTER
                canvas.drawText(when(key){"wifi"->UiText.t("Rede");"bluetooth"->"Bluetooth";"volume"->UiText.t("Volume");else->UiText.t("Lanterna")},x+(unit-7*d)/2,actionY+40*d,text);text.textAlign=Paint.Align.LEFT
            }
            val gridTop=gridTop()
            canvas.clipRect(12*d,gridTop,width-12*d,maxOf(gridTop,actionY-10*d))
            if(state.items.isEmpty())canvas.drawText(UiText.t("Nenhum indicador ativo"),22*d,gridTop+23*d,text)
            val cell=(width-32*d)/2
            state.items.forEachIndexed { index,item ->
                val x=16*d+(index%2)*cell;val y=gridTop+3*d+(index/2)*54*d-scroll
                if(y+50*d>=gridTop&&y<actionY-10*d) {
                    paint.style=Paint.Style.FILL;paint.color=0xFF171719.toInt();paint.alpha=(255*alpha).toInt()
                    canvas.drawRoundRect(RectF(x,y,x+cell-6*d,y+47*d),16*d,16*d,paint)
                    val mini=canvas.save();canvas.translate(x+22*d,y+23*d);canvas.scale(.5f*d,.5f*d)
                    // Timed cells already show their truthful value in detail; avoid a fake 0:00
                    // or 'calculating' glyph created from an empty EffectFrame.
                    val glyph=when(item.icon){SlotIcon.RECORD_TIME->SlotIcon.RECORD;SlotIcon.CHARGE_TIME->SlotIcon.BOLT;else->item.icon}
                    if(glyph==SlotIcon.RECORD||(glyph==SlotIcon.MEDIA&&state.playback.playing))movingGlyphVisible=true
                    iconPainter.draw(canvas,glyph,Color.WHITE,alpha,EffectFrame(networkText=item.detail.takeIf { item.icon==SlotIcon.NETWORK } ?: "",motionMs=android.os.SystemClock.uptimeMillis(),headphoneBattery=-1,musicPlaying=state.playback.playing))
                    canvas.restoreToCount(mini)
                    text.textSize=11*d;text.color=Color.WHITE;text.alpha=(255*alpha).toInt()
                    canvas.drawText(shorten(item.title,cell-51*d),x+43*d,y+20*d,text)
                    text.textSize=9*d;text.color=0xFF8D8D93.toInt();canvas.drawText(shorten(item.detail,cell-51*d),x+43*d,y+35*d,text)
                }
            }
            canvas.restoreToCount(save)
            scheduleMotion()
        }
        private fun shorten(value:String,width:Float):String {
            if(width<=0f)return ""
            return android.text.TextUtils.ellipsize(value,text,width,android.text.TextUtils.TruncateAt.END).toString()
        }
    }
}
