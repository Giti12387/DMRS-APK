package com.apps.apkstore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.model.DownloadStatus
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownload(download: DownloadEntity): Completable

    @Update
    fun updateDownload(download: DownloadEntity): Completable

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadById(id: String): Single<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE appId = :appId")
    fun getDownloadByAppId(appId: String): Single<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'PENDING', 'PAUSED')")
    fun getActiveDownloads(): Flowable<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY startTime DESC")
    fun getAllDownloads(): Flowable<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY endTime DESC")
    fun getCompletedDownloads(): Flowable<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'FAILED' ORDER BY endTime DESC")
    fun getFailedDownloads(): Flowable<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE id = :id")
    fun deleteDownload(id: String): Completable

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    fun clearHistory(): Completable

    @Query("UPDATE downloads SET progress = :progress, status = :status, downloadedBytes = :bytes, speed = :speed WHERE id = :id")
    fun updateProgress(id: String, progress: Float, status: String, bytes: Long, speed: Long): Completable

    @Query("UPDATE downloads SET localPath = :path, status = 'COMPLETED', endTime = :time, progress = 100 WHERE id = :id")
    fun markCompleted(id: String, path: String, time: Long): Completable

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error, endTime = :time WHERE id = :id")
    fun markFailed(id: String, error: String, time: Long): Completable

    @Query("UPDATE downloads SET status = 'PAUSED' WHERE id = :id")
    fun pauseDownload(id: String): Completable

    @Query("UPDATE downloads SET status = 'DOWNLOADING' WHERE id = :id")
    fun resumeDownload(id: String): Completable
}
