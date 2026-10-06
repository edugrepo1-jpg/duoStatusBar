package io.github.kvmy666.duostatusbar.fx

import android.app.ActivityManager
import android.app.KeyguardManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.CanvasMotion
import io.github.kvmy666.duostatusbar.hook.DuoIconHost

/** All registrations and callbacks belong to the monitor lifecycle. OEM reads fail closed and log. */
internal class EffectsController(private val context: Context, private val host: DuoIconHost, private val onPause: (Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var keyguardReadFailed = false
    private val unlockObserver = UnlockObserver(handler, {
        try {
            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked
        } catch (t: Throwable) {
            if (!keyguardReadFailed) {
                keyguardReadFailed = true
                L.w("Desbloqueio: estado indisponível: ${t.javaClass.simpleName}: ${t.message}")
            }
            null
        }
    }, { L.i(it) }) { source ->
        guarded("confirmar desbloqueio") { unlock(source); draw() }
    }
    private val cycle = SlotCycle()
    private val indicators = RuntimeIndicators(context, handler) { if(running) { updateCycle();draw() } }
    private val experience = RuntimeExperience(context,handler,{ if(running){updateCycle();draw()} }) { spotlight(SlotIcon.SCREENSHOT,1800) }
    private val volumeObserver=VolumeObserver(context,handler) { value -> volumePercent=value;spotlight(SlotIcon.VOLUME,2000) }
    private val estimator=ChargeEstimator()
    private var chargeRemaining=-1L
    private var volumePercent=-1
    private var compassDegrees=0f
    private val heading=HeadingObserver(context,handler) { value->compassDegrees=value;if(running&&cycle.frame(now()).icon==SlotIcon.LOCATION)draw() }
    private var recordingAt=-1L
    private var temporary:SlotIcon?=null
    private var temporaryUntil=0L
    private var temporaryDuration=0L
    private var lastScreenshot=-100000L
    private fun spotlight(icon:SlotIcon,duration:Long) = guarded("aviso breve") {
        if(!running||!screen)return@guarded
        if(icon==SlotIcon.SCREENSHOT) {
            val time=SystemClock.uptimeMillis()
            if(time-lastScreenshot<1500)return@guarded
            lastScreenshot=time;L.i("Captura de tela: obturador confirmado")
        }
        temporary=icon;temporaryDuration=duration;temporaryUntil=0L
        draw()
    }
    private var networkText=""
    private var dnd=false
    private var wireless=false
    private val indicatorsTick=object:Runnable {
        override fun run()=guarded("indicadores ativos") {
            if(!running||!screen||pocket)return@guarded
            indicators.refresh();bluetooth=indicators.bluetooth;nfc=indicators.nfc;hotspot=indicators.hotspot
            experience.refresh()
            if(charging&&Fx.experience.chargeEstimate) {
                val estimate=runCatching { context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1 }.getOrDefault(-1)
                chargeRemaining=estimator.remaining(SystemClock.elapsedRealtime(),estimate)
            }
            music=audio?.isMusicActive==true;updateCycle();draw()
            handler.postDelayed(this,2000)
        }
    }
    private var running = false
    private var screen = true
    private var pocket = false
    private var pocketCandidate = false
    private val pocketTick = Runnable { setPocket(pocketCandidate) }
    private fun requestPocket(value:Boolean) {
        if (!value) { pocketCandidate=false;handler.removeCallbacks(pocketTick);setPocket(false) }
        else if(!pocketCandidate) { pocketCandidate=true;handler.postDelayed(pocketTick,500L) }
    }
    private var near = false
    private var faceDown = false
    private var stoppedAt = 0L
    private var clockOffset = 0L
    private fun now() = (if (pocket) stoppedAt else SystemClock.uptimeMillis()) - clockOffset
    private var checkAt = -100000L
    private var checkWifiRestored=true
    private var checkCyclePaused=false
    private var activeIcons=emptyList<SlotIcon>()
    private var loggedSlot:SlotIcon?=null
    private var pulseAt = -100000L
    private var chargeAt = -100000L
    private var audioAt = -100000L
    private var lastPulse = -100000L
    private var pulseRed = false
    private var level = -1
    private var charging = false
    private var fastSeen = false
    private var screenOnAt = -100000L
    private var lastUnlock = -100000L
    private var biometricHooks = 0
    private var wifi = false
    private var airplane = false
    private var bluetooth = false
    private var nfc = false
    private var hotspot = false
    private var headphones = false
    private var music = false
    private var camera = false
    private var cameraReadFailed = false
    private var clockView: TextView? = null
    private var clockColors: ColorStateList? = null
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private val sensors by lazy { context.getSystemService(SensorManager::class.java) }
    private var sensorsRegistered = false
    private val tick = Runnable { guarded("quadro dos efeitos") { draw() } }
    private val cameraTick = object : Runnable {
        override fun run() = guarded("modo câmera") {
            if (!running || !screen || pocket || !Fx.enabled(32)) return@guarded
            val manager = context.getSystemService(ActivityManager::class.java)
            @Suppress("DEPRECATION") val task = manager?.getRunningTasks(1)?.firstOrNull()
            if (task == null) {
                if (!cameraReadFailed) { cameraReadFailed = true; L.w("Câmera: tarefa atual indisponível nesta ROM (não verificado)") }
            } else {
                val next = isCamera(task.topActivity?.packageName.orEmpty())
                if (next != camera) { camera = next; L.i("Câmera: invisível=$camera"); draw() }
            }
            handler.postDelayed(this, 500)
        }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = guarded("fones conectados") { readAudio() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = guarded("fones removidos") { readAudio() }
    }
    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = guarded("mídia") {
            val active = audio?.isMusicActive == true
            if (music && !active && headphones) audioEvent("pausa")
            music = active
            updateCycle();draw()
        }
    }
    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) = guarded("sensor") {
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> near = event.values[0] < minOf(event.sensor.maximumRange, 5f)
                Sensor.TYPE_ACCELEROMETER -> faceDown = event.values[2] < -7f
            }
            requestPocket(near || faceDown)
        }
    }
    private inline fun guarded(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { L.w("$name: ${t.javaClass.simpleName}: ${t.message}") }
    }
    private fun enabled(bit: Int) = Fx.enabled(bit) && host.animationsEnabled

    fun start() = guarded("iniciar efeitos") {
        if (running) return@guarded
        running = true
        unlockObserver.start(screen)
        indicators.start()
        experience.start()
        guarded("biometria") { installBiometrics() }
        guarded("monitor de áudio") {
            audio?.registerAudioDeviceCallback(devices, handler)
            audio?.registerAudioPlaybackCallback(playback, handler)
            music = audio?.isMusicActive == true
            readAudio()
        }
        guarded("hotspot inicial") {
            val manager = context.getSystemService(WifiManager::class.java)
            hotspot = manager?.let { XposedHelpers.callMethod(it, "isWifiApEnabled") as? Boolean } == true
        }
        settingsChanged()
    }
    fun stop() = guarded("encerrar efeitos") {
        running = false
        unlockObserver.stop()
        indicators.stop()
        experience.stop();volumeObserver.stop()
        heading.stop()
        handler.removeCallbacksAndMessages(null)
        guarded("encerrar sensores") { sensors?.unregisterListener(sensorListener) }
        guarded("encerrar áudio") { audio?.unregisterAudioDeviceCallback(devices); audio?.unregisterAudioPlaybackCallback(playback) }
        hooks.forEach { guarded("remover gancho") { it.unhook() } }; hooks.clear()
        restoreClock(); host.effectsHidden(false); host.applyEffects(EffectFrame(), false)
    }
    fun settingsChanged() = guarded("configuração dos efeitos") {
        if (!running) return@guarded
        cycle.configure(Fx.experience,now())
        experience.configure()
        if(Fx.experience.volume)volumeObserver.start() else volumeObserver.stop()
        if (!enabled(1)) { checkAt = -100000L; restoreClock() }
        if (!enabled(512)) pulseAt = -100000L
        if (!enabled(256)) chargeAt = -100000L
        if (!enabled(16)) audioAt = -100000L
        if (!Fx.enabled(32)) { camera = false; host.effectsHidden(false) }
        if (!Fx.enabled(64)) setPocket(false)
        updateSensors()
        handler.removeCallbacks(cameraTick)
        if (screen && !pocket && Fx.enabled(32)) handler.post(cameraTick)
        handler.removeCallbacks(indicatorsTick)
        if(screen&&!pocket)handler.post(indicatorsTick)
        updateCycle(); draw()
    }
    fun network(wifiOn: Boolean, plane: Boolean, generation: String = "", quiet: Boolean = false) = guarded("conexões") {
        wifi = wifiOn; airplane = plane; networkText=generation; dnd=quiet
        indicators.refresh()
        guarded("Bluetooth") { bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }
        guarded("NFC") { nfc = android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }
        updateCycle(); draw()
    }
    fun broadcast(intent: Intent) = guarded("evento dos efeitos") {
        indicators.bluetoothBattery(intent)
        when (intent.action) {
            android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED -> {
                nfc = intent.getIntExtra(android.nfc.NfcAdapter.EXTRA_ADAPTER_STATE, android.nfc.NfcAdapter.STATE_OFF) == android.nfc.NfcAdapter.STATE_ON
                L.i("NFC: $nfc"); updateCycle()
            }
            "android.net.wifi.WIFI_AP_STATE_CHANGED" -> { hotspot = intent.getIntExtra("wifi_state", 11) == 13; L.i("Hotspot: $hotspot"); updateCycle() }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                bluetooth = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) == BluetoothAdapter.STATE_ON
                updateCycle()
            }
            Intent.ACTION_SCREEN_ON -> {
                screen = true; screenOnAt = SystemClock.uptimeMillis()
                unlockObserver.screenChanged(true)
                val type = EffectTimeline.criticalWake(level, charging, enabled(512), now() - lastPulse)
                if (type != 0) beginPulse(type)
                settingsChanged(); host.continuum(true, enabled(128))
            }
            Intent.ACTION_SCREEN_OFF -> {
                host.dismissSummary()
                host.continuum(false, enabled(128))
                screen = false; pulseAt = -100000L; checkAt = -100000L; chargeAt = -100000L
                temporary=null;cycle.transient(null,now())
                heading.stop()
                unlockObserver.screenChanged(false)
                restoreClock(); camera = false; host.effectsHidden(false)
                handler.removeCallbacks(tick); handler.removeCallbacks(cameraTick);handler.removeCallbacks(indicatorsTick)
                updateSensors()
            }
            Intent.ACTION_USER_PRESENT -> {
                L.i("Desbloqueio: USER_PRESENT recebido")
                unlockObserver.userPresent()
            }
        }
        draw()
    }
    fun battery(intent: Intent, realLevel: Int, plugged: Boolean) = guarded("efeito de bateria") {
        val previous = level; level = realLevel
        val justPlugged = plugged && !charging
        charging = plugged
        estimator.observe(realLevel,plugged,SystemClock.elapsedRealtime())
        chargeRemaining=estimator.remaining(SystemClock.elapsedRealtime())
        wireless=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==BatteryManager.BATTERY_PLUGGED_WIRELESS
        if (!charging) fastSeen = false
        L.i("Bateria: nível=$level carregando=$charging tela=$screen")
        if (charging) pulseAt = -100000L
        else {
            val type = EffectTimeline.criticalCrossing(previous, level, false, enabled(512))
            if (type != 0 && screen) beginPulse(type)
        }
        if (charging && !fastSeen && enabled(256)) {
            val current = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val systemFast = readSystemFastCharge(intent)
            if (systemFast == true || (systemFast == null && EffectTimeline.fastCharge(current, voltage))) {
                fastSeen = true; chargeAt = now()
                L.i("Carga rápida: ${if (systemFast == true) "BatteryStatus do sistema" else "estimativa corrente × tensão >= 15 W"}; brilho 3000 ms")
            } else if (justPlugged) L.i("Carga rápida: potência não confirmada; brilho não disparado")
        }
        // The connection effect exists for every charger, independently of fast-charge telemetry.
        if (charging && justPlugged && enabled(256)) {
            chargeAt=now();L.i("Carregamento: brilho no arco 3000 ms; rotação de ícones mantida")
        }
        if (!charging) chargeAt = -100000L
        updateCycle();draw()
    }
    private fun beginPulse(type: Int) { pulseRed = type == 2; pulseAt = now(); lastPulse = pulseAt; L.i("Pulso crítico: ${if (pulseRed) "vermelho" else "âmbar"}, 3 ciclos") }
    private var batterySpeedClass: Class<*>? = null
    private var batterySpeedLookedUp = false
    private fun readSystemFastCharge(intent: Intent): Boolean? = try {
        // AOSP SettingsLib BatteryStatus; availability and constructor are checked on this ROM.
        if (!batterySpeedLookedUp) {
            batterySpeedLookedUp = true
            batterySpeedClass = XposedHelpers.findClassIfExists("com.android.settingslib.fuelgauge.BatteryStatus", context.classLoader)
        }
        batterySpeedClass?.let { cls ->
            val battery = cls.getConstructor(Intent::class.java).newInstance(intent)
            val speed = cls.getMethod("getChargingSpeed", Context::class.java).invoke(battery, context) as Int
            val fast = cls.getField("CHARGING_FAST").getInt(null)
            if (speed < 0) null else speed == fast
        }
    } catch (_: Throwable) { null }
    private fun readAudio() {
        val next = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in HEADPHONE_TYPES } == true
        if (next != headphones) { headphones = next; indicators.refresh();updateCycle();audioEvent(if (next) "conexão" else "desconexão") }
    }
    private fun audioEvent(reason: String) {
        if (!enabled(16)) return
        audioAt = now(); L.i("Fones: $reason, aviso 4000 ms"); draw()
    }
    private fun updateCycle() {
        if(indicators.recording&&recordingAt<0)recordingAt=SystemClock.elapsedRealtime()
        if(!indicators.recording)recordingAt=-1
        val items=buildList {
            if(Fx.enabled(4)||Fx.enabled(8192)) {
                if(airplane&&host.showAirplane)add(SlotIcon.AIRPLANE)
                if(wifi&&indicators.wifiConnected)add(if(indicators.wifiValidated)SlotIcon.WIFI else SlotIcon.WIFI_OFFLINE)
                if(!airplane&&networkText.isNotBlank())add(SlotIcon.NETWORK)
                if((if(Fx.enabled(8192))indicators.dnd else dnd)&&host.showDnd)add(SlotIcon.DND)
                if(bluetooth)add(SlotIcon.BLUETOOTH)
            }
            if(Fx.enabled(4096)&&nfc)add(SlotIcon.NFC)
            if(Fx.enabled(8)&&hotspot)add(SlotIcon.SHARE)
            if(Fx.enabled(8192)) {
                if(charging&&!wireless)add(SlotIcon.BOLT)
                if(headphones)add(SlotIcon.AIRPODS)
                if(indicators.camera)add(SlotIcon.CAMERA)
                if(indicators.microphone)add(SlotIcon.MICROPHONE)
                if(indicators.alarm)add(SlotIcon.ALARM)
                if(indicators.vpn)add(SlotIcon.VPN)
                if(indicators.location)add(SlotIcon.LOCATION)
                if(indicators.ringer==AudioManager.RINGER_MODE_SILENT)add(SlotIcon.SILENT)
                if(indicators.ringer==AudioManager.RINGER_MODE_VIBRATE)add(SlotIcon.VIBRATE)
                if(music||experience.playback.playing)add(SlotIcon.MEDIA)
                if(wireless)add(SlotIcon.WIRELESS)
                if(indicators.torch)add(SlotIcon.TORCH)
                if(indicators.recording)add(SlotIcon.RECORD)
            }
            if(Fx.experience.chargeEstimate&&charging)add(SlotIcon.CHARGE_TIME)
            if(Fx.experience.recordingTime&&indicators.recording)add(SlotIcon.RECORD_TIME)
            if(Fx.experience.music&&experience.playback.playing)add(SlotIcon.MEDIA)
        }.distinct()
        if(items!=activeIcons) {
            activeIcons=items
            L.i("Ícones ativos (${items.size}): ${items.joinToString()}; BT=$bluetooth GPS=${indicators.location} carga=$charging")
        }
        cycle.update(items, now())
        if(Fx.experience.compass&&screen&&!pocket&&SlotIcon.LOCATION in items)heading.start() else heading.stop()
        host.updateSummary(IslandState(level,charging,items.distinct().map { icon ->
            IslandItem(icon,iconLabel(icon),when(icon) {
                SlotIcon.CHARGE_TIME->estimateLabel(chargeRemaining)
                SlotIcon.RECORD,SlotIcon.RECORD_TIME->durationLabel(if(recordingAt<0)0 else SystemClock.elapsedRealtime()-recordingAt)
                SlotIcon.MEDIA->experience.playback.title.ifBlank { "Reproduzindo" }
                SlotIcon.AIRPODS->if(indicators.headphoneBattery>=0)"${indicators.headphoneBattery}%" else "Conectados"
                SlotIcon.NETWORK->networkText
                else->"Ativo"
            })
        }))
    }
    private fun setPocket(value: Boolean) {
        val checkPriority = SystemClock.uptimeMillis()-lastUnlock in 0 until EffectTimeline.UNLOCK_MS
        val next = value && Fx.enabled(64) && screen && !checkPriority
        if (next == pocket) return
        if (next) { host.dismissSummary(); heading.stop(); stoppedAt = SystemClock.uptimeMillis(); pocket = true; handler.removeCallbacks(tick); handler.removeCallbacks(cameraTick);handler.removeCallbacks(indicatorsTick) }
        else { clockOffset += SystemClock.uptimeMillis() - stoppedAt; pocket = false; if (screen) { if(Fx.enabled(32))handler.post(cameraTick);handler.post(indicatorsTick) } }
        L.i("Bolso/tela pra baixo: pausa=$pocket")
        host.pauseEffects(pocket)
        onPause(pocket)
        if (!pocket) draw()
    }
    private fun updateSensors() {
        val wanted = screen && Fx.enabled(64)
        if (wanted == sensorsRegistered) return
        sensorsRegistered = wanted
        if (!wanted) { sensors?.unregisterListener(sensorListener); near = false; faceDown = false; requestPocket(false) }
        else for (type in intArrayOf(Sensor.TYPE_PROXIMITY, Sensor.TYPE_ACCELEROMETER)) {
            sensors?.getDefaultSensor(type)?.let { sensors?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        }
    }
    private fun installBiometrics() {
        // Name explicitly supplied in the target specification; OEM availability is measured at runtime.
        val monitor = XposedHelpers.findClassIfExists("com.android.keyguard.KeyguardUpdateMonitor", context.classLoader)
        if (monitor != null) {
            val candidates = listOf(monitor) + monitor.declaredClasses
            for (cls in candidates) for (method in cls.declaredMethods) {
                if (method.name.matches(Regex("^(handle|on)(Fingerprint|Face|Biometric)Authenticated$")) || method.name == "onAuthenticationSucceeded") {
                    guarded("gancho biométrico") {
                        hooks += XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                handler.post { guarded("confirmação biométrica") { unlockObserver.authenticated() } }
                            }
                        })
                        biometricHooks++
                    }
                }
            }
        }
        L.i("Check pronto: observador Keyguard + USER_PRESENT; ganchos biométricos=$biometricHooks")
    }
    private fun unlock(source: String) {
        val realNow = SystemClock.uptimeMillis()
        if (!running) return
        if (!enabled(1)) { L.i("Check ignorado: efeito ou animações desativados; origem=$source"); return }
        if (realNow-lastUnlock < EffectTimeline.UNLOCK_MS) return
        screen=true;lastUnlock=realNow
        host.dismissSummary()
        if(pocket)setPocket(false)
        checkAt=now();audioAt=-100000L;checkWifiRestored=false
        cycle.pause(now());checkCyclePaused=true
        host.setElementsVisible(true);host.effectsHidden(false)
        restoreClock();clockView=host.effectClock();clockColors=clockView?.textColors
        L.i("Check de desbloqueio: 3000 ms, prioridade exclusiva e saída com fade out; origem=$source")
    }
    private fun restoreClock() { clockColors?.let { clockView?.setTextColor(it) }; clockColors = null; clockView = null }
    private fun draw() {
        handler.removeCallbacks(tick)
        if (!running || pocket) return
        val time = now()
        fun age(at: Long, duration: Long, bit: Int): Long = EffectTimeline.age(time, at, duration, screen && enabled(bit))
        val check = age(checkAt, EffectTimeline.UNLOCK_MS, 1)
        if(check<0&&temporary!=null) {
            if(temporaryUntil==0L){temporaryUntil=time+temporaryDuration;cycle.transient(temporary,time)}
            if(time>=temporaryUntil){temporary=null;cycle.transient(null,time)}
        }
        if(check<0&&checkCyclePaused) {
            cycle.resume(time);checkCyclePaused=false
            cycle.preferWifi(time);checkWifiRestored=true
            pocketCandidate=false;requestPocket(near||faceDown)
        }
        val pulse = age(pulseAt, EffectTimeline.PULSE_MS, 512)
        val charge = age(chargeAt, EffectTimeline.CHARGE_MS, 256)
        val audioAge = if(check>=0)-1L else age(audioAt, EffectTimeline.AUDIO_MS, 16)
        host.effectsHidden(camera && check<0)
        val slot=cycle.frame(time).let { if(host.animationsEnabled)it else it.copy(opacity=1f,scale=1f) }
        if(slot.icon!=loggedSlot) { loggedSlot=slot.icon;L.i("Ícone exibido: ${slot.icon}; check=${check>=0} carga=$charging") }
        if (check >= 0) clockColors?.let { clockView?.setTextColor(CanvasMotion.blend(it.defaultColor, 0xFF3DDC84.toInt(), EffectTimeline.unlockColor(check))) }
        else restoreClock()
        host.applyEffects(EffectFrame(check, pulse, pulseRed, charge, audioAge,
            slot, (Fx.enabled(8) && hotspot) || (Fx.enabled(4) && airplane),
            Fx.enabled(512) && !charging && level in 0..9,
            Fx.enabled(1024), enabled(2048), time,
            indicators.headphoneBattery, networkText, Fx.enabled(4)||Fx.enabled(8192)||Fx.enabled(4096), charging&&enabled(256),
            musicPlaying=Fx.experience.music&&experience.playback.playing,
            musicProgress=if(Fx.experience.music)experience.playback.progress(SystemClock.elapsedRealtime()) else -1f,
            albumColor=if(Fx.experience.albumColors)experience.playback.color else 0,
            chargeRemainingMs=chargeRemaining,recordElapsedMs=if(recordingAt<0)0 else SystemClock.elapsedRealtime()-recordingAt,
            volumePercent=volumePercent,drawIcons=Fx.experience.drawIcons&&host.animationsEnabled,
            iconPercent=Fx.experience.iconPercent,iconRadius=(55.5f-8f*host.thickPercent/100f-2f).coerceAtLeast(12f),
            compassDegrees=compassDegrees,compass=Fx.experience.compass), camera)
        if (!screen || (camera&&check<0) || !host.animationsEnabled) return
        val selected=cycle.frame(time).icon
        val active = check >= 0 || pulse >= 0 || charge >= 0 || audioAge >= 0 || charging || temporary!=null || selected==SlotIcon.RECORD || selected==SlotIcon.RECORD_TIME || selected==SlotIcon.MEDIA
        val delay = if (active || (Fx.enabled(8) && hotspot)) 16L else if(Fx.experience.music&&experience.playback.playing) minOf(250L,cycle.nextDelay(time)) else cycle.nextDelay(time)
        if (delay != Long.MAX_VALUE) handler.postDelayed(tick, delay)
    }
    companion object {
        private val HEADPHONE_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET)
        fun isCamera(pkg: String) = pkg in setOf("com.sec.android.app.camera", "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.motorola.camera3")
    }
}
