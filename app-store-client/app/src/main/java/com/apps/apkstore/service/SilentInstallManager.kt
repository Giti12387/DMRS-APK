package com.apps.apkstore.service

import android.content.Context
import android.util.Log
import com.apps.apkstore.service.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

object SilentInstallManager {

    private const val TAG = "SilentInstallManager"

    enum class InstallMethod {
        ROOT,
        WIRELESS_ADB,
        SYSTEM
    }

    data class InstallResult(
        val method: InstallMethod,
        val success: Boolean,
        val message: String = ""
    )

    suspend fun install(
        context: Context,
        apkFile: File,
        packageName: String,
        preferredMethod: InstallMethod
    ): InstallResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "install: preferredMethod=$preferredMethod, pkg=$packageName")
        when (preferredMethod) {
            InstallMethod.ROOT -> {
                val result = tryRootInstall(apkFile)
                if (result.success) return@withContext result
                Log.w(TAG, "Root failed, trying ADB")
                val adbResult = tryAdbInstall(apkFile)
                if (adbResult.success) return@withContext adbResult
                InstallResult(InstallMethod.SYSTEM, false, "Root and ADB unavailable")
            }
            InstallMethod.WIRELESS_ADB -> {
                val result = tryAdbInstall(apkFile)
                if (result.success) return@withContext result
                Log.w(TAG, "ADB failed, trying root")
                val rootResult = tryRootInstall(apkFile)
                if (rootResult.success) return@withContext rootResult
                InstallResult(InstallMethod.SYSTEM, false, "ADB unavailable")
            }
            InstallMethod.SYSTEM -> {
                InstallResult(InstallMethod.SYSTEM, false, "Use PackageInstaller")
            }
        }
    }

    private fun tryRootInstall(file: File): InstallResult {
        return try {
            Log.d(TAG, "tryRootInstall: attempting")
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("pm install -r -d -g \"${file.absolutePath}\"\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            Log.d(TAG, "tryRootInstall: exitCode=$exitCode")
            if (exitCode == 0) InstallResult(InstallMethod.ROOT, true, "Installed via root")
            else InstallResult(InstallMethod.ROOT, false, "Root install failed")
        } catch (e: Exception) {
            Log.e(TAG, "tryRootInstall: ${e.message}")
            InstallResult(InstallMethod.ROOT, false, e.message ?: "Root not available")
        }
    }

    private fun tryAdbInstall(file: File): InstallResult {
        return try {
            Log.d(TAG, "tryAdbInstall: connecting to 127.0.0.1:5555")
            val prefs = com.apps.apkstore.APKStoreApplication.instance
                .getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
            val savedHost = prefs.getString("adb_host", null)
            val savedPort = prefs.getInt("adb_port", 0)
            if (savedHost == null || savedPort == 0) {
                return InstallResult(InstallMethod.WIRELESS_ADB, false, "No ADB pairing saved")
            }
            AdbClient.create(savedHost, savedPort).use { adb ->
                val result = adb.install(file)
                Log.d(TAG, "tryAdbInstall: result=$result")
                if (result) InstallResult(InstallMethod.WIRELESS_ADB, true, "Installed via ADB")
                else InstallResult(InstallMethod.WIRELESS_ADB, false, "ADB install failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "tryAdbInstall: ${e.message}")
            InstallResult(InstallMethod.WIRELESS_ADB, false, e.message ?: "ADB connection failed")
        }
    }

    suspend fun pairDevice(host: String, port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "pairDevice: host=$host, port=$port")
            val result = AdbClient.pair(host, port, code)
            if (result) {
                savePairing(host, port, code)
                Log.d(TAG, "pairDevice: success")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "pairDevice failed: ${e.message}")
            false
        }
    }

    suspend fun checkAdbConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = com.apps.apkstore.APKStoreApplication.instance
                .getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
            val host = prefs.getString("adb_host", null) ?: return@withContext false
            val port = prefs.getInt("adb_port", 0)
            if (port == 0) return@withContext false
            AdbClient.create(host, port).use { adb ->
                adb.shell("echo ok").trim() == "ok"
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun savePairing(host: String, port: Int, code: String) {
        try {
            val context = com.apps.apkstore.APKStoreApplication.instance
            context.getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("adb_host", host)
                .putInt("adb_port", port)
                .putString("adb_code", code)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "savePairing: ${e.message}")
        }
    }

    fun getMethodFromString(value: String): InstallMethod = when (value) {
        "root" -> InstallMethod.ROOT
        "silent" -> InstallMethod.WIRELESS_ADB
        else -> InstallMethod.SYSTEM
    }
}
