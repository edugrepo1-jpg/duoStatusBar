package io.github.kvmy666.duostatusbar.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import java.io.File

/**
 * The update notifications: "new release" (with a Download action), "downloading", "downloaded — tap to
 * install", and "download failed". Tapping the first opens the release page; the Download action hands
 * the APK to [UpdateDownloadWorker]; the ready notification opens the system installer.
 */
internal object UpdateNotifications {

    private const val CHANNEL = "duo_updates"
    private const val ID = 0x5A18

    /** Android 13+ only shows a notification if the app was granted POST_NOTIFICATIONS. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posted by the periodic worker when a newer release exists. */
    fun notify(context: Context, info: UpdateInfo) {
        if (!permitted(context)) return
        try {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                ID,
                Intent(Intent.ACTION_VIEW, Uri.parse(info.url)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, info.version))
                .setContentIntent(open)
                .setAutoCancel(true)
            if (info.apkUrl.isNotBlank()) {
                builder.addAction(
                    android.R.drawable.stat_sys_download,
                    context.getString(R.string.update_action_download),
                    downloadPendingIntent(context, info)
                )
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, builder.build())
        } catch (t: Throwable) {
            // A missing notification must never matter more than the status bar.
            L.w("update notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** The Download action: enqueues the worker with everything it needs. */
    private fun downloadPendingIntent(context: Context, info: UpdateInfo): PendingIntent {
        val intent = Intent(context, UpdateDownloadReceiver::class.java).apply {
            action = UpdateDownloadReceiver.ACTION
            putExtra(UpdateDownloadWorker.KEY_VERSION, info.version)
            putExtra(UpdateDownloadWorker.KEY_APK_URL, info.apkUrl)
            putExtra(UpdateDownloadWorker.KEY_SHA, info.sha256)
        }
        return PendingIntent.getBroadcast(
            context,
            ID + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Posted once the APK is downloaded and verified; tapping it opens the system installer. */
    fun install(context: Context, file: File, version: String) {
        if (!permitted(context)) return
        try {
            ensureChannel(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                ID,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.update_ready_text, version))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, notification)
        } catch (t: Throwable) {
            L.w("update install notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Posted when the download or its verification failed. */
    fun downloadFailed(context: Context, version: String) {
        if (!permitted(context)) return
        try {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.update_failed_title))
                .setContentText(context.getString(R.string.update_failed_text, version))
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, notification)
        } catch (t: Throwable) {
            L.w("update failed notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Also called by the download worker before it builds its own foreground notification. */
    internal fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Duo Status Bar updates"
            }
        )
    }
}
