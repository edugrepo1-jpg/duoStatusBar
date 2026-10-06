package io.github.kvmy666.duostatusbar.fx

import android.content.*
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.SettingsBridge

/** Actual Android media and saved-screenshot events. Every registration is paired with stop. */
internal class RuntimeExperience(private val context:Context,private val handler:Handler,
    private val changed:()->Unit,private val screenshot:()->Unit) {
    var playback=PlaybackSnapshot();private set
    var playbackKnown=false;private set
    private var sessionListenerRegistered=false
    private var sessionAccessDenied=false
    private var sessionPollAt=-30000L
    private var cachedMetadata:MediaMetadata?=null
    private var cachedToken:MediaSession.Token?=null
    private var cachedColor=0
    private var directPlayback:PlaybackSnapshot?=null
    private var dirty=true
    private val sessionListener=MediaSessionManager.OnActiveSessionsChangedListener { next ->
        if(running) { sessionPollAt=SystemClock.elapsedRealtime();replaceControllers(next.orEmpty());refresh() }
    }
    private var relayedPlayback=PlaybackSnapshot()
    private var relayAt=-1L
    private var running=false
    private var receiverRegistered=false
    private var observer:ScreenshotObserver?=null
    private var controllers=emptyList<MediaController>()
    private val failures=mutableSetOf<String>()
    private val manager by lazy { context.getSystemService(MediaSessionManager::class.java) }
    private val callback=object:MediaController.Callback() {
        override fun onPlaybackStateChanged(state:PlaybackState?) { dirty=true;refresh() }
        override fun onMetadataChanged(metadata:MediaMetadata?) { dirty=true;refresh() }
        override fun onSessionDestroyed() { dirty=true;sessionPollAt=-30000L;refresh() }
    }
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context?,intent:Intent?) {
            if(!running||intent?.action!=ACTION)return
            if(intent.getBooleanExtra("shot",false)) { if(Fx.experience.screenshot)screenshot();return }
            relayedPlayback=PlaybackSnapshot(intent.getBooleanExtra("playing",false),
                intent.getLongExtra("position",-1).coerceAtLeast(-1),intent.getLongExtra("duration",0).coerceIn(0,7*86400000L),
                SystemClock.elapsedRealtime(),intent.getFloatExtra("speed",1f).takeIf { it.isFinite() }?.coerceIn(0f,8f) ?: 1f,
                intent.getIntExtra("color",0),intent.getStringExtra("title").orEmpty().take(120))
            relayAt=SystemClock.elapsedRealtime();refresh()
        }
    }
    private fun read(name:String,action:()->Unit) {
        try { action() } catch(t:Throwable) { if(failures.add(name))L.w("Experiência $name: ${t.javaClass.simpleName}; acesso pelo app disponível") }
    }
    fun start() {
        if(running)return
        running=true
        configure()
    }
    fun configure() {
        if(!running)return
        dirty=true
        val relay=Fx.experience.music||Fx.experience.island||Fx.experience.screenshot||Fx.enabled(8192)
        if(relay&&!receiverRegistered)read("canal do app") {
            context.registerReceiver(receiver,IntentFilter(ACTION),SettingsBridge.PERMISSION,handler,Context.RECEIVER_EXPORTED)
            receiverRegistered=true
        }
        if(!relay&&receiverRegistered) { read("encerrar canal") { context.unregisterReceiver(receiver) };receiverRegistered=false }
        if(Fx.experience.screenshot&&observer==null) {
            observer=ScreenshotObserver(context,handler) { if(running)screenshot() }.also { it.start() }
        } else if(!Fx.experience.screenshot) { observer?.stop();observer=null }
        if(!Fx.experience.music&&!Fx.experience.island&&!Fx.enabled(8192)) {
            stopMedia();playback=PlaybackSnapshot();playbackKnown=false
        } else {
            if(!sessionListenerRegistered&&!sessionAccessDenied)read("monitor de sessões") {
                try {
                    manager?.addOnActiveSessionsChangedListener(sessionListener,null,handler)
                    sessionListenerRegistered=manager!=null
                } catch(t:SecurityException) { sessionAccessDenied=true;throw t }
            }
            refresh()
        }
    }
    private fun replaceControllers(next:List<MediaController>) {
        if(controllers.map { it.sessionToken }==next.map { it.sessionToken })return
        controllers.forEach { read("encerrar mídia") { it.unregisterCallback(callback) } }
        controllers=next;dirty=true
        controllers.forEach { read("monitor de mídia") { it.registerCallback(callback,handler) } }
    }
    private fun stopMedia() {
        replaceControllers(emptyList())
        if(sessionListenerRegistered)read("encerrar sessões") { manager?.removeOnActiveSessionsChangedListener(sessionListener) }
        sessionListenerRegistered=false;cachedMetadata=null;cachedToken=null;cachedColor=0;directPlayback=null;dirty=true
    }
    fun refresh() {
        if(!running||(!Fx.experience.music&&!Fx.experience.island&&!Fx.enabled(8192)))return
        val now=SystemClock.elapsedRealtime()
        var direct=directPlayback
        // Session discovery is callback-driven. Permission denial is retried only on a new lifecycle,
        // not every two seconds on SystemUI's main thread.
        if(!sessionAccessDenied&&now-sessionPollAt>=30000) {
            sessionPollAt=now;dirty=true
            read("sessões de música") {
                try { replaceControllers(manager?.getActiveSessions(null).orEmpty()) }
                catch(t:SecurityException) { sessionAccessDenied=true;throw t }
            }
        }
        if(dirty) {
            dirty=false;directPlayback=null;direct=null
            read("estado de música") {
            val selected=controllers.firstOrNull { it.playbackState?.state==PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
            if(selected!=null&&selected.playbackState!=null) {
                val metadata=selected.metadata
                if(cachedToken!=selected.sessionToken||metadata!==cachedMetadata) {
                    cachedToken=selected.sessionToken;cachedMetadata=metadata;cachedColor=albumColor(metadata)
                }
                direct=snapshot(selected,cachedColor)
            }
                directPlayback=direct
            }
        }
        val relayFresh=relayAt>=0L&&now-relayAt<35000L
        // A confirmed pause cannot become "playing" merely because its heartbeat expires.
        val known=direct!=null||relayFresh||(relayAt>=0L&&!relayedPlayback.playing)
        val next=direct ?: relayedPlayback.takeIf { relayFresh } ?: PlaybackSnapshot()
        if(next!=playback||known!=playbackKnown) { playback=next;playbackKnown=known;changed() }
    }
    fun stop() {
        running=false
        observer?.stop();observer=null
        stopMedia()
        if(receiverRegistered) { read("encerrar canal") { context.unregisterReceiver(receiver) };receiverRegistered=false }
        playback=PlaybackSnapshot();relayedPlayback=PlaybackSnapshot();playbackKnown=false;relayAt=-1
        sessionAccessDenied=false;sessionPollAt=-30000L
    }
    companion object {
        const val ACTION="io.github.kvmy666.duostatusbar.action.EXPERIENCE"
        fun snapshot(controller:MediaController):PlaybackSnapshot=snapshot(controller,null)
        private fun snapshot(controller:MediaController,color:Int?):PlaybackSnapshot {
            val state=controller.playbackState
            val metadata=controller.metadata
            return PlaybackSnapshot(state?.state==PlaybackState.STATE_PLAYING,state?.position ?: -1,
                metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,state?.lastPositionUpdateTime ?: 0L,
                state?.playbackSpeed ?: 1f,color ?: albumColor(metadata),metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().take(120))
        }
        private fun albumColor(metadata:MediaMetadata?):Int {
            val bitmap=metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: return 0
            if(bitmap.isRecycled||bitmap.config==Bitmap.Config.HARDWARE)return 0
            var r=0L;var g=0L;var b=0L;var n=0
            for(x in 0..7)for(y in 0..7) {
                val c=bitmap.getPixel(x*(bitmap.width-1)/7,y*(bitmap.height-1)/7)
                if(android.graphics.Color.alpha(c)>100) { r+=android.graphics.Color.red(c);g+=android.graphics.Color.green(c);b+=android.graphics.Color.blue(c);n++ }
            }
            if(n==0)return 0
            val hsv=FloatArray(3)
            android.graphics.Color.RGBToHSV((r/n).toInt(),(g/n).toInt(),(b/n).toInt(),hsv)
            hsv[1]=hsv[1].coerceIn(.35f,.8f);hsv[2]=hsv[2].coerceAtLeast(.85f)
            return android.graphics.Color.HSVToColor(hsv)
        }
    }
}

/** Queries only names/path/date; does not open, copy or upload the captured image. */
internal class ScreenshotObserver(private val context:Context,private val main:Handler,private val detected:()->Unit) {
    private var thread:HandlerThread?=null
    private var worker:Handler?=null
    private var lastId=-1L
    @Volatile private var started=false
    @Volatile private var generation=0L
    private var failed=false
    private val scan=Runnable { query(false,generation) }
    private val observer=object:ContentObserver(main) {
        override fun onChange(selfChange:Boolean,uri:Uri?) {
            if(started) { worker?.removeCallbacks(scan);worker?.postDelayed(scan,350) }
        }
    }
    fun start() {
        if(started)return
        try {
            thread=HandlerThread("DuoScreenshotNames").also { it.start() };worker=Handler(thread!!.looper)
            generation++;val activeGeneration=generation;started=true
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer)
            worker?.post { query(true,activeGeneration) }
        } catch(t:Throwable) { L.w("Captura: observador indisponível: ${t.javaClass.simpleName}");stop() }
    }
    private fun query(baseline:Boolean,expectedGeneration:Long) {
        if(!started||expectedGeneration!=generation)return
        try {
            val args=Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,arrayOf(MediaStore.Images.Media._ID))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT,8)
            }
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.DATE_ADDED),args,null)?.use { cursor ->
                var newest=lastId;var shot=false
                while(cursor.moveToNext()) {
                    val id=cursor.getLong(0);newest=maxOf(newest,id)
                    val name=cursor.getString(1).orEmpty();val path=cursor.getString(2).orEmpty()
                    val age=System.currentTimeMillis()/1000-cursor.getLong(3)
                    if(!baseline&&lastId>=0&&id>lastId&&age in 0..15&&isScreenshot(name,path))shot=true
                }
                if(!started||expectedGeneration!=generation)return@use
                lastId=if(baseline)maxOf(0L,newest) else newest
                if(shot)main.post { if(started&&expectedGeneration==generation)detected() }
            }
        } catch(t:Throwable) { if(!failed){failed=true;L.w("Captura: leitura de nomes indisponível: ${t.javaClass.simpleName}; habilite acesso no app") } }
    }
    fun stop() {
        if(started)runCatching { context.contentResolver.unregisterContentObserver(observer) }
        started=false;generation++;worker?.removeCallbacksAndMessages(null);thread?.quitSafely();worker=null;thread=null;lastId=-1
    }
    companion object {
        fun isScreenshot(name:String,path:String):Boolean {
            val value=(path+"/"+name).lowercase()
            return value.contains("screenshot")||value.contains("screen_capture")||value.contains("screencapture")||value.contains("captura de tela")
        }
    }
}
