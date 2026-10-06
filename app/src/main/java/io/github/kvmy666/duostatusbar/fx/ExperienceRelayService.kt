package io.github.kvmy666.duostatusbar.fx

import android.content.*
import android.media.session.MediaSessionManager
import android.os.*
import android.service.notification.NotificationListenerService
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.*

/** Optional local fallback when SystemUI lacks media/photo access; no remote requests or storage. */
class ExperienceRelayService:NotificationListenerService() {
    private val handler=Handler(Looper.getMainLooper())
    private var connected=false
    private var registered=false
    private var music=false
    private var screenshots:ScreenshotObserver?=null
    private var failed=false
    private val refreshReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) { if(connected)configure() }
    }
    private val tick=object:Runnable {
        override fun run() {
            if(!connected||!music)return
            try {
                val sessions=getSystemService(MediaSessionManager::class.java)?.getActiveSessions(ComponentName(this@ExperienceRelayService,ExperienceRelayService::class.java)).orEmpty()
                val controller=sessions.firstOrNull { it.playbackState?.state==android.media.session.PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
                val s=controller?.let(RuntimeExperience::snapshot) ?: PlaybackSnapshot()
                send(Intent(RuntimeExperience.ACTION).putExtra("playing",s.playing)
                    .putExtra("position",if(s.positionMs<0)-1 else (s.progress(SystemClock.elapsedRealtime())*s.durationMs).toLong())
                    .putExtra("duration",s.durationMs).putExtra("speed",s.speed).putExtra("color",s.color).putExtra("title",s.title))
            } catch(t:Throwable) { if(!failed){failed=true;L.w("Acesso de música: ${t.javaClass.simpleName}") } }
            handler.postDelayed(this,1000)
        }
    }
    override fun onListenerConnected() {
        connected=true
        runCatching {
            if(!registered) { registerReceiver(refreshReceiver,IntentFilter(REFRESH),Context.RECEIVER_NOT_EXPORTED);registered=true }
            configure()
        }.onFailure { L.w("Acesso local: ${it.javaClass.simpleName}") }
    }
    private fun configure() {
        val p=DuoPrefs.read(this,DuoOrientation.PORTRAIT)
        val l=DuoPrefs.read(this,DuoOrientation.LANDSCAPE)
        val a=ExperienceOptions.decode(p.experienceJson);val b=ExperienceOptions.decode(l.experienceJson)
        music=(p.enabled&&a.music)||(l.enabled&&b.music)
        handler.removeCallbacks(tick)
        if(music)handler.post(tick)
        val shots=(p.enabled&&a.screenshot)||(l.enabled&&b.screenshot)
        val permitted=checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)==android.content.pm.PackageManager.PERMISSION_GRANTED
        if(shots&&permitted&&screenshots==null) {
            screenshots=ScreenshotObserver(this,handler) { if(connected)send(Intent(RuntimeExperience.ACTION).putExtra("shot",true)) }.also { it.start() }
        } else if(!shots||!permitted) { screenshots?.stop();screenshots=null }
    }
    private fun send(intent:Intent) { sendBroadcast(intent.setPackage(SettingsBridge.SYSTEMUI)) }
    private fun stop() {
        connected=false;handler.removeCallbacksAndMessages(null);screenshots?.stop();screenshots=null
        if(registered) { runCatching { unregisterReceiver(refreshReceiver) };registered=false }
    }
    override fun onListenerDisconnected() { stop();super.onListenerDisconnected() }
    override fun onDestroy() { stop();super.onDestroy() }
    companion object { const val REFRESH="io.github.kvmy666.duostatusbar.action.EXPERIENCE_REFRESH" }
}
