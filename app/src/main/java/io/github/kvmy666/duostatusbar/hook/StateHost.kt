package io.github.kvmy666.duostatusbar.hook

import android.widget.TextView
import io.github.kvmy666.duostatusbar.fx.EffectFrame
import io.github.kvmy666.duostatusbar.fx.IslandState

/** Drawing contract shared by SystemUI injection and the explicitly selected root process. */
internal interface EffectsHost {
    val rendererFailed:Boolean
    val effectsVisible:Boolean get()=true
    val animationsEnabled:Boolean
    val thickPercent:Int
    val showAirplane:Boolean
    val showDnd:Boolean
    fun applyEffects(frame:EffectFrame,hidden:Boolean)
    fun effectsHidden(hidden:Boolean)
    fun pauseEffects(paused:Boolean)
    fun setElementsVisible(on:Boolean)
    fun updateSummary(state:IslandState)
    fun showNfcParticles():Boolean = false
    fun dismissNfcParticles() {}
    fun dismissSummary()
    fun effectClock():TextView?
    fun continuum(enter:Boolean,enabled:Boolean)
    /** No privileged method hooks exist in the independent runtime. Keyguard observation remains. */
    fun installBiometricObserver(callback:()->Unit):()->Unit = {}
}

internal interface StateHost:EffectsHost {
    val rendererReady:Boolean
    val arrivalEnabled:Boolean
    val departureEnabled:Boolean
    val chargingEnabled:Boolean
    val revealMs:Int
    val showPercent:Boolean
    val percentHeight:Int
    val iconColor:String
    val simChoice:String
    val dndMode:String
    val wifiDots:Boolean
    fun render(v:DuoVisual)
    fun revealAll(ms:Int)
    fun reapplyHiding()
}
