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
    val playback:PlaybackSnapshot=PlaybackSnapshot(),val chargeRemainingMs:Long=-1,val headphoneBattery:Int=-1,val torch:Boolean=false,
    val foreground:SlotIcon?=null) {
    fun primaryItem():IslandItem? = items.firstOrNull {it.icon==foreground}
        ?: items.firstOrNull {playback.playing&&it.icon==SlotIcon.MEDIA}
        ?: items.firstOrNull {it.icon==SlotIcon.RECORD}
        ?: items.firstOrNull {it.icon==SlotIcon.MICROPHONE}
        ?: items.firstOrNull()
}

internal data class IslandLayout(val left:Int,val top:Int,val width:Int,val height:Int,val compactWidth:Int,val compactHeight:Int) {
    fun leftFor(compact:Boolean)=left+if(compact)(width-compactWidth)/2 else 0
}
internal object IslandGeometry {
    fun layout(screenWidth:Int,screenHeight:Int,density:Float,insets:Insets,items:Int):IslandLayout {
        val d=density.takeIf { it.isFinite() }?.coerceAtLeast(.1f) ?: 1f
        val sw=screenWidth.coerceAtLeast(1);val sh=screenHeight.coerceAtLeast(1)
        val safeLeft=insets.left.coerceIn(0,sw-1)
        val safeRight=insets.right.coerceIn(0,sw-safeLeft-1)
        val margin=minOf((12*d).toInt(),(sw-safeLeft-safeRight)/4)
        val safeWidth=(sw-safeLeft-safeRight-margin*2).coerceAtLeast(1)
        val width=minOf((360*d).toInt().coerceAtLeast(1),safeWidth)
        val bottom=insets.bottom.coerceIn(0,sh-1)
        val top=(maxOf(insets.top,(24*d).toInt())+(8*d).toInt()).coerceIn(0,sh-bottom-1)
        val available=(sh-top-bottom-minOf((16*d).toInt(),(sh-top-bottom)/4)).coerceAtLeast(1)
        val rows=maxOf(1,(items.coerceIn(0,128)+1)/2)
        val height=minOf(((290+rows*54)*d).toInt().coerceAtLeast(1),available)
        return IslandLayout(safeLeft+margin+(safeWidth-width)/2,top,width,height,minOf(width,(232*d).toInt().coerceAtLeast(1)),minOf(height,(64*d).toInt().coerceAtLeast(1)))
    }
}

/** Attached subwindow of the existing status bar: no app overlay permission or replacement window. */
internal class IslandSummary(private val context:Context,private val interactive:Boolean=true,private val visibility:(Boolean)->Unit) {
    private val actionInFlight=AtomicBoolean(false)
    private val main=Handler(Looper.getMainLooper())
    private var popup:PopupWindow?=null
    private var content:SummaryCanvas?=null
    @Volatile private var state=IslandState()
    internal val currentView get()=content
    internal val windowSize get()=popup?.let { it.width to it.height }
    fun update(next:IslandState) { if(state!=next){state=next;content?.update(next)} }
    fun open(anchor:View,animate:Boolean):Boolean {
        if(popup!=null){content?.close();return true}
        if(!anchor.isAttachedToWindow||!anchor.isShown)return false
        try {
            val density=context.resources.displayMetrics.density
            val metrics=context.resources.displayMetrics
            val insets=anchor.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()) ?: Insets.NONE
            val layout=IslandGeometry.layout(metrics.widthPixels,metrics.heightPixels,density,insets,state.items.size)
            val top=layout.top
            val location=IntArray(2);anchor.getLocationOnScreen(location)
            val left=layout.leftFor(true)
            val initial=RectF((location[0]-left).toFloat(),location[1].toFloat(),(location[0]-left+anchor.width).toFloat(),(location[1]+anchor.height).toFloat())
            val view=SummaryCanvas(context,state,initial,animate,top.toFloat(),{ key -> if(interactive)performAction(key) },
                startCompact=true,expandedBodyHeight=layout.height.toFloat(),resizeWindow={ expanded ->
                    val compact=!expanded
                    try { popup?.update(layout.leftFor(compact),0,if(compact)layout.compactWidth else layout.width,(if(compact)layout.compactHeight else layout.height)+top) }
                    catch(t:Throwable) { L.w("Island resize unavailable=${t.javaClass.simpleName}") }
                }) { dismiss() }
            val window=PopupWindow(view,layout.compactWidth,layout.compactHeight+top,true).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true
                isClippingEnabled=false;setIsLaidOutInScreen(true);elevation=12*density
                setOnDismissListener { content?.dispose();content=null;popup=null;visibility(false) }
            }
            content=view;popup=window
            window.showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,left,0)
            visibility(true)
            L.i("Resumo: compacto; estados=${state.items.size}")
            return true
        } catch(t:Throwable) { L.w("Resumo: ${t.javaClass.simpleName}: ${t.message}");dismiss();return false }
    }
    private fun performAction(key:String) {
        val snapshot=state
        // Media/camera services can block in Binder. Never wait for them on SystemUI's draw thread,
        // and never enqueue an unbounded series of taps while a device service is unresponsive.
        if(key in listOf("play","previous","next","torch")) {
            if(!actionInFlight.compareAndSet(false,true))return
            try { serviceActions.execute { try { performServiceAction(key,snapshot) } finally { actionInFlight.set(false) } } }
            catch(t:Throwable) { actionInFlight.set(false);L.w("Island action=$key unavailable=${t.javaClass.simpleName}") }
            return
        }
        performServiceAction(key,snapshot)
    }
    private fun performServiceAction(key:String,snapshot:IslandState=state) {
        try {
            when(key) {
                "play","previous","next" -> {
                    val sessions=context.getSystemService(android.media.session.MediaSessionManager::class.java)?.getActiveSessions(null).orEmpty()
                    val source=snapshot.playback.sourcePackage
                    val eligible=if(source.isBlank())sessions else sessions.filter { it.packageName==source }
                    val controller=eligible.firstOrNull { it.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING } ?: eligible.firstOrNull()
                    if(controller==null){main.post { performServiceAction("volume") };return}
                    when(key){"previous"->controller.transportControls.skipToPrevious();"next"->controller.transportControls.skipToNext();else->if(snapshot.playback.playing)controller.transportControls.pause() else controller.transportControls.play()}
                }
                "torch" -> {
                    val camera=context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    val id=camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
                    if(id!=null)camera.setTorchMode(id,!snapshot.torch)
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
        private val initial:RectF=RectF(0f,0f,48f,48f),private val animate:Boolean=true,private val insetTop:Float=0f,private val action:(String)->Unit={},
        startCompact:Boolean=false,private val expandedBodyHeight:Float?=null,private val resizeWindow:(Boolean)->Unit={},private val dismissed:()->Unit):View(context) {
        private val d=resources.displayMetrics.density
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val text=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        private val iconPainter=StatusIconPainter()
        private var amount=if(animate)0f else 1f
        private var animator:ValueAnimator?=null
        private var expansion=if(startCompact)0f else 1f
        private var expansionAnimator:ValueAnimator?=null
        internal var isExpanded=!startCompact;private set
        private var scroll=0f
        private var lastY=0f
        private var downY=0f
        private var downX=0f
        private var dragged=false
        private var gridGesture=false
        private var cancelledGesture=false
        private var pointerId=-1
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
            describe()
            scroll=scroll.coerceIn(0f,maxScroll())
            invalidate()
            scheduleMotion()
        }
        private fun describe() {
            contentDescription=UiText.format("Estados ativos. Bateria {0} por cento. ", state.battery)+
                state.items.joinToString { "${it.title}: ${it.detail}" }+UiText.t(if(isExpanded)". Arraste para cima para recolher." else ". Arraste para baixo para expandir.")
        }
        private fun bodyHeight()=(expandedBodyHeight ?: (height-insetTop)).coerceAtLeast(1f)
        private fun compactHeight()=minOf(64*d,bodyHeight())
        private fun compactBounds():RectF {
            val pad=minOf(d,width/4f,compactHeight()/4f)
            val w=minOf(232*d,width.toFloat()-2*pad).coerceAtLeast(.5f)
            return RectF((width-w)/2,insetTop+pad,(width+w)/2,insetTop+compactHeight()-pad)
        }
        internal fun displayedBounds():RectF {
            val p=EffectTimeline.smooth(expansion);val compact=compactBounds()
            val pad=minOf(d,width/4f,bodyHeight()/4f)
            fun lerp(x:Float,y:Float)=x+(y-x)*p
            return RectF(lerp(compact.left,pad),lerp(compact.top,insetTop+pad),lerp(compact.right,width-pad),lerp(compact.bottom,insetTop+bodyHeight()-pad))
        }
        internal fun setExpanded(expanded:Boolean) {
            if(isExpanded==expanded)return
            isExpanded=expanded;describe();expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();expansionAnimator=null
            if(expanded)resizeWindow(true)
            val to=if(expanded)1f else 0f
            if(!animate){expansion=to;if(!expanded)resizeWindow(false);invalidate();scheduleMotion();return}
            expansionAnimator=ValueAnimator.ofFloat(expansion,to).apply {
                duration=280;interpolator=android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { expansion=it.animatedValue as Float;invalidate();scheduleMotion() }
                addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(animation:android.animation.Animator){if(!isExpanded&&!disposed)resizeWindow(false)}})
                start()
            }
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        private fun needsMotion()= !disposed&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&amount>.95f&&
            movingGlyphVisible&&(state.playback.playing||state.items.any { it.icon==SlotIcon.RECORD||it.icon==SlotIcon.RECORD_TIME||(animate&&it.icon==SlotIcon.WIFI_OFFLINE) })
        private fun scheduleMotion() {
            if(!needsMotion()){removeCallbacks(motion);motionPosted=false;return}
            if(!motionPosted){motionPosted=true;postDelayed(motion,if(!animate)1000L else if(state.items.any {it.icon==SlotIcon.WIFI_OFFLINE})33L else 100L)}
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
        fun dispose() {
            disposed=true;removeCallbacks(motion);motionPosted=false
            animator?.removeAllListeners();animator?.cancel();animator=null
            expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();expansionAnimator=null
        }
        override fun onDetachedFromWindow() { dispose();super.onDetachedFromWindow() }
        override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,UiText.t("Fechar")))
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000020,UiText.t(if(isExpanded)"Recolher" else "Expandir")))
            if(isExpanded&&maxScroll()>0) { info.isScrollable=true;info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
            listOf("Anterior","Reproduzir ou pausar","Próxima","Abrir rede","Abrir Bluetooth","Abrir volume","Alternar lanterna").forEachIndexed { index,label ->
                if(isExpanded||(index==1&&hasMedia()))info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000000+index,UiText.t(label)))
            }
        }
        override fun performAccessibilityAction(id:Int,args:android.os.Bundle?):Boolean {
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)return performClick()
            if(id==0x01000020){setExpanded(!isExpanded);return true}
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                if(!isExpanded)return false
                val direction=if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1
                scroll=(scroll+direction*maxOf(54*d,bodyHeight()-gridTop()-82*d)).coerceIn(0f,maxScroll());invalidate();return true
            }
            val index=id-0x01000000
            if(index in 0..6&&(isExpanded||(index==1&&hasMedia()))){action(listOf("previous","play","next","wifi","bluetooth","volume","torch")[index]);return true}
            return super.performAccessibilityAction(id,args)
        }
        override fun performClick():Boolean { super.performClick();close();return true }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{lastY=event.y;downY=event.y;downX=event.x;dragged=false;cancelledGesture=false;pointerId=event.getPointerId(0);gridGesture=isExpanded&&event.y-insetTop in gridTop()..(bodyHeight()-72*d);return true}
                MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_POINTER_UP->{cancelledGesture=true;dragged=true;isPressed=false;return true}
                MotionEvent.ACTION_MOVE->{
                    if(cancelledGesture||event.pointerCount!=1||event.getPointerId(0)!=pointerId)return true
                    if(kotlin.math.abs(event.y-downY)>touchSlop||kotlin.math.abs(event.x-downX)>touchSlop)dragged=true
                    if(gridGesture&&dragged){scroll=(scroll+lastY-event.y).coerceIn(0f,maxScroll());invalidate()};lastY=event.y;return true
                }
                MotionEvent.ACTION_UP->{
                    if(cancelledGesture||event.pointerCount!=1||event.getPointerId(0)!=pointerId){isPressed=false;return true}
                    val dy=event.y-downY;val dx=event.x-downX
                    if(dragged&&!gridGesture&&kotlin.math.abs(dy)>40*d&&kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.3f) {
                        if(!isExpanded&&dy>0)setExpanded(true) else if(isExpanded&&dy<0)setExpanded(false)
                    } else if(!dragged&&kotlin.math.abs(dy)<=touchSlop&&kotlin.math.abs(dx)<=touchSlop&&amount>.95f&&(expansion<.05f||expansion>.95f)) {
                        if(isExpanded&&downX>width-60*d&&downY>=insetTop&&downY<insetTop+65*d)performClick()
                        else {
                            val hit=hits.entries.firstOrNull { it.value.contains(event.x,event.y-insetTop) }
                            if(hit!=null)action(hit.key) else if(!isExpanded&&compactBounds().contains(event.x,event.y))setExpanded(true)
                        }
                    };isPressed=false;return true
                }
                MotionEvent.ACTION_CANCEL->{cancelledGesture=true;dragged=true;pointerId=-1;isPressed=false;return true}
            }
            return false
        }
        private fun gridTop()=if(bodyHeight()>=310*d)205*d else if(bodyHeight()>=190*d)128*d else 80*d
        internal fun actionBounds(key:String):RectF?=hits[key]?.let { RectF(it).apply { offset(0f,insetTop) } }
        private fun maxScroll()=maxOf(0f,((state.items.size+1)/2)*54*d-(bodyHeight()-72*d-gridTop()))
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val a=EffectTimeline.smooth(amount)
            val p=EffectTimeline.smooth(expansion)
            fun lerp(x:Float,y:Float)=x+(y-x)*a
            val target=displayedBounds()
            val rect=RectF(lerp(initial.left,target.left),lerp(initial.top,target.top),lerp(initial.right,target.right),lerp(initial.bottom,target.bottom))
            val radius=lerp(minOf(initial.width(),initial.height())/2,(32+6*p)*d)
            paint.style=Paint.Style.FILL;paint.color=0xFF050505.toInt();canvas.drawRoundRect(rect,radius,radius,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=d;paint.color=0x22FFFFFF;canvas.drawRoundRect(rect,radius,radius,paint)
            val save=canvas.save();val clip=Path().apply { addRoundRect(rect,radius,radius,Path.Direction.CW) };canvas.clipPath(clip)
            canvas.translate(0f,insetTop)
            val bodyHeight=bodyHeight()
            // A single opacity owner covers all card/text/icon paints during the morph.
            canvas.saveLayerAlpha(0f,0f,width.toFloat(),bodyHeight,(255*((a-.35f)/.65f).coerceIn(0f,1f)).toInt())
            val alpha=1f
            movingGlyphVisible=false
            hits.clear()
            if(p<.45f) {
                val compactSave=canvas.saveLayerAlpha(0f,0f,width.toFloat(),compactHeight(),(255*(1-EffectTimeline.smooth(p/.45f))).toInt())
                drawCompact(canvas);canvas.restoreToCount(compactSave)
            }
            if(p>.55f) {
            val expandedSave=canvas.saveLayerAlpha(0f,0f,width.toFloat(),bodyHeight,(255*EffectTimeline.smooth((p-.55f)/.45f)).toInt())
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
            iconPainter.draw(canvas,SlotIcon.MEDIA,Color.WHITE,alpha,EffectFrame(motionMs=android.os.SystemClock.uptimeMillis(),musicPlaying=state.playback.playing&&animate,motionEnabled=animate))
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
                    if(glyph==SlotIcon.RECORD||(animate&&glyph==SlotIcon.WIFI_OFFLINE)||(glyph==SlotIcon.MEDIA&&state.playback.playing))movingGlyphVisible=true
                    iconPainter.draw(canvas,glyph,Color.WHITE,alpha,EffectFrame(networkText=item.detail.takeIf { item.icon==SlotIcon.NETWORK } ?: "",motionMs=android.os.SystemClock.uptimeMillis(),headphoneBattery=-1,musicPlaying=state.playback.playing&&animate,motionEnabled=animate))
                    canvas.restoreToCount(mini)
                    text.textSize=11*d;text.color=Color.WHITE;text.alpha=(255*alpha).toInt()
                    canvas.drawText(shorten(item.title,cell-51*d),x+43*d,y+20*d,text)
                    text.textSize=9*d;text.color=0xFF8D8D93.toInt();canvas.drawText(shorten(item.detail,cell-51*d),x+43*d,y+35*d,text)
                }
            }
            canvas.restoreToCount(expandedSave)
            }
            canvas.restoreToCount(save)
            scheduleMotion()
        }
        private fun hasMedia()=state.playback.playing||state.playback.title.isNotBlank()
        private fun drawCompact(canvas:Canvas) {
            val rect=compactBounds().apply { offset(0f,-insetTop) };val centerY=rect.centerY()
            val active=state.primaryItem()
            val glyph=active?.icon ?: if(state.playback.playing)SlotIcon.MEDIA else SlotIcon.BOLT
            movingGlyphVisible=state.playback.playing||glyph==SlotIcon.RECORD||(animate&&glyph==SlotIcon.WIFI_OFFLINE)
            val s=canvas.save();canvas.translate(rect.left+29*d,centerY);canvas.scale(.5f*d,.5f*d)
            iconPainter.draw(canvas,glyph,Color.WHITE,1f,EffectFrame(motionEnabled=animate,musicPlaying=state.playback.playing&&animate,motionMs=android.os.SystemClock.uptimeMillis(),networkText=active?.detail.takeIf { glyph==SlotIcon.NETWORK } ?: ""))
            canvas.restoreToCount(s)
            text.textAlign=Paint.Align.LEFT;text.color=Color.WHITE;text.alpha=255;text.textSize=12*d
            val title=if(glyph==SlotIcon.MEDIA&&hasMedia())state.playback.title.ifBlank { UiText.t("Música") } else active?.title ?: UiText.t("Seu resumo")
            canvas.drawText(shorten(title,rect.width()-112*d),rect.left+54*d,centerY-4*d,text)
            text.textSize=9*d;text.color=0xFF8D8D93.toInt()
            val detail=if(glyph==SlotIcon.MEDIA&&hasMedia())UiText.t(if(state.playback.playing)"Reproduzindo" else "Pausado") else if(glyph in setOf(SlotIcon.RECORD,SlotIcon.MICROPHONE))active?.detail.orEmpty() else if(state.charging)UiText.t("Carregando") else UiText.format("{0} estados ativos",state.items.size)
            canvas.drawText(shorten(detail,rect.width()-112*d),rect.left+54*d,centerY+12*d,text)
            if(hasMedia()) {
                val x=rect.right-28*d;hits["play"]=RectF(x-24*d,centerY-24*d,x+24*d,centerY+24*d)
                paint.style=Paint.Style.FILL;paint.color=Color.WHITE
                if(state.playback.playing) {canvas.drawRoundRect(RectF(x-6*d,centerY-7*d,x-2*d,centerY+7*d),d,d,paint);canvas.drawRoundRect(RectF(x+2*d,centerY-7*d,x+6*d,centerY+7*d),d,d,paint)}
                else canvas.drawPath(Path().apply { moveTo(x-5*d,centerY-8*d);lineTo(x+7*d,centerY);lineTo(x-5*d,centerY+8*d);close() },paint)
            } else {
                text.textAlign=Paint.Align.RIGHT;text.textSize=15*d;text.color=if(state.battery in 0..15)0xFFFF453A.toInt() else 0xFF30D158.toInt()
                canvas.drawText(if(state.battery<0)"—" else "${state.battery}%",rect.right-15*d,centerY+5*d,text);text.textAlign=Paint.Align.LEFT
            }
            paint.style=Paint.Style.FILL;paint.color=0xFF505054.toInt()
            canvas.drawRoundRect(RectF(rect.centerX()-10*d,rect.bottom-5*d,rect.centerX()+10*d,rect.bottom-3*d),d,d,paint)
        }
        private fun shorten(value:String,width:Float):String {
            if(width<=0f)return ""
            return android.text.TextUtils.ellipsize(value,text,width,android.text.TextUtils.TruncateAt.END).toString()
        }
    }
}
