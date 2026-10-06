package io.github.kvmy666.duostatusbar.fx

import org.junit.Assert.*
import org.junit.Test

class EffectTimelineTest {
    @Test fun `critical crossing fires only on descending thresholds`() {
        val inputs = listOf(Triple(11,10,1), Triple(10,9,0), Triple(6,5,2), Triple(12,4,2), Triple(4,6,0), Triple(-1,4,0))
        inputs.forEach { (old, level, expected) -> assertEquals(expected, EffectTimeline.criticalCrossing(old,level,false,true)) }
        assertEquals(0, EffectTimeline.criticalCrossing(11,10,true,true))
        assertEquals(0, EffectTimeline.criticalCrossing(11,10,false,false))
    }
    @Test fun `wake warning is rate limited and ignores charging and normal levels`() {
        assertEquals(1, EffectTimeline.criticalWake(8,false,true,61000))
        assertEquals(2, EffectTimeline.criticalWake(4,false,true,61000))
        assertEquals(0, EffectTimeline.criticalWake(4,false,true,30000))
        assertEquals(0, EffectTimeline.criticalWake(30,false,true,61000))
        assertEquals(0, EffectTimeline.criticalWake(4,true,true,61000))
    }
    @Test fun `warning has exactly three peaks and zero outside its interval`() {
        val values = (0L..1800L).map { EffectTimeline.pulse(it) }
        val peaks = (1 until values.lastIndex).count { values[it] > values[it-1] && values[it] > values[it+1] }
        assertEquals(3, peaks)
        assertEquals(.9f, values.max(), .0001f)
        listOf(-1L,0L,600L,1200L,1800L,5000L).forEach { assertEquals(0f,EffectTimeline.pulse(it),.0001f) }
    }
    @Test fun `audio and charge have precise exclusive endpoints and obey cancel`() {
        assertEquals(3999L, EffectTimeline.age(4099,100,EffectTimeline.AUDIO_MS,true))
        assertEquals(-1L, EffectTimeline.age(4100,100,EffectTimeline.AUDIO_MS,true))
        assertEquals(2999L, EffectTimeline.age(3099,100,EffectTimeline.CHARGE_MS,true))
        assertEquals(-1L, EffectTimeline.age(3100,100,EffectTimeline.CHARGE_MS,true))
        assertEquals(-1L, EffectTimeline.age(200,100,EffectTimeline.AUDIO_MS,false))
    }
    @Test fun `check color lasts 400ms and sequential handover ends at 980ms`() {
        assertTrue(EffectTimeline.unlockColor(200)>0f)
        assertEquals(0f,EffectTimeline.unlockColor(400),0f)
        assertTrue(EffectTimeline.check(650)>0f)
        assertEquals(0f,EffectTimeline.check(980),0f)
    }
    @Test fun `power threshold excludes invalid readings and discharging current`() {
        assertTrue(EffectTimeline.fastCharge(3_000_000,5000))
        assertFalse(EffectTimeline.fastCharge(2_999_999,5000))
        assertFalse(EffectTimeline.fastCharge(Int.MIN_VALUE,5000))
        assertFalse(EffectTimeline.fastCharge(-3_000_000,5000))
        assertFalse(EffectTimeline.fastCharge(3_000_000,0))
    }
    @Test fun `carousel fades out before next occupant enters`() {
        val cycle=SlotCycle(); cycle.update(listOf(SlotIcon.AIRPLANE,SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        assertEquals(SlotFrame(SlotIcon.AIRPLANE),cycle.frame(2749))
        assertEquals(SlotIcon.AIRPLANE,cycle.frame(2940).icon)
        assertEquals(.5f,cycle.frame(2940).opacity,.0001f)
        assertEquals(SlotIcon.WIFI,cycle.frame(3000).icon)
        assertEquals(0f,cycle.frame(3000).opacity,0f)
        assertEquals(SlotFrame(SlotIcon.WIFI),cycle.frame(3160))
        assertEquals(SlotIcon.BLUETOOTH,cycle.frame(6000).icon)
    }
    @Test fun `pause preserves phase over a long pocket interval`() {
        val cycle=SlotCycle(); cycle.update(listOf(SlotIcon.WIFI,SlotIcon.SHARE),0)
        cycle.frame(2880);cycle.pause(2940); val before=cycle.frame(2940)
        assertEquals(before,cycle.frame(120000))
        assertEquals(Long.MAX_VALUE,cycle.nextDelay(120000))
        cycle.resume(120000); assertEquals(before,cycle.frame(120000))
        assertEquals(SlotIcon.SHARE,cycle.frame(120060).icon)
    }
    @Test fun `single icon has no periodic wakeup and duplicate broadcasts do not restart cycle`() {
        val cycle=SlotCycle(); cycle.update(listOf(SlotIcon.WIFI),0)
        assertEquals(Long.MAX_VALUE,cycle.nextDelay(9999))
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.SHARE),0)
        cycle.update(listOf(SlotIcon.WIFI,SlotIcon.SHARE),2999)
        assertEquals(SlotIcon.SHARE,cycle.frame(3000).icon)
    }
    @Test fun `check and underlying icon never overlap at any millisecond`() {
        for(t in -1L..1100L)assertEquals("time=$t",0f,EffectTimeline.check(t)*EffectTimeline.checkNormal(t),0f)
        assertEquals(1f,EffectTimeline.checkNormal(0),0f)
        assertEquals(0f,EffectTimeline.checkNormal(700),0f)
        assertEquals(1f,EffectTimeline.checkNormal(980),0f)
    }
    @Test fun `audio and underlying icon never overlap at any millisecond`() {
        for(t in -1L..4100L)assertEquals("time=$t",0f,EffectTimeline.audio(t)*EffectTimeline.audioNormal(t),0f)
    }
    @Test fun `interrupted entry finishes exit from current opacity before latest icon enters`() {
        val swap=SequentialSwap();swap.update(1,0,false);swap.update(2,100,true)
        assertEquals(SwapFrame(2,0f),swap.frame(220))
        assertEquals(SwapFrame(2,.5f),swap.frame(300))
        swap.update(3,300,true)
        assertEquals(SwapFrame(2,.5f),swap.frame(300))
        assertEquals(SwapFrame(2,.25f),swap.frame(360))
        assertEquals(SwapFrame(3,0f),swap.frame(420))
        assertEquals(SwapFrame(3,1f),swap.frame(580))
    }
    @Test fun `empty carousel fades previous occupant to empty then stops callbacks`() {
        val c=SlotCycle();c.update(listOf(SlotIcon.CAMERA),0);c.update(emptyList(),100)
        assertEquals(SlotIcon.CAMERA,c.frame(160).icon)
        assertEquals(.5f,c.frame(160).opacity,.001f)
        assertNull(c.frame(220).icon)
        c.frame(380);assertEquals(Long.MAX_VALUE,c.nextDelay(380))
    }
    @Test fun `privacy indicators are distinct occupants and cycle through both`() {
        val c=SlotCycle();c.update(listOf(SlotIcon.CAMERA,SlotIcon.MICROPHONE),0)
        assertEquals(SlotIcon.CAMERA,c.frame(0).icon)
        assertEquals(SlotIcon.MICROPHONE,c.frame(3160).icon)
    }
    @Test fun `new privacy event enters promptly instead of waiting behind ordinary statuses`() {
        val c=SlotCycle();c.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH),0)
        c.update(listOf(SlotIcon.WIFI,SlotIcon.BLUETOOTH,SlotIcon.MICROPHONE),1000)
        assertEquals(SlotIcon.WIFI,c.frame(1060).icon)
        assertEquals(.5f,c.frame(1060).opacity,.001f)
        assertEquals(SlotIcon.MICROPHONE,c.frame(1120).icon)
        assertEquals(0f,c.frame(1120).opacity,0f)
        assertEquals(1f,c.frame(1280).opacity,0f)
    }
}
