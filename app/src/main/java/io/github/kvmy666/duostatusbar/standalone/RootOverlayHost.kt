package io.github.kvmy666.duostatusbar.standalone

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*

/** Independent privileged window. It never traverses or modifies another process's view tree. */
internal class RootOverlayHost(
    private val context: Context,
    private val windowManager: WindowManager,
    initial: ModuleSettings,
    private val firstDraw: () -> Unit,
    private val failed: (String) -> Unit
) : StateHost {
    private var settings = initial
    private val handler = Handler(Looper.getMainLooper())
    private val root = object:FrameLayout(context) {
        override fun dispatchTouchEvent(event:MotionEvent):Boolean {
            views().forEach {it.touchResponse(event.actionMasked==MotionEvent.ACTION_DOWN)}
            gestures.handle(event,views())
            return true
        }
    }.apply { clipChildren=false;clipToPadding=false }
    private val ring = DuoCanvasView(context)
    private var indicators: DuoCanvasView? = null
    private val gestures = ElementGestures(context)
    private var attached=false
    private var drawn=false
    private var closed=false
    private var visible=true
    private var hidden=false
    private var paused=false
    private var summaryOpen=false
    private var latestVisual:DuoVisual?=null
    private var latestFrame=EffectFrame()
    override var rendererFailed=false;private set
    override val rendererReady get()=attached&&!closed&&!rendererFailed
    override val effectsVisible get()=visible&&!hidden&&!paused&&!summaryOpen&&!closed&&!rendererFailed&&settings.enabled
    private val nfcParticles=NfcParticleOverlay(context)
    override fun showNfcParticles():Boolean = effectsVisible&&nfcParticles.show(root,animationsEnabled)
    override fun dismissNfcParticles()=nfcParticles.dismiss()
    private val summary = IslandSummary(context) { opened -> summaryOpen=opened;if(opened)nfcParticles.dismiss();syncActive() }
    private val params = WindowManager.LayoutParams().apply {
        @Suppress("DEPRECATION")
        type=WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
        format=PixelFormat.TRANSLUCENT
        flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        gravity=Gravity.TOP or Gravity.RIGHT
        layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        packageName=context.packageName
        title="DUO Recreate root Canvas"
    }
    private val drawListener:ViewTreeObserver.OnDrawListener = ViewTreeObserver.OnDrawListener {
        if(!drawn&&!closed)handler.post {
            // OnDraw listeners run before children. Check the renderer on the following main turn.
            if(!drawn&&!closed&&attached&&ring.isReady&&ring.width>0&&ring.height>0) {
                drawn=true
                if(root.viewTreeObserver.isAlive)root.viewTreeObserver.removeOnDrawListener(drawListenerRef)
                firstDraw()
            }
        }
    }
    // Indirection avoids referencing an incompletely initialized property from its callback.
    private val drawListenerRef:ViewTreeObserver.OnDrawListener get()=drawListener

    fun attach(): Boolean = try {
        check(!closed)
        root.addView(ring)
        ring.onFailed { fail("Canvas draw failed") }
        applySettings(settings)
        root.viewTreeObserver.addOnDrawListener(drawListener)
        windowManager.addView(root,params);attached=true
        ring.start();syncActive();true
    } catch(t:Throwable) { fail("Window rejected: ${t.javaClass.simpleName}");false }

    fun applySettings(next: ModuleSettings) {
        settings=next
        if(!next.enabled||!next.animationsEnabled)nfcParticles.dismiss()
        if(next.splitIndicators&&indicators==null) {
            indicators=DuoCanvasView(context,DuoPart.INDICATORS).also { child ->
                root.addView(child);child.onFailed {fail("Indicator Canvas draw failed")};child.start()
                child.effects=latestFrame;latestVisual?.let(child::render)
            }
        } else if(!next.splitIndicators) {
            indicators?.let {it.teardown();root.removeView(it)};indicators=null
        }
        ring.part=if(next.splitIndicators)DuoPart.RING else DuoPart.ALL
        views().forEach { it.animationsEnabled=next.animationsEnabled;it.arrivalEnabled=next.arrivalEnabled
            it.thickPercent=next.thickPercent;it.globalPercent=next.globalPercent }
        gestures.install(ring,next.tapAction,next.doubleTapAction,next.longPressAction,
            if(Fx.experience.island)({ summary.open(gestures.anchor?:ring,animationsEnabled) }) else null)
        val interactive=Fx.experience.island||listOf(next.tapAction,next.doubleTapAction,next.longPressAction).any {it!="no_action"}
        params.flags=if(interactive)params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        reapplyHiding();syncActive()
    }

    private fun views()=listOfNotNull(ring,indicators)
    private fun syncActive() {
        val active=effectsVisible
        root.visibility=if(visible&&settings.enabled&&!closed)View.VISIBLE else View.GONE
        views().forEach {it.alpha=if(hidden||summaryOpen)0f else 1f;it.isEnabled=!hidden;it.setRenderActive(active)}
    }
    private fun fail(reason:String) {
        if(rendererFailed||closed)return
        nfcParticles.dismiss()
        rendererFailed=true;syncActive();failed(reason)
    }
    fun close() {
        if(closed)return
        nfcParticles.dismiss()
        closed=true;handler.removeCallbacksAndMessages(null);summary.dismiss();gestures.clear()
        if(root.viewTreeObserver.isAlive)root.viewTreeObserver.removeOnDrawListener(drawListener)
        views().forEach {it.teardown()}
        if(attached)runCatching {windowManager.removeViewImmediate(root)}
        attached=false
    }
    override fun render(visual:DuoVisual) {latestVisual=visual;views().forEach {it.render(visual)} }
    override fun revealAll(ms:Int) {if(animationsEnabled)views().forEach {it.reveal(ms)}}
    override fun reapplyHiding() {
        val metrics=windowManager.currentWindowMetrics.bounds
        val id=context.resources.getIdentifier("status_bar_height","dimen","android")
        val bar=if(id!=0)context.resources.getDimensionPixelSize(id) else (32*context.resources.displayMetrics.density).toInt()
        val box=RootOverlayPolicy.box(metrics.width(),metrics.height(),context.resources.displayMetrics.density,
            bar,settings.sizePercent,settings.offsetX,settings.splitIndicators,settings.indicatorsOffsetX)
        params.width=box.width;params.height=box.height;params.x=box.right;params.y=box.top
        ring.layoutParams=FrameLayout.LayoutParams(box.side,box.height,Gravity.RIGHT)
        indicators?.layoutParams=FrameLayout.LayoutParams(box.side,box.height,Gravity.LEFT)
        if(attached&&!closed)windowManager.updateViewLayout(root,params)
        // Stock visibility belongs to RootStockIcons, not to a nonexistent foreign View hierarchy.
    }
    override fun applyEffects(frame:EffectFrame,hidden:Boolean) {latestFrame=frame;views().forEach {it.effects=frame}}
    override fun effectsHidden(hidden:Boolean) {this.hidden=hidden;if(hidden){summary.dismiss();nfcParticles.dismiss()};syncActive()}
    override fun pauseEffects(paused:Boolean) {this.paused=paused;if(paused)nfcParticles.dismiss();syncActive()}
    override fun setElementsVisible(visible:Boolean) {this.visible=visible;if(!visible)nfcParticles.dismiss();syncActive()}
    override fun updateSummary(state:IslandState)=summary.update(state)
    override fun dismissSummary()=summary.dismiss()
    override fun effectClock():TextView?=null
    override fun continuum(enter:Boolean,enabled:Boolean) { /* No access to another process's clock. */ }
    override val animationsEnabled get()=settings.animationsEnabled
    override val thickPercent get()=settings.thickPercent
    override val arrivalEnabled get()=settings.animationsEnabled&&settings.arrivalEnabled
    override val departureEnabled get()=settings.animationsEnabled&&settings.departureEnabled
    override val chargingEnabled get()=settings.animationsEnabled&&settings.chargingEnabled
    override val revealMs get()=settings.revealMs
    override val showPercent get()=settings.showPercent
    override val percentHeight get()=settings.percentHeight
    override val iconColor get()=settings.iconColor
    override val simChoice get()=settings.simChoice
    override val dndMode get()=settings.dndMode
    override val showAirplane get()=settings.showAirplane
    override val showDnd get()=settings.showDnd
    override val wifiDots get()=settings.wifiDots
}
