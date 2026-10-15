package io.github.kvmy666.duostatusbar.fx

import android.content.*
import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.settings.*

/** Local fallback driven by session changes. It never polls playback at animation rate. */
class ExperienceRelayService:NotificationListenerService() {
    private val handler=Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this,ExperienceRelayService::class.java) }
    private var connected=false
    private var registered=false
    private var watching=false
    private var screen=true
    private var music=false
    private var clocks=false
    private val clockTracker=ClockNotificationTracker()
    private var tasks=false
    private val progress=NotificationProgressTracker()
    private var screenshots:ScreenshotObserver?=null
    private var failed=false
    private var controllers=emptyList<MediaController>()
    private var latest=PlaybackSnapshot()
    private var sentAt=0L
    private var retryNotBefore=0L
    private var metadataDirty=true
    private var cachedToken:android.media.session.MediaSession.Token?=null
    private var cachedMetadata:MediaMetadata?=null
    private var cachedColor=0
    private val artworkCache=MediaArtworkCache()
    private var cachedArtwork:android.graphics.Bitmap?=null
    private val callback=object:MediaController.Callback() {
        override fun onPlaybackStateChanged(state:PlaybackState?) { publish() }
        override fun onMetadataChanged(metadata:MediaMetadata?) { metadataDirty=true;publish() }
        override fun onSessionDestroyed() { readSessions() }
    }
    private val sessionListener=MediaSessionManager.OnActiveSessionsChangedListener { next ->
        if(connected&&music) { replaceControllers(next.orEmpty());publish() }
    }
    private val refreshReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(!connected)return
            when(intent?.action) {
                Intent.ACTION_SCREEN_OFF -> { screen=false;scheduleHeartbeat() }
                Intent.ACTION_SCREEN_ON -> { screen=true;if(music)readSessions(force=true) }
                REFRESH -> configure()
            }
        }
    }
    private val heartbeat=Runnable {
        if(connected&&music&&screen)readSessions(force=true)
    }
    override fun onListenerConnected() {
        connected=true
        screen=getSystemService(PowerManager::class.java)?.isInteractive!=false
        runCatching {
            if(!registered) {
                registerReceiver(refreshReceiver,IntentFilter(REFRESH).apply {
                    addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_SCREEN_OFF)
                },null,handler,Context.RECEIVER_NOT_EXPORTED)
                registered=true
            }
            configure()
        }.onFailure { L.w("Acesso local: ${it.javaClass.simpleName}") }
    }
    private fun configure() {
        val p=DuoPrefs.read(this,DuoOrientation.PORTRAIT)
        val l=DuoPrefs.read(this,DuoOrientation.LANDSCAPE)
        val a=ExperienceOptions.decode(p.experienceJson);val b=ExperienceOptions.decode(l.experienceJson)
        val clocksWanted=(p.enabled&&a.aurora.timer)||(l.enabled&&b.aurora.timer)
        clocks=clocksWanted
        clockTracker.clear()
        if(clocks)runCatching {activeNotifications.orEmpty().forEach {clockTracker.observe(it)}}
            .onFailure {L.w("Rel�gio: metadados indispon�veis=${it.javaClass.simpleName}")}
        send(clockTracker.intent())
        val tasksWanted=(p.enabled&&a.aurora.taskComplete)||(l.enabled&&b.aurora.taskComplete)
        if(tasksWanted!=tasks) {
            tasks=tasksWanted;progress.clear()
            if(tasks)runCatching {activeNotifications.orEmpty().forEach {trackTask(it,seed=true)}}
                .onFailure {L.w("Tarefas: acesso às notificações indisponível=${it.javaClass.simpleName}")}
        }
        val wanted=(p.enabled&&(a.music||a.island||(p.featFlags and BuildConfig.FEATURE_MASK and 8192 != 0)))||
            (l.enabled&&(b.music||b.island||(l.featFlags and BuildConfig.FEATURE_MASK and 8192 != 0)))
        if(!wanted&&music) { music=false;stopMedia();latest=PlaybackSnapshot();sendPlayback(latest) }
        music=wanted
        if(music) {
            if(!watching)runCatching {
                manager?.addOnActiveSessionsChangedListener(sessionListener,component,handler)
                watching=manager!=null
            }.onFailure(::failure)
            readSessions(force=true)
        }
        val shots=(p.enabled&&a.screenshot)||(l.enabled&&b.screenshot)
        val permitted=checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)==android.content.pm.PackageManager.PERMISSION_GRANTED
        if(shots&&permitted&&screenshots==null) {
            screenshots=ScreenshotObserver(this,handler) {
                if(connected)send(Intent(RuntimeExperience.ACTION).putExtra("shot",true))
            }.also { it.start() }
        } else if(!shots||!permitted) { screenshots?.stop();screenshots=null }
    }
    private fun replaceControllers(next:List<MediaController>) {
        if(controllers.map { it.sessionToken }==next.map { it.sessionToken })return
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers=next
        controllers.forEach { runCatching { it.registerCallback(callback,handler) }.onFailure(::failure) }
    }
    private fun readSessions(force:Boolean=false) {
        if(!connected||!music)return
        if(force)metadataDirty=true
        runCatching { replaceControllers(manager?.getActiveSessions(component).orEmpty());publish(force) }
            .onFailure { mediaFailure(it);scheduleHeartbeat() }
    }
    private fun publish(force:Boolean=false) {
        if(!connected||!music)return
        runCatching {
            val states=controllers.map { it to it.playbackState }
            val selected=states.firstOrNull { it.second?.state==PlaybackState.STATE_PLAYING } ?: states.firstOrNull()
            val next=if(selected==null)PlaybackSnapshot() else {
                val controller=selected.first
                if(metadataDirty||cachedToken!=controller.sessionToken) {
                    cachedToken=controller.sessionToken;cachedMetadata=controller.metadata;cachedArtwork=artworkCache.read(cachedMetadata)
                    RuntimeExperience.snapshot(controller,selected.second,cachedMetadata,artwork=cachedArtwork).also {
                        cachedColor=it.color;metadataDirty=false
                    }
                } else RuntimeExperience.snapshot(controller,selected.second,cachedMetadata,cachedColor,cachedArtwork)
            }
            retryNotBefore=0L
            if(force||next!=latest) { latest=next;sendPlayback(next) }
        }.onFailure(::mediaFailure)
        scheduleHeartbeat()
    }
    private fun scheduleHeartbeat() {
        handler.removeCallbacks(heartbeat)
        if(shouldHeartbeat(connected,music,screen,latest.playing,watching)) {
            val now=SystemClock.elapsedRealtime()
            handler.postDelayed(heartbeat,maxOf(heartbeatDelay(sentAt,now),(retryNotBefore-now).coerceAtLeast(0L)))
        }
    }
    private fun sendPlayback(s:PlaybackSnapshot) {
        sentAt=SystemClock.elapsedRealtime()
        send(Intent(RuntimeExperience.ACTION).putExtra("playing",s.playing)
            .putExtra("position",if(s.positionMs<0)-1 else (s.progress(SystemClock.elapsedRealtime())*s.durationMs).toLong())
            .putExtra("duration",s.durationMs).putExtra("speed",s.speed).putExtra("color",s.color).putExtra("title",s.title)
            .putExtra("source",s.sourcePackage).putExtra("artwork",s.artwork?.takeIf {MediaArtwork.valid(it)}))
    }
    private fun send(intent:Intent) {
        val target=if(io.github.kvmy666.duostatusbar.settings.RuntimeBackend.mode(this)=="root")
            io.github.kvmy666.duostatusbar.BuildConfig.APPLICATION_ID else SettingsBridge.SYSTEMUI
        runCatching { sendBroadcast(intent.setPackage(target)) }.onFailure(::failure)
    }
    private fun trackTask(notification:StatusBarNotification,seed:Boolean=false) {
        if(!connected||!tasks)return
        val extras=notification.notification.extras
        val done=progress.observe(notification.key,extras.getInt(Notification.EXTRA_PROGRESS,-1),
            extras.getInt(Notification.EXTRA_PROGRESS_MAX,-1),!extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE,false),seed)
        if(done)send(Intent(RuntimeExperience.ACTION).putExtra("taskComplete",true)
            .putExtra("source",RuntimeExperience.sourceIdentity(notification.packageName)))
    }
    override fun onNotificationPosted(sbn:StatusBarNotification?) {
        if(sbn!=null)runCatching {trackTask(sbn);if(clocks&&clockTracker.observe(sbn))send(clockTracker.intent())}.onFailure {L.w("Tarefas: progresso indisponível=${it.javaClass.simpleName}")}
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification?) {sbn?.let {progress.remove(it.key);if(clocks&&clockTracker.remove(it.key))send(clockTracker.intent())}}
    private fun failure(t:Throwable) {
        if(!failed) { failed=true;L.w("Acesso de música: ${t.javaClass.simpleName}") }
    }
    private fun mediaFailure(t:Throwable) {
        // An overdue heartbeat must not spin on the main looper while Binder/permission access fails.
        // Genuine session callbacks can still recover immediately; only scheduled retries back off.
        retryNotBefore=SystemClock.elapsedRealtime()+HEARTBEAT_MS
        failure(t)
    }
    private fun stopMedia() {
        handler.removeCallbacks(heartbeat)
        controllers.forEach { runCatching { it.unregisterCallback(callback) } };controllers=emptyList()
        if(watching)runCatching { manager?.removeOnActiveSessionsChangedListener(sessionListener) }
        watching=false
        retryNotBefore=0L
        cachedToken=null;cachedMetadata=null;cachedColor=0;cachedArtwork=null;artworkCache.clear();metadataDirty=true
    }
    private fun stop() {
        if(connected&&music)sendPlayback(PlaybackSnapshot())
        clockTracker.clear();if(connected)send(clockTracker.intent());clocks=false
        connected=false;music=false;stopMedia();handler.removeCallbacksAndMessages(null)
        tasks=false;progress.clear()
        screenshots?.stop();screenshots=null
        if(registered) { runCatching { unregisterReceiver(refreshReceiver) };registered=false }
        latest=PlaybackSnapshot()
    }
    override fun onListenerDisconnected() { stop();super.onListenerDisconnected() }
    override fun onDestroy() { stop();super.onDestroy() }
    companion object {
        const val REFRESH="io.github.RECREATE.statusbar.action.EXPERIENCE_REFRESH"
        const val HEARTBEAT_MS=30000L
        fun heartbeatDelay(sentAt:Long,now:Long)=(HEARTBEAT_MS-(now-sentAt).coerceAtLeast(0L)).coerceIn(0L,HEARTBEAT_MS)
        fun shouldHeartbeat(connected:Boolean,music:Boolean,screen:Boolean,playing:Boolean,watching:Boolean)=
            connected&&music&&screen&&(playing||!watching)
    }
}
