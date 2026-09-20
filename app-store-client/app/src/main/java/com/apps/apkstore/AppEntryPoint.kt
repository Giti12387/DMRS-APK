package com.apps.apkstore

import android.content.Context
import com.apps.apkstore.data.local.PreferencesManager
import com.apps.apkstore.data.local.AppDatabase
import com.apps.apkstore.data.repository.AppRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun preferencesManager(): PreferencesManager
    fun appDatabase(): AppDatabase
    fun repository(): AppRepository
}

fun Context.getAppEntryPoint(): AppEntryPoint {
    return EntryPointAccessors.fromApplication(this.applicationContext, AppEntryPoint::class.java)
}