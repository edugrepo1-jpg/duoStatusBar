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
import io.github.kvmy666.duostatusbar.L

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
    private var started = false
    private val operations = mutableSetOf<String>()
    private val torches = mutableSetOf<String>()
    private val failures = mutableSetOf<String>()
    private val ops by lazy { context.getSystemService(AppOpsManager::class.java) }
    private val cameras by lazy { context.getSystemService(CameraManager::class.java) }
    private fun read(name: String, action: () -> Unit) {
        try { action() } catch (t: Throwable) {
            when(name){"Bluetooth"->bluetooth=false;"NFC"->nfc=false;"hotspot"->hotspot=false;"alarme"->alarm=false;"rede"->{vpn=false;wifiConnected=false;wifiValidated=false};"localização habilitada"->locationEnabled=false;"captura da tela"->recording=false;"bateria dos fones"->headphoneBattery=-1}
            if (failures.add(name)) L.w("Indicador $name indisponível: ${t.javaClass.simpleName}; estado não confirmado") }
    }
    private val opListener = AppOpsManager.OnOpActiveChangedListener { op, uid, pkg, active ->
        if (started) {
            val key="$op|$uid|$pkg"
            if (active) operations.add(key) else operations.remove(key)
            camera=operations.any { it.startsWith(AppOpsManager.OPSTR_CAMERA+"|") }
            microphone=operations.any { it.startsWith(AppOpsManager.OPSTR_RECORD_AUDIO+"|") }
            locationInUse=operations.any { it.startsWith(AppOpsManager.OPSTR_FINE_LOCATION+"|") || it.startsWith(AppOpsManager.OPSTR_COARSE_LOCATION+"|") }
            changed()
        }
    }
    private val torchListener = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (!started) return
            if (enabled) torches.add(cameraId) else torches.remove(cameraId)
            torch=torches.isNotEmpty(); changed()
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            torches.remove(cameraId);torch=torches.isNotEmpty();if(started)changed()
        }
    }
    fun start() {
        if(started)return
        started=true
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
        refresh()
    }
    fun stop() {
        started=false
        read("encerrar privacidade") { ops?.stopWatchingActive(opListener) }
        read("encerrar lanterna") { cameras?.unregisterTorchCallback(torchListener) }
        operations.clear();torches.clear()
    }
    fun bluetoothBattery(intent: Intent) {
        if(intent.action=="android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") {
            val value=intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL",-1)
            if(value in 0..100) { refresh();changed() }
        }
    }
    fun refresh() {
        read("localização habilitada") {
            locationEnabled=context.getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled==true
        }
        read("Bluetooth") { bluetooth=context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled==true }
        read("NFC") { nfc=android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled==true }
        read("hotspot") { val manager=context.getSystemService(android.net.wifi.WifiManager::class.java);hotspot=manager?.javaClass?.getMethod("isWifiApEnabled")?.invoke(manager)==true }
        read("alarme") { alarm=(context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: 0)>System.currentTimeMillis() }
        read("não perturbe") { val filter=context.getSystemService(android.app.NotificationManager::class.java)?.currentInterruptionFilter;dnd=filter!=null&&filter!=android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN&&filter!=android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
        read("silencioso") { ringer=context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL }
        read("rede") {
            val cm=context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION") val caps=cm?.allNetworks?.mapNotNull { cm.getNetworkCapabilities(it) }.orEmpty()
            vpn=caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
            wifiConnected=caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            wifiValidated=caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
        }
        read("bateria dos fones") {
            val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter
            val addresses=context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.map { it.address.lowercase() }?.filter { it.isNotBlank() }.orEmpty()
            val levels=adapter?.bondedDevices.orEmpty().filter { device -> device.address.lowercase() in addresses || (addresses.isEmpty() && device.bluetoothClass?.majorDeviceClass==android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO) }.mapNotNull { device ->
                // AOSP BluetoothDevice system methods; check actual availability on this ROM.
                val connected=device.javaClass.getMethod("isConnected").invoke(device) as? Boolean == true
                if(!connected) null else (device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int)?.takeIf { it in 0..100 }
            }
            headphoneBattery=levels.minOrNull() ?: -1
        }
        read("captura da tela") {
            val manager=context.getSystemService(MediaProjectionManager::class.java)
            // AOSP system API; capability checked at runtime. Projection also covers screen sharing.
            recording=manager?.javaClass?.getMethod("getActiveProjectionInfo")?.invoke(manager)!=null
        }
    }
}
