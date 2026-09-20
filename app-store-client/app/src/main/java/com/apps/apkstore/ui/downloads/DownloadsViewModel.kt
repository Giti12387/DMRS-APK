package com.apps.apkstore.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.DownloadStatus
import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.service.DownloadService
import com.apps.apkstore.service.InstallService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: AppRepository,
    private val downloadDao: DownloadDao
) : ViewModel() {

    private val disposables = CompositeDisposable()

    fun loadDownloads(tab: Int, onResult: (List<DownloadEntity>) -> Unit) {
        val flow = when (tab) {
            0 -> downloadDao.getActiveDownloads()
            1 -> downloadDao.getCompletedDownloads()
            2 -> downloadDao.getFailedDownloads()
            else -> downloadDao.getAllDownloads()
        }

        disposables.add(
            flow
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ downloads ->
                    onResult(downloads)
                }, { error ->
                    onResult(emptyList())
                })
        )
    }

    fun pauseDownload(downloadId: String) {
        disposables.add(
            downloadDao.pauseDownload(downloadId)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }

    fun resumeDownload(downloadId: String) {
        disposables.add(
            downloadDao.resumeDownload(downloadId)
                .subscribeOn(Schedulers.io())
                .subscribe({
                    // Restart download service
                    val context = com.apps.apkstore.APKStoreApplication.instance
                    val intent = android.content.Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_RESUME
                        putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
                    }
                    context.startForegroundService(intent)
                }, { })
        )
    }

    fun cancelDownload(downloadId: String) {
        disposables.add(
            downloadDao.deleteDownload(downloadId)
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
        
        // Stop service
        val context = com.apps.apkstore.APKStoreApplication.instance
        val intent = android.content.Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.startForegroundService(intent)
    }

    fun installDownload(download: DownloadEntity) {
        val context = com.apps.apkstore.APKStoreApplication.instance
        val intent = android.content.Intent(context, InstallService::class.java).apply {
            action = InstallService.ACTION_INSTALL
            putExtra(InstallService.EXTRA_APK_PATH, download.localPath)
            putExtra(InstallService.EXTRA_APP_ID, download.appId)
            putExtra(InstallService.EXTRA_PACKAGE_NAME, download.packageName)
            putExtra(InstallService.EXTRA_VERSION_CODE, download.versionCode)
            putExtra(InstallService.EXTRA_BYPASS_SIGNATURE, true)
        }
        context.startForegroundService(intent)
    }

    fun clearHistory() {
        disposables.add(
            downloadDao.clearHistory()
                .subscribeOn(Schedulers.io())
                .subscribe()
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}