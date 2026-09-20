package com.apps.apkstore.utils

import android.content.Context
import org.json.JSONObject

object SupabaseConfig {
    const val URL = "https://tarnljxakeomtcjleofj.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1OTY3MjksImV4cCI6MjEwNDE3MjcyOX0.EjLS31HVNjrM0bk9dmTRi96Vay33oOWP6H_56_BkzIg"
    const val SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODU5NjcyOSwiZXhwIjoyMTA0MTcyNzI5fQ.Gpxx8M04SrccOD7EFFBIX3TPjxqjwQ3vG6Re4X_vI40"

    const val DEEP_LINK_DOMAIN = "https://apk-store-topaz.vercel.app"
    const val DEEP_LINK_PATH = "/app/"
    const val CONFIG_URL = "$DEEP_LINK_DOMAIN/admin_config.json"

    private var authConfig: AuthConfig? = null

    data class AuthConfig(
        val url: String,
        val anonKey: String,
        val serviceKey: String
    )

    fun getAppUrl(appId: String): String = "$DEEP_LINK_DOMAIN${DEEP_LINK_PATH}$appId"

    fun getShareUrl(appId: String, appName: String = "app", version: String = "1.0"): String {
        val safeName = appName.replace("\\s+".toRegex(), "-").lowercase().replace("[^a-z0-9-]".toRegex(), "")
        return "$DEEP_LINK_DOMAIN/$safeName/v$version/share?$appId"
    }

    fun initAuthConfig(context: Context) {
        try {
            val prefs = context.getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
            val configJson = prefs.getString("auth_config", null)
            if (configJson != null) {
                val obj = JSONObject(configJson)
                authConfig = AuthConfig(
                    url = obj.optString("url", URL),
                    anonKey = obj.optString("anon_key", ANON_KEY),
                    serviceKey = obj.optString("service_key", SERVICE_KEY)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveAuthConfig(context: Context, url: String, anonKey: String, serviceKey: String) {
        val obj = JSONObject().apply {
            put("url", url)
            put("anon_key", anonKey)
            put("service_key", serviceKey)
        }
        context.getSharedPreferences("zoro_app_store_prefs", Context.MODE_PRIVATE)
            .edit().putString("auth_config", obj.toString()).apply()
        authConfig = AuthConfig(url, anonKey, serviceKey)
    }

    fun getAuthUrl(): String = authConfig?.url ?: URL
    fun getAuthAnonKey(): String = authConfig?.anonKey ?: ANON_KEY
    fun getAuthServiceKey(): String = authConfig?.serviceKey ?: SERVICE_KEY

    fun isCustomAuthConfig(): Boolean = authConfig != null
}
