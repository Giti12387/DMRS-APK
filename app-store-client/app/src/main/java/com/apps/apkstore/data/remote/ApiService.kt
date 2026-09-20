package com.apps.apkstore.data.remote

import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.AppUpdateInfo
import com.apps.apkstore.data.model.BannerModel
import com.apps.apkstore.data.model.CategoryModel
import com.apps.apkstore.data.model.HomePageData
import com.apps.apkstore.data.model.ReviewModel
import com.apps.apkstore.data.model.SearchResult
import io.reactivex.rxjava3.core.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    @GET("api/v1/home")
    fun getHomeData(): Single<HomePageData>

    @GET("api/v1/apps")
    fun getApps(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null
    ): Single<SearchResult>

    @GET("api/v1/apps/{id}")
    fun getAppDetail(@Path("id") id: String): Single<AppModel>

    @GET("api/v1/apps/search")
    fun searchApps(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Single<SearchResult>

    @GET("api/v1/categories")
    fun getCategories(): Single<List<CategoryModel>>

    @GET("api/v1/apps/{id}/reviews")
    fun getReviews(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Single<List<ReviewModel>>

    @POST("api/v1/apps/{id}/reviews")
    fun postReview(
        @Path("id") id: String,
        @Body review: ReviewRequest
    ): Single<ReviewModel>

    @GET("api/v1/apps/{id}/updates")
    fun checkUpdate(@Path("id") id: String): Single<AppUpdateInfo>

    @GET("api/v1/apps/updates")
    fun checkAllUpdates(@Body request: UpdateCheckRequest): Single<List<AppUpdateInfo>>

    @GET("api/v1/banners")
    fun getBanners(): Single<List<BannerModel>>

    @GET("api/v1/apps/{id}/download")
    fun getDownloadUrl(@Path("id") id: String): Single<DownloadUrlResponse>

    @POST("api/v1/apps/upload")
    fun uploadApp(
        @Part("file") file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody
    ): Single<AppModel>

    @GET("api/v1/stats")
    fun getStats(): Single<StatsResponse>

    @POST("api/v1/apps/{id}/download-count")
    fun incrementDownloadCount(@Path("id") id: String): Single<Unit>

    @GET("api/v1/developer/apps")
    fun getDeveloperApps(@Query("developer_id") developerId: String): Single<List<AppModel>>
}

data class ReviewRequest(
    val rating: Float,
    val title: String,
    val comment: String
)

data class UpdateCheckRequest(
    val installedApps: List<InstalledAppInfo>
)

data class InstalledAppInfo(
    val packageName: String,
    val versionCode: Int
)

data class DownloadUrlResponse(
    val url: String,
    val expiresAt: String,
    val signatureHash: String
)

data class StatsResponse(
    val totalApps: Int,
    val totalDownloads: Long,
    val totalDevelopers: Int,
    val totalCategories: Int
)