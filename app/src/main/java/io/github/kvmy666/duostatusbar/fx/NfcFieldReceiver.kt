package io.github.kvmy666.duostatusbar.fx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.RuntimeBackend
import io.github.kvmy666.duostatusbar.settings.SettingsBridge

/** Receives RF presence only. The manifest requires a privileged sender; no payment data is read. */
class NfcFieldReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val present = when (intent.action) {
            FIELD_ON -> true
            FIELD_OFF -> false
            else -> return
        }
        runCatching {
            val settings = DuoPrefs.read(context)
            val options = ExperienceOptions.decode(settings.experienceJson)
            if (!settings.enabled || !options.nfcParticles || options.networkOnly) return
            val target = if (RuntimeBackend.mode(context) == RuntimeBackend.ROOT)
                BuildConfig.APPLICATION_ID else SettingsBridge.SYSTEMUI
            // Build a new message: never forward extras, AIDs, APDUs, identifiers or caller timestamps.
            context.sendBroadcast(Intent(RuntimeExperience.ACTION).setPackage(target)
                .putExtra("nfcField", present).putExtra("nfcFieldAt", SystemClock.elapsedRealtime()))
        }.onFailure { L.w("NFC RF relay unavailable: ${it.javaClass.simpleName}") }
    }

    companion object {
        const val FIELD_ON = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED"
        const val FIELD_OFF = "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED"
    }
}
