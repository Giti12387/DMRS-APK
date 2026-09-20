package com.apps.apkstore.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.apps.apkstore.BuildConfig
import java.io.File

object InstallUtils {

    const val REQUEST_INSTALL_PERMISSION = 1001
    const val REQUEST_INSTALL_APK = 1002

    fun canInstallUnknownApps(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.packageManager.canRequestPackageInstalls()
        }
        return true
    }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${activity.packageName}"))
            activity.startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    fun installApk(context: Context, apkFile: File, authority: String = "${BuildConfig.APPLICATION_ID}.fileprovider") {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri: Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(context, authority, apkFile)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            uri = Uri.fromFile(apkFile)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK

        if (context is Activity) {
            context.startActivityForResult(intent, REQUEST_INSTALL_APK)
        } else {
            context.startActivity(intent)
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledVersionCode(context: Context, packageName: String): Int {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    fun getInstalledVersionName(context: Context, packageName: String): String {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    fun getDefaultDownloadDir(context: Context): File {
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        } else {
            context.filesDir
        }
    }

    fun getAppStoreDownloadDir(context: Context): File {
        val dir = File(getDefaultDownloadDir(context), "APKStore")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}