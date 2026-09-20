package com.apps.apkstore.utils

import android.content.Context
import android.content.Intent

object ShareUtils {
    private const val BASE_URL = "https://apk-store-topaz.vercel.app"

    fun shareApp(context: Context, appId: String, appName: String, appDescription: String, version: String = "1.0") {
        val shareUrl = getShareUrl(appId, appName, version)
        val shareText = "Check out $appName on APK Store!\n\n$appDescription\n\n$shareUrl"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out $appName")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun getShareUrl(appId: String, appName: String = "app", version: String = "1.0"): String {
        val safeName = appName.replace("\\s+".toRegex(), "-").lowercase().replace("[^a-z0-9-]".toRegex(), "")
        return "$BASE_URL/$safeName/v$version/share?$appId"
    }

    fun getAppUrl(appId: String): String {
        return "$BASE_URL/app/$appId"
    }
}
