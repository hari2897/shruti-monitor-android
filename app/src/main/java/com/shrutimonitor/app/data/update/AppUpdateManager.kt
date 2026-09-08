package com.shrutimonitor.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility for semantic version comparison.
 */
object VersionComparator {
    /**
     * Compares two version strings (e.g., "1.1.2" vs "1.1.1" or "v1.1.2").
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

/**
 * Manager handling GitHub Releases update checking, downloading, and package installation.
 */
class AppUpdateManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val GITHUB_REPO = "hari2897/shruti-monitor-android"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 15000
    }

    /**
     * Queries GitHub Releases API to check for updates against [currentVersion].
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateCheckStatus = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "ShrutiMonitorAndroid/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckStatus.Error("GitHub API returned HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseDto = json.decodeFromString<GitHubReleaseDto>(responseBody)

            val apkAsset = releaseDto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val remoteVersion = releaseDto.tagName.trim().removePrefix("v").removePrefix("V")

            if (VersionComparator.isNewer(releaseDto.tagName, currentVersion)) {
                if (apkAsset == null) {
                    return@withContext UpdateCheckStatus.Error("New version found (${releaseDto.tagName}) but no APK asset is attached.")
                }

                val updateInfo = UpdateInfo(
                    currentVersionName = currentVersion,
                    latestVersionName = remoteVersion,
                    releaseTitle = releaseDto.name ?: "Version ${releaseDto.tagName}",
                    releaseNotes = releaseDto.body ?: "No changelog provided.",
                    downloadUrl = apkAsset.downloadUrl,
                    apkSize = apkAsset.size,
                    publishedAt = releaseDto.publishedAt ?: "",
                    htmlUrl = releaseDto.htmlUrl ?: "https://github.com/$GITHUB_REPO/releases/latest"
                )
                UpdateCheckStatus.UpdateAvailable(updateInfo)
            } else {
                UpdateCheckStatus.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            UpdateCheckStatus.Error(e.message ?: "Failed to connect to update server.")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Downloads the APK file from [updateInfo] and emits real-time download progress.
     */
    fun downloadApk(updateInfo: UpdateInfo): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.Downloading(0.0f, 0L, updateInfo.apkSize))

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean up previous temporary update downloads
        updatesDir.listFiles()?.forEach { it.delete() }

        val destinationFile = File(updatesDir, "ShrutiMonitor-v${updateInfo.latestVersionName}.apk")

        var currentUrl = updateInfo.downloadUrl
        var totalBytes = updateInfo.apkSize
        var connection: HttpURLConnection? = null

        try {
            // Handle HTTP 301/302/307 redirects commonly returned by GitHub release downloads
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "ShrutiMonitorAndroid/${updateInfo.currentVersionName}")
                    instanceFollowRedirects = true
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl != null) {
                        currentUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val conn = connection ?: throw IllegalStateException("Could not establish connection.")
            val contentLength = conn.contentLengthLong
            if (contentLength > 0) {
                totalBytes = contentLength
            }

            conn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastEmittedProgress = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progressFraction = if (totalBytes > 0) {
                            (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0.0f, 1.0f)
                        } else {
                            0.0f
                        }

                        // Emit update if progress increased by at least 1%
                        val progressPercent = (progressFraction * 100).toInt()
                        if (progressPercent > lastEmittedProgress || totalRead == totalBytes) {
                            lastEmittedProgress = progressPercent
                            emit(DownloadStatus.Downloading(progressFraction, totalRead, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            emit(DownloadStatus.ReadyToInstall(destinationFile))
        } catch (e: Exception) {
            destinationFile.delete()
            emit(DownloadStatus.Failed(e.message ?: "Download failed."))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Checks whether the app has permission to request package installations.
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Launches the system settings screen requesting user permission to install packages from this app.
     */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Launches the system package installer for the downloaded [apkFile].
     */
    fun installApk(apkFile: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Opens a web URL (e.g. GitHub Releases page or direct download) in an external browser.
     */
    fun openWebUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
