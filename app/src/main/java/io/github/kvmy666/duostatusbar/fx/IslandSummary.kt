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

internal data class IslandItem(val icon:SlotIcon,val title:String,val detail:String=UiText.t("Ativo"),
    val sourcePackage:String="",val elapsedMs:Long=-1,val remainingMs:Long=-1,val timerRunning:Boolean=false)
internal data class IslandState(val battery:Int=-1,val charging:Boolean=false,val items:List<IslandItem> = emptyList(),
    val playback:PlaybackSnapshot=PlaybackSnapshot(),val chargeRemainingMs:Long=-1,val headphoneBattery:Int=-1,val torch:Boolean=false,
    val foreground:SlotIcon?=null,val aurora:AuroraOptions=AuroraOptions(),val communication:Boolean=false,
    val updatedAtElapsed:Long=android.os.SystemClock.elapsedRealtime(),val appearance:IslandAppearance=IslandAppearance()) {
    fun primaryItem():IslandItem? = items.firstOrNull {it.icon==foreground}
        ?: items.firstOrNull {playback.playing&&it.icon==SlotIcon.MEDIA}
        ?: items.firstOrNull {it.icon==SlotIcon.RECORD}
        ?: items.firstOrNull {it.icon==SlotIcon.MICROPHONE}
        ?: items.firstOrNull()
    fun secondaryItems():List<IslandItem> {
        val primary=primaryItem()?.icon?.let(::islandGlyph)
        return items.distinctBy {islandGlyph(it.icon)}.filter {islandGlyph(it.icon)!=primary}
    }
}
private val ACTIVITY_ICONS=setOf(SlotIcon.MEDIA,SlotIcon.RECORD,SlotIcon.RECORD_TIME,SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH,SlotIcon.CAMERA,SlotIcon.MICROPHONE)
private fun islandGlyph(icon:SlotIcon)=when(icon){SlotIcon.RECORD_TIME->SlotIcon.RECORD;SlotIcon.CHARGE_TIME->SlotIcon.BOLT;else->icon}

internal data class IslandLayout(val left:Int,val top:Int,val width:Int,val height:Int,val compactWidth:Int,val compactHeight:Int) {
    fun leftFor(compact:Boolean)=left+if(compact)(width-compactWidth)/2 else 0
}
internal object IslandGeometry {
    fun layout(screenWidth:Int,screenHeight:Int,density:Float,insets:Insets,items:Int,aurora:AuroraOptions=AuroraOptions()):IslandLayout {
        val d=density.takeIf { it.isFinite() }?.coerceAtLeast(.1f) ?: 1f
        val sw=screenWidth.coerceAtLeast(1);val sh=screenHeight.coerceAtLeast(1)
        val safeLeft=insets.left.coerceIn(0,sw-1)
        val safeRight=insets.right.coerceIn(0,sw-safeLeft-1)
        val margin=minOf((12*d).toInt(),(sw-safeLeft-safeRight)/4)
        val safeWidth=(sw-safeLeft-safeRight-margin*2).coerceAtLeast(1)
        val landscape=aurora.landscapeLayout&&sw>sh
        val width=minOf(((if(landscape)500 else 352)*d).toInt().coerceAtLeast(1),safeWidth)
        val bottom=insets.bottom.coerceIn(0,sh-1)
        val top=(maxOf(insets.top,(24*d).toInt())+(8*d).toInt()).coerceIn(0,sh-bottom-1)
        val available=(sh-top-bottom-minOf((16*d).toInt(),(sh-top-bottom)/4)).coerceAtLeast(1)
        val rows=maxOf(1,(items.coerceIn(0,128)+1)/2)
        val wanted=if(landscape)240 else if(aurora.threeLevels)minOf(420,258+rows*54) else minOf(420,258+rows*54)
        val height=minOf((wanted*d).toInt().coerceAtLeast(1),available)
        return IslandLayout(safeLeft+margin+(safeWidth-width)/2,top,width,height,minOf(width,(224*d).toInt().coerceAtLeast(1)),minOf(height,(56*d).toInt().coerceAtLeast(1)))
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
            val layout=IslandGeometry.layout(metrics.widthPixels,metrics.heightPixels,density,insets,state.items.size,state.aurora)
            val top=layout.top
            // Keep one horizontal coordinate space for the whole gesture. WindowManager applies
            // position and size asynchronously; changing both caused a sideways jump on One UI.
            val left=layout.left
            // Enter from a shallow centered pose instead of translating from a clipped edge icon.
            val initial=RectF(layout.width/2f-layout.compactWidth*.42f,top+layout.compactHeight*.12f,
                layout.width/2f+layout.compactWidth*.42f,top+layout.compactHeight*.88f)
            val view=SummaryCanvas(context,state,initial,animate,top.toFloat(),{ key -> if(interactive)performAction(key) },
                startCompact=true,expandedBodyHeight=layout.height.toFloat(),resizeWindow={ expanded ->
                    val compact=!expanded
                    try {
                        popup?.update(left,0,layout.width,(if(compact)layout.compactHeight else layout.height)+top)
                    }
                    catch(t:Throwable) { L.w("Island resize unavailable=${t.javaClass.simpleName}") }
                }) { dismiss() }
            val window=PopupWindow(view,layout.width,layout.compactHeight+top,true).apply {
                animationStyle=0 // Canvas owns the transition; do not add an OEM window animation.
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true
                isClippingEnabled=false;setIsLaidOutInScreen(true);elevation=12*density
                setOnDismissListener { content?.dispose();content=null;popup=null;visibility(false) }
            }
            content=view;popup=window
            window.showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,left,0)
            applyWindowBackdrop()
            visibility(true)
            L.i("Resumo: compacto; estados=${state.items.size}")
            return true
        } catch(t:Throwable) { L.w("Resumo: ${t.javaClass.simpleName}: ${t.message}");dismiss();return false }
    }
    private fun applyWindowBackdrop() {
        val root=content?.rootView ?: return
        val params=root.layoutParams as? WindowManager.LayoutParams ?: return
        try {
            val manager=context.getSystemService(WindowManager::class.java) ?: return
            // Compositor blur is requested only while this subwindow exists. Dismissal removes it;
            // no wallpaper capture, CPU bitmap blur or permission escalation is used.
            params.flags=params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount=.10f
            if(manager.isCrossWindowBlurEnabled){params.flags=params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND;params.setBlurBehindRadius((20*context.resources.displayMetrics.density).toInt())}
            manager.updateViewLayout(root,params)
        } catch(t:Throwable){L.w("Island backdrop unavailable=${t.javaClass.simpleName}")}
    }
    private fun performAction(key:String) {
        val snapshot=state
        if(key.startsWith("source:")) {
            val source=key.removePrefix("source:")
            if(!snapshot.aurora.openSource||source.isBlank()||snapshot.items.none {it.sourcePackage==source})return
            try { context.packageManager.getLaunchIntentForPackage(source)?.let { dismiss();context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            catch(t:Throwable){L.w("Island source unavailable=${t.javaClass.simpleName}")}
            return
        }
        if(key in listOf("timerPause","timerPlus","stopwatchPause","stopwatchReset")) {
            if(snapshot.aurora.contextActions&&snapshot.aurora.timer)AuroraActivities.command(key)
            return
        }
        if(key in listOf("play","previous","next")&&!snapshot.playback.playing&&snapshot.playback.title.isBlank()&&snapshot.playback.sourcePackage.isBlank())return
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
                    if(controller==null){L.w("Island action=$key unavailable=NoMediaSession");return}
                    when(key){"previous"->controller.transportControls.skipToPrevious();"next"->controller.transportControls.skipToNext();else->if(controller.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING)controller.transportControls.pause() else controller.transportControls.play()}
                }
                "torch" -> {
                    val camera=context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    val id=camera?.cameraIdList?.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)==true }
                    if(id!=null)camera.setTorchMode(id,!snapshot.torch)
                }
                "wifi"->launch(android.provider.Settings.ACTION_WIFI_SETTINGS)
                "bluetooth"->launch(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                "nfc"->launch(android.provider.Settings.ACTION_NFC_SETTINGS)
                "call"->if(snapshot.aurora.contextActions&&snapshot.communication)launch(android.content.Intent.ACTION_DIAL)
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
        private val glass=IslandGlassPainter(d)
        private val coverPainter=AlbumCoverPainter()
        private val decoration=CoverEffectPainter()
        private val clipOutline=Path()
        private var coverMotionVisible=false
        private var pressedKey:String?=null
        private var amount=if(animate)0f else 1f
        private var animator:ValueAnimator?=null
        private var expansion=if(startCompact)0f else 1f
        internal var expansionLevel=if(startCompact)0 else 2;private set
        private var expansionAtDown=0f
        private var expansionAnimator:ValueAnimator?=null
        private var windowExpanded=!startCompact
        internal var isExpanded=!startCompact;private set
        private var scroll=0f
        private var lastY=0f
        private var downY=0f
        private var downX=0f
        private var levelAtDown=expansionLevel
        private var dragged=false
        private var gridGesture=false
        private var cancelledGesture=false
        private var pointerId=-1
        private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
        private var disposed=false
        private var motionPosted=false
        private var ready=false
        private var movingGlyphVisible=false
        private var offlineGlyphVisible=false
        private var timedGlyphVisible=false
        private var mediaProgressVisible=false
        private val sourceNames=linkedMapOf<String,String>()
        private val motion=object:Runnable {
            override fun run() { motionPosted=false;if(needsMotion()){invalidate();scheduleMotion()} }
        }
        private val hits=linkedMapOf<String,RectF>()
        init { isFocusable=true;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;ready=true;update(state) }
        fun update(next:IslandState) {
            state=next
            if(next.aurora.privacyOrigins)next.items.filter {it.icon in setOf(SlotIcon.CAMERA,SlotIcon.MICROPHONE)}.map {it.sourcePackage}.filter {it.isNotBlank()&&!sourceNames.containsKey(it)}.forEach {source->
                sourceNames[source]=runCatching {context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(source,0)).toString().replace('\n',' ').replace('\r',' ').take(60)}.getOrDefault(source)
                while(sourceNames.size>128)sourceNames.remove(sourceNames.keys.first())
            }
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
        private fun compactHeight()=minOf(56*d,bodyHeight())
        private fun compactBounds():RectF {
            val pad=minOf(d,width/4f,compactHeight()/4f)
            val w=minOf(224*d,width.toFloat()-2*pad).coerceAtLeast(.5f)
            return RectF((width-w)/2,insetTop+pad,(width+w)/2,insetTop+compactHeight()-pad)
        }
        internal fun displayedBounds():RectF {
            val p=expansion.coerceIn(0f,1f);val compact=compactBounds()
            val pad=minOf(d,width/4f,bodyHeight()/4f)
            fun lerp(x:Float,y:Float)=x+(y-x)*p
            if(state.aurora.threeLevels){
                val quick=minOf(230*d,bodyHeight())
                val bottom=if(p<=.5f)compact.bottom+(insetTop+quick-pad-compact.bottom)*p*2 else insetTop+quick-pad+(bodyHeight()-quick)*(p-.5f)*2
                val w=(p*2).coerceAtMost(1f)
                return RectF(compact.left+(pad-compact.left)*w,compact.top,compact.right+(width-pad-compact.right)*w,bottom)
            }
            return RectF(lerp(compact.left,pad),lerp(compact.top,insetTop+pad),lerp(compact.right,width-pad),lerp(compact.bottom,insetTop+bodyHeight()-pad))
        }
        internal fun setExpanded(expanded:Boolean)=setLevel(if(expanded)2 else 0)
        internal fun setLevel(level:Int) {
            val wanted=if(state.aurora.threeLevels)level.coerceIn(0,2)else if(level>0)2 else 0
            val to=wanted/2f
            if(expansionLevel==wanted&&kotlin.math.abs(expansion-to)<.001f){requestWindow(wanted>0);return}
            expansionLevel=wanted;isExpanded=wanted>0;describe();expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();expansionAnimator=null
            if(isExpanded)requestWindow(true)
            if(!animate){expansion=to;if(!isExpanded)requestWindow(false);invalidate();scheduleMotion();return}
            expansionAnimator=ValueAnimator.ofFloat(expansion,to).apply {
                duration=(280*kotlin.math.abs(expansion-to)).toLong().coerceIn(140,280);interpolator=android.view.animation.PathInterpolator(.2f,.8f,.2f,1f)
                addUpdateListener { expansion=it.animatedValue as Float;invalidate();scheduleMotion() }
                addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(animation:android.animation.Animator){if(!isExpanded&&!disposed)requestWindow(false)}})
                start()
            }
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
        private fun requestWindow(expanded:Boolean) {
            if(windowExpanded==expanded)return
            windowExpanded=expanded;resizeWindow(expanded)
        }
        private fun timedItem(item:IslandItem?)=item?.icon==SlotIcon.CALL||(item?.icon in setOf(SlotIcon.TIMER,SlotIcon.STOPWATCH)&&item?.timerRunning==true)
        private fun timedActive()=timedGlyphVisible
        private fun needsMotion()= !disposed&&isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&amount>.95f&&
            (timedActive()||coverMotionVisible||mediaProgressVisible||movingGlyphVisible&&(state.playback.playing||state.items.any { it.icon==SlotIcon.RECORD||it.icon==SlotIcon.RECORD_TIME||(animate&&it.icon==SlotIcon.WIFI_OFFLINE) }))
        private fun scheduleMotion() {
            if(!needsMotion()){removeCallbacks(motion);motionPosted=false;return}
            if(!motionPosted){motionPosted=true;postOnAnimationDelayed(motion,motionDelayMs())}
        }
        private fun motionDelayMs()=if(animate&&(coverMotionVisible||mediaProgressVisible&&state.appearance.waveProgress))33L else if(!animate||!movingGlyphVisible)1000L else 33L
        override fun onAttachedToWindow() { super.onAttachedToWindow();disposed=false;if(animate)move(1f);scheduleMotion() }
        override fun onWindowVisibilityChanged(visibility:Int) { super.onWindowVisibilityChanged(visibility);if(ready)scheduleMotion() }
        override fun onVisibilityChanged(changedView:View,visibility:Int) { super.onVisibilityChanged(changedView,visibility);if(ready)scheduleMotion() }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
            super.onSizeChanged(w,h,oldw,oldh)
            // Never let a tap target from the previous window size survive until a redraw.
            hits.clear()
            if(ready)scroll=scroll.coerceIn(0f,maxScroll())
        }
        private fun move(to:Float) {
            animator?.removeAllListeners();animator?.cancel()
            animator=ValueAnimator.ofFloat(amount,to).apply {
                duration=if(to==1f)320 else 220
                interpolator=android.view.animation.PathInterpolator(.2f,.8f,.2f,1f)
                addUpdateListener { amount=it.animatedValue as Float;invalidate();scheduleMotion() }
                if(to==0f)addListener(object:android.animation.AnimatorListenerAdapter(){ override fun onAnimationEnd(animation:android.animation.Animator){dismissed()} })
                start()
            }
        }
        fun close() { if(disposed)return;disposed=true;removeCallbacks(motion);motionPosted=false;expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();if(animate)move(0f) else dismissed() }
        fun dispose() {
            disposed=true;removeCallbacks(motion);motionPosted=false
            animator?.removeAllListeners();animator?.cancel();animator=null
            expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();expansionAnimator=null
        }
        override fun onDetachedFromWindow() { dispose();super.onDetachedFromWindow() }
        override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,UiText.t("Fechar")))
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000020,UiText.t(if(expansionLevel==2)"Recolher" else "Expandir")))
            if(isExpanded&&maxScroll()>0) { info.isScrollable=true;info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
            listOf("Anterior","Reproduzir ou pausar","Próxima","Abrir Wi-Fi","Abrir Bluetooth","Abrir NFC","Alternar lanterna").forEachIndexed { index,label ->
                val key=listOf("previous","play","next","wifi","bluetooth","nfc","torch")[index]
                if(actionAvailable(key)&&((index>=3&&isExpanded)||(index<3&&hasMedia()&&(isExpanded||index==1))))info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000000+index,UiText.t(label)))
            }
            auroraActionKeys().forEach {key->if(actionAvailable(key))info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01000030+auroraActionIndex(key),actionLabel(key)))}
        }
        override fun performAccessibilityAction(id:Int,args:android.os.Bundle?):Boolean {
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)return performClick()
            if(id==0x01000020){setLevel(if(state.aurora.threeLevels)(expansionLevel+1)%3 else if(isExpanded)0 else 2);return true}
            if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                if(!isExpanded)return false
                val direction=if(id==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)1 else -1
                scroll=(scroll+direction*maxOf(54*d,bodyHeight()-gridTop()-82*d)).coerceIn(0f,maxScroll());invalidate();return true
            }
            val index=id-0x01000000
            if(index in 0..6&&((index>=3&&isExpanded)||(index<3&&hasMedia()&&(isExpanded||index==1)))){
                val key=listOf("previous","play","next","wifi","bluetooth","nfc","torch")[index]
                if(!actionAvailable(key))return false
                action(key);return true
            }
            auroraActionKeys().firstOrNull {auroraActionIndex(it)==id-0x01000030&&actionAvailable(it)}?.let {action(it);return true}
            return super.performAccessibilityAction(id,args)
        }
        override fun performClick():Boolean { super.performClick();close();return true }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            if(disposed)return true
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{
                    if(amount<1f)move(1f);expansionAnimator?.removeAllListeners();expansionAnimator?.cancel()
                    pressedKey=hits.entries.firstOrNull {it.value.contains(event.x,event.y-insetTop)}?.key;invalidate()
                    // Local x changes as PopupWindow widens. Only screen coordinates describe a
                    // continuous finger gesture across that layout change.
                    lastY=event.rawY;downY=event.rawY;downX=event.rawX;expansionAtDown=expansion;levelAtDown=expansionLevel;dragged=false;cancelledGesture=false;pointerId=event.getPointerId(0)
                    gridGesture=expansionLevel==2&&event.y-insetTop in gridTop()..(bodyHeight()-72*d)&&(!landscape()||event.x>=width*.51f);return true
                }
                MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_POINTER_UP->{pressedKey=null;cancelledGesture=true;dragged=true;isPressed=false;setLevel(levelAtDown);return true}
                MotionEvent.ACTION_MOVE->{
                    if(cancelledGesture||event.pointerCount!=1||event.getPointerId(0)!=pointerId)return true
                    if(kotlin.math.abs(event.rawY-downY)>touchSlop||kotlin.math.abs(event.rawX-downX)>touchSlop){dragged=true;pressedKey=null}
                    if(gridGesture&&dragged){scroll=(scroll+lastY-event.rawY).coerceIn(0f,maxScroll());invalidate()}
                    else if(dragged&&state.aurora.fingerExpansion&&kotlin.math.abs(event.rawY-downY)>kotlin.math.abs(event.rawX-downX)*1.3f){
                        expansionAnimator?.removeAllListeners();expansionAnimator?.cancel();requestWindow(true)
                        expansion=(expansionAtDown+(event.rawY-downY)/maxOf(80*d,minOf(220*d,bodyHeight()-compactHeight()))).coerceIn(0f,1f);invalidate()
                    };lastY=event.rawY;return true
                }
                MotionEvent.ACTION_UP->{
                    val releasedKey=pressedKey;pressedKey=null;invalidate()
                    if(cancelledGesture||event.pointerCount!=1||event.getPointerId(0)!=pointerId){isPressed=false;return true}
                    val dy=event.rawY-downY;val dx=event.rawX-downX
                    if(dragged&&!gridGesture&&kotlin.math.abs(dy)>touchSlop&&kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.3f) {
                        if(state.aurora.fingerExpansion){
                            val nearest=if(state.aurora.threeLevels)kotlin.math.round(expansion*2).toInt()else if(expansion>.5f)2 else 0
                            val step=if(state.aurora.threeLevels)1 else 2
                            setLevel(if(nearest==levelAtDown&&kotlin.math.abs(dy)>40*d)(levelAtDown+if(dy>0)step else -step).coerceIn(0,2)else nearest)
                        }
                        else if(kotlin.math.abs(dy)>40*d)setLevel(if(state.aurora.threeLevels)(expansionLevel+if(dy>0)1 else -1).coerceIn(0,2)else if(dy>0)2 else 0)
                    } else if(dragged&&!gridGesture) {
                        setLevel(levelAtDown)
                    } else if(!dragged&&kotlin.math.abs(dy)<=touchSlop&&kotlin.math.abs(dx)<=touchSlop&&amount>.95f) {
                        if(!displayedBounds().contains(event.x,event.y))performClick()
                        else {
                            val hit=hits.entries.firstOrNull { it.value.contains(event.x,event.y-insetTop) }
                            // A live state may replace controls while the finger is down. Never
                            // dispatch the new action that appeared beneath a stationary finger.
                            if(hit?.key==releasedKey&&releasedKey!=null) {
                                if(releasedKey=="close")performClick()else if(actionAvailable(releasedKey))action(releasedKey)
                            } else if(releasedKey==null&&hit==null&&!isExpanded&&compactBounds().contains(event.x,event.y))setLevel(if(state.aurora.threeLevels)1 else 2)
                        }
                    };isPressed=false;return true
                }
                MotionEvent.ACTION_CANCEL->{pressedKey=null;invalidate();cancelledGesture=true;dragged=true;pointerId=-1;isPressed=false;setLevel(levelAtDown);return true}
            }
            return false
        }
        private fun actionAvailable(key:String):Boolean=when {
            key in listOf("previous","play","next")->hasMedia()&&(!state.aurora.contextActions||state.primaryItem()?.icon==SlotIcon.MEDIA)
            key.startsWith("source:")->state.aurora.openSource&&state.items.any {it.sourcePackage==key.removePrefix("source:")}
            key.startsWith("timer")->state.aurora.contextActions&&state.primaryItem()?.icon==SlotIcon.TIMER
            key.startsWith("stopwatch")->state.aurora.contextActions&&state.primaryItem()?.icon==SlotIcon.STOPWATCH
            key=="call"->state.aurora.contextActions&&state.communication
            else->true
        }
        private fun auroraActionKeys():List<String> {
            val item=state.primaryItem()
            val keys=if(isExpanded&&state.aurora.contextActions)when(item?.icon){
                SlotIcon.TIMER->emptyList()
                SlotIcon.STOPWATCH->emptyList()
                SlotIcon.CALL->listOf("call")
                else->emptyList()
            }else emptyList()
            return keys+if(state.aurora.openSource&&item?.sourcePackage?.isNotBlank()==true)listOf("source:${item.sourcePackage}")else emptyList()
        }
        private fun auroraActionIndex(key:String)=if(key.startsWith("source:"))5 else listOf("timerPause","timerPlus","stopwatchPause","stopwatchReset","call").indexOf(key)
        private fun actionLabel(key:String)=when {
            key.startsWith("source:")->AuroraText.t("Abrir aplicativo da atividade","Open activity app","Abrir aplicación de la actividad")
            key=="timerPlus"->AuroraText.t("Adicionar 15 segundos","Add 15 seconds","Añadir 15 segundos")
            key=="stopwatchReset"->AuroraText.t("Zerar cronômetro","Reset stopwatch","Reiniciar cronómetro")
            key=="call"->AuroraText.t("Abrir telefone","Open phone","Abrir teléfono")
            else->AuroraText.t("Pausar ou retomar relógio","Pause or resume clock","Pausar o reanudar reloj")
        }
        private fun auroraBody()=true
        private fun landscape()=state.aurora.landscapeLayout&&width>bodyHeight()*1.4f
        private fun condensed()=!landscape()&&bodyHeight()<310*d
        private fun gridItems()=if(hasHero())state.secondaryItems()else state.items.distinctBy {islandGlyph(it.icon)}
        private fun hasHero()=state.primaryItem()?.icon in ACTIVITY_ICONS||(state.primaryItem()==null&&hasMedia())
        private fun gridTop()=(if(!hasHero()||landscape())52 else if(condensed())148 else 178)*d
        internal fun actionBounds(key:String):RectF?=hits[key]?.takeIf {actionAvailable(key)}?.let { RectF(it).apply { offset(0f,insetTop) } }
        private fun maxScroll()=maxOf(0f,(((if(auroraBody())gridItems().size else state.items.size)+1)/2)*54*d-(bodyHeight()-72*d-gridTop()))
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val a=amount.coerceIn(0f,1f);val p=expansion.coerceIn(0f,1f)
            fun lerp(x:Float,y:Float)=x+(y-x)*a
            val target=displayedBounds()
            val rect=RectF(lerp(initial.left,target.left),lerp(initial.top,target.top),lerp(initial.right,target.right),lerp(initial.bottom,target.bottom))
            val radius=lerp(minOf(initial.width(),initial.height())/2,(28+8*p)*d)
            glass.draw(canvas,rect,radius,state.appearance.opacity)
            val save=canvas.save();clipOutline.reset();clipOutline.addRoundRect(rect,radius,radius,Path.Direction.CW);canvas.clipPath(clipOutline)
            canvas.translate(0f,insetTop)
            if(a>=1f)canvas.save()else canvas.saveLayerAlpha(0f,0f,width.toFloat(),bodyHeight(),(255*((a-.28f)/.72f).coerceIn(0f,1f)).toInt())
            movingGlyphVisible=false;offlineGlyphVisible=false;timedGlyphVisible=false;mediaProgressVisible=false;coverMotionVisible=false;hits.clear()
            if(p<.18f) {
                val compact=if(p<=0f)canvas.save()else canvas.saveLayerAlpha(0f,0f,width.toFloat(),compactHeight(),(255*(1-EffectTimeline.smooth(p/.18f))).toInt())
                drawCompact(canvas);canvas.restoreToCount(compact)
            }
            if(p>.18f) {
                val expanded=if(p>=.48f)canvas.save()else canvas.saveLayerAlpha(0f,0f,width.toFloat(),bodyHeight(),(255*EffectTimeline.smooth((p-.18f)/.3f)).toInt())
                canvas.translate(0f,6*d*(1-EffectTimeline.smooth((p-.18f)/.3f)))
                if(bodyHeight()<218*d)drawCompact(canvas)else drawAurora(canvas,expansion>.5f)
                canvas.restoreToCount(expanded)
            }
            val visible=RectF(rect).apply {offset(0f,-insetTop)}
            val iterator=hits.entries.iterator()
            while(iterator.hasNext())if(!iterator.next().value.intersect(visible))iterator.remove()
            if(a<.95f||(p>=.18f&&p<.48f))hits.clear()
            canvas.restoreToCount(save);scheduleMotion()
        }
        private fun hasMedia()=state.playback.playing||state.playback.title.isNotBlank()||state.playback.sourcePackage.isNotBlank()
        private fun detail(item:IslandItem):String {
            val delta=(android.os.SystemClock.elapsedRealtime()-state.updatedAtElapsed).coerceAtLeast(0)
            return when(item.icon) {
                SlotIcon.CALL->durationLabel(item.elapsedMs.coerceAtLeast(0)+delta)
                SlotIcon.TIMER->durationLabel((item.remainingMs-if(item.timerRunning)delta else 0).coerceAtLeast(0))
                SlotIcon.STOPWATCH->durationLabel(item.elapsedMs.coerceAtLeast(0)+if(item.timerRunning)delta else 0)
                SlotIcon.CAMERA,SlotIcon.MICROPHONE->if(state.aurora.privacyOrigins&&item.sourcePackage.isNotBlank())sourceNames[item.sourcePackage] ?: item.sourcePackage else item.detail
                else->item.detail
            }
        }
        private fun frame(item:IslandItem?):EffectFrame {
            val delta=(android.os.SystemClock.elapsedRealtime()-state.updatedAtElapsed).coerceAtLeast(0)
            val elapsed=(item?.elapsedMs ?: -1).let {if(it<0)it else it+if(item?.icon==SlotIcon.CALL||item?.timerRunning==true)delta else 0}
            val remaining=(item?.remainingMs ?: -1).let {if(it<0)it else (it-if(item?.timerRunning==true)delta else 0).coerceAtLeast(0)}
            return EffectFrame(motionEnabled=animate,musicPlaying=state.playback.playing&&animate,
            motionMs=android.os.SystemClock.uptimeMillis(),networkText=item?.detail.takeIf {item?.icon==SlotIcon.NETWORK}.orEmpty(),
            aurora=state.aurora,callElapsedMs=elapsed.coerceAtLeast(0),
            timerElapsedMs=elapsed,timerRemainingMs=remaining,stopwatchElapsedMs=elapsed)
        }
        private fun drawAurora(canvas:Canvas,full:Boolean) {
            val hero=hasHero()
            val wide=landscape()&&full&&hero
            val small=condensed()
            val active=state.primaryItem()
            val glyph=active?.icon?.let(::islandGlyph) ?: if(hasMedia())SlotIcon.MEDIA else null
            timedGlyphVisible=timedItem(active)
            movingGlyphVisible=glyph==SlotIcon.RECORD||(glyph==SlotIcon.MEDIA&&state.playback.playing&&!MediaArtwork.valid(state.playback.artwork))||(animate&&glyph==SlotIcon.WIFI_OFFLINE)
            offlineGlyphVisible=animate&&glyph==SlotIcon.WIFI_OFFLINE
            text.textAlign=Paint.Align.LEFT;text.color=0xFFD8E1EF.toInt();text.textSize=11*d
            canvas.drawText(UiText.t(if(state.charging)"Carregando" else "Agora"),20*d,28*d,text)
            text.color=if(state.battery in 0..15)0xFFFF6A62.toInt()else 0xFF6BEBAC.toInt();text.textSize=12*d;text.textAlign=Paint.Align.RIGHT
            canvas.drawText(if(state.battery<0)"—" else "${state.battery}%",width-64*d,28*d,text);text.textAlign=Paint.Align.LEFT
            button(canvas,"close",UiText.t("×"),RectF(width-57*d,2*d,width-9*d,50*d))
            val panel=RectF(14*d,46*d,if(wide)width*.48f else width-14*d,if(!hero)46*d else if(wide)bodyHeight()-76*d else if(small)140*d else 162*d)
            if(hero) {
            glass.surface(canvas,panel,24*d)
            val tileSize=if(small)28*d else 44*d
            val tile=RectF(panel.left+12*d,panel.top+10*d,panel.left+12*d+tileSize,panel.top+10*d+tileSize)
            glass.surface(canvas,tile,15*d)
            if(glyph!=SlotIcon.MEDIA||!drawCover(canvas,tile)) {
                val mini=canvas.save();canvas.translate(tile.centerX(),tile.centerY());canvas.scale(tileSize/76f,tileSize/76f)
                if(glyph!=null)iconPainter.drawShortcut(canvas,glyph,Color.WHITE,1f,frame(active));canvas.restoreToCount(mini)
            }
            val textLeft=tile.right+12*d
            text.color=Color.WHITE;text.textSize=13*d
            val title=if(glyph==SlotIcon.MEDIA)state.playback.title.ifBlank {UiText.t("Música")}else active?.title ?: UiText.t("Seu resumo")
            clippedText(canvas,title,textLeft,panel.top+25*d,panel.right-textLeft-12*d)
            text.color=0xFFD5E0EC.toInt();text.textSize=10*d
            val detail=if(glyph==SlotIcon.MEDIA)UiText.t(if(state.playback.playing)"Reproduzindo" else "Pausado")else active?.let(::detail) ?: UiText.format("{0} estados ativos",state.items.size)
            clippedText(canvas,detail,textLeft,panel.top+(if(small)36 else 43)*d,panel.right-textLeft-12*d)
            if(state.aurora.openSource&&active?.sourcePackage?.isNotBlank()==true)hits["source:${active.sourcePackage}"]=RectF(panel.left+8*d,panel.top+4*d,panel.right-8*d,panel.top+(if(small)38 else 56)*d)
            val keys=if(state.aurora.contextActions)when(active?.icon) {
                SlotIcon.MEDIA->listOf("previous","play","next")
                SlotIcon.TIMER->emptyList()
                SlotIcon.STOPWATCH->emptyList()
                SlotIcon.CALL->listOf("call")
                else->emptyList()
            } else if(hasMedia())listOf("previous","play","next")else emptyList()
            val controlY=panel.bottom-52*d
            val media=keys.any {it=="play"}
            val unit=if(media)64*d else (panel.width()-24*d)/maxOf(1,keys.size)
            val controlLeft=if(media)panel.centerX()-unit*keys.size/2 else panel.left+12*d
            keys.forEachIndexed {i,key->
                val label=when(key){"play"->UiText.t(if(state.playback.playing)"Pausar" else "Reproduzir");"next"->UiText.t("Próxima");"previous"->UiText.t("Anterior");"timerPlus"->"+15 s";"stopwatchReset"->AuroraText.t("Zerar","Reset","Restablecer");"call"->AuroraText.t("Abrir telefone","Open phone","Abrir teléfono");else->AuroraText.t(if(active?.timerRunning==true)"Pausar"else"Continuar",if(active?.timerRunning==true)"Pause"else"Resume",if(active?.timerRunning==true)"Pausar"else"Continuar")}
                button(canvas,key,label,RectF(controlLeft+i*unit,controlY,controlLeft+(i+1)*unit-4*d,panel.bottom-4*d))
            }
            if(glyph==SlotIcon.MEDIA) {
                val progress=state.playback.progress(android.os.SystemClock.elapsedRealtime())
                mediaProgressVisible=state.playback.playing&&progress>=0
                val progressY=panel.top+(if(small)38 else 60)*d
                if(progress>=0)decoration.progress(canvas,panel.left+16*d,panel.right-16*d,progressY,progress,
                    android.os.SystemClock.uptimeMillis(),animate&&state.playback.playing&&state.appearance.waveProgress,d)

            } else if(keys.isEmpty()) {
                text.textSize=10*d;text.color=0xFFD5E0EC.toInt()
                val info=if(state.charging&&state.chargeRemainingMs>=0)estimateLabel(state.chargeRemainingMs)else if(state.headphoneBattery>=0)UiText.format("Fones: {0}%",state.headphoneBattery)else UiText.format("{0} estados ativos",state.items.size)
                canvas.drawText(shorten(info,panel.width()-32*d),panel.left+16*d,panel.bottom-18*d,text)
            }
            } // Activity card only; connectivity belongs in the state grid.
            val bottom=minOf(displayedBounds().bottom-insetTop-14*d,bodyHeight()-14*d)
            val shortcutY=maxOf(panel.bottom+8*d,bottom-48*d)
            val size=(width-28*d)/4
            listOf("wifi","bluetooth","nfc","torch").forEachIndexed {i,key->button(canvas,key,shortcutLabel(key),RectF(14*d+i*size,shortcutY,14*d+(i+1)*size-5*d,shortcutY+48*d))}
            if(full||!hero) {
                val gridLeft=if(wide)width*.51f else 16*d;val gridRight=width-16*d
                val top=gridTop();val end=shortcutY-10*d
                val clip=canvas.save();canvas.clipRect(gridLeft,top,gridRight,maxOf(top,end))
                val cell=(gridRight-gridLeft)/2
                val secondary=gridItems()
                secondary.forEachIndexed {i,item->
                    val x=gridLeft+(i%2)*cell;val y=top+(i/2)*54*d-scroll
                    if(y+48*d<top||y>end)return@forEachIndexed
                    if(animate&&item.icon==SlotIcon.WIFI_OFFLINE)offlineGlyphVisible=true
                    if(timedItem(item))timedGlyphVisible=true
                    if(item.icon==SlotIcon.RECORD||item.icon==SlotIcon.RECORD_TIME||(animate&&item.icon==SlotIcon.WIFI_OFFLINE)||(item.icon==SlotIcon.MEDIA&&state.playback.playing))movingGlyphVisible=true
                    val r=RectF(x,y,x+cell-6*d,y+48*d);glass.surface(canvas,r,16*d)
                    if(state.aurora.openSource&&item.sourcePackage.isNotBlank())hits["source:${item.sourcePackage}"]=RectF(r).apply {intersect(gridLeft,top,gridRight,end)}
                    val icon=canvas.save();canvas.translate(x+19*d,y+24*d);canvas.scale(.4f*d,.4f*d)
                    iconPainter.drawShortcut(canvas,islandGlyph(item.icon),Color.WHITE,1f,frame(item));canvas.restoreToCount(icon)
                    text.color=Color.WHITE;text.textSize=10*d;canvas.drawText(shorten(item.title,cell-45*d),x+38*d,y+19*d,text)
                    text.color=0xFFD5E0EC.toInt();text.textSize=9*d;canvas.drawText(shorten(detail(item),cell-45*d),x+38*d,y+34*d,text)
                };canvas.restoreToCount(clip)
            }
            paint.style=Paint.Style.FILL;paint.color=0x607E9ABB
            canvas.drawRoundRect(RectF(width/2-14*d,bottom+6*d,width/2+14*d,bottom+8*d),d,d,paint)
        }
        private fun drawCover(canvas:Canvas,rect:RectF):Boolean {
            if(!MediaArtwork.valid(state.playback.artwork))return false
            val moving=animate&&state.playback.playing&&state.appearance.coverEffect in 2..19
            coverMotionVisible=coverMotionVisible||moving
            decoration.draw(canvas,rect,state.appearance.coverEffect,android.os.SystemClock.uptimeMillis(),state.playback.color,
                state.playback.progress(android.os.SystemClock.elapsedRealtime()),moving)
            val layer=if(state.appearance.coverOpacity>=100)canvas.save()else canvas.saveLayerAlpha(rect.left,rect.top,rect.right,rect.bottom,state.appearance.coverOpacity.coerceIn(20,100)*255/100)
            val drawn=coverPainter.draw(canvas,state.playback.artwork,rect,state.playback.color)
            canvas.restoreToCount(layer)
            return drawn
        }
        private fun shortcutLabel(key:String)=when(key){"wifi"->"Wi-Fi";"bluetooth"->"Bluetooth";"nfc"->"NFC";else->UiText.t("Lanterna")}
        private fun button(canvas:Canvas,key:String,label:String,rect:RectF) {
            hits[key]=rect
            val media=key in listOf("play","previous","next")
            val close=key=="close"
            val surface=if(media||close)RectF(rect.centerX()-if(close)15*d else 22*d,rect.centerY()-if(close)15*d else 22*d,rect.centerX()+if(close)15*d else 22*d,rect.centerY()+if(close)15*d else 22*d)else rect
            glass.surface(canvas,surface,if(media||close)24*d else 16*d,key=="play"||key=="torch"&&state.torch,pressedKey==key)
            paint.style=Paint.Style.FILL;paint.color=Color.WHITE
            val x=rect.centerX();val y=rect.centerY()
            if(media) {
                if(key=="play"&&state.playback.playing) {canvas.drawRoundRect(RectF(x-6*d,y-7*d,x-2*d,y+7*d),d,d,paint);canvas.drawRoundRect(RectF(x+2*d,y-7*d,x+6*d,y+7*d),d,d,paint)}
                else {val sign=if(key=="previous")-1 else 1;canvas.drawPath(Path().apply {moveTo(x-sign*5*d,y-7*d);lineTo(x+sign*6*d,y);lineTo(x-sign*5*d,y+7*d);close()},paint);if(key!="play")canvas.drawRoundRect(RectF(minOf(x+sign*7*d,x+sign*9*d),y-7*d,maxOf(x+sign*7*d,x+sign*9*d),y+7*d),d,d,paint)}
            } else if(close) {
                paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=1.7f*d
                canvas.drawLine(x-4*d,y-4*d,x+4*d,y+4*d,paint);canvas.drawLine(x+4*d,y-4*d,x-4*d,y+4*d,paint);paint.style=Paint.Style.FILL
            } else {
                text.textAlign=Paint.Align.CENTER;text.textSize=10*d;text.color=Color.WHITE
                canvas.drawText(shorten(label,rect.width()-10*d),x,y+3.5f*d,text);text.textAlign=Paint.Align.LEFT
            }
        }
        private fun drawCompact(canvas:Canvas) {
            val rect=compactBounds().apply { offset(0f,-insetTop) };val centerY=rect.centerY()
            val active=state.primaryItem()
            timedGlyphVisible=timedItem(active)
            val glyph=active?.icon?.let(::islandGlyph) ?: if(state.playback.playing)SlotIcon.MEDIA else null
            movingGlyphVisible=(glyph==SlotIcon.MEDIA&&state.playback.playing&&!MediaArtwork.valid(state.playback.artwork))||glyph==SlotIcon.RECORD||(animate&&glyph==SlotIcon.WIFI_OFFLINE)
            offlineGlyphVisible=animate&&glyph==SlotIcon.WIFI_OFFLINE
            val cover=RectF(rect.left+11*d,centerY-18*d,rect.left+47*d,centerY+18*d)
            if(glyph!=SlotIcon.MEDIA||!drawCover(canvas,cover)) {
                val s=canvas.save();canvas.translate(rect.left+29*d,centerY);canvas.scale(.5f*d,.5f*d)
                if(glyph!=null)iconPainter.draw(canvas,glyph,Color.WHITE,1f,frame(active));canvas.restoreToCount(s)
            }
            text.textAlign=Paint.Align.LEFT;text.color=Color.WHITE;text.alpha=255;text.textSize=12*d
            val title=if(glyph==SlotIcon.MEDIA&&hasMedia())state.playback.title.ifBlank { UiText.t("Música") } else active?.title ?: UiText.t("Seu resumo")
            clippedText(canvas,title,rect.left+54*d,centerY-4*d,rect.width()-112*d)
            if(state.aurora.openSource&&active?.sourcePackage?.isNotBlank()==true)hits["source:${active.sourcePackage}"]=RectF(rect.left+50*d,rect.top+6*d,rect.right-54*d,rect.bottom-8*d)
            text.textSize=9*d;text.color=0xFFD5E0EC.toInt()
            val detail=if(active?.icon in setOf(SlotIcon.CALL,SlotIcon.TIMER,SlotIcon.STOPWATCH,SlotIcon.CAMERA,SlotIcon.MICROPHONE))active?.let(::detail).orEmpty()else if(glyph==SlotIcon.MEDIA&&hasMedia())UiText.t(if(state.playback.playing)"Reproduzindo" else "Pausado") else if(glyph==SlotIcon.RECORD||active?.icon==SlotIcon.CHARGE_TIME)active?.detail.orEmpty() else if(state.charging)UiText.t("Carregando") else UiText.format("{0} estados ativos",state.items.size)
            clippedText(canvas,detail,rect.left+54*d,centerY+12*d,rect.width()-112*d)
            if(hasMedia()&&actionAvailable("play")) {
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
        private fun clippedText(canvas:Canvas,value:String,x:Float,y:Float,width:Float) {
            if(width<=0f)return
            val saved=canvas.save();canvas.clipRect(x,0f,x+width,bodyHeight())
            canvas.drawText(shorten(value,width-2*d),x,y,text);canvas.restoreToCount(saved)
        }
        private fun shorten(value:String,width:Float):String {
            if(width<=0f)return ""
            return android.text.TextUtils.ellipsize(value,text,width,android.text.TextUtils.TruncateAt.END).toString()
        }
    }
}
