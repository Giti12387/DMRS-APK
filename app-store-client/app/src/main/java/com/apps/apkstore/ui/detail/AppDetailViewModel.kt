package com.apps.apkstore.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.data.repository.AppRepository
import android.content.Context
import com.apps.apkstore.utils.ShareUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val repository: AppRepository,
    private val appDao: AppDao
) : ViewModel() {

    private val disposables = CompositeDisposable()
    private val TAG = "AppDetailVM"

    fun loadAppDetail(
        appId: String,
        onSuccess: (AppModel) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        disposables.add(
            repository.getAppDetail(appId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ app ->
                    onSuccess(app)
                }, { error ->
                    onError(error)
                })
        )
    }

    fun observeApp(appId: String, onStateChanged: (AppModel) -> Unit) {
        disposables.add(
            appDao.getAppById(appId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ app ->
                    onStateChanged(app)
                }, { })
        )
    }

    fun handlePrimaryAction(app: AppModel) {
        when {
            app.isInstalled -> openApp(app)
            app.downloadStatus == DownloadStatus.COMPLETED -> installApp(app)
            else -> downloadApp(app)
        }
    }

    private fun openApp(app: AppModel) {
        try {
            val context = com.apps.apkstore.APKStoreApplication.instance
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun downloadApp(app: AppModel) {
        Log.d(TAG, "downloadApp: name=${app.name}, id=${app.id}, url=${app.downloadUrl}")
        disposables.add(
            repository.startDownload(app)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ download ->
                    Log.d(TAG, "downloadApp: startDownload returned downloadId=${download.id}")
                    val context = com.apps.apkstore.APKStoreApplication.instance
                    val intent = android.content.Intent(context, com.apps.apkstore.service.DownloadService::class.java).apply {
                        action = com.apps.apkstore.service.DownloadService.ACTION_START
                        putExtra(com.apps.apkstore.service.DownloadService.EXTRA_DOWNLOAD_ID, download.id)
                    }
                    try {
                        context.startForegroundService(intent)
                        Log.d(TAG, "downloadApp: startForegroundService SUCCESS")
                    } catch (e: Exception) {
                        Log.e(TAG, "downloadApp: startForegroundService FAILED: ${e.message}", e)
                        try {
                            context.startService(intent)
                            Log.d(TAG, "downloadApp: fallback startService SUCCESS")
                        } catch (e2: Exception) {
                            Log.e(TAG, "downloadApp: fallback startService FAILED: ${e2.message}", e2)
                        }
                    }
                }, { error ->
                    Log.e(TAG, "downloadApp: startDownload FAILED", error)
                })
        )
    }

    fun installApp(app: AppModel) {
        val context = com.apps.apkstore.APKStoreApplication.instance
        val intent = android.content.Intent(context, com.apps.apkstore.service.InstallService::class.java).apply {
            action = com.apps.apkstore.service.InstallService.ACTION_INSTALL
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_APK_PATH, app.localPath)
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_APP_ID, app.id)
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_PACKAGE_NAME, app.packageName)
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_VERSION_CODE, app.versionCode)
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_APP_NAME, app.name)
            putExtra(com.apps.apkstore.service.InstallService.EXTRA_BYPASS_SIGNATURE, true)
        }
        context.startForegroundService(intent)
    }

    fun cancelDownload(downloadId: String) {
        val context = com.apps.apkstore.APKStoreApplication.instance
        val intent = android.content.Intent(context, com.apps.apkstore.service.DownloadService::class.java).apply {
            action = com.apps.apkstore.service.DownloadService.ACTION_CANCEL
            putExtra(com.apps.apkstore.service.DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startForegroundService(intent)
    }

    fun uninstallApp(app: AppModel) {
        try {
            val context = com.apps.apkstore.APKStoreApplication.instance
            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppDetailVM", "uninstallApp failed: ${e.message}", e)
        }
    }

    fun shareApp(app: AppModel, context: Context) {
        ShareUtils.shareApp(context, app.id, app.name, app.description)
    }

    fun addToWishlist(app: AppModel) {
    }

    fun checkForUpdate(app: AppModel) {
        disposables.add(
            repository.checkForUpdates()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updates -> }, { error -> })
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}
