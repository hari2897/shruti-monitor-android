package com.shrutimonitor.app.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Information regarding an available app update.
 */
data class UpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val htmlUrl: String
)

/**
 * Status of checking for app updates.
 */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckStatus
    data class UpToDate(val currentVersionName: String) : UpdateCheckStatus
    data class Error(val message: String) : UpdateCheckStatus
}

/**
 * Real-time status of the in-app APK download.
 */
sealed interface DownloadStatus {
    data object NotStarted : DownloadStatus
    data class Downloading(
        val progress: Float, // 0.0f to 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus
    data class ReadyToInstall(val apkFile: File) : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}

/**
 * GitHub Releases API Response DTO.
 */
@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

/**
 * GitHub Release Asset DTO.
 */
@Serializable
internal data class GitHubReleaseAssetDto(
    val name: String,
    val size: Long = 0L,
    @SerialName("browser_download_url") val downloadUrl: String
)
