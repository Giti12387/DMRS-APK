package com.apps.apkstore

import android.app.Application
import com.apps.apkstore.data.local.AppDatabase
import com.apps.apkstore.data.local.PreferencesManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class APKStoreApplication : Application() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var appDatabase: AppDatabase

    override fun onCreate() {
        super.onCreate()
        instance = this
        PreferencesManager.getInstance(this)
        AppDatabase.getInstance(this)
        com.apps.apkstore.utils.UpdateCheckWorker.schedule(this)
    }

    fun getAppEntryPoint(): AppEntryPoint {
        return EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
    }

    companion object {
        lateinit var instance: APKStoreApplication
            private set
    }
}
