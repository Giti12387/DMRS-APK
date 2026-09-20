package com.apps.apkstore.data.local

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREF_NAME = "zoro_app_store_prefs"
        private var instance: PreferencesManager? = null

        @JvmStatic
        fun getInstance(context: Context): PreferencesManager {
            if (instance == null) {
                instance = PreferencesManager(context.applicationContext)
            }
            return instance!!
        }
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    fun putBoolean(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPrefs.getBoolean(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        sharedPrefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPrefs.getInt(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        sharedPrefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPrefs.getLong(key, defaultValue)
    }

    fun remove(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }

    fun clear() {
        sharedPrefs.edit().clear().apply()
    }

    var isAutoUpdateEnabled: Boolean
        get() = getBoolean("auto_update", false)
        set(value) = putBoolean("auto_update", value)

    var isSilentAutoUpdateEnabled: Boolean
        get() = getBoolean("silent_auto_update", false)
        set(value) = putBoolean("silent_auto_update", value)

    var downloadPreference: String
        get() = getString("download_preference", "wifi_only")
        set(value) = putString("download_preference", value)

    var downloadNotificationsEnabled: Boolean
        get() = getBoolean("download_notifications", true)
        set(value) = putBoolean("download_notifications", value)

    var updateNotificationsEnabled: Boolean
        get() = getBoolean("update_notifications", true)
        set(value) = putBoolean("update_notifications", value)

    var biometricAuthEnabled: Boolean
        get() = getBoolean("biometric_auth", false)
        set(value) = putBoolean("biometric_auth", value)

    var lastUpdateCheck: Long
        get() = getLong("last_update_check", 0)
        set(value) = putLong("last_update_check", value)

    var cacheSize: Long
        get() = getLong("cache_size", 0)
        set(value) = putLong("cache_size", value)

    var downloadLocation: String
        get() = getString("download_location", "internal")
        set(value) = putString("download_location", value)

    var themeMode: Int
        get() = getInt("theme_mode", 0)
        set(value) = putInt("theme_mode", value)

    var language: String
        get() = getString("language", "system")
        set(value) = putString("language", value)

    var isFirstLaunch: Boolean
        get() = getBoolean("first_launch", true)
        set(value) = putBoolean("first_launch", value)

    var acceptedTos: Boolean
        get() = getBoolean("accepted_tos", false)
        set(value) = putBoolean("accepted_tos", value)

    var installMethod: String
        get() = getString("install_method", "")
        set(value) = putString("install_method", value)

    var installMethodChosen: Boolean
        get() = getBoolean("install_method_chosen", false)
        set(value) = putBoolean("install_method_chosen", value)
}