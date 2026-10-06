package io.github.kvmy666.duostatusbar

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.kvmy666.duostatusbar.ui.DuoSettingsScreen
import io.github.kvmy666.duostatusbar.ui.DuoTheme

/**
 * The settings surface (Phase 5).
 *
 * Everything the user can change lives in [DuoSettingsScreen], and every change reaches the status bar over
 * the app ↔ module channel described in `settings/DuoPrefs.kt` — no restart, no root, no adb.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(base:android.content.Context) { super.attachBaseContext(io.github.kvmy666.duostatusbar.i18n.AppLanguage.localized(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Issue #3: without this the app's own status bar keeps white icons on a light background. The
        // default SystemBarStyle.auto makes the bar icons light/dark to match the theme (which follows the
        // system dark-mode setting), so they are black in light mode like the system's own bar.
        io.github.kvmy666.duostatusbar.i18n.UiText.initialize(this,io.github.kvmy666.duostatusbar.i18n.AppLanguage.selected(this))
        enableEdgeToEdge()
        setContent {
            var startupReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val imported=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    io.github.kvmy666.duostatusbar.settings.LegacySettingsMigration.import(this@MainActivity)
                }
                if(imported) io.github.kvmy666.duostatusbar.settings.SettingsBridge.push(this@MainActivity)
                startupReady=true
            }
            var needsLanguage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!io.github.kvmy666.duostatusbar.i18n.AppLanguage.chosen(this)) }
            DuoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if(!startupReady) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                    else if(needsLanguage) androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center) {
                        io.github.kvmy666.duostatusbar.ui.LanguagePicker(true) { tag ->
                            io.github.kvmy666.duostatusbar.i18n.AppLanguage.choose(this@MainActivity,tag);needsLanguage=false;recreate()
                        }
                    } else DuoSettingsScreen()
                }
            }
        }
    }
}
