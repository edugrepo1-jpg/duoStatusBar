package io.github.kvmy666.duostatusbar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.fx.*
import io.github.kvmy666.duostatusbar.hook.*
import io.github.kvmy666.duostatusbar.i18n.UiText
import io.github.kvmy666.duostatusbar.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuroraControlsTest {
    @Before fun language() {UiText.initialize(ApplicationProvider.getApplicationContext<Context>(),"pt-BR")}
    @Test fun `all Aurora controls have one category and none are omitted`() {
        val effects=AuroraEffectGroups.flatten()
        assertEquals(effects.size,effects.toSet().size)
        assertTrue(AuroraVisualKeys.intersect(effects.toSet()).isEmpty())
        assertEquals(AuroraOptions.KEYS,AuroraVisualKeys+effects.toSet())
    }
    @Test fun `timer command cannot target an arbitrary receiver or command`() {
        assertNull(auroraTimerIntent("reboot"))
        for(key in listOf("timerStart","timerPause","timerReset","stopwatchStart","stopwatchPause","stopwatchReset")) {
            val intent=auroraTimerIntent(key)!!
            assertEquals(RuntimeExperience.ACTION,intent.action);assertEquals(SettingsBridge.SYSTEMUI,intent.`package`)
            assertEquals(key,intent.getStringExtra("timerCommand"))
        }
    }
    @Test fun `timeline uses separate exit and entry from the real transition engine`() {
        val settings=DuoSettings(enabled=true);val options=ExperienceOptions(exitMs=120,entryMs=160)
        val outgoing=auroraTimelinePose(AuroraTimelineScene.SWAP,60,settings,options)
        val boundary=auroraTimelinePose(AuroraTimelineScene.SWAP,120,settings,options)
        val incoming=auroraTimelinePose(AuroraTimelineScene.SWAP,200,settings,options)
        assertEquals(SlotIcon.WIFI,outgoing.frame.slot.icon);assertTrue(outgoing.frame.slot.opacity in .49f.. .51f)
        assertEquals(SlotIcon.BLUETOOTH,boundary.frame.slot.icon);assertEquals(0f,boundary.frame.slot.opacity,0f)
        assertEquals(SlotIcon.BLUETOOTH,incoming.frame.slot.icon);assertTrue(incoming.frame.slot.opacity in .49f.. .51f)
        assertEquals(outgoing,auroraTimelinePose(AuroraTimelineScene.SWAP,60,settings,options))
    }
    @Test fun `disabled motion keeps scrubbing from inventing animated check`() {
        val pose=auroraTimelinePose(AuroraTimelineScene.UNLOCK,420,DuoSettings(enabled=true,animationsEnabled=false),ExperienceOptions.ALL)
        assertEquals(-1L,pose.frame.checkMs);assertFalse(pose.frame.motionEnabled)
        val instant=auroraTimelinePose(AuroraTimelineScene.SWAP,0,DuoSettings(enabled=true),ExperienceOptions(fadeEnabled=false))
        assertEquals(SlotIcon.BLUETOOTH,instant.frame.slot.icon);assertEquals(1f,instant.frame.slot.opacity,0f)
    }
    @Test fun `timeline respects unlock opt out unless an Aurora unlock effect is enabled`() {
        val settings=DuoSettings(enabled=true,featFlags=0)
        assertEquals(-1L,auroraTimelinePose(AuroraTimelineScene.UNLOCK,420,settings,ExperienceOptions()).frame.checkMs)
        assertEquals(420L,auroraTimelinePose(AuroraTimelineScene.UNLOCK,420,settings,ExperienceOptions(aurora=AuroraOptions(setOf("unlockBuild")))).frame.checkMs)
    }
    @Test fun `timeline respects the same short dwell fade budget as runtime`() {
        for(options in listOf(ExperienceOptions(dwellMs=1000,exitMs=600,entryMs=800),
            ExperienceOptions(dwellMs=60000,exitMs=600,entryMs=800,universalTiming=false,iconSeconds=mapOf("NFC" to 1)))) {
            val cycle=SlotCycle().apply {configure(options,0)}
            val (exit,entry)=cycle.transitionDurations()
            val pose=auroraTimelinePose(AuroraTimelineScene.SWAP,exit,DuoSettings(enabled=true),options)
            assertEquals(exit+entry,pose.durationMs);assertTrue(pose.durationMs<=800)
            assertEquals(SlotIcon.BLUETOOTH,pose.frame.slot.icon);assertEquals(0f,pose.frame.slot.opacity,0f)
        }
    }
    @Test fun `native timeline pixels are deterministic when rewound`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val settings=DuoSettings(enabled=true);val options=ExperienceOptions(aurora=AuroraOptions.ALL)
        val view=DuoCanvasView(context).apply {animationsEnabled=false;arrivalEnabled=false;layout(0,0,360,408)}
        fun pixels(time:Long):IntArray {
            view.effects=auroraTimelinePose(AuroraTimelineScene.UNLOCK,time,settings,options).frame
            view.render(DuoMapping.visual(72,false,false,true,3,4,false))
            val image=Bitmap.createBitmap(360,408,Bitmap.Config.ARGB_8888);view.draw(Canvas(image))
            return IntArray(360*408).also {image.getPixels(it,0,360,0,0,360,408);image.recycle()}
        }
        try {
            val first=pixels(420);assertTrue(first.any {it ushr 24>0})
            val other=pixels(3160);assertFalse(first.contentEquals(other))
            assertArrayEquals(first,pixels(420))
        } finally {view.teardown()}
    }
}
