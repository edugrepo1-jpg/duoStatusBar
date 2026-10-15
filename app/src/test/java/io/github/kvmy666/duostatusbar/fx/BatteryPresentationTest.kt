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
        assertEquals(options.batteryMid,BatteryPresentation.visual(phone(80),frame.copy(headphoneBattery=-1)).tint)
        assertFalse(ExperienceOptions.decode("{}").customBatteryColors)
    }
    @Test fun `discharge bands are suspended for phone charging and return without rewriting preferences`() {
        val colors=BatteryBandColors(0xFFAA1122.toInt(),0xFF2233AA.toInt(),0xFF11BB22.toInt())
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.WIFI),batteryColors=colors)
        for(deviceCharging in listOf(false,true))for(effectCharging in listOf(false,true)) {
            val device=phone(80,charging=deviceCharging)
            val input=frame.copy(charging=effectCharging)
            val result=BatteryPresentation.visual(device,input)
            val presentation=BatteryPresentation.frameForSource(device,input)
            if(deviceCharging||effectCharging) {
                assertEquals(device,result);assertNull(presentation.batteryColors)
            } else {assertEquals(colors.middle,result.tint);assertSame(input,presentation)}
            assertEquals("The user's saved palette is immutable",colors,input.batteryColors)
        }
        assertEquals(colors.middle,BatteryPresentation.visual(phone(80),frame).tint)
    }
    @Test fun `headset keeps its palette while phone charging is suspended independently`() {
        val colors=BatteryBandColors(0xFFAA1122.toInt(),0xFF2233AA.toInt(),0xFF11BB22.toInt())
        val frame=EffectFrame(slot=SlotFrame(SlotIcon.AIRPODS),headphoneBattery=20,charging=true,batteryColors=colors)
        val device=phone(80,charging=true)
        assertSame(frame,BatteryPresentation.frameForSource(device,frame))
        val headset=BatteryPresentation.visual(device,frame)
        assertEquals(colors.low,headset.tint);assertEquals("20",headset.percentText);assertFalse(headset.charging)
        val withoutHeadphones=frame.copy(slot=SlotFrame(SlotIcon.WIFI))
        assertEquals(device,BatteryPresentation.visual(device,withoutHeadphones))
        assertNull(BatteryPresentation.frameForSource(device,withoutHeadphones).batteryColors)
    }
    @Test fun `source changes only at invisible midpoint retaining actual prior percentage`() {
        val swap=BatterySourceSwap()
        val phone=phone(72)
        val frame=EffectFrame(aurora=AuroraOptions(setOf("batterySource")),headphoneBattery=15,slot=SlotFrame(SlotIcon.AIRPODS))
        val headphones=BatteryPresentation.visual(phone,frame)
        assertEquals("72",swap.present(phone,null,frame.copy(batterySourceMs=-1)).visual.percentText)
        for(age in listOf(0L,100L,299L)) {
            val selected=swap.present(headphones,15,frame.copy(batterySourceMs=age))
            assertEquals("72",selected.visual.percentText);assertNull(selected.headphoneLevel)
        }
        assertEquals(0f,AuroraRender.sourceOpacity(frame.copy(batterySourceMs=300)),0f)
        for(age in listOf(300L,500L,599L)) {
            val selected=swap.present(headphones,15,frame.copy(batterySourceMs=age))
            assertEquals("15",selected.visual.percentText);assertEquals(15,selected.headphoneLevel)
        }
        swap.present(headphones,15,frame.copy(batterySourceMs=-1))
        assertEquals("15",swap.present(phone,null,frame.copy(batterySourceMs=0)).visual.percentText)
        assertEquals("72",swap.present(phone,null,frame.copy(batterySourceMs=300)).visual.percentText)
    }
    @Test fun `disabling source animation snaps once and never retains an obsolete headset`() {
        val swap=BatterySourceSwap();val phone=phone()
        val frame=EffectFrame(aurora=AuroraOptions(setOf("batterySource")),headphoneBattery=15,slot=SlotFrame(SlotIcon.AIRPODS))
        val headphones=BatteryPresentation.visual(phone,frame)
        swap.present(headphones,15,frame)
        assertEquals(phone,swap.present(phone,null,frame.copy(batterySourceMs=100,motionEnabled=false)).visual)
        assertEquals(phone,swap.present(phone,null,frame.copy(batterySourceMs=100,aurora=AuroraOptions())).visual)
    }
    @Test fun `unlock retains exclusive battery presentation without a simultaneous source fade`() {
        val swap=BatterySourceSwap();val phone=phone()
        val frame=EffectFrame(aurora=AuroraOptions(setOf("batterySource")),headphoneBattery=15,slot=SlotFrame(SlotIcon.AIRPODS))
        swap.present(BatteryPresentation.visual(phone,frame),15,frame)
        val unlock=frame.copy(checkMs=300,batterySourceMs=300)
        assertEquals(phone,swap.present(phone,null,unlock).visual)
        assertEquals(1f,AuroraRender.sourceOpacity(unlock),0f)
    }
}
