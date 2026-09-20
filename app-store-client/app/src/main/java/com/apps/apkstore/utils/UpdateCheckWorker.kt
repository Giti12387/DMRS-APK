package com.apps.apkstore.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.service.AutoUpdateService
import com.apps.apkstore.service.SilentAutoUpdateService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AppRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "auto_update_check"
        private const val PREFS_NAME = "zoro_app_store_prefs"
        private const val KEY_AUTO_UPDATE = "auto_update"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs: SharedPreferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val autoUpdateEnabled = prefs.getBoolean(KEY_AUTO_UPDATE, false)

            if (!autoUpdateEnabled) {
                return Result.success()
            }

            repository.syncInstalledApps().blockingAwait()
            repository.checkForUpdates().blockingGet()

            val intent = Intent(applicationContext, SilentAutoUpdateService::class.java).apply {
                action = SilentAutoUpdateService.ACTION_CHECK_AND_UPDATE
            }
            applicationContext.startForegroundService(intent)

            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateCheckWorker", "Failed to check updates", e)
            Result.failure()
        }
    }
}