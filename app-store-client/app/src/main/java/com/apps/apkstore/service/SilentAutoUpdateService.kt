package com.apps.apkstore.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.apps.apkstore.R
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.data.remote.SupabaseApiService
import com.apps.apkstore.utils.UrlResolver
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SilentAutoUpdateService : Service() {

    companion object {
        const val CHANNEL_ID = "silent_auto_update_channel"
        const val NOTIFICATION_ID_BASE = 5000
        const val ACTION_START_SILENT_UPDATE = "com.apps.apkstore.START_SILENT_UPDATE"
        const val ACTION_CHECK_AND_UPDATE = "com.apps.apkstore.CHECK_AND_UPDATE_ALL"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_UPDATE_ALL = "update_all"
    }

    @Inject lateinit var appDao: AppDao
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var apiService: SupabaseApiService
    @Inject lateinit var urlResolver: UrlResolver

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val activeUpdates = mutableMapOf<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_AND_UPDATE -> checkAndUpdateAll()
            ACTION_START_SILENT_UPDATE -> {
                val appId = intent.getStringExtra(EXTRA_APP_ID)
                if (appId != null) {
                    silentUpdateApp(appId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun checkAndUpdateAll() {
        showProgressNotification("Checking for updates...", "Scanning installed apps", 0)

        val prefs = getSharedPreferences("zoro_app_store_prefs", MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("auto_update_selected_apps", emptySet()) ?: emptySet()

        appDao.getInstalledApps()
            .subscribeOn(Schedulers.io())
            .subscribe({ installedApps ->
                val updatableApps = installedApps.filter { app ->
                    app.versionCode > 0 && app.hasUpdate() && !app.isShareOnly &&
                    (selectedApps.isEmpty() || app.packageName in selectedApps)
                }

                if (updatableApps.isEmpty()) {
                    showCompleteNotification("All apps are up to date")
                    stopSelf()
                    return@subscribe
                }

                showProgressNotification(
                    "Updating ${updatableApps.size} app(s)",
                    "Downloading updates in background...",
                    0
                )

                updatableApps.forEachIndexed { index, app ->
                    silentUpdateApp(app.id, index, updatableApps.size)
                }
            }, { error ->
                showErrorNotification("Failed to check updates: ${error.message}")
                stopSelf()
            })
    }

    private fun silentUpdateApp(appId: String, currentIndex: Int = 0, totalCount: Int = 1) {
        if (activeUpdates.containsKey(appId)) return
        activeUpdates[appId] = true

        apiService.getAppById(appId)
            .subscribeOn(Schedulers.io())
            .subscribe({ app ->
                val installedVersionCode = getInstalledVersionCode(app.packageName)
                if (installedVersionCode >= app.versionCode) {
                    activeUpdates.remove(appId)
                    checkIfAllComplete()
                    return@subscribe
                }

                showProgressNotification(
                    "Downloading ${app.name}",
                    "Version ${app.versionName} (${currentIndex + 1}/$totalCount)",
                    0
                )

                downloadAndInstallSilently(app, currentIndex, totalCount)
            }, { error ->
                activeUpdates.remove(appId)
                showErrorNotification("Failed to get update info: ${error.message}")
                checkIfAllComplete()
            })
    }

    private fun downloadAndInstallSilently(app: AppModel, currentIndex: Int, totalCount: Int) {
        Thread {
            val downloadId = "silent_${app.id}_${System.currentTimeMillis()}"
            val file = File(cacheDir, "silent_updates/${app.packageName}_v${app.versionCode}.apk")
            file.parentFile?.mkdirs()

            try {
                val resolvedUrl = runBlocking { urlResolver.resolve(app.downloadUrl) }
                val request = Request.Builder()
                    .url(resolvedUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code}")
                }

                val totalBytes = response.body?.contentLength() ?: app.fileSize
                var downloadedBytes = 0L

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes * 100 / totalBytes).toInt()
                            } else 0

                            showProgressNotification(
                                "Downloading ${app.name}",
                                "${progress}% - Version ${app.versionName}",
                                progress
                            )
                        }
                    }
                }

                showProgressNotification(
                    "Installing ${app.name}",
                    "Please wait...",
                    100
                )

                silentInstallApk(file, app.packageName, app.versionCode)

                file.delete()
                activeUpdates.remove(app.id)

                showCompleteNotification("${app.name} updated to v${app.versionName}")

                checkIfAllComplete()

            } catch (e: Exception) {
                file.delete()
                activeUpdates.remove(app.id)
                showErrorNotification("Failed to update ${app.name}: ${e.message}")
                checkIfAllComplete()
            }
        }.start()
    }

    private fun silentInstallApk(file: File, packageName: String, versionCode: Int) {
        val packageInstaller = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        try {
            val setInstallFlagsMethod = params.javaClass.getMethod(
                "setInstallFlags", Int::class.javaPrimitiveType
            )
            var flags = 0
            flags = flags or 0x00000004 // INSTALL_GRANT_RUNTIME_PERMISSIONS
            flags = flags or 0x00000002 // INSTALL_ALLOW_TEST
            flags = flags or 0x00000008 // INSTALL_SKIP_VERIFICATION
            setInstallFlagsMethod.invoke(params, flags)
        } catch (e: Exception) {}

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        try {
            val out = session.openWrite("apk", 0, file.length())
            file.inputStream().use { input -> input.copyTo(out) }
            session.fsync(out)
            out.close()

            val intent = Intent(this, SilentInstallReceiver::class.java)
            intent.action = SilentInstallReceiver.ACTION_SESSION_COMMITTED
            intent.putExtra("package_name", packageName)
            intent.putExtra("version_code", versionCode)
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            try { session.abandon() } catch (ignored: Exception) {}
            packageInstaller.abandonSession(sessionId)
            throw e
        }
    }

    private fun getInstalledVersionCode(packageName: String): Int {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun checkIfAllComplete() {
        if (activeUpdates.isEmpty()) {
            stopSelf()
        }
    }

    private fun showProgressNotification(title: String, text: String, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_BASE + 1

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun showCompleteNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_BASE + 2

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Auto-Update Complete")
            .setContentText(text)
            .setAutoCancel(true)
            .setOngoing(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun showErrorNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_BASE + 3

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Auto-Update Failed")
            .setContentText(text)
            .setAutoCancel(true)
            .setOngoing(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Silent Auto Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background app update progress"
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "APKStore::SilentAutoUpdate"
        )
        wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class SilentInstallReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val ACTION_SESSION_COMMITTED = "com.apps.apkstore.SILENT_SESSION_COMMITTED"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_SUCCESS) {
            // Installation successful
        }
    }
}
