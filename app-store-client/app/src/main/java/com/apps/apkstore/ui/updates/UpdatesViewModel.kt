package com.apps.apkstore.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apps.apkstore.data.local.InstalledAppDao
import com.apps.apkstore.data.local.InstalledAppEntity
import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.service.InstallService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repository: AppRepository,
    private val installedAppDao: InstalledAppDao
) : ViewModel() {

    private val disposables = CompositeDisposable()

    fun checkForUpdates(
        onSuccess: (List<InstalledAppEntity>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        disposables.add(
            repository.checkForUpdates()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateInfos ->
                    getAppsWithUpdates(onSuccess)
                }, { error ->
                    onError(error)
                })
        )
    }

    private fun getAppsWithUpdates(onSuccess: (List<InstalledAppEntity>) -> Unit) {
        disposables.add(
            installedAppDao.getAppsWithUpdates()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ apps ->
                    onSuccess(apps)
                }, { error ->
                    onSuccess(emptyList())
                })
        )
    }

    fun updateApp(app: InstalledAppEntity) {
        // Find the app in store and download/update
        // This would trigger download and install
        val context = com.apps.apkstore.APKStoreApplication.instance
        // Implementation would search for app and start download
    }

    fun updateAll() {
        // Update all apps with available updates
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}