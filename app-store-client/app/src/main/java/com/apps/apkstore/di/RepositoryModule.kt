package com.apps.apkstore.di

import com.apps.apkstore.data.repository.AppRepository
import com.apps.apkstore.data.remote.SupabaseApiService
import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.local.CategoryDao
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.InstalledAppDao
import com.apps.apkstore.data.local.AppDatabase
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAppDao(database: AppDatabase): AppDao = database.appDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideInstalledAppDao(database: AppDatabase): InstalledAppDao = database.installedAppDao()

    @Provides
    @Singleton
    fun provideAppRepository(
        supabaseApi: SupabaseApiService,
        appDao: AppDao,
        categoryDao: CategoryDao,
        downloadDao: DownloadDao,
        installedAppDao: InstalledAppDao,
        @ApplicationContext context: Context
    ): AppRepository = AppRepository(supabaseApi, appDao, categoryDao, downloadDao, installedAppDao, context)
}
