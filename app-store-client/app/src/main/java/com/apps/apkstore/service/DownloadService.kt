package com.apps.apkstore.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.apps.apkstore.R
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.utils.UrlResolver
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID_BASE = 1000
        const val ACTION_START = "com.apps.apkstore.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.apps.apkstore.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.apps.apkstore.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.apps.apkstore.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }

    @Inject
    lateinit var downloadDao: DownloadDao

    @Inject
    lateinit var appDao: AppDao

    @Inject
    lateinit var urlResolver: UrlResolver

    private val activeDownloads = mutableMapOf<String, DownloadTask>()
    private val lastProgressUpdates = mutableMapOf<String, Long>()
    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val disposables = io.reactivex.rxjava3.disposables.CompositeDisposable()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, downloadId=${intent?.getStringExtra(EXTRA_DOWNLOAD_ID)}")
        try {
            startForeground(NOTIFICATION_ID_BASE, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("APK Store")
                .setContentText("Download service active")
                .setSilent(true)
                .build())
            Log.d(TAG, "startForeground SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground FAILED: ${e.message}", e)
        }
        intent?.let { handleIntent(it) }
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        Log.d(TAG, "handleIntent: action=$action, downloadId=$downloadId")

        when (action) {
            ACTION_START -> startDownload(downloadId)
            ACTION_PAUSE -> activeDownloads[downloadId]?.pause()
            ACTION_RESUME -> activeDownloads[downloadId]?.resume()
            ACTION_CANCEL -> cancelDownload(downloadId)
        }
    }

    private fun startDownload(downloadId: String) {
        Log.d(TAG, "startDownload: downloadId=$downloadId, activeDownloads=${activeDownloads.keys}")
        if (activeDownloads.containsKey(downloadId)) {
            Log.d(TAG, "startDownload: already active, skipping")
            return
        }

        disposables.add(downloadDao.getDownloadById(downloadId)
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .subscribe({ download ->
                Log.d(TAG, "startDownload: got download entity: id=${download.id}, url=${download.downloadUrl}, status=${download.status}, size=${download.fileSize}")
                if (download.status == DownloadStatus.DOWNLOADING) {
                    Log.d(TAG, "startDownload: already DOWNLOADING, skipping")
                    return@subscribe
                }
                val task = DownloadTask(download, this::onProgress, this::onComplete, this::onError)
                activeDownloads[downloadId] = task
                task.start()
                updateNotification(download)
                Log.d(TAG, "startDownload: task started")
            }, { error ->
                Log.e(TAG, "startDownload: failed to get download entity", error)
            }))
    }

    private fun cancelDownload(downloadId: String) {
        activeDownloads.remove(downloadId)?.cancel()
        disposables.add(
            downloadDao.deleteDownload(downloadId)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        disposables.add(
            appDao.updateDownloadProgress(downloadId, 0f, DownloadStatus.NONE.name)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        notificationManager?.cancel(NOTIFICATION_ID_BASE + downloadId.hashCode())
    }

    private fun onProgress(download: DownloadEntity, progress: Float, speed: Long) {
        val now = System.currentTimeMillis()
        val lastUpdate = lastProgressUpdates[download.id] ?: 0L
        if (now - lastUpdate < 500) return
        lastProgressUpdates[download.id] = now

        disposables.add(downloadDao.updateProgress(download.id, progress, DownloadStatus.DOWNLOADING.name, (download.totalBytes * progress / 100).toLong(), speed)
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .subscribe())
        disposables.add(appDao.updateDownloadProgress(download.appId, progress, DownloadStatus.DOWNLOADING.name)
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .subscribe())
    }

    private fun onComplete(download: DownloadEntity, file: File) {
        Log.d(TAG, "onComplete: downloadId=${download.id}, file=${file.absolutePath}, exists=${file.exists()}, size=${file.length()}")
        activeDownloads.remove(download.id)
        disposables.add(
            downloadDao.markCompleted(download.id, file.absolutePath, System.currentTimeMillis())
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        disposables.add(
            appDao.updateLocalPath(download.appId, file.absolutePath)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        showCompletionNotification(download, file)
        autoInstall(download, file)
    }

    private fun autoInstall(download: DownloadEntity, file: File) {
        Log.d(TAG, "autoInstall: file=${file.absolutePath}, appId=${download.appId}, package=${download.packageName}")
        try {
            val intent = Intent(this, InstallService::class.java).apply {
                action = InstallService.ACTION_INSTALL
                putExtra(InstallService.EXTRA_APK_PATH, file.absolutePath)
                putExtra(InstallService.EXTRA_APP_ID, download.appId)
                putExtra(InstallService.EXTRA_PACKAGE_NAME, download.packageName)
                putExtra(InstallService.EXTRA_VERSION_CODE, download.versionCode)
                putExtra(InstallService.EXTRA_APP_NAME, download.appName)
                putExtra(InstallService.EXTRA_BYPASS_SIGNATURE, true)
            }
            startForegroundService(intent)
            Log.d(TAG, "autoInstall: startForegroundService SUCCESS")
        } catch (e: Exception) {
            Log.e(TAG, "autoInstall: startForegroundService FAILED: ${e.message}", e)
            try {
                startService(Intent(this, InstallService::class.java).apply {
                    action = InstallService.ACTION_INSTALL
                    putExtra(InstallService.EXTRA_APK_PATH, file.absolutePath)
                    putExtra(InstallService.EXTRA_APP_ID, download.appId)
                })
                Log.d(TAG, "autoInstall: fallback startService SUCCESS")
            } catch (e2: Exception) {
                Log.e(TAG, "autoInstall: fallback startService also FAILED: ${e2.message}", e2)
            }
        }
    }

    private fun onError(download: DownloadEntity, error: String) {
        Log.e(TAG, "onError: downloadId=${download.id}, error=$error")
        activeDownloads.remove(download.id)
        disposables.add(
            downloadDao.markFailed(download.id, error, System.currentTimeMillis())
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        disposables.add(
            appDao.updateDownloadProgress(download.appId, 0f, DownloadStatus.FAILED.name)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe()
        )
        showErrorNotification(download, error)
    }

    private fun updateNotification(download: DownloadEntity) {
        val nm = getNotificationManager()
        val notificationId = NOTIFICATION_ID_BASE + download.id.hashCode()

        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseAction = createActionIntent(ACTION_PAUSE, download.id, R.drawable.ic_pause, "Pause")
        val cancelAction = createActionIntent(ACTION_CANCEL, download.id, R.drawable.ic_cancel, "Cancel")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(download.appName)
            .setContentText("${download.progress.toInt()}% - ${formatSpeed(download.speed)}")
            .setProgress(100, download.progress.toInt(), false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(pauseAction)
            .addAction(cancelAction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun showCompletionNotification(download: DownloadEntity, file: File) {
        val nm = getNotificationManager()
        val notificationId = NOTIFICATION_ID_BASE + download.id.hashCode()

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(this@DownloadService, "${packageName}.fileprovider", file)
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download Complete")
            .setContentText("${download.appName} is ready to install")
            .setProgress(0, 0, false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun showErrorNotification(download: DownloadEntity, error: String) {
        val nm = getNotificationManager()
        val notificationId = NOTIFICATION_ID_BASE + download.id.hashCode()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download Failed")
            .setContentText("${download.appName}: $error")
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setOngoing(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        nm.notify(notificationId, builder.build())
    }

    private fun createActionIntent(action: String, downloadId: String, icon: Int, title: String): NotificationCompat.Action {
        val intent = Intent(this, DownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        val pendingIntent = PendingIntent.getService(
            this, downloadId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            androidx.core.graphics.drawable.IconCompat.createWithResource(this, icon),
            title,
            pendingIntent
        ).build()
    }

    private fun getNotificationManager(): NotificationManager {
        if (notificationManager == null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return notificationManager!!
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
                enableVibration(false)
                setSound(null, null)
            }
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "APKStore::DownloadWakeLock")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_048_576 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        disposables.clear()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class DownloadTask(
        private val download: DownloadEntity,
        private val onProgress: (DownloadEntity, Float, Long) -> Unit,
        private val onComplete: (DownloadEntity, File) -> Unit,
        private val onError: (DownloadEntity, String) -> Unit
    ) {
        private val lock = Object()
        private var isPaused = false
        private var isCancelled = false
        private var thread: Thread? = null

        fun start() {
            thread = Thread { downloadFile() }.apply { start() }
        }

        fun pause() { isPaused = true }

        fun resume() {
            isPaused = false
            synchronized(lock) { lock.notifyAll() }
        }

        fun cancel() {
            isCancelled = true
            isPaused = false
            synchronized(lock) { lock.notifyAll() }
        }

        private fun downloadFile() {
            Log.d(TAG, "DownloadTask.downloadFile: START for ${download.appName}, url=${download.downloadUrl}")
            val file = getDownloadFile(download)
            
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "DownloadTask: file already exists (${file.length()} bytes), skipping download")
                onComplete(download, file)
                return
            }

            val tempFile = File(file.parentFile, "${file.name}.tmp")
            var downloaded = if (tempFile.exists()) tempFile.length() else 0L
            val startTime = System.currentTimeMillis()

            val resolvedUrl = runBlocking {
                Log.d(TAG, "DownloadTask: resolving URL: ${download.downloadUrl}")
                val resolved = urlResolver.resolve(download.downloadUrl)
                Log.d(TAG, "DownloadTask: resolved URL: $resolved")
                resolved
            }

            val request = Request.Builder()
                .url(resolvedUrl)
                .addHeader("Range", "bytes=$downloaded-")
                .build()

            Log.d(TAG, "DownloadTask: making HTTP request to $resolvedUrl")
            try {
                val response = okHttpClient.newCall(request).execute()
                Log.d(TAG, "DownloadTask: response code=${response.code}, successful=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code}")
                }

                val total = response.body?.contentLength()?.plus(downloaded) ?: download.fileSize
                Log.d(TAG, "DownloadTask: total=$total, downloaded=$downloaded")
                val sink = FileOutputStream(tempFile, true)

                response.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead = input.read(buffer)
                    var bytesWritten = 0L
                    while (bytesRead != -1) {
                        while (isPaused && !isCancelled) {
                            synchronized(lock) { (lock as java.lang.Object).wait() }
                        }
                        if (isCancelled) break

                        sink.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        bytesWritten += bytesRead

                        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                        val speed = if (elapsed > 0) ((downloaded) / elapsed).toLong() else 0L
                        val progress = if (total > 0) (downloaded * 100f / total) else 0f
                        if (bytesWritten % (100 * 1024) < 8192) {
                            Log.d(TAG, "DownloadTask: progress=${String.format("%.1f", progress)}%, downloaded=${downloaded/1024}KB, speed=${speed/1024}KB/s")
                        }
                        onProgress(download, progress.coerceIn(0f, 100f), speed)
                        bytesRead = input.read(buffer)
                    }
                }
                sink.close()
                Log.d(TAG, "DownloadTask: download complete, total downloaded=${downloaded} bytes")

                if (isCancelled) {
                    tempFile.delete()
                    return
                }

                if (tempFile.renameTo(file)) {
                    Log.d(TAG, "DownloadTask: file renamed to ${file.absolutePath}")
                    onComplete(download, file)
                } else {
                    Log.e(TAG, "DownloadTask: rename FAILED from ${tempFile.absolutePath} to ${file.absolutePath}")
                    onError(download, "Failed to save file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DownloadTask: EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!isCancelled) {
                    onError(download, e.message ?: "Unknown error")
                }
            }
        }

        private fun getDownloadFile(download: DownloadEntity): File {
            val baseDir = if (android.os.Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED) {
                getExternalFilesDir(null)
            } else {
                filesDir
            }
            val dir = File(baseDir, "downloads")
            dir.mkdirs()
            return File(dir, "${download.appName}_v${download.versionName}.apk")
        }
    }
}