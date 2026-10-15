package io.github.kvmy666.duostatusbar.standalone

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.LocalServerSocket
import android.net.Uri
import android.os.*
import android.view.Display
import android.view.WindowManager
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.fx.Fx
import io.github.kvmy666.duostatusbar.hook.DuoSettingsClient
import io.github.kvmy666.duostatusbar.hook.DuoStateMonitor
import io.github.kvmy666.duostatusbar.hook.ModuleSettings
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import java.util.concurrent.Executors

/** Optional app_process entry. Root is required; this path never loads an Xposed entry point. */
object RootOverlayMain {
    @JvmStatic fun main(args:Array<String>) {
        val generation=args.firstOrNull().orEmpty()
        if(Process.myUid()!=0||!RootOverlayPolicy.validGeneration(generation))kotlin.system.exitProcess(1)
        if(args.size!=1 && !(args.size==2 && args[1]=="--restore-only"))kotlin.system.exitProcess(1)
        var session:RootSession?=null
        var socket:LocalServerSocket?=null
        var reportingContext:Context?=null
        var failedStartup=false
        try {
            if(Looper.getMainLooper()==null)Looper.prepareMainLooper()
            val activityThread=Class.forName("android.app.ActivityThread")
            val thread=activityThread.getDeclaredMethod("systemMain").invoke(null)
            val system=activityThread.getDeclaredMethod("getSystemContext").invoke(thread) as Context
            // The installed APK owns resources and provider identity; no framework resource IDs are guessed.
            val context=system.createPackageContext(BuildConfig.APPLICATION_ID,Context.CONTEXT_IGNORE_SECURITY)
            reportingContext=context
            if(args.getOrNull(1)=="--restore-only") {
                if(!RootStockIcons.recover(context,generation))kotlin.system.exitProcess(1)
                return
            }
            // Binding, rather than killing a global PID, guarantees only one renderer per device.
            val deadline=SystemClock.elapsedRealtime()+10000L
            while(socket==null) {
                try {socket=LocalServerSocket(RootOverlayPolicy.SOCKET)}
                catch(problem:java.io.IOException) {
                    if(SystemClock.elapsedRealtime()>=deadline)throw problem
                    Thread.sleep(200)
                }
            }
            session=RootSession(context,generation)
            session.start()
            Looper.loop()
        } catch(t:Throwable) {
            failedStartup=true
            L.w("Root Canvas startup unavailable=${t.javaClass.simpleName}")
            runCatching {reportingContext?.contentResolver?.call(Uri.parse("content://${BuildConfig.APPLICATION_ID}.settings"),
                "root_health",generation,Bundle().apply {putString("state","failed");putString("reason",t.javaClass.simpleName);putInt("versionCode",BuildConfig.VERSION_CODE)})}
        } finally {
            runCatching {session?.close()}
            runCatching {socket?.close()}
        }
        if(failedStartup)kotlin.system.exitProcess(1)
    }
}

private class RootSession(private val context:Context,private val generation:String) {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { task -> Thread(task,"DuoRootSettings").apply {isDaemon=true} }
    private val uri=Uri.parse("content://${BuildConfig.APPLICATION_ID}.settings")
    private val stock=RootStockIcons(context,generation)
    @Volatile private var host:RootOverlayHost?=null
    private var monitor:DuoStateMonitor?=null
    @Volatile private var closed=false
    private var reading=false
    private var failures=0
    private var loaded:ModuleSettings?=null
    private var loadedOrientation=Configuration.ORIENTATION_UNDEFINED
    private var screen=true
    private var ready=false
    private var consumedDumpRequest=0L
    private var lastHealth=-10000L
    private var stockStored=false
    private var stockRestored=false
    private var journalRecovered=false
    private var monitorStarted=false
    private var readToken=0L
    private var receiverRegistered=false
    private val changes=object:BroadcastReceiver() {
        override fun onReceive(c:Context?,intent:Intent?) {if(!closed)requestRead()}
    }
    private val displayManager=context.getSystemService(DisplayManager::class.java)
    private val displayListener=object:DisplayManager.DisplayListener {
        override fun onDisplayAdded(id:Int) {}
        override fun onDisplayRemoved(id:Int) {if(id==Display.DEFAULT_DISPLAY)stop("Display removed")}
        override fun onDisplayChanged(id:Int) {if(id==Display.DEFAULT_DISPLAY)requestRead()}
    }
    private val poll=Runnable {requestRead()}
    private val startupTimeout=Runnable {if(!closed&&loaded?.enabled==true&&!ready)stop("No successful Canvas frame within startup limit")}
    fun start() {
        displayManager?.registerDisplayListener(displayListener,handler)
        receiverRegistered=runCatching {
            context.registerReceiver(changes,IntentFilter(SettingsBridge.ACTION_SETTINGS_PUSH),SettingsBridge.PERMISSION,handler,Context.RECEIVER_EXPORTED)
            true
        }.getOrDefault(false)
        requestRead()
    }
    private fun orientation():Int {
        val display=displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val size=android.graphics.Point()
        @Suppress("DEPRECATION")
        display?.getRealSize(size)
        return if(size.x>size.y)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
    }
    private fun requestRead() {
        if(closed||reading)return
        handler.removeCallbacks(poll)
        reading=true
        val token=++readToken
        val currentOrientation=orientation()
        // A wedged external provider cannot leave a hidden stock bar indefinitely.
        val timeout=Runnable {if(!closed&&reading&&token==readToken)stop("Settings IPC timed out")}
        handler.postDelayed(timeout,RootOverlayPolicy.STARTUP_TIMEOUT_MS)
        worker.execute {
            val result=runCatching {
                val backend=context.contentResolver.call(uri,"runtime_backend",null,null)
                    ?: error("Backend provider unavailable")
                if(!RootOverlayPolicy.authorized(Process.myUid(),generation,backend.getString("mode"),backend.getString("generation")))
                    return@runCatching null
                val dumpRequest=backend.getLong("dumpRequest",0)
                if(dumpRequest>consumedDumpRequest) {consumedDumpRequest=dumpRequest;publishDump()}
                if(!journalRecovered) {
                    val journal=context.contentResolver.call(uri,"root_stock_journal",generation,null)
                        ?: error("Stock journal unavailable")
                    val previous=journal.getString("generation").orEmpty()
                    if(previous.isNotEmpty()&&previous!=generation)check(RootStockIcons.recover(context,previous)) {"Previous stock ownership could not be restored"}
                    journalRecovered=true
                }
                // The safety poll normally reads a small backend Bundle only. Decode/query the full
                // settings row after a revision/orientation change, not every two seconds.
                val cached=loaded
                if(cached!=null&&backend.containsKey("revision")&&backend.getLong("revision")==cached.revision&&currentOrientation==loadedOrientation)
                    return@runCatching cached
                context.contentResolver.query(uri,null,null,null,null)?.use { cursor ->
                    check(cursor.moveToFirst()) {"Settings row missing"}
                    DuoSettingsClient.fromCursor(cursor,currentOrientation)
                } ?: error("Settings cursor missing")
            }
            handler.post {
                handler.removeCallbacks(timeout)
                if(closed)return@post
                reading=false
                result.fold(onSuccess={ next ->
                    failures=0
                    if(next==null){stop("Root mode or generation changed");return@fold}
                    try {apply(next,currentOrientation)}
                    catch(problem:Throwable) {stop("Runtime configuration failed: ${problem.javaClass.simpleName}")}
                },onFailure={ problem ->
                    failures++
                    L.w("Root settings read failed=$failures class=${problem.javaClass.simpleName}")
                    if(failures>=RootOverlayPolicy.MAX_READ_FAILURES)stop("Settings unavailable after bounded retries")
                })
                if(!closed) {
                    screen=runCatching {context.getSystemService(PowerManager::class.java)?.isInteractive!=false}.getOrDefault(false)
                    if(ready&&SystemClock.elapsedRealtime()-lastHealth>=10000) {
                        lastHealth=SystemClock.elapsedRealtime()
                        val stored=stockStored
                        worker.execute {if(!closed)report("ready","Root runtime heartbeat",stored)}
                    }
                    handler.postDelayed(poll,if(screen)RootOverlayPolicy.VISIBLE_POLL_MS else RootOverlayPolicy.HIDDEN_POLL_MS)
                }
            }
        }
    }
    private fun apply(next:ModuleSettings,orientation:Int) {
        if(next==loaded&&orientation==loadedOrientation)return
        loaded=next;loadedOrientation=orientation
        DuoSettingsClient.useOrientation(orientation)
        Fx.sync(next,context)
        if(!next.enabled) {
            releaseRenderer()
            worker.execute {if(!closed)report("inactive", "Current orientation disabled")}
            return
        }
        if(host==null) {
            ready=false
            val display=displayManager?.getDisplay(Display.DEFAULT_DISPLAY) ?: error("Default display unavailable")
            @Suppress("DEPRECATION")
            val windowContext=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,null)
            val manager=windowContext.getSystemService(WindowManager::class.java) ?: error("Window manager unavailable")
            val candidate=RootOverlayHost(windowContext,manager,next,::firstDraw) { reason ->stop(reason) }
            host=candidate
            if(!candidate.attach())return
            monitor=DuoStateMonitor(windowContext,candidate).also {it.start();monitorStarted=true}
            handler.removeCallbacks(startupTimeout)
            handler.postDelayed(startupTimeout,RootOverlayPolicy.STARTUP_TIMEOUT_MS)
            worker.execute {if(!closed)report("starting","Waiting for first Canvas frame")}
        } else {
            host?.applySettings(next)
            monitor?.refresh()
        }
    }
    private fun firstDraw() {
        if(closed||ready||host?.rendererFailed!=false)return
        val expectedHost=host
        // Settings/provider Binder work runs away from the draw Looper. The existing startup
        // watchdog remains armed until this transaction returns, not merely until a frame exists.
        worker.execute {
            if(closed||host!==expectedHost)return@execute
            val stored=runCatching {stock.hideAfterDraw()}.getOrDefault(false)
            handler.post {
                if(closed||host!==expectedHost)return@post
                stockStored=stored;ready=true;handler.removeCallbacks(startupTimeout)
                worker.execute {
                    report("ready",if(stored)"Stock blacklist stored; visual result unverified" else "Stock blacklist unavailable; originals retained",stored)
                    publishDump()
                }
            }
        }
    }
    private fun report(state:String,reason:String,hidden:Boolean=false) {
        lastHealth=SystemClock.elapsedRealtime()
        val fields=Bundle().apply {
            putString("state",state);putString("reason",reason);putInt("versionCode",BuildConfig.VERSION_CODE)
            putBoolean("stockRestored",stockRestored);putBoolean("hideRequestedSuccess",hidden);putBoolean("stockVisualVerified",false)
            putBoolean("panelTracking",false);putBoolean("clockAccess",false)
            putBoolean("canvasReady",ready);putBoolean("monitorStarted",monitorStarted)
            putInt("windowType",2010);putInt("orientation",loadedOrientation)
        }
        runCatching {context.contentResolver.call(uri,"root_health",generation,fields)}
    }
    private fun publishDump() {
        val base="runtime=root; uid=0; Canvas=$ready; SystemUIInjection=false; panelTracking=false; independentPanelSizes=unavailable; stockVisualVerified=false; autoTint=systemThemeFallback; clockContinuum=unavailable"
        runCatching {context.contentResolver.call(uri,"dump",null,Bundle().apply {putString("dump",RuntimeTelemetry.decorate(base)+"\n"+L.recentText())})}
    }
    private fun releaseRenderer() {
        ready=false;stockStored=false;handler.removeCallbacks(startupTimeout)
        runCatching {monitor?.stop()};monitor=null;monitorStarted=false
        runCatching {host?.close()};host=null
        stockRestored=runCatching {stock.restore()}.getOrDefault(false)
    }
    private fun stop(reason:String) {
        if(closed)return
        releaseRenderer()
        report("stopped",reason)
        close()
        // Android's main Looper cannot be quit. This is our dedicated root helper, never the app
        // or SystemUI process; all windows, listeners and stock changes have already been released.
        kotlin.system.exitProcess(0)
    }
    fun close() {
        if(closed)return
        closed=true;handler.removeCallbacksAndMessages(null)
        runCatching {displayManager?.unregisterDisplayListener(displayListener)}
        if(receiverRegistered)runCatching {context.unregisterReceiver(changes)}
        receiverRegistered=false
        releaseRenderer();worker.shutdownNow()
    }
}
