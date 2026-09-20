package com.apps.apkstore.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.apps.apkstore.data.model.DownloadStatus

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val appId: String,
    val packageName: String,
    val appName: String,
    val iconUrl: String,
    val downloadUrl: String,
    val fileSize: Long,
    val localPath: String = "",
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.NONE,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speed: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val errorMessage: String = "",
    val versionCode: Int = 0,
    val versionName: String = ""
)