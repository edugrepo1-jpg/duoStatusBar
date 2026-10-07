package io.github.kvmy666.duostatusbar.fx

import android.app.AlarmManager
import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.TelemetryCounter
import io.github.kvmy666.duostatusbar.TelemetryFeature

/** Reads system state in SystemUI's context. Never starts recording, location or camera access. */
internal class RuntimeIndicators(private val context: Context, private val handler: Handler, private val changed: () -> Unit) {
    var camera = false; private set
    var microphone = false; private set
    private var locationInUse = false
    var locationEnabled = false; private set
    val location get() = locationEnabled || locationInUse
    var torch = false; private set
    var recording = false; private set
    var alarm = false; private set
    var dnd = false; private set
    var vpn = false; private set
    var ringer = AudioManager.RINGER_MODE_NORMAL; private set
    var headphoneBattery = -1; private set
    var wifiConnected = false; private set
    var wifiValidated = false; private set
    var bluetooth = false; private set
    var nfc = false; private set
    var hotspot = false; private set
    @Volatile private var started = false
    private var lifecycle=0L
    private var thread:HandlerThread?=null
    private var worker:Handler?=null
    private val refreshGate=IndicatorRefreshGate()
    private var followUp=false
    private var projectionPending=false
    private val operations = mutableSetOf<String>()
    private val torches = mutableSetOf<String>()
    private val failures = mutableSetOf<String>()
    private val ops by lazy { context.getSystemService(AppOpsManager::class.java) }
    private val cameras by lazy { context.getSystemService(CameraManager::class.java) }
    private fun read(name: String, action: () -> Unit) {
        try { action();evidence(name,true) } catch (t: Throwable) {
            evidence(name,false)
            when(name){"Bluetooth"->bluetooth=false;"NFC"->nfc=false;"hotspot"->hotspot=false;"alarme"->alarm=false;"rede"->{vpn=false;wifiConnected=false;wifiValidated=false};"localização habilitada"->locationEnabled=false;"captura da tela"->recording=false;"bateria dos fones"->headphoneBattery=-1}
            if (synchronized(failures){failures.add(name)}) L.w("Indicador $name indisponível: ${t.javaClass.simpleName}; estado não confirmado") }
    }
    private val opListener = AppOpsManager.OnOpActiveChangedListener { op, uid, pkg, active ->
        if (started) {
            when(op) {
                AppOpsManager.OPSTR_CAMERA -> RuntimeTelemetry.probe(TelemetryFeature.CAMERA,true)
                AppOpsManager.OPSTR_RECORD_AUDIO -> RuntimeTelemetry.probe(TelemetryFeature.MICROPHONE,true)
                AppOpsManager.OPSTR_FINE_LOCATION,AppOpsManager.OPSTR_COARSE_LOCATION -> RuntimeTelemetry.probe(TelemetryFeature.LOCATION,true)
            }
            val key="$op|$uid|$pkg"
            val previous=Triple(camera,microphone,locationInUse)
            if (active) operations.add(key) else operations.remove(key)
            camera=operations.any { it.startsWith(AppOpsManager.OPSTR_CAMERA+"|") }
            microphone=operations.any { it.startsWith(AppOpsManager.OPSTR_RECORD_AUDIO+"|") }
            locationInUse=operations.any { it.startsWith(AppOpsManager.OPSTR_FINE_LOCATION+"|") || it.startsWith(AppOpsManager.OPSTR_COARSE_LOCATION+"|") }
            if(previous!=Triple(camera,microphone,locationInUse))changed()
        }
    }
    private val torchListener = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!started) return
            RuntimeTelemetry.probe(TelemetryFeature.TORCH,true)
            if (enabled) torches.add(cameraId) else torches.remove(cameraId)
            val next=torches.isNotEmpty();if(next!=torch){torch=next;changed()}
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if(!started)return
            torches.remove(cameraId);val next=torches.isNotEmpty();if(next!=torch){torch=next;changed()}
        }
    }
    fun start() {
        if(started)return
        started=true;lifecycle++
        thread=HandlerThread("DuoIndicators").also { it.start() };worker=Handler(thread!!.looper)
        refreshGate.reset()
        read("privacidade") { ops?.startWatchingActive(arrayOf(AppOpsManager.OPSTR_CAMERA,AppOpsManager.OPSTR_RECORD_AUDIO,AppOpsManager.OPSTR_FINE_LOCATION,AppOpsManager.OPSTR_COARSE_LOCATION), { command -> handler.post(command); Unit },opListener) }
        read("lanterna") { cameras?.registerTorchCallback(torchListener,handler) }
        read("privacidade inicial") {
            val names=arrayOf(AppOpsManager.OPSTR_CAMERA,AppOpsManager.OPSTR_RECORD_AUDIO,AppOpsManager.OPSTR_FINE_LOCATION,AppOpsManager.OPSTR_COARSE_LOCATION)
            val packages=ops?.javaClass?.getMethod("getPackagesForOps",Array<String>::class.java)?.invoke(ops,names as Any) as? List<*>
            packages.orEmpty().filterNotNull().forEach { pkg ->
                val uid=pkg.javaClass.getMethod("getUid").invoke(pkg) as Int
                val pkgName=pkg.javaClass.getMethod("getPackageName").invoke(pkg) as String
                val entries=pkg.javaClass.getMethod("getOps").invoke(pkg) as? List<*>
                entries.orEmpty().filterNotNull().forEach { entry ->
                    if(entry.javaClass.getMethod("isRunning").invoke(entry)==true) {
                        val op=entry.javaClass.getMethod("getOpStr").invoke(entry) as String
                        opListener.onOpActiveChanged(op,uid,pkgName,true)
                    }
                }
            }
        }
        refresh(force=true)
    }
    fun stop() {
        started=false;lifecycle++
        worker?.removeCallbacksAndMessages(null);thread?.quitSafely();worker=null;thread=null
        refreshGate.reset();followUp=false;projectionPending=false
        read("encerrar privacidade") { ops?.stopWatchingActive(opListener) }
        read("encerrar lanterna") { cameras?.unregisterTorchCallback(torchListener) }
        operations.clear();torches.clear();camera=false;microphone=false;locationInUse=false;torch=false
    }
    fun bluetoothBattery(intent: Intent) {
        if(intent.action=="android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") {
            val value=intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL",-1)
            if(value in 0..100)refresh(force=true)
        }
    }
    /** Work is coalesced off the main thread; broadcasts bypass the slow-state cache. */
    fun refresh(force:Boolean=false) {
        if(!started) { applySnapshot(poll());return }
        if(refreshGate.pending) { if(force)followUp=true;return }
        if(!refreshGate.begin(SystemClock.uptimeMillis(),force)){RuntimeTelemetry.increment(TelemetryCounter.CACHE_HIT);return}
        val expected=lifecycle
        worker?.post {
            RuntimeTelemetry.increment(TelemetryCounter.INVENTORY_QUERY)
            val snapshot=poll()
            handler.post apply@ {
                if(!started||expected!=lifecycle)return@apply
                refreshGate.finish()
                val before=currentSnapshot();applySnapshot(snapshot)
                if(before!=snapshot)changed()
                if(followUp){followUp=false;refresh(force=true)}
            }
        }
    }
    /** One cheap capture-state probe while the screen is visible; no network/audio inventory. */
    fun refreshProjection() {
        if(!started||projectionPending)return
        projectionPending=true
        val expected=lifecycle
        worker?.post {
            RuntimeTelemetry.increment(TelemetryCounter.PROJECTION_QUERY)
            val active=projectionActive()
            handler.post apply@ {
                if(!started||expected!=lifecycle)return@apply
                projectionPending=false
                if(active!=recording){recording=active;changed()}
            }
        }
    }
    private data class Snapshot(val location:Boolean=false,val bluetooth:Boolean=false,val nfc:Boolean=false,
        val hotspot:Boolean=false,val alarm:Boolean=false,val dnd:Boolean=false,val ringer:Int=AudioManager.RINGER_MODE_NORMAL,
        val vpn:Boolean=false,val wifi:Boolean=false,val validated:Boolean=false,val battery:Int=-1,val recording:Boolean=false)
    private fun currentSnapshot()=Snapshot(locationEnabled,bluetooth,nfc,hotspot,alarm,dnd,ringer,vpn,wifiConnected,wifiValidated,headphoneBattery,recording)
    private fun applySnapshot(s:Snapshot) {
        locationEnabled=s.location;bluetooth=s.bluetooth;nfc=s.nfc;hotspot=s.hotspot;alarm=s.alarm;dnd=s.dnd;ringer=s.ringer
        vpn=s.vpn;wifiConnected=s.wifi;wifiValidated=s.validated;headphoneBattery=s.battery;recording=s.recording
    }
    private fun evidence(name:String,success:Boolean) {
        val features=when(name) {
            "Bluetooth" -> listOf(TelemetryFeature.BLUETOOTH)
            "NFC" -> listOf(TelemetryFeature.NFC)
            "hotspot" -> listOf(TelemetryFeature.SHARE)
            "alarme" -> listOf(TelemetryFeature.ALARM)
            "não perturbe" -> listOf(TelemetryFeature.DND)
            "silencioso" -> listOf(TelemetryFeature.SILENT,TelemetryFeature.VIBRATE)
            "rede" -> listOf(TelemetryFeature.WIFI,TelemetryFeature.WIFI_OFFLINE,TelemetryFeature.VPN)
            "localização habilitada" -> listOf(TelemetryFeature.LOCATION)
            "captura da tela" -> listOf(TelemetryFeature.RECORD,TelemetryFeature.RECORD_TIME)
            // Successful registrations do not certify an active privacy/torch state.
            "privacidade","privacidade inicial" -> if(success)emptyList() else listOf(TelemetryFeature.CAMERA,TelemetryFeature.MICROPHONE)
            "lanterna" -> if(success)emptyList() else listOf(TelemetryFeature.TORCH)
            else -> emptyList()
        }
        features.forEach {RuntimeTelemetry.probe(it,success)}
    }
    private fun <T> probe(name:String,fallback:T,read:()->T):T=try {read().also {evidence(name,true)}}catch(t:Throwable) {
        evidence(name,false)
        val first=synchronized(failures) { failures.add(name) }
        if(first)L.w("Indicador $name indisponível: ${t.javaClass.simpleName}; estado não confirmado")
        fallback
    }
    private fun poll():Snapshot {
        val location=probe("localização habilitada",false) { context.getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled==true }
        val bluetooth=probe("Bluetooth",false) { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled==true }
        val nfc=probe("NFC",false) { android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled==true }
        val hotspot=probe("hotspot",false) { val m=context.getSystemService(android.net.wifi.WifiManager::class.java);m?.javaClass?.getMethod("isWifiApEnabled")?.invoke(m)==true }
        val alarm=probe("alarme",false) { (context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: 0)>System.currentTimeMillis() }
        val dnd=probe("não perturbe",false) { val f=context.getSystemService(android.app.NotificationManager::class.java)?.currentInterruptionFilter;f!=null&&f!=android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN&&f!=android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
        val ringer=probe("silencioso",AudioManager.RINGER_MODE_NORMAL) { context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL }
        val network=probe("rede",Triple(false,false,false)) {
            val cm=context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION") val caps=cm?.allNetworks?.mapNotNull { cm.getNetworkCapabilities(it) }.orEmpty()
            Triple(caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
                caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) })
        }
        val battery=probe("bateria dos fones",-1) {
            if(!bluetooth)-1 else {
                val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter
                val outputs=context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.filter {
                    it.type in setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                }.orEmpty()
                if(outputs.isEmpty())return@probe -1
                val addresses=outputs.map { it.address.lowercase() }.filter { it.isNotBlank() }
                adapter?.bondedDevices.orEmpty().filter { device -> device.address.lowercase() in addresses || (addresses.isEmpty()&&device.bluetoothClass?.majorDeviceClass==android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO) }.mapNotNull { device ->
                    val connected=device.javaClass.getMethod("isConnected").invoke(device) as? Boolean == true
                    if(!connected)null else (device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int)?.takeIf { it in 0..100 }
                }.minOrNull() ?: -1
            }
        }
        val recording=projectionActive()
        return Snapshot(location,bluetooth,nfc,hotspot,alarm,dnd,ringer,network.first,network.second,network.third,battery,recording)
    }
    private fun projectionActive()=probe("captura da tela",false) {
        val m=context.getSystemService(MediaProjectionManager::class.java)
        m?.javaClass?.getMethod("getActiveProjectionInfo")?.invoke(m)!=null
    }
}

/** A periodic fallback is a safety net, not a replacement for broadcasts and AppOps callbacks. */
internal class IndicatorRefreshGate {
    var pending=false;private set
    private var last=-100000L
    fun begin(now:Long,force:Boolean=false):Boolean {
        if(pending||(!force&&now-last<INTERVAL_MS))return false
        pending=true;last=now;return true
    }
    fun finish(){pending=false}
    fun reset(){pending=false;last=-100000L}
    companion object { const val INTERVAL_MS=10000L }
}
