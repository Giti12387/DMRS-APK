package com.apps.apkstore.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.apps.apkstore.data.model.DownloadStatus

class Converters {

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus?): String {
        return status?.name ?: DownloadStatus.NONE.name
    }

    @TypeConverter
    fun toDownloadStatus(status: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(status)
        } catch (e: Exception) {
            DownloadStatus.NONE
        }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }
}