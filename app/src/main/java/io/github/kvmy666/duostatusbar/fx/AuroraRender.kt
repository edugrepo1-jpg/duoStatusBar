package io.github.kvmy666.duostatusbar.fx

import android.graphics.Color
import kotlin.math.abs

/** Bounded presentation timing; no device state or battery percentage is synthesized here. */
internal object AuroraRender {
    // Both options share the check's 3000ms clock. They never restart a private animation clock.
    fun glow(frame:EffectFrame)=if(frame.motionEnabled&&frame.aurora.unlockGlow&&frame.checkMs in 0 until EffectTimeline.CHECK_MS) {
        EffectTimeline.smooth(frame.checkMs/120f)*(1f-EffectTimeline.smooth(
            (frame.checkMs-EffectTimeline.CHECK_FADE_OUT_AT).toFloat()/(EffectTimeline.CHECK_MS-EffectTimeline.CHECK_FADE_OUT_AT)))
    } else 0f
    fun build(frame:EffectFrame,phase:Int):Float {
        if(!frame.motionEnabled||!frame.aurora.unlockBuild)return 1f
        val duration=EffectTimeline.CHECK_MS/3
        return AuroraMotion.build(frame.checkMs,phase.coerceIn(0,2)*duration,duration)
    }
    fun headphone(frame:EffectFrame)=frame.motionEnabled&&frame.aurora.headphoneConfirm&&frame.headphoneConnectMs in 0 until 1200
    fun headphoneOpacity(frame:EffectFrame)=if(headphone(frame))1f-EffectTimeline.smooth((frame.headphoneConnectMs-1000)/200f) else 0f
    fun headphoneReturnOpacity(frame:EffectFrame)=if(frame.motionEnabled&&frame.aurora.headphoneConfirm&&frame.headphoneConnectMs in 1200 until 1360)
        EffectTimeline.smooth((frame.headphoneConnectMs-1200)/160f) else 1f
    fun taskGlow(frame:EffectFrame)=if(frame.motionEnabled&&frame.aurora.taskComplete&&frame.taskCompleteMs in 0 until 1800)
        EffectTimeline.smooth(frame.taskCompleteMs/120f)*(1f-EffectTimeline.smooth((frame.taskCompleteMs-1250)/550f)) else 0f
    fun sourceOpacity(frame:EffectFrame)=if(frame.checkMs<0&&frame.motionEnabled&&frame.aurora.batterySource&&frame.batterySourceMs in 0 until 600)
        EffectTimeline.smooth(abs(frame.batterySourceMs-300)/300f) else 1f
    fun opposite(color:Int)=if(Color.red(color)*.2126f+Color.green(color)*.7152f+Color.blue(color)*.0722f>128f)Color.BLACK else Color.WHITE
    fun thickness(thick:Int,safe:Boolean)=(thick.coerceIn(1,300)/100f).let {if(safe)it.coerceAtMost(1.6f) else it}
    fun iconScale(requested:Float,radius:Float,artRadius:Float,safe:Boolean)=if(safe)minOf(requested,(radius/artRadius).coerceAtLeast(.25f))
        else if(requested<=1f)requested else minOf(requested,(radius/artRadius).coerceAtLeast(1f))
}
