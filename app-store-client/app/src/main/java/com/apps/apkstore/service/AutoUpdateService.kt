package com.apps.apkstore.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.apps.apkstore.R
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class AutoUpdateService : Service() {

    companion object {
        const val CHANNEL_ID = "auto_update_channel"
        const val NOTIFICATION_ID = 3000
        const val ACTION_CHECK_UPDATES = "com.apps.apkstore.CHECK_UPDATES_AUTO"
    }

    @Inject lateinit var appDao: AppDao
    @Inject lateinit var downloadDao: DownloadDao

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_UPDATES) {
            checkAndAutoUpdate()
        }
        return START_NOT_STICKY
    }

    private fun checkAndAutoUpdate() {
        showNotification("Checking for updates...", "Scanning installed apps")

        appDao.getInstalledApps()
            .subscribeOn(Schedulers.io())
            .subscribe({ installedApps ->
                val updatableApps = installedApps.filter { app ->
                    app.versionCode > 0 && app.hasUpdate()
                }

                if (updatableApps.isNotEmpty()) {
                    showNotification(
                        "Updates Available",
                        "${updatableApps.size} app(s) can be updated"
                    )
                    updatableApps.forEach { app ->
                        autoDownloadUpdate(app)
                    }
                } else {
                    showNotification("No Updates", "All apps are up to date")
                }
                stopSelf()
            }, { stopSelf() })
    }

    private fun autoDownloadUpdate(app: com.apps.apkstore.data.model.AppModel) {
        val downloadId = UUID.randomUUID().toString()
        val download = DownloadEntity(
            id = downloadId,
            appId = app.id,
            packageName = app.packageName,
            appName = app.name,
            iconUrl = app.iconUrl,
            downloadUrl = app.downloadUrl,
            fileSize = app.fileSize,
            totalBytes = app.fileSize,
            versionCode = app.versionCode,
            versionName = app.versionName,
            status = DownloadStatus.PENDING
        )

        downloadDao.insertDownload(download)
            .subscribeOn(Schedulers.io())
            .subscribe()

        val intent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        startForegroundService(intent)
    }

    private fun showNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(CHANNEL_ID)
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Auto Updates", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
