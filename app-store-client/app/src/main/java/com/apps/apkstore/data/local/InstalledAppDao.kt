package com.apps.apkstore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apps.apkstore.data.local.InstalledAppEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@Dao
interface InstalledAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInstalledApp(app: InstalledAppEntity): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInstalledApps(apps: List<InstalledAppEntity>): Completable

    @Update
    fun updateInstalledApp(app: InstalledAppEntity): Completable

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName")
    fun getInstalledApp(packageName: String): Single<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    fun getAllInstalledApps(): Flowable<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    fun getAllInstalledAppsSync(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE updateAvailable = 1")
    fun getAppsWithUpdates(): Flowable<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE source = :source")
    fun getInstalledAppsBySource(source: String): Flowable<List<InstalledAppEntity>>

    @Query("DELETE FROM installed_apps WHERE packageName = :packageName")
    fun deleteInstalledApp(packageName: String): Completable

    @Query("UPDATE installed_apps SET lastUsedTime = :time WHERE packageName = :packageName")
    fun updateLastUsed(packageName: String, time: Long): Completable

    @Query("UPDATE installed_apps SET updateAvailable = 1, latestVersionCode = :versionCode, latestVersionName = :versionName WHERE packageName = :packageName")
    fun markUpdateAvailable(packageName: String, versionCode: Int, versionName: String): Completable

    @Query("UPDATE installed_apps SET updateAvailable = 0, versionCode = :versionCode, versionName = :versionName, isUpdated = 1 WHERE packageName = :packageName")
    fun markUpdated(packageName: String, versionCode: Int, versionName: String): Completable
}
