package com.apps.apkstore.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.apps.apkstore.service.DownloadService
import java.util.concurrent.TimeUnit

class UpdateCheckReceiver : BroadcastReceiver() {

    companion object {
        const val WORK_NAME = "periodic_update_check"
        const val CHECK_INTERVAL_HOURS = 6L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                schedulePeriodicUpdateCheck(context)
                startDownloadService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startDownloadService(context)
            }
            "com.apps.apkstore.CHECK_UPDATES" -> {
                triggerUpdateCheck(context)
            }
        }
    }

    private fun schedulePeriodicUpdateCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    private fun triggerUpdateCheck(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "manual_update_check",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    private fun startDownloadService(context: Context) {
        val intent = Intent(context, DownloadService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}