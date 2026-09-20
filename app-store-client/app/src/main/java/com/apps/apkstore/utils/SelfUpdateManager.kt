package com.apps.apkstore.utils

import android.app.AlertDialog
import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object SelfUpdateManager {

    private const val SELF_PACKAGE = "com.apps.apkstore"
    private const val CHECK_URL = "${SupabaseConfig.URL}/rest/v1/app_updates?select=*&package_name=eq.$SELF_PACKAGE&order=version_code.desc&limit=1"

    private var progressDialog: ProgressDialog? = null
    private var downloadId: Long = -1
    private var onUpdateComplete: (() -> Unit)? = null

    fun checkForUpdate(context: Context, onComplete: () -> Unit) {
        onUpdateComplete = onComplete
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(CHECK_URL)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post { onComplete() }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: "[]"
                    val json = org.json.JSONArray(body)
                    if (json.length() == 0) {
                        Handler(Looper.getMainLooper()).post { onComplete() }
                        return
                    }

                    val update = json.getJSONObject(0)
                    val latestVersionCode = update.optInt("version_code", 0)
                    val latestVersionName = update.optString("version_name", "")
                    val apkUrl = update.optString("apk_url", "")
                    val releaseNotes = update.optString("release_notes", "")

                    val pm = context.packageManager
                    val pkgInfo = pm.getPackageInfo(SELF_PACKAGE, 0)
                    val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode
                    }

                    if (latestVersionCode > currentVersionCode && apkUrl.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            showUpdateDialog(context, latestVersionName, latestVersionCode, apkUrl, releaseNotes)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post { onComplete() }
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post { onComplete() }
                }
            }
        })
    }

    private fun showUpdateDialog(context: Context, versionName: String, versionCode: Int, apkUrl: String, releaseNotes: String) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Update Available")
        builder.setMessage(
            "A new version is available!\n\n" +
            "Version: $versionName ($versionCode)\n" +
            if (releaseNotes.isNotEmpty()) "What's new:\n$releaseNotes\n" else ""
        )
        builder.setCancelable(false)
        builder.setPositiveButton("Update") { _, _ ->
            startDownload(context, apkUrl, versionName)
        }
        builder.setNegativeButton("Exit") { _, _ ->
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }
        builder.show()
    }

    private fun startDownload(context: Context, url: String, versionName: String) {
        progressDialog = ProgressDialog(context).apply {
            setTitle("Downloading Update")
            setMessage("Please wait...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }

        Thread {
            val resolvedUrl = try {
                kotlinx.coroutines.runBlocking { UrlResolver().resolve(url) }
            } catch (e: Exception) { url }

            Handler(Looper.getMainLooper()).post {
                val request = DownloadManager.Request(Uri.parse(resolvedUrl))
                    .setTitle("Downloading Update")
                    .setDescription("Version $versionName")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "APKStoreUpdate.apk")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = dm.enqueue(request)

                Thread {
                    var downloading = true
                    while (downloading) {
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = dm.query(query)
                        if (cursor.moveToFirst()) {
                            val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                            if (totalSize > 0) {
                                val progress = (bytesDownloaded * 100 / totalSize)
                                Handler(Looper.getMainLooper()).post {
                                    progressDialog?.progress = progress
                                    progressDialog?.setMessage("$progress% downloaded")
                                }
                            }

                            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                                downloading = false
                            }
                        }
                        cursor.close()
                        if (downloading) Thread.sleep(500)
                    }

                    Handler(Looper.getMainLooper()).post {
                        progressDialog?.dismiss()
                        installApk(context)
                    }
                }.start()
            }
        }.start()
    }

    private fun installApk(context: Context) {
        val file = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "APKStoreUpdate.apk"
        )
        if (!file.exists()) {
            AlertDialog.Builder(context)
                .setTitle("Error")
                .setMessage("Update file not found. Please try again.")
                .setCancelable(false)
                .setPositiveButton("Retry") { _, _ ->
                    onUpdateComplete?.invoke()
                }
                .show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
