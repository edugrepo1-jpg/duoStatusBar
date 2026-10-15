package io.github.kvmy666.duostatusbar.fx

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class AuroraRuntimeIngressTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @After fun restore(){AuroraActivities.reset();Fx.sync(ModuleSettings.DEFAULT,context)}
    private fun field(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply {isAccessible=true}
    @Test fun `privacy source ownership and source-only changes update local state without merging profiles`() {
        var changes=0
        val indicators=RuntimeIndicators(context,Handler(Looper.getMainLooper())) {changes++}
        field(indicators,"started").set(indicators,true)
        val listener=field(indicators,"opListener").get(indicators) as AppOpsManager.OnOpActiveChangedListener
        try {
            listener.onOpActiveChanged(AppOpsManager.OPSTR_CAMERA,1001,"camera.owner",true)
            listener.onOpActiveChanged(AppOpsManager.OPSTR_RECORD_AUDIO,1001,"camera.owner",true)
            assertTrue(indicators.privacySharedSource)
            assertEquals(setOf("camera.owner"),indicators.cameraSources)
            val before=changes
            listener.onOpActiveChanged(AppOpsManager.OPSTR_CAMERA,1002,"other.owner",true)
            assertTrue(changes>before) // Camera remains true: an origin-only change still reaches island.
            listener.onOpActiveChanged(AppOpsManager.OPSTR_CAMERA,1001,"camera.owner",false)
            assertFalse(indicators.privacySharedSource)
            listener.onOpActiveChanged(AppOpsManager.OPSTR_CAMERA,2001,"camera.owner",true)
            assertFalse(indicators.privacySharedSource) // Same package in a different UID/profile is distinct.
            indicators.stop()
            assertTrue(indicators.cameraSources.isEmpty());assertTrue(indicators.microphoneSources.isEmpty())
        } finally {indicators.stop()}
    }
    @Test fun `timer and completion ingress works with every media option disabled and stops with lifecycle`() {
        val options=ExperienceOptions(aurora=AuroraOptions(setOf("timer","taskComplete")))
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=options.encode()),context)
        var changes=0
        val experience=RuntimeExperience(context,Handler(Looper.getMainLooper()),{changes++},{})
        try {
            experience.start()
            assertEquals(false,field(experience,"mediaEnabled").get(experience))
            assertEquals(true,field(experience,"receiverRegistered").get(experience))
            // Invoke the same registered receiver; signature permission authorization is a platform contract.
            val receiver=field(experience,"receiver").get(experience) as android.content.BroadcastReceiver
            receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("timerCommand","timerStart"))
            assertFalse(AuroraActivities.snapshot(android.os.SystemClock.elapsedRealtime()).timerRunning)
            receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("clockSnapshot",true)
                .putExtra("clockTimerWhen",System.currentTimeMillis()+60000))
            assertTrue(AuroraActivities.snapshot(android.os.SystemClock.elapsedRealtime()).timerRunning)
            val before=changes
            receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("taskComplete",true).putExtra("source","task.owner"))
            assertTrue(changes>before)
            assertEquals("task.owner",AuroraActivities.snapshot(android.os.SystemClock.elapsedRealtime()).completedSource)
            experience.stop()
            val stopped=changes;AuroraActivities.command("timerPause")
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(stopped,changes)
            assertEquals(false,field(experience,"receiverRegistered").get(experience))
        } finally {experience.stop()}
    }
}
