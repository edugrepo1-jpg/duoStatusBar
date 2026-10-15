package io.github.kvmy666.duostatusbar.fx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class NfcRuntimeTest {
    @Test fun `only fresh opted in field events reach effect callback without changing playback`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val events=mutableListOf<Boolean>()
        val runtime=RuntimeExperience(context,Handler(Looper.getMainLooper()),{},{})
        runtime.onNfcField={events.add(it)}
        Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=ExperienceOptions(nfcParticles=true).encode()),context)
        val receiver=runtime.javaClass.getDeclaredField("receiver").apply {isAccessible=true}.get(runtime) as BroadcastReceiver
        fun send(at:Long,on:Boolean=true)=receiver.onReceive(context,Intent(RuntimeExperience.ACTION).putExtra("nfcField",on).putExtra("nfcFieldAt",at))
        runtime.start()
        try {
            val now=SystemClock.elapsedRealtime()
            send(-1);send(now+10000);assertTrue(events.isEmpty())
            send(now);send(now);assertEquals(listOf(true),events)
            assertFalse(runtime.playback.playing)
            Fx.sync(ModuleSettings.DEFAULT.copy(featFlags=0,experienceJson=ExperienceOptions().encode()),context)
            runtime.configure();send(now+1);assertEquals(1,events.size)
            runtime.stop();send(now);assertEquals(1,events.size)
        } finally {runtime.stop();Fx.sync(ModuleSettings.DEFAULT,context)}
    }
}
