package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val releaseNotes: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val release: GitHubReleaseInfo) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data object Checking : UpdateDownloadState
    data class Available(val release: GitHubReleaseInfo) : UpdateDownloadState
    data class Downloading(
        val release: GitHubReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File, val release: GitHubReleaseInfo) : UpdateDownloadState
    data class Error(val message: String, val release: GitHubReleaseInfo? = null) : UpdateDownloadState
}

object UpdateManager {

    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/charfeddine23/DNS/releases/latest"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Fetches the latest GitHub release and compares its tag_name against currentVersionName.
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DNSPulse-Android")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        return@withContext when (response.code) {
                            403, 429 -> UpdateCheckResult.Error(
                                "GitHub API rate limit reached. Please try again later."
                            )
                            404 -> UpdateCheckResult.Error(
                                "No published releases found on GitHub yet."
                            )
                            else -> UpdateCheckResult.Error(
                                "Failed to check updates (HTTP ${response.code})."
                            )
                        }
                    }

                    if (responseBody.isBlank()) {
                        return@withContext UpdateCheckResult.Error("Empty response from GitHub.")
                    }

                    parseAndCompareRelease(responseBody, currentVersionName)
                }
            } catch (_: UnknownHostException) {
                UpdateCheckResult.Error("No internet connection. Please check your network.")
            } catch (_: ConnectException) {
                UpdateCheckResult.Error("Unable to reach GitHub servers.")
            } catch (_: SocketTimeoutException) {
                UpdateCheckResult.Error("Connection timed out while checking for updates.")
            } catch (e: IOException) {
                UpdateCheckResult.Error("Network error: ${e.localizedMessage ?: "Unknown IO error"}")
            } catch (e: Exception) {
                UpdateCheckResult.Error("Unexpected error: ${e.localizedMessage ?: "Unknown error"}")
            }
        }

    internal fun parseAndCompareRelease(
        jsonString: String,
        currentVersionName: String
    ): UpdateCheckResult {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                return UpdateCheckResult.Error("Release tag is missing in GitHub response.")
            }

            val remoteNormalized = normalizeVersion(tagName)
            if (!isNewerVersion(remoteNormalized, currentVersionName)) {
                return UpdateCheckResult.UpToDate
            }

            val assets = json.optJSONArray("assets")
            var apkName: String? = null
            var apkUrl: String? = null
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkName = name
                        apkUrl = downloadUrl
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl == null || apkName == null) {
                return UpdateCheckResult.Error(
                    "Release $tagName is available, but no .apk asset was found."
                )
            }

            val notes = json.optString("body", "").trim().ifBlank {
                "Performance improvements, stability fixes, and latest DNS updates."
            }

            UpdateCheckResult.UpdateAvailable(
                GitHubReleaseInfo(
                    tagName = tagName,
                    normalizedVersion = remoteNormalized,
                    releaseNotes = notes,
                    apkAssetName = apkName,
                    apkDownloadUrl = apkUrl,
                    apkSizeBytes = apkSize
                )
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error("Invalid update metadata from GitHub.")
        }
    }

    /**
     * Strips leading 'v'/'V' and any pre-release/build metadata suffix (e.g. v1.2.0-beta -> 1.2.0).
     */
    fun normalizeVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
    }

    /**
     * Semantic version comparison returning true strictly when [remoteVersion] > [currentVersion].
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = normalizeVersion(remoteVersion)
            .split(".")
            .mapNotNull { it.toIntOrNull() }
        val currentParts = normalizeVersion(currentVersion)
            .split(".")
            .mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val remoteNum = remoteParts.getOrElse(i) { 0 }
            val currentNum = currentParts.getOrElse(i) { 0 }
            if (remoteNum > currentNum) return true
            if (remoteNum < currentNum) return false
        }
        return false
    }

    /**
     * Downloads the APK with OkHttp into the app's updates cache directory while reporting progress.
     */
    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply {
            if (!exists()) mkdirs()
            // Clean up older downloaded APKs
            listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                    file.delete()
                }
            }
        }

        val safeFileName = release.apkAssetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(updatesDir, safeFileName)

        val request = Request.Builder()
            .url(release.apkDownloadUrl)
            .header("User-Agent", "DNSPulse-Android")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Download failed with HTTP ${response.code}")
                    )
                }

                val body = response.body
                    ?: return@withContext Result.failure(IOException("Empty download response"))

                val contentLength = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
                var downloaded = 0L
                var lastReportedPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val percent = if (contentLength > 0) {
                                ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }

                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent, downloaded, contentLength)
                            }
                        }
                        output.flush()
                    }
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    return@withContext Result.failure(IOException("Downloaded APK file is empty"))
                }

                onProgress(100, targetFile.length(), targetFile.length())
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            Result.failure(e)
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildManageUnknownSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Launches Android's package installer using FileProvider.
     */
    fun triggerApkInstall(context: Context, apkFile: File): Boolean {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
