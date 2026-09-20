package com.apps.apkstore.utils

import android.content.Context
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ConfigManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    var lastFetchError: String? = null
        private set

    var isConfigActive: Boolean = false
        private set

    fun fetchAndApplyConfig(context: Context) {
        try {
            val url = "${SupabaseConfig.URL}/rest/v1/supabase_accounts?select=*&order=created_at"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    lastFetchError = "Cannot reach server"
                    isConfigActive = false
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            lastFetchError = "Server returned ${response.code}"
                            isConfigActive = false
                            return
                        }
                        val body = response.body?.string() ?: return
                        val arr = JSONArray(body)

                        if (arr.length() == 0) {
                            lastFetchError = "No accounts configured"
                            isConfigActive = false
                            return
                        }

                        for (i in 0 until arr.length()) {
                            val acc = arr.getJSONObject(i)
                            if (acc.optBoolean("is_active", false)) {
                                val supUrl = acc.optString("sup_url", "")
                                val anonKey = acc.optString("sup_anon_key", "")
                                val serviceKey = acc.optString("sup_service_key", "")
                                if (supUrl.isNotBlank() && anonKey.isNotBlank()) {
                                    SupabaseConfig.saveAuthConfig(context, supUrl, anonKey, serviceKey)
                                    isConfigActive = true
                                    lastFetchError = null
                                }
                                return
                            }
                        }

                        lastFetchError = "No active account"
                        isConfigActive = false
                    } catch (e: Exception) {
                        lastFetchError = "Parse error"
                        isConfigActive = false
                    }
                }
            })
        } catch (e: Exception) {
            lastFetchError = "Fetch error"
            isConfigActive = false
        }
    }
}
