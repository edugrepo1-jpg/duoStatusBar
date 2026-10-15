package io.github.kvmy666.duostatusbar.standalone

import android.content.Context
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.settings.RuntimeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Launch only our installed APK. Never disable SELinux or kill a PID from an external file. */
internal object RootRuntimeManager {
    enum class Stage { AUTHORIZATION, STARTING, FIRST_FRAME, READY, ACCESS_FAILED, LAUNCH_FAILED, FRAME_FAILED }
    @Volatile var stage: Stage = Stage.ACCESS_FAILED
        private set
    private val mutex = Mutex()
    private const val MAIN = "io.github.kvmy666.duostatusbar.standalone.RootOverlayMain"
    internal fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
    internal fun launchScript(apk: String, generation: String): String {
        require(generation.matches(Regex("[a-f0-9]{32}")))
        val app = "/system/bin/app_process --nice-name=DUO-Recreate-root /system/bin $MAIN $generation"
        val watchdog = "export CLASSPATH=${quote(apk)}; $app; $app --restore-only"
        return "[ \"\$(id -u)\" = 0 ] || exit 1; nohup /system/bin/sh -c ${quote(watchdog)} </dev/null >/dev/null 2>&1 &\nprintf DUO_LAUNCHED"
    }
    suspend fun start(context: Context): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (RuntimeBackend.ready(app)) { stage = Stage.READY; return@withContext true }
        stage = Stage.AUTHORIZATION
        if (!RootLogs.requestRoot(app)) { stage = Stage.ACCESS_FAILED; return@withContext false }
        stage = Stage.STARTING
        val generation = RuntimeBackend.begin(app)
        try {
            val script = launchScript(app.applicationInfo.sourceDir, generation)
            // requestRoot verified this exact executable; do not prompt/probe every path again.
            val candidate = RootLogs.findSuBinary()
            val launched = RootLogs.runRoot(candidate, script, 5).contains("DUO_LAUNCHED")
            if (!launched) { stage = Stage.LAUNCH_FAILED; return@withContext false }
            stage = Stage.FIRST_FRAME
            repeat(120) {
                if (RuntimeBackend.ready(app)) { stage = Stage.READY; return@withContext true }
                if (RuntimeBackend.health(app).getString("state") == "failed") { stage = Stage.FRAME_FAILED; return@withContext false }
                delay(100)
            }
            stage = Stage.FRAME_FAILED
            false
        } finally {
            if (!RuntimeBackend.ready(app)) RuntimeBackend.stop(app)
        }
    } }
    suspend fun stop(context: Context): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val generation = RuntimeBackend.generation(app)
        RuntimeBackend.stop(app)
        repeat(100) {
            val health=RuntimeBackend.health(app)
            if (health.getString("state") == "stopped" && health.getBoolean("stockRestored")) return@withContext true
            delay(100)
        }
        if (!generation.matches(Regex("[a-f0-9]{32}"))) return@withContext true
        val command = "[ \"\$(id -u)\" = 0 ] || exit 1; export CLASSPATH=${quote(app.applicationInfo.sourceDir)}; /system/bin/app_process /system/bin $MAIN $generation --restore-only"
        // Use the verified route; multiple aliases can otherwise repeat authorization waits.
        RootLogs.runRoot(RootLogs.findSuBinary(), command, 5, allowEmpty = true).isBlank()
    } }
}
