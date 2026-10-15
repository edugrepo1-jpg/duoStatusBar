package io.github.kvmy666.duostatusbar.fx

import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.hook.RingGeometry

/** Inclusive, disjoint ranges: 20 belongs to low and 80 belongs to medium. */
internal data class BatteryBandColors(val low:Int=0xFFFF453A.toInt(),val middle:Int=0xFFFFCC00.toInt(),val high:Int=0xFF30D158.toInt()) {
    fun color(level:Int):Int=(when(level.coerceIn(0,100)){in 0..20->low;in 21..80->middle;else->high}) or 0xFF000000.toInt()
}

/** Presentation only: never overwrites the device battery, charge estimator or diagnostics. */
internal object BatteryPresentation {
    private val defaults=BatteryBandColors()
    fun headphoneLevel(frame:EffectFrame):Int? {
        if(frame.headphoneBattery !in 0..100 || EffectTimeline.checkNormal(frame.checkMs)<=.001f)return null
        val audioVisible=frame.audioMs>=0&&EffectTimeline.audio(frame.audioMs)>.001f
        val slotVisible=frame.slot.icon==SlotIcon.AIRPODS&&frame.slot.opacity>.001f&&EffectTimeline.audioNormal(frame.audioMs)>.001f
        return frame.headphoneBattery.takeIf {audioVisible||slotVisible}
    }
    /** Discharge bands do not replace phone charging artwork; a headset remains a separate source. */
    fun frameForSource(device:DuoVisual,frame:EffectFrame,headphoneLevel:Int?=headphoneLevel(frame)):EffectFrame =
        if(headphoneLevel==null&&(device.charging||frame.charging)&&frame.batteryColors!=null)frame.copy(batteryColors=null) else frame
    fun visual(device:DuoVisual,frame:EffectFrame,headphoneLevel:Int?=headphoneLevel(frame)):DuoVisual {
        if(headphoneLevel==null) {
            val palette=frameForSource(device,frame,headphoneLevel).batteryColors ?: return device
            return device.copy(tint=palette.color(device.batteryLevel))
        }
        val show=device.percentEnabled
        val closed=RingGeometry.gapClosed(false,show)
        val number=if(show)headphoneLevel.toString() else ""
        return device.copy(trimLeftEnd=RingGeometry.trimLeft(headphoneLevel,false,closed),trimRightEnd=RingGeometry.trimRight(headphoneLevel,false,closed),
            leftArc=RingGeometry.leftArc(false,show),rightArc=RingGeometry.rightArc(false,show),percentText=number,
            percentOpacity=if(show)1f else 0f,percentFontSize=RingGeometry.percentFontSize(number),
            tint=(frame.batteryColors ?: defaults).color(headphoneLevel),charging=false,boltOpacity=0f)
    }
}

/** Keeps the last real presentation until fade-out reaches zero; percentages are never interpolated. */
internal class BatterySourceSwap {
    data class Snapshot(val visual:DuoVisual,val headphoneLevel:Int?)
    private var shown:Snapshot?=null
    private var outgoing:Snapshot?=null
    private var lastAge=-1L
    fun present(visual:DuoVisual,headphoneLevel:Int?,frame:EffectFrame):Snapshot {
        val incoming=Snapshot(visual,headphoneLevel)
        val animated=frame.checkMs<0&&frame.motionEnabled&&frame.aurora.batterySource&&frame.batterySourceMs in 0 until 600
        if(!animated) {outgoing=null;lastAge=-1;shown=incoming;return incoming}
        if(lastAge<0||frame.batterySourceMs<lastAge)outgoing=shown
        lastAge=frame.batterySourceMs
        return (if(frame.batterySourceMs<300)outgoing?:incoming else incoming).also {shown=it}
    }
    fun clear() {shown=null;outgoing=null;lastAge=-1}
}
