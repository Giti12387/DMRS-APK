package com.apps.apkstore.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Int,
    val iconPath: String = "",
    val installTime: Long = System.currentTimeMillis(),
    val lastUsedTime: Long = 0,
    val source: String = "apk_store",
    val isSystemApp: Boolean = false,
    val isUpdated: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersionCode: Int = 0,
    val latestVersionName: String = ""
)