package com.apps.apkstore.data.remote

import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.CategoryModel
import com.apps.apkstore.utils.SupabaseConfig
import io.reactivex.rxjava3.core.Single
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .addHeader("Accept", "application/json")
            .build()
    }

    fun getApps(): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?select=*&or=(is_share_only.is.null,is_share_only.eq.false)&order=created_at.desc"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    emitter.onError(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) {
                            apps.add(parseApp(json.getJSONObject(i)))
                        }
                        emitter.onSuccess(apps)
                    } catch (e: Exception) {
                        emitter.onError(e)
                    }
                }
            })
        }
    }

    fun getFeaturedApps(): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?is_featured=eq.true&or=(is_share_only.is.null,is_share_only.eq.false)&order=created_at.desc&limit=20"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) apps.add(parseApp(json.getJSONObject(i)))
                        emitter.onSuccess(apps)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun getNewApps(): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?is_new=eq.true&or=(is_share_only.is.null,is_share_only.eq.false)&order=created_at.desc&limit=20"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) apps.add(parseApp(json.getJSONObject(i)))
                        emitter.onSuccess(apps)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun getUpdatedApps(): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?is_updated=eq.true&or=(is_share_only.is.null,is_share_only.eq.false)&order=last_updated.desc&limit=20"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) apps.add(parseApp(json.getJSONObject(i)))
                        emitter.onSuccess(apps)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun getAppById(id: String): Single<AppModel> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?id=eq.$id&select=*"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        if (json.length() > 0) emitter.onSuccess(parseApp(json.getJSONObject(0)))
                        else emitter.onError(Exception("App not found"))
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun searchApps(query: String): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?or=(name.ilike.*$query*,package_name.ilike.*$query*,category.ilike.*$query*)&or=(is_share_only.is.null,is_share_only.eq.false)&order=download_count.desc"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) apps.add(parseApp(json.getJSONObject(i)))
                        emitter.onSuccess(apps)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun getAppsByCategory(category: String): Single<List<AppModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/apps?category=eq.$category&or=(is_share_only.is.null,is_share_only.eq.false)&order=download_count.desc"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val apps = mutableListOf<AppModel>()
                        for (i in 0 until json.length()) apps.add(parseApp(json.getJSONObject(i)))
                        emitter.onSuccess(apps)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun getCategories(): Single<List<CategoryModel>> {
        return Single.create { emitter ->
            val url = "${SupabaseConfig.URL}/rest/v1/categories?select=*&order=order_index.asc"
            val request = buildRequest(url)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val cats = mutableListOf<CategoryModel>()
                        for (i in 0 until json.length()) {
                            val obj = json.getJSONObject(i)
                            cats.add(CategoryModel(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", ""),
                                icon = obj.optString("icon", ""),
                                appCount = obj.optInt("app_count", 0),
                                order = obj.optInt("order_index", 0)
                            ))
                        }
                        emitter.onSuccess(cats)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun login(email: String, password: String): Single<Boolean> {
        return Single.create { emitter ->
            val deviceId = android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL + "_" + android.os.Build.SERIAL
            val authUrl = SupabaseConfig.getAuthUrl()
            val authKey = SupabaseConfig.getAuthAnonKey()
            val url = "$authUrl/rest/v1/user_accounts?email=eq.$email&password_hash=eq.$password&is_active=eq.true&select=id"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", authKey)
                .addHeader("Authorization", "Bearer $authKey")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        val success = json.length() > 0
                        if (success) {
                            updateDeviceId(email, deviceId) { }
                        }
                        emitter.onSuccess(success)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    fun updateDeviceId(email: String, deviceId: String, callback: (Boolean) -> Unit) {
        val authUrl = SupabaseConfig.getAuthUrl()
        val serviceKey = SupabaseConfig.getAuthServiceKey()
        val jsonBody = JSONObject().apply { put("device_id", deviceId) }
        val body = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$authUrl/rest/v1/user_accounts?email=eq.$email")
            .addHeader("apikey", serviceKey)
            .addHeader("Authorization", "Bearer $serviceKey")
            .addHeader("Content-Type", "application/json")
            .put(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false) }
            override fun onResponse(call: Call, response: Response) { callback(response.isSuccessful) }
        })
    }

    fun register(name: String, email: String, password: String): Single<Boolean> {
        return Single.create { emitter ->
            val authUrl = SupabaseConfig.getAuthUrl()
            val authKey = SupabaseConfig.getAuthAnonKey()
            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password_hash", password)
                put("display_name", name)
                put("is_active", true)
                put("device_id", android.os.Build.MANUFACTURER + "_" + android.os.Build.MODEL)
                put("role", "user")
            }
            val body = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val url = "$authUrl/rest/v1/user_accounts"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", authKey)
                .addHeader("Authorization", "Bearer $authKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    emitter.onSuccess(response.isSuccessful || response.code == 409)
                }
            })
        }
    }

    fun forgotPassword(email: String): Single<Boolean> {
        return Single.create { emitter ->
            val authUrl = SupabaseConfig.getAuthUrl()
            val authKey = SupabaseConfig.getAuthAnonKey()
            val url = "$authUrl/rest/v1/user_accounts?email=eq.$email&select=id"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", authKey)
                .addHeader("Authorization", "Bearer $authKey")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { emitter.onError(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: "[]"
                        val json = JSONArray(body)
                        emitter.onSuccess(json.length() > 0)
                    } catch (e: Exception) { emitter.onError(e) }
                }
            })
        }
    }

    private fun parseApp(obj: JSONObject): AppModel {
        return AppModel(
            id = obj.optString("id", ""),
            packageName = obj.optString("package_name", ""),
            name = obj.optString("name", ""),
            versionName = obj.optString("version_name", ""),
            versionCode = obj.optInt("version_code", 0),
            description = obj.optString("description", ""),
            shortDescription = obj.optString("short_description", ""),
            iconUrl = obj.optString("icon_url", ""),
            bannerUrl = obj.optString("banner_url", ""),
            screenshotUrls = parseJsonArray(obj.optJSONArray("screenshot_urls")),
            developerName = obj.optString("developer_name", ""),
            developerEmail = obj.optString("developer_email", ""),
            developerWebsite = obj.optString("developer_website", ""),
            category = obj.optString("category", ""),
            tags = parseJsonArray(obj.optJSONArray("tags")),
            fileSize = obj.optLong("file_size", 0),
            downloadUrl = obj.optString("download_url", ""),
            minSdk = obj.optInt("min_sdk", 21),
            targetSdk = obj.optInt("target_sdk", 34),
            permissions = parseJsonArray(obj.optJSONArray("permissions")),
            whatsNew = obj.optString("whats_new", ""),
            releaseDate = obj.optString("release_date", ""),
            lastUpdated = obj.optString("last_updated", ""),
            rating = obj.optDouble("rating", 0.0).toFloat(),
            reviewCount = obj.optInt("review_count", 0),
            downloadCount = obj.optLong("download_count", 0),
            isFeatured = obj.optBoolean("is_featured", false),
            isNew = obj.optBoolean("is_new", false),
            isUpdated = obj.optBoolean("is_updated", false),
            signatureHash = obj.optString("signature_hash", ""),
            signingCertificate = obj.optString("signing_certificate", ""),
            isVerified = obj.optBoolean("is_verified", false),
            isShareOnly = obj.optBoolean("is_share_only", false),
            changelog = obj.optString("changelog", "")
        )
    }

    private fun parseJsonArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }
}
