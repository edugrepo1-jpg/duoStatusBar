package io.github.kvmy666.duostatusbar

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import io.github.kvmy666.duostatusbar.i18n.AppLanguage
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/** On-demand allowlisted app-side observations. No root, shell, background timer or global logcat. */
internal object RootlessCapture {
    fun collect(context: Context): String = DiagnosticPrivacy.clean(buildString {
        appendLine("=== rootless app capture (schema=1) ===")
        appendLine("capturedAtMs=${System.currentTimeMillis()} elapsedRealtimeMs=${SystemClock.elapsedRealtime()} uptimeMs=${SystemClock.uptimeMillis()}")
        appendLine("scope=app process and whole-device public APIs; SystemUI renderer evidence is in the separately refreshed module dump")
        appendLine("appPackage=${BuildConfig.APPLICATION_ID} targetSdk=${context.applicationInfo.targetSdkVersion} minSdk=${context.applicationInfo.minSdkVersion} debug=${BuildConfig.DEBUG}")
        section("runtime backend") {
            val backend=io.github.kvmy666.duostatusbar.settings.RuntimeBackend
            val health=backend.health(context)
            appendLine("backend=${backend.mode(context)} rootReady=${backend.ready(context)} state=${health.getString("state")} runtimeVersionCode=${health.getInt("versionCode")} lastHeartbeatElapsed=${health.getLong("elapsed")}")
            appendLine("rootPanelTracking=false rootStockVisualVerified=false; root stock-setting acceptance does not certify OEM rendering")
        }
        section("power") {
            val power=context.getSystemService(PowerManager::class.java)
            appendLine("interactive=${power?.isInteractive ?: "unknown"} powerSave=${power?.isPowerSaveMode ?: "unknown"} deviceIdle=${power?.isDeviceIdleMode ?: "unknown"}")
            if(Build.VERSION.SDK_INT>=29)appendLine("thermalStatus=${power?.currentThermalStatus ?: "unknown"} (platform enum; no temperature inferred)")
            val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val manager=context.getSystemService(BatteryManager::class.java)
            fun extra(key:String)=battery?.getIntExtra(key,Int.MIN_VALUE)?.takeUnless {it==Int.MIN_VALUE}?.toString() ?: "unknown"
            appendLine("batteryLevelRaw=${extra(BatteryManager.EXTRA_LEVEL)} batteryScale=${extra(BatteryManager.EXTRA_SCALE)} plugged=${extra(BatteryManager.EXTRA_PLUGGED)} batteryStatus=${extra(BatteryManager.EXTRA_STATUS)} batteryHealth=${extra(BatteryManager.EXTRA_HEALTH)}")
            appendLine("voltageMv=${extra(BatteryManager.EXTRA_VOLTAGE)} temperatureDeciC=${extra(BatteryManager.EXTRA_TEMPERATURE)}")
            val current=manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeUnless {it==Int.MIN_VALUE}
            val charge=manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf {it>=0}
            appendLine("deviceCurrentMicroA=${current ?: "unknown"} deviceChargeCounterMicroAh=${charge ?: "unknown"} (vendor measurement; sign convention may vary)")
            appendLine("batteryAttribution=whole-device snapshot; no per-module mAh attribution; a single current sample is not average drain")
        }
        section("memory") {
            val info=ActivityManager.MemoryInfo()
            val manager=context.getSystemService(ActivityManager::class.java)
            manager?.getMemoryInfo(info)
            if(manager!=null)appendLine("deviceTotalRamBytes=${info.totalMem} deviceAvailableRamBytes=${info.availMem} deviceLowMemory=${info.lowMemory}")
            else appendLine("deviceRam=unknown")
            val runtime=Runtime.getRuntime()
            appendLine("appHeapUsedBytes=${runtime.totalMemory()-runtime.freeMemory()} appHeapMaxBytes=${runtime.maxMemory()}")
            appendLine("appProcessCpuElapsedMs=${android.os.Process.getElapsedCpuTime()} (entire app process since start; not SystemUI/module energy)")
        }
        section("our permissions and capabilities") {
            val permissions=listOf(Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.REQUEST_INSTALL_PACKAGES,Manifest.permission.WRITE_SETTINGS)
            permissions.forEach {permission->appendLine("permission.$permission=${context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED}")}
            appendLine("ourNotificationListenerEnabled=${context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)}")
            appendLine("ourCanWriteSettings=${android.provider.Settings.System.canWrite(context)} ourCanInstallPackages=${context.packageManager.canRequestPackageInstalls()}")
            listOf(PackageManager.FEATURE_BLUETOOTH,PackageManager.FEATURE_NFC,PackageManager.FEATURE_SENSOR_COMPASS,
                PackageManager.FEATURE_SENSOR_GYROSCOPE,PackageManager.FEATURE_CAMERA_ANY).forEach {feature ->
                appendLine("capability.$feature=${context.packageManager.hasSystemFeature(feature)}")
            }
            appendLine("permissionScope=app only; injected SystemUI has its own permissions; denied is not proof of a module failure")
        }
        section("configuration for both orientations") {
            appendLine("language=${AppLanguage.selected(context)} appTheme=${io.github.kvmy666.duostatusbar.ui.ThemePreference.read(context)} linkedOrientations=${DuoPrefs.linkOrientations(context)} rootAllowed=${DuoPrefs.rootAllowed(context)} updateChecks=${DuoPrefs.checkUpdates(context)} hideStockIcons=${DuoPrefs.hideStockIcons(context)}")
            for(orientation in DuoOrientation.entries)appendLine("settings.${orientation.name.lowercase()}=${DiagnosticConfiguration.render(DuoPrefs.read(context,orientation,independent=true))}")
        }
        appendLine("privacy=technical allowlist; no device serial, IMEI, Android ID, hostname, SSID, addresses, notification/media titles, contact names, coordinates or other-app log capture")
        appendLine("limitations=missing SystemUI dump requires module injection/scope/bridge diagnosis; root is optional and cannot manufacture missing observations")
    })

    private inline fun StringBuilder.section(name:String,body:StringBuilder.()->Unit) {
        appendLine("--- $name ---")
        try {body()}catch(error:Throwable){appendLine("unavailable=${error.javaClass.simpleName}; not confirmed")}
    }
}
