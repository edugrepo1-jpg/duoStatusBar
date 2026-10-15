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
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.RuntimeTelemetry
import io.github.kvmy666.duostatusbar.TelemetryCounter
import io.github.kvmy666.duostatusbar.TelemetryFeature
import io.github.kvmy666.duostatusbar.TelemetrySampler
import io.github.kvmy666.duostatusbar.hook.CanvasMotion
import io.github.kvmy666.duostatusbar.hook.EffectsHost

/** All registrations and callbacks belong to the monitor lifecycle. OEM reads fail closed and log. */
internal class EffectsController(private val context: Context, private val host: EffectsHost, private val onPause: (Boolean) -> Unit) {
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
    private var cycleMotionEnabled: Boolean? = null
    private fun configureCycle() {
        cycleMotionEnabled = host.animationsEnabled
        cycle.configure(Fx.experience.copy(fadeEnabled=Fx.experience.fadeEnabled&&host.animationsEnabled),now())
    }
    private val foreground=ForegroundEvents()
    private var foregroundIcon:SlotIcon?=null
    private fun mediaPlaying()=if(experience.playbackKnown)experience.playback.playing else music
    private val indicators = RuntimeIndicators(context, handler) { indicatorsChanged() }
    private val experience = RuntimeExperience(context,handler,{ if(running){updateCycle();activityChanged();draw()} }) { spotlight(SlotIcon.SCREENSHOT,1800) }.apply {
        onNfcField={active->
            if(active&&running&&screen&&!pocket&&Fx.experience.nfcParticles&&!Fx.experience.networkOnly&&!host.rendererFailed)
                guarded("partículas NFC") { L.i("NFC partículas: janela=${host.showNfcParticles()}; sinal RF, sem resultado financeiro") }
        }
    }
    private val volumeObserver=VolumeObserver(context,handler) { value -> volumePercent=value;spotlight(SlotIcon.VOLUME,2000) }
    private val estimator=ChargeEstimator()
    private var chargeRemaining=-1L
    private var volumePercent=-1
    private var compassDegrees=0f
    private val heading=HeadingObserver(context,handler) { value->compassDegrees=value;if(running&&cycle.frame(now()).icon==SlotIcon.LOCATION)draw() }
    private var recordingAt=-1L
    private var communicationMode=AudioManager.MODE_NORMAL
    private var communicationAt=-1L
    private var modeRegistered=false
    private var headphoneConnectAt=-1L
    private var batterySourceAt=-1L
    private var presentedHeadphoneBattery:Boolean?=null
    private var completedAt=-1L
    private var completedSeen=-1L
    private var lastNotice=-1L
    private val audioModeListener=AudioManager.OnModeChangedListener { mode ->
        if(running&&modeRegistered)guarded("modo de comunicação") { communicationChanged(mode);updateCycle();draw() }
    }
    private fun communication()=communicationMode==AudioManager.MODE_IN_CALL||communicationMode==AudioManager.MODE_IN_COMMUNICATION
    private fun communicationChanged(mode:Int) {
        val was=communication();communicationMode=mode
        if(communication()&&!was)communicationAt=SystemClock.elapsedRealtime()
        else if(!communication())communicationAt=-1L
    }
    private fun configureCommunication() {
        val aurora=Fx.experience.aurora
        val wanted=!Fx.experience.networkOnly&&(aurora.voiceCall||aurora.privacyPair||aurora.contextActions)
        if(wanted==modeRegistered)return
        if(wanted) {
            guarded("acompanhar comunicação") {
                audio?.let { manager ->
                    manager.addOnModeChangedListener({ command -> handler.post(command);Unit },audioModeListener)
                    modeRegistered=true;communicationChanged(manager.mode)
                }
            }
        } else {
            guarded("encerrar comunicação") {audio?.removeOnModeChangedListener(audioModeListener)}
            modeRegistered=false;communicationMode=AudioManager.MODE_NORMAL;communicationAt=-1L
        }
    }
    private fun activityChanged() {
        val snapshot=AuroraActivities.snapshot(SystemClock.elapsedRealtime())
        if(snapshot.completedAtUptime>=0&&snapshot.completedAtUptime!=completedSeen) {
            completedSeen=snapshot.completedAtUptime
            if(Fx.experience.aurora.taskComplete&&screen&&!pocket) {
                completedAt=now()
                spotlight(SlotIcon.TASK_DONE,1800)
            }
        }
    }
    private var temporary:SlotIcon?=null
    private var temporaryUntil=0L
    private var temporaryDuration=0L
    private var lastScreenshot=-100000L
    private fun spotlight(icon:SlotIcon,duration:Long) = guarded("aviso breve") {
        if(!running||!screen||Fx.experience.networkOnly||foregroundIcon!=null)return@guarded
        val time=now()
        if(Fx.experience.aurora.singleNotices&&!AuroraMotion.latestNotice(lastNotice,time))return@guarded
        lastNotice=time
        if(icon==SlotIcon.SCREENSHOT) {
            val time=SystemClock.uptimeMillis()
            if(time-lastScreenshot<1500)return@guarded
            lastScreenshot=time;L.i("Captura de tela: obturador confirmado")
        }
        temporary=icon;temporaryDuration=duration;temporaryUntil=0L
        draw()
    }
    private fun indicatorsChanged() {
        if(!running)return
        bluetooth=indicators.bluetooth;nfc=indicators.nfc;hotspot=indicators.hotspot
        updateCycle();draw()
    }
    private var networkText=""
    private var dnd=false
    private var wireless=false
    private val indicatorsTick=object:Runnable {
        override fun run()=guarded("indicadores ativos") {
            if(!running||!screen||pocket)return@guarded
            RuntimeTelemetry.increment(TelemetryCounter.INDICATOR_TICK)
            indicators.refresh();indicators.refreshProjection();bluetooth=indicators.bluetooth;nfc=indicators.nfc;hotspot=indicators.hotspot
            experience.refresh()
            if(charging&&Fx.experience.chargeEstimate) {
                val estimate=runCatching { context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1 }.getOrDefault(-1)
                chargeRemaining=estimator.remaining(SystemClock.elapsedRealtime(),estimate)
            }
            val nextMusic=runCatching { audio?.isMusicActive==true }.getOrDefault(false)
            val musicChanged=nextMusic!=music;music=nextMusic
            updateCycle()
            if(musicChanged||cycle.frame(now()).icon==SlotIcon.CHARGE_TIME)draw()
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
    private var stopBiometrics:(()->Unit)?=null
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
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = guarded("fones conectados") { if(running)readAudio() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = guarded("fones removidos") { if(running)readAudio() }
    }
    private val playback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) = guarded("mídia") {
            if(!running)return@guarded
            val active = audio?.isMusicActive == true
            if (music && !active && headphones) audioEvent("pausa")
            music = active
            updateCycle();draw()
        }
    }
    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) = guarded("sensor") {
            if(!running)return@guarded
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
    private fun unlockEnabled()=host.animationsEnabled&&!Fx.experience.networkOnly&&
        (Fx.enabled(1)||Fx.experience.aurora.unlockGlow||Fx.experience.aurora.unlockBuild)
    private fun chargeEnabled()=host.animationsEnabled&&!Fx.experience.networkOnly&&
        (Fx.enabled(256)||Fx.experience.aurora.chargeStyles)
    private fun configureTelemetry() {
        val rich=Fx.enabled(8192)&&!Fx.experience.networkOnly
        for(icon in SlotIcon.entries) {
            val wanted=when(icon) {
                SlotIcon.WIFI,SlotIcon.WIFI_OFFLINE,SlotIcon.NETWORK -> Fx.enabled(4)||Fx.enabled(8192)||Fx.experience.networkOnly
                SlotIcon.AIRPLANE -> !Fx.experience.networkOnly&&host.showAirplane&&(Fx.enabled(4)||Fx.enabled(8192))
                SlotIcon.DND -> !Fx.experience.networkOnly&&host.showDnd&&(Fx.enabled(4)||Fx.enabled(8192))
                SlotIcon.BLUETOOTH -> !Fx.experience.networkOnly&&(Fx.enabled(4)||Fx.enabled(8192))
                SlotIcon.NFC -> !Fx.experience.networkOnly&&Fx.enabled(4096)
                SlotIcon.SHARE -> !Fx.experience.networkOnly&&Fx.enabled(8)
                SlotIcon.MEDIA -> rich||(!Fx.experience.networkOnly&&Fx.experience.music)
                SlotIcon.CHARGE_TIME -> !Fx.experience.networkOnly&&Fx.experience.chargeEstimate
                SlotIcon.RECORD_TIME -> !Fx.experience.networkOnly&&Fx.experience.recordingTime
                SlotIcon.VOLUME -> !Fx.experience.networkOnly&&Fx.experience.volume
                SlotIcon.SCREENSHOT -> !Fx.experience.networkOnly&&Fx.experience.screenshot
                SlotIcon.CALL -> !Fx.experience.networkOnly&&Fx.experience.aurora.voiceCall
                SlotIcon.TIMER,SlotIcon.STOPWATCH -> !Fx.experience.networkOnly&&Fx.experience.aurora.timer
                SlotIcon.TASK_DONE -> !Fx.experience.networkOnly&&Fx.experience.aurora.taskComplete
                SlotIcon.CAMERA,SlotIcon.MICROPHONE -> rich||(!Fx.experience.networkOnly&&Fx.experience.aurora.privacyPair)
                SlotIcon.NOTIFICATION -> false // No runtime notification listener: do not certify a preview glyph.
                else -> rich
            }
            RuntimeTelemetry.configure(TelemetryFeature.valueOf(icon.name),wanted)
        }
        val extras=arrayOf(TelemetryFeature.UNLOCK_CHECK to unlockEnabled(), TelemetryFeature.CHARGE_EFFECT to chargeEnabled(),
            TelemetryFeature.CRITICAL_PULSE to enabled(512),TelemetryFeature.FADE to (host.animationsEnabled&&Fx.experience.fadeEnabled),
            TelemetryFeature.DRAW_ICONS to (host.animationsEnabled&&Fx.experience.drawIcons),TelemetryFeature.COMPASS to Fx.experience.compass,
            TelemetryFeature.ISLAND to Fx.experience.island,TelemetryFeature.POCKET to Fx.enabled(64),TelemetryFeature.GLASS to Fx.enabled(1024),
            TelemetryFeature.SPRING to enabled(2048),TelemetryFeature.ALBUM_COLORS to Fx.experience.albumColors)
        for((feature,wanted) in extras)RuntimeTelemetry.configure(feature,wanted&&!Fx.experience.networkOnly)
    }

    fun start() = guarded("iniciar efeitos") {
        if (running) return@guarded
        running = true
        completedSeen=AuroraActivities.snapshot(SystemClock.elapsedRealtime()).completedAtUptime
        RuntimeTelemetry.increment(TelemetryCounter.CONTROLLER_START)
        screen=runCatching { context.getSystemService(android.os.PowerManager::class.java)?.isInteractive!=false }.getOrDefault(true)
        RuntimeTelemetry.lifecycle(true,screen,pocket)
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
            hotspot = manager?.let { it.javaClass.getMethod("isWifiApEnabled").invoke(it) as? Boolean } == true
        }
        settingsChanged()
    }
    fun stop() = guarded("encerrar efeitos") {
        host.dismissNfcParticles()
        if(running)RuntimeTelemetry.increment(TelemetryCounter.CONTROLLER_STOP)
        running = false
        RuntimeTelemetry.lifecycle(false,screen,pocket)
        unlockObserver.stop()
        indicators.stop()
        experience.stop();volumeObserver.stop();foreground.clear();foregroundIcon=null;cycle.hold(null,now())
        heading.stop()
        guarded("encerrar comunicação") {if(modeRegistered)audio?.removeOnModeChangedListener(audioModeListener)}
        modeRegistered=false;communicationMode=AudioManager.MODE_NORMAL;communicationAt=-1L
        headphoneConnectAt=-1L;batterySourceAt=-1L;presentedHeadphoneBattery=null;completedAt=-1L;completedSeen=-1L;lastNotice=-1L
        handler.removeCallbacksAndMessages(null)
        guarded("encerrar sensores") { sensors?.unregisterListener(sensorListener) }
        sensorsRegistered=false;near=false;faceDown=false;pocketCandidate=false
        pocket=false;stoppedAt=0L;clockOffset=0L
        checkAt=-100000L;checkCyclePaused=false;pulseAt=-100000L;chargeAt=-100000L;audioAt=-100000L
        temporary=null;temporaryUntil=0L;temporaryDuration=0L
        cycle.resume(now());cycle.transient(null,now());cycleMotionEnabled=null
        onPause(false)
        guarded("encerrar áudio") { audio?.unregisterAudioDeviceCallback(devices); audio?.unregisterAudioPlaybackCallback(playback) }
        guarded("remover gancho") {stopBiometrics?.invoke()};stopBiometrics=null
        restoreClock(); host.effectsHidden(false); host.applyEffects(EffectFrame(), false)
    }
    fun settingsChanged() = guarded("configuração dos efeitos") {
        if (!running) return@guarded
        configureTelemetry()
        configureCycle()
        if(Fx.experience.networkOnly){temporary=null;cycle.transient(null,now())}
        experience.configure()
        if(!Fx.experience.nfcParticles||Fx.experience.networkOnly)host.dismissNfcParticles()
        configureCommunication()
        if(Fx.experience.volume)volumeObserver.start() else volumeObserver.stop()
        if (!unlockEnabled()) { checkAt = -100000L; restoreClock() }
        if (!enabled(512)) pulseAt = -100000L
        if (!chargeEnabled()) chargeAt = -100000L
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
        indicators.refresh(force=true)
        guarded("Bluetooth") { bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }
        guarded("NFC") { nfc = android.nfc.NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }
        updateCycle(); draw()
    }
    fun broadcast(intent: Intent) = guarded("evento dos efeitos") {
        TelemetrySampler.battery(intent)
        indicators.bluetoothBattery(intent)
        if(intent.action in setOf(android.nfc.NfcAdapter.ACTION_ADAPTER_STATE_CHANGED,
            "android.net.wifi.WIFI_AP_STATE_CHANGED",BluetoothAdapter.ACTION_STATE_CHANGED,
            "android.net.conn.CONNECTIVITY_CHANGE","android.location.MODE_CHANGED",
            "android.app.action.NEXT_ALARM_CLOCK_CHANGED"))indicators.refresh(force=true)
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
                host.dismissNfcParticles()
                host.dismissSummary()
                host.continuum(false, enabled(128))
                screen = false; pulseAt = -100000L; checkAt = -100000L; chargeAt = -100000L
                headphoneConnectAt=-1L;batterySourceAt=-1L;completedAt=-1L
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
        val systemEstimate=if(plugged&&Fx.experience.chargeEstimate)
            runCatching {context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() ?: -1L}.getOrDefault(-1L) else -1L
        chargeRemaining=estimator.remaining(SystemClock.elapsedRealtime(),systemEstimate)
        wireless=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==BatteryManager.BATTERY_PLUGGED_WIRELESS
        if (!charging) fastSeen = false
        L.i("Bateria: nível=$level carregando=$charging tela=$screen")
        if (charging) pulseAt = -100000L
        else {
            val type = EffectTimeline.criticalCrossing(previous, level, false, enabled(512))
            if (type != 0 && screen) beginPulse(type)
        }
        if (charging && !fastSeen && chargeEnabled()) {
            val current = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val systemFast = readSystemFastCharge(intent)
            if (systemFast == true || (systemFast == null && EffectTimeline.fastCharge(current, voltage))) {
                fastSeen = true; chargeAt = now()
                L.i("Carga rápida: ${if (systemFast == true) "BatteryStatus do sistema" else "estimativa corrente × tensão >= 15 W"}; brilho 3000 ms")
            } else if (justPlugged) L.i("Carga rápida: potência não confirmada; brilho não disparado")
        }
        // The connection effect exists for every charger, independently of fast-charge telemetry.
        if (charging && justPlugged && chargeEnabled()) {
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
            batterySpeedClass = runCatching {Class.forName("com.android.settingslib.fuelgauge.BatteryStatus",false,context.classLoader)}.getOrNull()
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
        if (next != headphones) {
            headphones=next
            indicators.refresh(force=true);updateCycle()
            val confirmation=next&&Fx.experience.aurora.headphoneConfirm&&screen&&!pocket&&foregroundIcon==null&&
                EffectTimeline.age(now(),checkAt,EffectTimeline.UNLOCK_MS,unlockEnabled())<0
            headphoneConnectAt=if(confirmation)now() else -1L
            if(confirmation)spotlight(SlotIcon.AIRPODS,1200)
            audioEvent(if (next) "conexão" else "desconexão");draw()
        }
    }
    private fun audioEvent(reason: String) {
        if (!enabled(16)||foregroundIcon!=null) return
        audioAt = now(); L.i("Fones: $reason, aviso 4000 ms"); draw()
    }
    private fun updateCycle() {
        val aurora=Fx.experience.aurora
        val activity=AuroraActivities.snapshot(SystemClock.elapsedRealtime())
        val videoPair=!Fx.experience.networkOnly&&RuntimePrivacyPair.active(aurora.privacyPair,communication(),
            indicators.camera,indicators.microphone,indicators.privacySharedSource,indicators.cameraSources,indicators.microphoneSources)
        val states = arrayOf(
            TelemetryFeature.WIFI to (wifi&&indicators.wifiConnected&&indicators.wifiValidated),
            TelemetryFeature.WIFI_OFFLINE to (wifi&&indicators.wifiConnected&&!indicators.wifiValidated),
            TelemetryFeature.NETWORK to (!airplane&&networkText.isNotBlank()),
            TelemetryFeature.AIRPLANE to airplane, TelemetryFeature.DND to (if(Fx.enabled(8192))indicators.dnd else dnd),
            TelemetryFeature.BLUETOOTH to bluetooth, TelemetryFeature.NFC to nfc, TelemetryFeature.SHARE to hotspot,
            TelemetryFeature.AIRPODS to headphones, TelemetryFeature.BOLT to (charging&&!wireless),
            TelemetryFeature.CAMERA to indicators.camera, TelemetryFeature.MICROPHONE to indicators.microphone,
            TelemetryFeature.ALARM to indicators.alarm, TelemetryFeature.VPN to indicators.vpn,
            TelemetryFeature.LOCATION to indicators.location, TelemetryFeature.SILENT to (indicators.ringer==AudioManager.RINGER_MODE_SILENT),
            TelemetryFeature.VIBRATE to (indicators.ringer==AudioManager.RINGER_MODE_VIBRATE),
            TelemetryFeature.MEDIA to mediaPlaying(), TelemetryFeature.WIRELESS to wireless,
            TelemetryFeature.TORCH to indicators.torch, TelemetryFeature.RECORD to indicators.recording,
            TelemetryFeature.RECORD_TIME to indicators.recording, TelemetryFeature.CHARGE_TIME to (charging&&chargeRemaining>=0),
            TelemetryFeature.CALL to communication(),TelemetryFeature.TIMER to activity.timerRunning,
            TelemetryFeature.STOPWATCH to activity.stopwatchRunning)
        for((feature,active) in states)RuntimeTelemetry.detect(feature,active)

        if(indicators.recording&&recordingAt<0)recordingAt=SystemClock.elapsedRealtime()
        if(!indicators.recording)recordingAt=-1
        val items=buildList {
            if(Fx.enabled(4)||Fx.enabled(8192)||Fx.experience.networkOnly) {
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
                if(mediaPlaying())add(SlotIcon.MEDIA)
                if(wireless)add(SlotIcon.WIRELESS)
                if(indicators.torch)add(SlotIcon.TORCH)
                if(indicators.recording)add(SlotIcon.RECORD)
            }
            if(Fx.experience.chargeEstimate&&charging&&chargeRemaining>=0)add(SlotIcon.CHARGE_TIME)
            if(Fx.experience.recordingTime&&indicators.recording)add(SlotIcon.RECORD_TIME)
            if(Fx.experience.music&&mediaPlaying())add(SlotIcon.MEDIA)
            if(aurora.voiceCall&&communication()&&!videoPair)add(SlotIcon.CALL)
            if(aurora.timer) {
                if(activity.timerElapsedMs>=0)add(SlotIcon.TIMER)
                if(activity.stopwatchElapsedMs>=0)add(SlotIcon.STOPWATCH)
            }
            if(videoPair){add(SlotIcon.CAMERA);add(SlotIcon.MICROPHONE)}
        }.distinct()
        if(items!=activeIcons) {
            activeIcons=items
            L.i("Ícones ativos (${items.size}): ${items.joinToString()}; BT=$bluetooth GPS=${indicators.location} carga=$charging")
        }
        val carouselItems=when {
            Fx.experience.networkOnly->listOfNotNull(items.firstOrNull { it==SlotIcon.WIFI||it==SlotIcon.WIFI_OFFLINE } ?: items.firstOrNull { it==SlotIcon.NETWORK })
            videoPair->listOf(SlotIcon.CAMERA,SlotIcon.MICROPHONE)
            else->items
        }
        cycle.update(carouselItems, now())
        val heldItems=items.filter { icon -> when(icon) {
            SlotIcon.TIMER->activity.timerRunning
            SlotIcon.STOPWATCH->activity.stopwatchRunning
            else->true
        } }
        val newest=foreground.update(if(Fx.experience.networkOnly)emptyList() else heldItems)
        foregroundIcon=if(videoPair)null else newest
        cycle.hold(foregroundIcon,now(),Fx.experience.recordingTime)
        if(foregroundIcon!=null) { temporary=null;cycle.transient(null,now());audioAt=-100000L }
        host.updateSummary(IslandState(level,charging,items.distinct().map { icon ->
            IslandItem(icon,iconLabel(icon),when(icon) {
                SlotIcon.CHARGE_TIME->estimateLabel(chargeRemaining)
                SlotIcon.RECORD,SlotIcon.RECORD_TIME->durationLabel(if(recordingAt<0)0 else SystemClock.elapsedRealtime()-recordingAt)
                SlotIcon.MEDIA->experience.playback.title.ifBlank { io.github.kvmy666.duostatusbar.i18n.UiText.t("Reproduzindo") }
                SlotIcon.AIRPODS->if(indicators.headphoneBattery>=0)"${indicators.headphoneBattery}%" else io.github.kvmy666.duostatusbar.i18n.UiText.t("Conectados")
                SlotIcon.NETWORK->networkText
                SlotIcon.CALL->durationLabel(if(communicationAt<0)0 else SystemClock.elapsedRealtime()-communicationAt)
                SlotIcon.TIMER->durationLabel(activity.timerRemainingMs.coerceAtLeast(0))
                SlotIcon.STOPWATCH->durationLabel(activity.stopwatchElapsedMs.coerceAtLeast(0))
                else->io.github.kvmy666.duostatusbar.i18n.UiText.t("Ativo")
            },sourcePackage=when(icon) {
                SlotIcon.CAMERA->if(aurora.privacyOrigins)indicators.cameraSources.singleOrNull().orEmpty() else ""
                SlotIcon.MICROPHONE->if(aurora.privacyOrigins)indicators.microphoneSources.singleOrNull().orEmpty() else ""
                SlotIcon.MEDIA->experience.playback.sourcePackage
                else->""
            },elapsedMs=when(icon) {
                SlotIcon.CALL->if(communicationAt<0)0 else SystemClock.elapsedRealtime()-communicationAt
                SlotIcon.TIMER->activity.timerElapsedMs
                SlotIcon.STOPWATCH->activity.stopwatchElapsedMs
                else->0
            },remainingMs=if(icon==SlotIcon.TIMER)activity.timerRemainingMs else -1,
                timerRunning=when(icon){SlotIcon.TIMER->activity.timerRunning;SlotIcon.STOPWATCH->activity.stopwatchRunning;else->false})
        },experience.playback,chargeRemaining,indicators.headphoneBattery,indicators.torch,foregroundIcon,aurora=aurora,appearance=Fx.experience.islandAppearance,communication=communication(),
            updatedAtElapsed=if((aurora.voiceCall&&communication())||(aurora.timer&&(activity.timerRunning||activity.stopwatchRunning))||
                (Fx.experience.recordingTime&&indicators.recording))SystemClock.elapsedRealtime() else 0L))
    }
    private fun setPocket(value: Boolean) {
        val checkPriority = SystemClock.uptimeMillis()-lastUnlock in 0 until EffectTimeline.UNLOCK_MS
        val next = value && Fx.enabled(64) && screen && !checkPriority
        if (next == pocket) return
        if (next) { host.dismissNfcParticles();host.dismissSummary(); heading.stop(); stoppedAt = SystemClock.uptimeMillis(); pocket = true; handler.removeCallbacks(tick); handler.removeCallbacks(cameraTick);handler.removeCallbacks(indicatorsTick) }
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
        stopBiometrics?.invoke()
        stopBiometrics=host.installBiometricObserver {
            handler.post {if(running)guarded("confirmação biométrica") {unlockObserver.authenticated()}}
        }
        L.i("Check pronto: observador Keyguard + USER_PRESENT; autenticação opcional do backend")
    }
    private fun unlock(source: String) {
        val realNow = SystemClock.uptimeMillis()
        if (!running) return
        if (!unlockEnabled()) { L.i("Check ignorado: efeito ou animações desativados; origem=$source"); return }
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
        RuntimeTelemetry.lifecycle(running,screen,pocket)
        handler.removeCallbacks(tick)
        if (!running || pocket) return
        if(host.rendererFailed){stop();return}
        if(cycleMotionEnabled!=host.animationsEnabled)configureCycle()
        RuntimeTelemetry.increment(TelemetryCounter.EFFECT_TICK)
        val time = now()
        fun age(at: Long, duration: Long, bit: Int): Long = EffectTimeline.age(time, at, duration, screen && enabled(bit) && !Fx.experience.networkOnly)
        val check = EffectTimeline.age(time,checkAt,EffectTimeline.UNLOCK_MS,screen&&unlockEnabled())
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
        val charge = EffectTimeline.age(time,chargeAt,EffectTimeline.CHARGE_MS,screen&&chargeEnabled())
        val audioAge = if(check>=0||foregroundIcon!=null)-1L else age(audioAt, EffectTimeline.AUDIO_MS, 16)
        host.effectsHidden(camera && check<0 && !Fx.experience.networkOnly)
        val slot=cycle.frame(time).let { if(host.animationsEnabled&&Fx.experience.fadeEnabled&&!Fx.experience.networkOnly)it else it.copy(opacity=1f,scale=1f) }
        val aurora=Fx.experience.aurora
        val headsetSource=indicators.headphoneBattery in 0..100&&headphones&&EffectTimeline.checkNormal(check)>.001f&&
            ((audioAge>=0&&EffectTimeline.audio(audioAge)>.001f)||
                (slot.icon==SlotIcon.AIRPODS&&slot.opacity>.001f&&EffectTimeline.audioNormal(audioAge)>.001f))
        if(presentedHeadphoneBattery!=null&&presentedHeadphoneBattery!=headsetSource&&aurora.batterySource&&screen)batterySourceAt=time
        presentedHeadphoneBattery=headsetSource
        val headphoneAge=EffectTimeline.age(time,headphoneConnectAt,1360,screen&&aurora.headphoneConfirm&&!Fx.experience.networkOnly&&foregroundIcon==null&&check<0&&
            (slot.icon==SlotIcon.AIRPODS||audioAge>=0))
        val sourceAge=EffectTimeline.age(time,batterySourceAt,600,screen&&aurora.batterySource&&!Fx.experience.networkOnly)
        val doneAge=EffectTimeline.age(time,completedAt,1800,screen&&aurora.taskComplete&&!Fx.experience.networkOnly)
        val activity=AuroraActivities.snapshot(SystemClock.elapsedRealtime())
        if(Fx.experience.compass&&host.effectsVisible&&screen&&!pocket&&!camera&&check<0&&audioAge<0&&slot.icon==SlotIcon.LOCATION)heading.start() else heading.stop()
        if(slot.icon!=loggedSlot) { loggedSlot=slot.icon;L.i("Ícone exibido: ${slot.icon}; check=${check>=0} carga=$charging") }
        if (check >= 0) clockColors?.let { clockView?.setTextColor(CanvasMotion.blend(it.defaultColor, 0xFF3DDC84.toInt(), EffectTimeline.unlockColor(check))) }
        else restoreClock()
        host.applyEffects(EffectFrame(check, pulse, pulseRed, charge, audioAge,
            slot, (Fx.enabled(8) && hotspot) || (Fx.enabled(4) && airplane),
            Fx.enabled(512) && !charging && level in 0..9,
            Fx.enabled(1024), enabled(2048), time,
            indicators.headphoneBattery.takeIf { headphones } ?: -1, networkText, Fx.enabled(4)||Fx.enabled(8192)||Fx.enabled(4096)||Fx.experience.networkOnly, charging&&chargeEnabled(),
            musicPlaying=Fx.experience.music&&mediaPlaying()&&!Fx.experience.networkOnly,
            musicProgress=if(Fx.experience.music)experience.playback.progress(SystemClock.elapsedRealtime()) else -1f,
            albumColor=if(Fx.experience.albumColors)experience.playback.color else 0,
            chargeRemainingMs=chargeRemaining,recordElapsedMs=if(recordingAt<0)0 else SystemClock.elapsedRealtime()-recordingAt,
            volumePercent=volumePercent,drawIcons=Fx.experience.drawIcons&&host.animationsEnabled&&!Fx.experience.networkOnly,
            iconPercent=Fx.experience.iconPercent,iconRadius=(55.5f-8f*host.thickPercent/100f-2f).coerceAtLeast(12f),
            compassDegrees=compassDegrees,compass=Fx.experience.compass,motionEnabled=host.animationsEnabled&&!Fx.experience.networkOnly,
            batteryColors=Fx.experience.takeIf { it.customBatteryColors }?.let { BatteryBandColors(it.batteryLow,it.batteryMid,it.batteryHigh) },
            aurora=aurora,headphoneConnectMs=headphoneAge,wirelessCharging=wireless,
            callElapsedMs=if(communicationAt<0)0 else SystemClock.elapsedRealtime()-communicationAt,
            timerElapsedMs=activity.timerElapsedMs,timerRemainingMs=activity.timerRemainingMs,stopwatchElapsedMs=activity.stopwatchElapsedMs,
            taskCompleteMs=doneAge,batterySourceMs=sourceAge), camera)
        if (!screen || Fx.experience.networkOnly || (camera&&check<0)) return
        val deadline=minOf(if(temporary!=null&&temporaryUntil>time)temporaryUntil-time else Long.MAX_VALUE,
            if(headphoneAge>=0)1360-headphoneAge else Long.MAX_VALUE,
            if(sourceAge>=0)600-sourceAge else Long.MAX_VALUE,if(doneAge>=0)1800-doneAge else Long.MAX_VALUE)
        val selected=slot.icon
        val exclusive=check>=0||audioAge>=0
        val seconds=!exclusive&&(selected==SlotIcon.RECORD_TIME||(Fx.experience.music&&mediaPlaying())||
            selected==SlotIcon.CALL||(selected==SlotIcon.TIMER&&activity.timerRunning)||(selected==SlotIcon.STOPWATCH&&activity.stopwatchRunning))
        if(!host.effectsVisible) {
            // An open island or hidden renderer needs state deadlines, not animation frames. Poll
            // slowly only when continuous charge/location must notice presentation becoming visible.
            // Cycle transitions normally ask for 16ms frames. Hidden poses may skip interpolation;
            // only actual notice/check deadlines and running clocks require an exact callback here.
            val quietCycle=cycle.nextDelay(time).let {if(it==Long.MAX_VALUE)it else it.coerceAtLeast(1000L)}
            val quietDelay=minOf(deadline,quietCycle,
                if(check>=0)EffectTimeline.UNLOCK_MS-check else Long.MAX_VALUE,
                if(seconds||charging||selected==SlotIcon.LOCATION)1000L else Long.MAX_VALUE)
            if(quietDelay!=Long.MAX_VALUE)handler.postDelayed(tick,quietDelay.coerceAtLeast(1))
            return
        }
        if(!host.animationsEnabled) {
            // Static notices still need one expiration callback; motion does not own their lifetime.
            val staticDelay=minOf(deadline,if(seconds)1000L else Long.MAX_VALUE,cycle.nextDelay(time))
            if(staticDelay!=Long.MAX_VALUE)handler.postDelayed(tick,staticDelay)
            return
        }
        val transition=check>=0||pulse>=0||charge>=0||audioAge>=0||slot.opacity<1f||headphoneAge>=0||sourceAge>=0||doneAge>=0
        val continuous=(charging&&chargeEnabled())||(!exclusive&&selected in setOf(SlotIcon.MEDIA,SlotIcon.RECORD,SlotIcon.SHARE))||OfflineWifiPulse.active(selected,exclusive,host.animationsEnabled)
        val delay=EffectCadence.delay(cycle.nextDelay(time),transition,continuous,seconds,deadline)
        if (delay != Long.MAX_VALUE) handler.postDelayed(tick, delay)
    }
    companion object {
        private val HEADPHONE_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET)
        fun isCamera(pkg: String) = pkg in setOf("com.sec.android.app.camera", "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.motorola.camera3")
    }
}
