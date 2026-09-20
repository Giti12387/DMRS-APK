package com.apps.apkstore.data.repository

import com.apps.apkstore.data.local.AppDao
import com.apps.apkstore.data.local.CategoryDao
import com.apps.apkstore.data.local.DownloadDao
import com.apps.apkstore.data.local.DownloadEntity
import com.apps.apkstore.data.local.InstalledAppDao
import com.apps.apkstore.data.local.InstalledAppEntity
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.AppUpdateInfo
import com.apps.apkstore.data.model.BannerModel
import com.apps.apkstore.data.model.CategoryModel
import com.apps.apkstore.data.model.HomePageData
import com.apps.apkstore.data.model.SearchResult
import com.apps.apkstore.data.remote.SupabaseApiService
import com.apps.apkstore.utils.SupabaseConfig
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import android.content.Context
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val supabaseApi: SupabaseApiService,
    private val appDao: AppDao,
    private val categoryDao: CategoryDao,
    private val downloadDao: DownloadDao,
    private val installedAppDao: InstalledAppDao,
    private val context: Context
) {

    fun getHomeData(): Single<HomePageData> {
        val fromCache = Single.fromCallable {
            val featured = appDao.getFeaturedAppsSync(20)
            val allApps = appDao.getAllAppsSync()
            val newApps = appDao.getNewAppsSync(20)
            val updated = appDao.getUpdatedAppsSync(20)
            val categories = categoryDao.getAllCategoriesSync()

            HomePageData(
                featuredApps = featured,
                topCharts = allApps.take(20),
                newReleases = newApps,
                recommendedApps = updated,
                categories = categories,
                banners = emptyList()
            )
        }.subscribeOn(Schedulers.io())

        val fromNetwork = Single.zip(
            supabaseApi.getFeaturedApps().onErrorReturn { emptyList() },
            supabaseApi.getApps().onErrorReturn { emptyList() },
            supabaseApi.getNewApps().onErrorReturn { emptyList() },
            supabaseApi.getUpdatedApps().onErrorReturn { emptyList() },
            supabaseApi.getCategories().onErrorReturn { emptyList() },
            Single.just(emptyList<BannerModel>())
        ) { featured, allApps, newApps, updated, categories, banners ->
            if (allApps.isNotEmpty()) {
                appDao.insertApps(featured).blockingAwait()
                appDao.insertApps(allApps).blockingAwait()
                appDao.insertApps(newApps).blockingAwait()
                appDao.insertApps(updated).blockingAwait()
                categoryDao.insertCategories(categories).blockingAwait()
            }
            HomePageData(
                featuredApps = featured,
                topCharts = allApps.take(20),
                newReleases = newApps,
                recommendedApps = updated,
                categories = categories,
                banners = banners
            )
        }.subscribeOn(Schedulers.io())

        return fromCache.flatMap { cached ->
            if (cached.topCharts.isNotEmpty() || cached.categories.isNotEmpty()) {
                fromNetwork.onErrorReturn { cached }
            } else {
                fromNetwork
            }
        }.onErrorResumeNext { _ -> fromCache }
    }

    fun getApps(page: Int, limit: Int, category: String?, sort: String?): Single<SearchResult> {
        val source = if (!category.isNullOrBlank()) {
            supabaseApi.getAppsByCategory(category)
        } else {
            supabaseApi.getApps()
        }
        return source.map { apps ->
            val start = (page - 1) * limit
            val paged = if (start < apps.size) apps.subList(start, minOf(start + limit, apps.size)) else emptyList()
            if (page == 1) appDao.insertApps(apps)
            SearchResult(paged, apps.size, page, limit, start + limit < apps.size)
        }.onErrorResumeNext { _ ->
            if (page == 1) {
                getCachedApps(category, limit)
            } else {
                Single.error(Exception("Network error"))
            }
        }
    }

    private fun getCachedApps(category: String?, limit: Int): Single<SearchResult> {
        return if (category.isNullOrBlank()) {
            appDao.getTopCharts(limit).firstOrError()
        } else {
            appDao.getAppsByCategory(category, limit).firstOrError()
        }.map { apps -> SearchResult(apps, apps.size, 1, limit, false) }
    }

    fun getAppDetail(appId: String): Single<AppModel> {
        return supabaseApi.getAppById(appId)
            .doOnSuccess { appDao.insertApp(it) }
            .onErrorResumeNext { _ -> appDao.getAppById(appId) }
    }

    fun searchApps(query: String, page: Int, limit: Int): Single<SearchResult> {
        return if (query.isBlank()) {
            getApps(page, limit, null, null)
        } else {
            supabaseApi.searchApps(query).map { apps ->
                val start = (page - 1) * limit
                val paged = if (start < apps.size) apps.subList(start, minOf(start + limit, apps.size)) else emptyList()
                if (page == 1) appDao.insertApps(apps)
                SearchResult(paged, apps.size, page, limit, start + limit < apps.size)
            }.onErrorResumeNext { _ ->
                appDao.searchApps("%$query%", limit).firstOrError()
                    .map { apps -> SearchResult(apps, apps.size, page, limit, false) }
            }
        }
    }

    fun getCategories(): Single<List<CategoryModel>> {
        return supabaseApi.getCategories()
            .doOnSuccess { categoryDao.insertCategories(it) }
            .onErrorResumeNext { _ -> categoryDao.getAllCategories().firstOrError() }
    }

    fun login(email: String, password: String): Single<Boolean> {
        return supabaseApi.login(email, password)
    }

    fun register(name: String, email: String, password: String): Single<Boolean> {
        return supabaseApi.register(name, email, password)
    }

    fun forgotPassword(email: String): Single<Boolean> {
        return supabaseApi.forgotPassword(email)
    }

    fun startDownload(app: AppModel): Single<DownloadEntity> {
        val download = DownloadEntity(
            id = app.id,
            appId = app.id,
            packageName = app.packageName,
            appName = app.name,
            iconUrl = app.iconUrl,
            downloadUrl = app.downloadUrl,
            fileSize = app.fileSize,
            totalBytes = app.fileSize,
            versionCode = app.versionCode,
            versionName = app.versionName
        )
        return downloadDao.insertDownload(download)
            .andThen(Single.just(download))
    }

    fun getActiveDownloads(): Flowable<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    fun getAllDownloads(): Flowable<List<DownloadEntity>> = downloadDao.getAllDownloads()
    fun pauseDownload(downloadId: String): Completable = downloadDao.pauseDownload(downloadId)
    fun resumeDownload(downloadId: String): Completable = downloadDao.resumeDownload(downloadId)
    fun cancelDownload(downloadId: String): Completable = downloadDao.deleteDownload(downloadId)
    fun clearDownloadHistory(): Completable = downloadDao.clearHistory()

    fun syncInstalledApps(): Completable {
        return Completable.fromAction {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val installedApps = packages.map { pkg ->
                val appInfo = pm.getApplicationInfo(pkg.packageName, 0)
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(pkg.packageName, 0)
                InstalledAppEntity(
                    packageName = pkg.packageName,
                    appName = pkg.applicationInfo.loadLabel(pm).toString(),
                    versionName = pkg.versionName ?: "1.0",
                    versionCode = pkgInfo.versionCode,
                    iconPath = "",
                    installTime = pkg.firstInstallTime,
                    lastUsedTime = 0,
                    source = if (isSystem) "system" else "apk_store",
                    isSystemApp = isSystem
                )
            }.toList()
            installedAppDao.insertInstalledApps(installedApps)
        }.subscribeOn(Schedulers.io())
    }

    fun getInstalledApps(): Flowable<List<InstalledAppEntity>> = installedAppDao.getAllInstalledApps()
    fun getAppsWithUpdates(): Flowable<List<InstalledAppEntity>> = installedAppDao.getAppsWithUpdates()

    fun checkForUpdates(): Single<List<AppUpdateInfo>> = Single.fromCallable {
        val installed = installedAppDao.getAllInstalledAppsSync()
        if (installed.isEmpty()) return@fromCallable emptyList<AppUpdateInfo>()

        val packageNames = installed.filter { it.source != "system" }.map { it.packageName }
        if (packageNames.isEmpty()) return@fromCallable emptyList<AppUpdateInfo>()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val pkgFilter = packageNames.joinToString(",") { "package_name.eq.$it" }
        val url = "${SupabaseConfig.URL}/rest/v1/app_updates?select=*&or=($pkgFilter)&order=version_code.desc"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val json = JSONArray(body)
            val updates = mutableListOf<AppUpdateInfo>()
            val seen = mutableSetOf<String>()

            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val pkg = obj.optString("package_name", "")
                if (pkg in seen) continue
                seen.add(pkg)

                val installedApp = installed.find { it.packageName == pkg } ?: continue
                val latestCode = obj.optInt("version_code", 0)
                if (latestCode > installedApp.versionCode) {
                    updates.add(
                        AppUpdateInfo(
                            id = obj.optString("id", ""),
                            packageName = pkg,
                            versionName = obj.optString("version_name", ""),
                            versionCode = latestCode,
                            apkUrl = obj.optString("apk_url", ""),
                            releaseNotes = obj.optString("release_notes", ""),
                            createdAt = obj.optString("created_at", "")
                        )
                    )
                    installedAppDao.markUpdateAvailable(pkg, latestCode, obj.optString("version_name", "")).blockingAwait()
                }
            }
            updates
        } catch (e: Exception) {
            emptyList()
        }
    }.subscribeOn(Schedulers.io())
}
