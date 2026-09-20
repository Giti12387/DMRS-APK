package com.apps.apkstore.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.apps.apkstore.data.local.PreferencesManager
import com.apps.apkstore.APKStoreApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: PreferencesManager
) : ViewModel() {

    fun clearCache() {
        // Clear app cache
        val context = APKStoreApplication.instance
        val cacheDir = context.cacheDir
        deleteRecursive(cacheDir)
        
        val externalCacheDir = context.externalCacheDir
        externalCacheDir?.let { deleteRecursive(it) }
        
        prefs.cacheSize = 0
    }

    private fun deleteRecursive(file: java.io.File) {
        file.listFiles()?.forEach { deleteRecursive(it) }
        file.delete()
    }

    fun openUnknownSourcesSettings() {
        val context = APKStoreApplication.instance
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getCacheSize(): Long {
        val context = APKStoreApplication.instance
        var size = 0L
        context.cacheDir.let { size += getDirSize(it) }
        context.externalCacheDir?.let { size += getDirSize(it) }
        return size
    }

    private fun getDirSize(file: java.io.File): Long {
        var size = 0L
        file.listFiles()?.forEach { f ->
            if (f.isDirectory) size += getDirSize(f) else size += f.length()
        }
        return size
    }
}