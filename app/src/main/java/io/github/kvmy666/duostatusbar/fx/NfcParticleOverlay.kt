package io.github.kvmy666.duostatusbar.fx

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow

/** Decorative finite window only. Never intercepts a wallet's touch, focus or payment result. */
internal class NfcParticleOverlay(private val context:Context) {
    private val handler=Handler(Looper.getMainLooper())
    private var popup:PopupWindow?=null
    private var anchor:View?=null
    private var content:View?=null
    private var started=0L
    private var rotation=0
    private var orientation=0
    internal val isShowing get()=popup?.isShowing==true
    internal val currentWindow get()=popup
    private val detached=object:View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v:View) {}
        override fun onViewDetachedFromWindow(v:View) { dismiss() }
    }
    private val finish=Runnable { dismiss() }
    private val tick=object:Runnable {
        override fun run() {
            val a=anchor
            if(!ValueAnimator.areAnimatorsEnabled()||a==null||!a.isAttachedToWindow||!a.isShown||a.display?.rotation!=rotation||
                context.resources.configuration.orientation!=orientation||SystemClock.uptimeMillis()-started>=NfcParticlePainter.DURATION_MS) {
                dismiss();return
            }
            content?.invalidate()
            handler.postDelayed(this,NfcParticlePainter.FRAME_DELAY_MS)
        }
    }
    fun show(target:View,animationsEnabled:Boolean,reducedMotion:Boolean=false):Boolean {
        if(isShowing)return true // Repeated field events never prolong an active animation.
        dismiss()
        if(!animationsEnabled||!ValueAnimator.areAnimatorsEnabled()||!target.isAttachedToWindow||!target.isShown||target.windowToken==null)return false
        return try {
            val display=target.display ?: return false
            val size=Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            if(size.x<=0||size.y<=0)return false
            val density=context.resources.displayMetrics.density
            val height=(116*density).toInt().coerceAtMost(size.y)
            val barId=context.resources.getIdentifier("status_bar_height","dimen","android")
            val bar=if(barId!=0)context.resources.getDimensionPixelSize(barId).toFloat() else 24*density
            val painter=NfcParticlePainter()
            started=SystemClock.uptimeMillis();rotation=display.rotation;orientation=context.resources.configuration.orientation
            val view=object:View(context) {
                override fun onDraw(canvas:Canvas) {
                    painter.draw(canvas,width.toFloat(),height.toFloat(),SystemClock.uptimeMillis()-started,
                        reducedMotion=reducedMotion,statusBarHeightPx=bar)
                }
            }.apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;isClickable=false;isFocusable=false }
            val window=PopupWindow(view,size.x,height,false).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isTouchable=false;isOutsideTouchable=false;isFocusable=false
                isClippingEnabled=false;setIsLaidOutInScreen(true)
                inputMethodMode=PopupWindow.INPUT_METHOD_NOT_NEEDED
                elevation=0f;animationStyle=0
                setOnDismissListener { clear() }
            }
            anchor=target;content=view;popup=window
            target.addOnAttachStateChangeListener(detached)
            window.showAtLocation(target,Gravity.TOP or Gravity.LEFT,0,0)
            handler.post(tick)
            handler.postDelayed(finish,NfcParticlePainter.DURATION_MS)
            true
        } catch(_:Throwable) { dismiss();false }
    }
    private fun clear() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(finish)
        anchor?.removeOnAttachStateChangeListener(detached)
        anchor=null;content=null;popup=null
    }
    fun dismiss() {
        val old=popup
        clear()
        runCatching { old?.dismiss() }
    }
}

