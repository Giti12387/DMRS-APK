package com.apps.apkstore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apps.apkstore.data.model.AppModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@Dao
interface AppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApp(app: AppModel): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApps(apps: List<AppModel>): Completable

    @Update
    fun updateApp(app: AppModel): Completable

    @Query("SELECT * FROM apps WHERE id = :id")
    fun getAppById(id: String): Single<AppModel>

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    fun getAppByPackageName(packageName: String): Single<AppModel>

    @Query("SELECT * FROM apps WHERE category = :category ORDER BY downloadCount DESC LIMIT :limit")
    fun getAppsByCategory(category: String, limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE isFeatured = 1 ORDER BY downloadCount DESC LIMIT :limit")
    fun getFeaturedApps(limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE isNew = 1 ORDER BY releaseDate DESC LIMIT :limit")
    fun getNewApps(limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps ORDER BY downloadCount DESC LIMIT :limit")
    fun getTopCharts(limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE name LIKE :query OR description LIKE :query ORDER BY downloadCount DESC LIMIT :limit")
    fun searchApps(query: String, limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentUpdates(limit: Int): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE isInstalled = 1")
    fun getInstalledApps(): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE downloadStatus = 'DOWNLOADING' OR downloadStatus = 'PENDING'")
    fun getActiveDownloads(): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps WHERE downloadStatus = 'COMPLETED' AND isInstalled = 0")
    fun getCompletedDownloads(): Flowable<List<AppModel>>

    @Query("DELETE FROM apps WHERE id = :id")
    fun deleteApp(id: String): Completable

    @Query("DELETE FROM apps WHERE downloadStatus IN ('COMPLETED', 'FAILED', 'CANCELLED') AND isInstalled = 0")
    fun clearDownloadHistory(): Completable

    @Query("UPDATE apps SET downloadProgress = :progress, downloadStatus = :status WHERE id = :id")
    fun updateDownloadProgress(id: String, progress: Float, status: String): Completable

    @Query("UPDATE apps SET localPath = :path, downloadStatus = 'COMPLETED' WHERE id = :id")
    fun updateLocalPath(id: String, path: String): Completable

    @Query("UPDATE apps SET isInstalled = 1, installedVersionCode = :versionCode, downloadStatus = 'INSTALLED' WHERE id = :id")
    fun markAsInstalled(id: String, versionCode: Int): Completable

    @Query("UPDATE apps SET isInstalled = 1, installedVersionCode = :versionCode, downloadStatus = 'INSTALLED' WHERE packageName = :packageName")
    fun markAsInstalledByPackage(packageName: String, versionCode: Int): Completable

    @Query("UPDATE apps SET isInstalled = 0 WHERE packageName = :packageName")
    fun markAsUninstalled(packageName: String): Completable

    @Query("SELECT COUNT(*) FROM apps WHERE isInstalled = 1")
    fun getInstalledCount(): Single<Int>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM apps WHERE isInstalled = 1")
    fun getTotalInstalledSize(): Single<Long>

    @Query("SELECT * FROM apps WHERE developerName = :developer ORDER BY lastUpdated DESC")
    fun getAppsByDeveloper(developer: String): Flowable<List<AppModel>>

    @Query("SELECT * FROM apps ORDER BY name ASC")
    fun getAllAppsSync(): List<AppModel>

    @Query("SELECT * FROM apps WHERE isFeatured = 1 ORDER BY downloadCount DESC LIMIT :limit")
    fun getFeaturedAppsSync(limit: Int): List<AppModel>

    @Query("SELECT * FROM apps WHERE isNew = 1 ORDER BY releaseDate DESC LIMIT :limit")
    fun getNewAppsSync(limit: Int): List<AppModel>

    @Query("SELECT * FROM apps WHERE isUpdated = 1 ORDER BY lastUpdated DESC LIMIT :limit")
    fun getUpdatedAppsSync(limit: Int): List<AppModel>
}
