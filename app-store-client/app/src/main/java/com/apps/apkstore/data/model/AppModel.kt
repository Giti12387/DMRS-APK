package com.apps.apkstore.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "apps")
data class AppModel(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("version_name")
    val versionName: String,

    @SerializedName("version_code")
    val versionCode: Int,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("short_description")
    val shortDescription: String = "",

    @SerializedName("icon_url")
    val iconUrl: String = "",

    @SerializedName("banner_url")
    val bannerUrl: String = "",

    @SerializedName("screenshot_urls")
    val screenshotUrls: List<String> = emptyList(),

    @SerializedName("developer_name")
    val developerName: String = "",

    @SerializedName("developer_email")
    val developerEmail: String = "",

    @SerializedName("developer_website")
    val developerWebsite: String = "",

    @SerializedName("category")
    val category: String = "",

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName("file_size")
    val fileSize: Long = 0,

    @SerializedName("download_url")
    val downloadUrl: String = "",

    @SerializedName("min_sdk")
    val minSdk: Int = 21,

    @SerializedName("target_sdk")
    val targetSdk: Int = 34,

    @SerializedName("permissions")
    val permissions: List<String> = emptyList(),

    @SerializedName("whats_new")
    val whatsNew: String = "",

    @SerializedName("release_date")
    val releaseDate: String = "",

    @SerializedName("last_updated")
    val lastUpdated: String = "",

    @SerializedName("rating")
    val rating: Float = 0f,

    @SerializedName("review_count")
    val reviewCount: Int = 0,

    @SerializedName("download_count")
    val downloadCount: Long = 0,

    @SerializedName("is_featured")
    val isFeatured: Boolean = false,

    @SerializedName("is_new")
    val isNew: Boolean = false,

    @SerializedName("is_updated")
    val isUpdated: Boolean = false,

    @SerializedName("signature_hash")
    val signatureHash: String = "",

    @SerializedName("signing_certificate")
    val signingCertificate: String = "",

    @SerializedName("is_verified")
    val isVerified: Boolean = false,

    @SerializedName("is_share_only")
    val isShareOnly: Boolean = false,

    @SerializedName("changelog")
    val changelog: String = "",

    // Local fields
    @SerializedName("local_path")
    val localPath: String = "",

    @SerializedName("download_progress")
    val downloadProgress: Float = 0f,

    @SerializedName("download_status")
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,

    @SerializedName("installed_version_code")
    val installedVersionCode: Int = 0,

    @SerializedName("is_installed")
    val isInstalled: Boolean = false
) : Parcelable {
    companion object {
        fun createEmpty(): AppModel = AppModel(
            id = "",
            packageName = "",
            name = "",
            versionName = "",
            versionCode = 0
        )
    }

    fun getFormattedSize(): String {
        return formatFileSize(fileSize)
    }

    fun getFormattedRating(): String {
        return String.format("%.1f", rating)
    }

    fun getFormattedDownloads(): String {
        return when {
            downloadCount >= 1_000_000_000 -> String.format("%.1fB", downloadCount / 1_000_000_000.0)
            downloadCount >= 1_000_000 -> String.format("%.1fM", downloadCount / 1_000_000.0)
            downloadCount >= 1_000 -> String.format("%.1fK", downloadCount / 1_000.0)
            else -> downloadCount.toString()
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0)
            size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0)
            size >= 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> "$size B"
        }
    }

    fun hasUpdate(): Boolean {
        return versionCode > installedVersionCode && isInstalled
    }

    fun copyWithDownloadProgress(progress: Float, status: DownloadStatus): AppModel {
        return copy(downloadProgress = progress, downloadStatus = status)
    }

    fun copyWithLocalPath(path: String): AppModel {
        return copy(localPath = path)
    }

    fun copyAsInstalled(versionCode: Int): AppModel {
        return copy(
            isInstalled = true,
            installedVersionCode = versionCode,
            downloadStatus = DownloadStatus.COMPLETED
        )
    }
}

enum class DownloadStatus {
    NONE,
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    INSTALLING,
    INSTALLED,
    CANCELLED
}

@Parcelize
@Entity(tableName = "categories")
data class CategoryModel(
    @PrimaryKey
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("icon")
    val icon: String = "",
    @SerializedName("app_count")
    val appCount: Int = 0,
    @SerializedName("order")
    val order: Int = 0
) : Parcelable

@Parcelize
@Entity(tableName = "reviews")
data class ReviewModel(
    @PrimaryKey
    @SerializedName("id")
    val id: String,
    @SerializedName("app_id")
    val appId: String,
    @SerializedName("user_name")
    val userName: String,
    @SerializedName("user_avatar")
    val userAvatar: String = "",
    @SerializedName("rating")
    val rating: Float,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("comment")
    val comment: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("helpful_count")
    val helpfulCount: Int = 0,
    @SerializedName("developer_reply")
    val developerReply: String = ""
) : Parcelable

@Parcelize
data class AppUpdateInfo(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("package_name")
    val packageName: String,
    @SerializedName("version_name")
    val versionName: String = "",
    @SerializedName("version_code")
    val versionCode: Int = 0,
    @SerializedName("apk_url")
    val apkUrl: String = "",
    @SerializedName("release_notes")
    val releaseNotes: String = "",
    @SerializedName("created_at")
    val createdAt: String = ""
) : Parcelable

data class SearchResult(
    val apps: List<AppModel>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)

data class HomePageData(
    val featuredApps: List<AppModel> = emptyList(),
    val topCharts: List<AppModel> = emptyList(),
    val newReleases: List<AppModel> = emptyList(),
    val recommendedApps: List<AppModel> = emptyList(),
    val categories: List<CategoryModel> = emptyList(),
    val banners: List<BannerModel> = emptyList()
)

@Parcelize
data class BannerModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("subtitle")
    val subtitle: String = "",
    @SerializedName("image_url")
    val imageUrl: String,
    @SerializedName("action_type")
    val actionType: String = "",
    @SerializedName("action_value")
    val actionValue: String = "",
    @SerializedName("order")
    val order: Int = 0
) : Parcelable