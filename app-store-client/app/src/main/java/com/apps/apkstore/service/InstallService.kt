package com.apps.apkstore.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.apps.apkstore.R
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.utils.ApkUtils
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstallService : Service() {

    companion object {
        private const val TAG = "InstallService"
        const val CHANNEL_ID = "install_channel"
        const val NOTIFICATION_ID = 2000
        const val ACTION_INSTALL = "com.apps.apkstore.INSTALL_APK"
        const val ACTION_INSTALL_SILENT = "com.apps.apkstore.INSTALL_SILENT"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_BYPASS_SIGNATURE = "bypass_signature"
    }

    @Inject
    lateinit var appDao: AppDao

    private var notificationManager: NotificationManager? = null
    private var packageInstaller: PackageInstaller? = null
    private var sessionId = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        packageInstaller = packageManager.packageInstaller
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        try {
            startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("APK Store")
                .setContentText("Preparing installation...")
                .setSilent(true)
                .build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        intent?.let {
            when (it.action) {
                ACTION_INSTALL -> {
                    val apkPath = it.getStringExtra(EXTRA_APK_PATH) ?: return START_STICKY
                    val appId = it.getStringExtra(EXTRA_APP_ID) ?: return START_STICKY
                    val packageName = it.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                    val versionCode = it.getIntExtra(EXTRA_VERSION_CODE, 0)
                    val appName = it.getStringExtra(EXTRA_APP_NAME) ?: "App"
                    val bypassSignature = it.getBooleanExtra(EXTRA_BYPASS_SIGNATURE, true)
                    installApk(apkPath, appId, packageName, versionCode, appName, bypassSignature)
                }
                ACTION_INSTALL_SILENT -> {
                    val apkPath = it.getStringExtra(EXTRA_APK_PATH) ?: return START_STICKY
                    val packageName = it.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_STICKY
                    val versionCode = it.getIntExtra(EXTRA_VERSION_CODE, 0)
                    silentInstall(apkPath, packageName, versionCode)
                }
            }
        }
        return START_STICKY
    }

    private fun installApk(apkPath: String, appId: String, packageName: String, versionCode: Int, appName: String, bypassSignature: Boolean) {
        Log.d(TAG, "installApk: path=$apkPath, pkg=$packageName, version=$versionCode, bypass=$bypassSignature")
        showStageNotification(appId, appName, "Preparing installation...", 10)

        val prefs = getSharedPreferences("zoro_app_store_prefs", MODE_PRIVATE)
        val installMethod = prefs.getString("install_method", "system") ?: "system"
        Log.d(TAG, "installApk: installMethod=$installMethod")

        Completable.fromAction {
            val file = File(apkPath)
            if (!file.exists()) {
                Log.e(TAG, "installApk: APK file not found: $apkPath")
                throw IOException("APK file not found: $apkPath")
            }
            Log.d(TAG, "installApk: file exists, size=${file.length()}")

            showStageNotification(appId, appName, "Verifying APK...", 20)
            Thread.sleep(300)

            val verificationResult = ApkUtils.verifyApk(this, file, bypassSignature)
            if (!verificationResult.isValid && !bypassSignature) {
                throw SecurityException("APK verification failed: ${verificationResult.errorMessage}")
            }

            showStageNotification(appId, appName, "Checking version...", 30)
            Thread.sleep(200)

            val existingVersionCode = getInstalledVersionCode(packageName)
            if (existingVersionCode > 0 && existingVersionCode >= versionCode) {
                showAlreadyInstalledNotification(appId, packageName)
                return@fromAction
            }

            when (installMethod) {
                "root" -> {
                    showStageNotification(appId, appName, "Installing (root)...", 50)
                    Log.d(TAG, "installApk: using ROOT method")
                    val result = kotlinx.coroutines.runBlocking {
                        SilentInstallManager.install(this@InstallService, file, packageName, SilentInstallManager.InstallMethod.ROOT)
                    }
                    if (result.success) {
                        showStageNotification(appId, appName, "Finalizing...", 90)
                        Thread.sleep(500)
                        appDao.markAsInstalled(appId, versionCode)
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                        showInstallSuccessNotification(appId, packageName, appName)
                        return@fromAction
                    } else {
                        Log.w(TAG, "installApk: root install failed, falling back to PackageInstaller")
                        showStageNotification(appId, appName, "Root unavailable, trying alternative...", 40)
                        installViaPackageInstaller(file, packageName, versionCode, bypassSignature, appId, appName)
                    }
                }
                "silent" -> {
                    showStageNotification(appId, appName, "Installing (ADB)...", 50)
                    Log.d(TAG, "installApk: using SILENT/ADB method")
                    val result = kotlinx.coroutines.runBlocking {
                        SilentInstallManager.install(this@InstallService, file, packageName, SilentInstallManager.InstallMethod.WIRELESS_ADB)
                    }
                    if (result.success) {
                        showStageNotification(appId, appName, "Finalizing...", 90)
                        Thread.sleep(500)
                        appDao.markAsInstalled(appId, versionCode)
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                        showInstallSuccessNotification(appId, packageName, appName)
                        return@fromAction
                    } else {
                        Log.w(TAG, "installApk: silent install failed, falling back to PackageInstaller")
                        showStageNotification(appId, appName, "ADB unavailable, trying alternative...", 40)
                        installViaPackageInstaller(file, packageName, versionCode, bypassSignature, appId, appName)
                    }
                }
                else -> {
                    showStageNotification(appId, appName, "Writing app data...", 40)
                    Log.d(TAG, "installApk: using SYSTEM method (PackageInstaller)")
                    installViaPackageInstaller(file, packageName, versionCode, bypassSignature, appId, appName)
                }
            }
        }.subscribeOn(Schedulers.io())
            .subscribe(
                { 
                    Log.d(TAG, "installApk: Completable SUCCESS")
                },
                { error -> 
                    Log.e(TAG, "installApk: Completable ERROR: ${error.message}", error)
                    showInstallErrorNotification(appId, packageName, appName, error.message ?: "Unknown error")
                }
            )
    }

    private fun installViaPackageInstaller(file: File, packageName: String, versionCode: Int, bypassSignature: Boolean, appId: String, appName: String) {
        Log.d(TAG, "installViaPackageInstaller: pkg=$packageName, file=${file.absolutePath}, size=${file.length()}")
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        try {
            val setInstallFlagsMethod = params.javaClass.getMethod(
                "setInstallFlags", Int::class.javaPrimitiveType
            )
            var flags = 0
            flags = flags or 0x00000004 // INSTALL_GRANT_RUNTIME_PERMISSIONS
            flags = flags or 0x00000002 // INSTALL_ALLOW_TEST
            if (bypassSignature) {
                flags = flags or 0x00000008 // INSTALL_SKIP_VERIFICATION
            }
            setInstallFlagsMethod.invoke(params, flags)
            Log.d(TAG, "installViaPackageInstaller: set install flags=$flags")
        } catch (e: Exception) {
            Log.w(TAG, "installViaPackageInstaller: setInstallFlags failed: ${e.message}")
        }

        try {
            val setInstallReasonMethod = params.javaClass.getMethod(
                "setInstallReason", Int::class.javaPrimitiveType
            )
            setInstallReasonMethod.invoke(params, 1) // INSTALL_REASON_USER_REQUEST
        } catch (e: Exception) {
        }

        sessionId = packageInstaller!!.createSession(params)
        Log.d(TAG, "installViaPackageInstaller: session created, sessionId=$sessionId")
        val session = packageInstaller!!.openSession(sessionId)

        try {
            showStageNotification(appId, appName, "Writing APK to device...", 50)
            val out = session.openWrite("apk", 0, file.length())
            file.inputStream().use { input -> input.copyTo(out) }
            session.fsync(out)
            out.close()
            Log.d(TAG, "installViaPackageInstaller: APK written to session")

            showStageNotification(appId, appName, "Installing app...", 70)

            val intent = Intent(this, InstallReceiver::class.java)
            intent.action = InstallReceiver.ACTION_SESSION_COMMITTED
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(EXTRA_VERSION_CODE, versionCode)
            intent.putExtra(EXTRA_APK_PATH, file.absolutePath)
            intent.putExtra(EXTRA_APP_ID, appId)
            intent.putExtra(EXTRA_APP_NAME, appName)
            val pendingIntent = PendingIntent.getBroadcast(
                this, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "installViaPackageInstaller: session committed, waiting for callback...")
            session.close()
        } catch (e: Exception) {
            Log.e(TAG, "installViaPackageInstaller: EXCEPTION: ${e.message}", e)
            try { session.abandon() } catch (ignored: Exception) {}
            packageInstaller!!.abandonSession(sessionId)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun installViaIntent(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun silentRootInstall(file: File, packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("pm install -r -d -g \"${file.absolutePath}\"\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun silentShizukuInstall(file: File): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "pm install -r -d -g \"${file.absolutePath}\""
            ))
            val exitCode = process.waitFor()
            Log.d(TAG, "silentShizukuInstall: exitCode=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "silentShizukuInstall failed: ${e.message}", e)
            false
        }
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which su")
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result != null && result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun silentInstall(apkPath: String, packageName: String, versionCode: Int) {
        Completable.fromAction {
            val file = File(apkPath)
            if (!file.exists()) throw IOException("APK file not found: $apkPath")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                installViaPackageInstaller(file, packageName, versionCode, true, "", "")
            } else {
                installViaIntent(file)
            }
        }.subscribeOn(Schedulers.io()).subscribe()
    }

    private fun getInstalledVersionCode(packageName: String): Int {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun showStageNotification(appId: String, appName: String, stage: String, progress: Int) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Installing $appName")
            .setContentText(stage)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(CHANNEL_ID)
        getNotificationManager().notify(NOTIFICATION_ID, builder.build())
    }

    private fun showInstallSuccessNotification(appId: String, packageName: String, appName: String) {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = openIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$appName installed!")
            .setContentText("Tap to open")
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setOngoing(false)
        pendingIntent?.let { builder.setContentIntent(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(CHANNEL_ID)
        getNotificationManager().notify(NOTIFICATION_ID, builder.build())
    }

    private fun showInstallErrorNotification(appId: String, packageName: String, appName: String, error: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Installation failed")
            .setContentText("$appName: $error")
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setOngoing(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(CHANNEL_ID)
        getNotificationManager().notify(NOTIFICATION_ID, builder.build())
    }

    private fun showAlreadyInstalledNotification(appId: String, packageName: String) {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = openIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Already installed")
            .setContentText("This version is already installed")
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setOngoing(false)
        pendingIntent?.let { builder.setContentIntent(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(CHANNEL_ID)
        getNotificationManager().notify(NOTIFICATION_ID, builder.build())
    }

    private fun getNotificationManager(): NotificationManager {
        if (notificationManager == null) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return notificationManager!!
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App Installation", NotificationManager.IMPORTANCE_LOW).apply {
                description = "App installation progress"
                enableVibration(false)
                setSound(null, null)
            }
            getNotificationManager().createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (sessionId != -1) {
            try { packageInstaller?.abandonSession(sessionId) } catch (ignored: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class InstallReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val TAG = "InstallReceiver"
        const val ACTION_SESSION_COMMITTED = "com.apps.apkstore.SESSION_COMMITTED"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) ?: return
        val packageName = intent.getStringExtra(InstallService.EXTRA_PACKAGE_NAME) ?: return
        val versionCode = intent.getIntExtra(InstallService.EXTRA_VERSION_CODE, 0)
        val apkPath = intent.getStringExtra(InstallService.EXTRA_APK_PATH) ?: ""
        val appId = intent.getStringExtra(InstallService.EXTRA_APP_ID) ?: ""
        val appName = intent.getStringExtra(InstallService.EXTRA_APP_NAME) ?: "App"
        android.util.Log.d(TAG, "onReceive: status=$status, pkg=$packageName, versionCode=$versionCode, extra=${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                android.util.Log.d(TAG, "STATUS_SUCCESS for $packageName")
                try {
                    val db = androidx.room.Room.databaseBuilder(
                        context!!.applicationContext,
                        com.apps.apkstore.data.local.AppDatabase::class.java,
                        "zoro_app_store.db"
                    ).build()
                    io.reactivex.rxjava3.schedulers.Schedulers.io().scheduleDirect {
                        db.appDao().markAsInstalledByPackage(packageName, versionCode).blockingAwait()
                    }
                    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    val pendingIntent = launchIntent?.let {
                        it.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        android.app.PendingIntent.getActivity(context, 0, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    }
                    val builder = androidx.core.app.NotificationCompat.Builder(context, "install_channel")
                        .setSmallIcon(com.apps.apkstore.R.drawable.ic_notification)
                        .setContentTitle("Installation complete")
                        .setContentText("Tap to open $appName")
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                        .setOngoing(false)
                    pendingIntent?.let { builder.setContentIntent(it) }
                    nm.notify(2000, builder.build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else -> {
                android.util.Log.w(TAG, "STATUS_FAILURE($status) for $packageName - falling back to ACTION_VIEW")
                if (apkPath.isNotEmpty() && context != null) {
                    try {
                        val file = java.io.File(apkPath)
                        if (file.exists()) {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW)
                            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                androidx.core.content.FileProvider.getUriForFile(
                                    context.applicationContext,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            } else {
                                android.net.Uri.fromFile(file)
                            }
                            fallbackIntent.setDataAndType(uri, "application/vnd.android.package-archive")
                            fallbackIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(fallbackIntent)
                            android.util.Log.d(TAG, "Fallback ACTION_VIEW launched for $apkPath")
                        } else {
                            android.util.Log.e(TAG, "APK file not found: $apkPath")
                            showFallbackError(context, appName)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Fallback failed: ${e.message}", e)
                        showFallbackError(context, appName)
                    }
                } else {
                    showFallbackError(context, appName)
                }
            }
        }
    }

    private fun showFallbackError(context: Context?, appName: String) {
        if (context == null) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val builder = androidx.core.app.NotificationCompat.Builder(context, "install_channel")
                .setSmallIcon(com.apps.apkstore.R.drawable.ic_notification)
                .setContentTitle("Installation failed")
                .setContentText("Could not install $appName")
                .setAutoCancel(true)
            nm.notify(2000, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}