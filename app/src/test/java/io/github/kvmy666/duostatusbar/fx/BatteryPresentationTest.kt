package io.github.kvmy666.duostatusbar.fx

import io.github.kvmy666.duostatusbar.hook.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class BatteryPresentationTest {
    private fun phone(level:Int=72,charging:Boolean=false,show:Boolean=true)=DuoMapping.visual(level,charging,false,show,3,4,false)
    @Test fun `colors have exact disjoint boundaries and unknown levels are never a headset zero`() {
        val colors=BatteryBandColors(1,2,3)
        for(level in 0..100)assertEquals((if(level<=20)1 else if(level<=80)2 else 3) or 0xFF000000.toInt(),colors.color(level))
        for(level in listOf(-1,101))assertNull(BatteryPresentation.headphoneLevel(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=level)))
        assertEquals(0,BatteryPresentation.headphoneLevel(EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=0)))
    }
    @Test fun `headphones own both arcs and number only while actually visible including fades`() {
        val source=phone()
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS,.5f),headphoneBattery=35)
        val headset=BatteryPresentation.visual(source,frame)
        assertEquals("35",headset.percentText);assertEquals(RingGeometry.trimLeft(35,false,false),headset.trimLeftEnd,0f)
        assertEquals(0f,headset.trimRightEnd,0f);assertEquals("72",source.percentText)
        for(f in listOf(frame.copy(slot=SlotFrame(SlotIcon.WIFI)),frame.copy(slot=SlotFrame(SlotIcon.AIRPODS,0f)),frame.copy(headphoneBattery=-1),frame.copy(checkMs=420))) {
            assertNull(BatteryPresentation.headphoneLevel(f));assertEquals(source,BatteryPresentation.visual(source,f))
        }
        assertEquals(35,BatteryPresentation.headphoneLevel(frame.copy(slot=SlotFrame(SlotIcon.WIFI),audioMs=420)))
        assertNull(BatteryPresentation.headphoneLevel(frame.copy(slot=SlotFrame(SlotIcon.WIFI),audioMs=3900)))
    }
    @Test fun `phone charging never hides headset percentage or makes the headset appear to charge`() {
        val device=phone(charging=true)
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=100,charging=true)
        val headset=BatteryPresentation.visual(device,frame)
        assertEquals("100",headset.percentText);assertFalse(headset.charging);assertEquals(0f,headset.boltOpacity,0f)
        assertEquals(RingGeometry.percentFontSize("100"),headset.percentFontSize,0f)
        assertEquals(device,BatteryPresentation.visual(device,frame.copy(slot=SlotFrame(SlotIcon.BOLT))))
        val hidden=BatteryPresentation.visual(phone(show=false),frame)
        assertEquals("",hidden.percentText);assertEquals(0f,hidden.percentOpacity,0f);assertEquals(0f,hidden.rightArc,0f)
    }
    @Test fun `custom colors follow each source and survive encode decode without altering other features`() {
        val options=ExperienceOptions.ALL.copy(customBatteryColors=true,batteryLow=0xFFAA1122.toInt(),batteryMid=0xFF2233AA.toInt(),batteryHigh=0xFF11BB22.toInt())
        assertEquals(options,ExperienceOptions.decode(options.encode()))
        val colors=BatteryBandColors(options.batteryLow,options.batteryMid,options.batteryHigh)
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=20,batteryColors=colors)
        assertEquals(options.batteryLow,BatteryPresentation.visual(phone(81),frame).tint)
        assertEquals(options.batteryHigh,BatteryPresentation.visual(phone(81),frame.copy(slot=SlotFrame(SlotIcon.WIFI))).tint)
        assertEquals(options.batteryMid,BatteryPresentation.visual(phone(80,charging=true),frame.copy(headphoneBattery=-1)).tint)
        assertFalse(ExperienceOptions.decode("{}").customBatteryColors)
    }
}
