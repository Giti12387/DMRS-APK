package com.apps.apkstore.di

import com.apps.apkstore.service.DownloadService
import com.apps.apkstore.service.InstallService
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    // Services are AndroidEntryPoint, no need to provide them here
    // But we can bind interfaces if needed
}