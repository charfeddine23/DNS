package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
    data class UpToDate(val latestTag: String? = null) : UpdateCheckResult
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

    private const val REPO_PATH = "charfeddine23/DNS"
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_PATH/releases/latest"
    private const val ALL_RELEASES_API_URL =
        "https://api.github.com/repos/$REPO_PATH/releases?per_page=10"
    private const val WEB_RELEASES_LATEST_URL =
        "https://github.com/$REPO_PATH/releases/latest"
    private const val WEB_RELEASES_URL =
        "https://github.com/$REPO_PATH/releases"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Fetches the latest GitHub release with automatic fallbacks:
     * 1. GitHub REST API `/releases/latest`
     * 2. GitHub REST API `/releases?per_page=10` (covers pre-releases or releases not flagged as latest)
     * 3. GitHub Web `/releases/latest` + `/releases/expanded_assets/<tag>` (bypasses API rate limits)
     */
    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                // 1. Try /releases/latest API
                val latestApiResult = fetchApiJson(LATEST_RELEASE_API_URL)
                if (latestApiResult.isSuccess) {
                    val body = latestApiResult.body.orEmpty()
                    if (body.isNotBlank()) {
                        val parsed = parseAndCompareRelease(body, currentVersionName)
                        if (parsed is UpdateCheckResult.UpdateAvailable || parsed is UpdateCheckResult.UpToDate) {
                            return@withContext parsed
                        }
                    }
                }

                // 2. Try /releases list API (handles pre-releases, non-latest releases, or latest missing APK)
                val listApiResult = fetchApiJson(ALL_RELEASES_API_URL)
                if (listApiResult.isSuccess) {
                    val body = listApiResult.body.orEmpty()
                    if (body.isNotBlank()) {
                        val parsed = parseReleaseListAndCompare(body, currentVersionName)
                        if (parsed !is UpdateCheckResult.Error || latestApiResult.statusCode == 404) {
                            return@withContext parsed
                        }
                    }
                }

                // 3. Fallback to GitHub Web Releases (bypasses unauthenticated API 403/429 rate limits)
                val webFallback = fetchFromWebReleases(currentVersionName)
                if (webFallback != null) {
                    return@withContext webFallback
                }

                // Report most informative error
                val code = latestApiResult.statusCode.takeIf { it > 0 } ?: listApiResult.statusCode
                when (code) {
                    403, 429 -> UpdateCheckResult.Error(
                        "GitHub API rate limit reached. Please try again shortly."
                    )
                    404 -> UpdateCheckResult.Error(
                        "No accessible releases found on GitHub ($REPO_PATH). Ensure the repository is public and has a published release with an .apk file."
                    )
                    else -> UpdateCheckResult.Error(
                        "Failed to check updates (HTTP $code)."
                    )
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

    private data class HttpFetchResult(
        val statusCode: Int,
        val body: String?,
        val finalUrl: String?
    ) {
        val isSuccess: Boolean get() = statusCode in 200..299
    }

    private fun fetchApiJson(url: String): HttpFetchResult {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DNSPulse-Android")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return HttpFetchResult(
                statusCode = response.code,
                body = response.body?.string(),
                finalUrl = response.request.url.toString()
            )
        }
    }

    private fun fetchWebPage(url: String): HttpFetchResult {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) DNSPulse-Updater")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return HttpFetchResult(
                statusCode = response.code,
                body = response.body?.string(),
                finalUrl = response.request.url.toString()
            )
        }
    }

    private fun fetchFromWebReleases(currentVersionName: String): UpdateCheckResult? {
        return try {
            val latestRes = fetchWebPage(WEB_RELEASES_LATEST_URL)
            var tagName = extractTagFromUrl(latestRes.finalUrl)

            if (tagName == null) {
                val allRes = fetchWebPage(WEB_RELEASES_URL)
                if (allRes.isSuccess && !allRes.body.isNullOrBlank()) {
                    tagName = extractFirstTagFromReleasesHtml(allRes.body)
                }
            }

            if (tagName.isNullOrBlank()) return null

            val expandedAssetsUrl = "https://github.com/$REPO_PATH/releases/expanded_assets/$tagName"
            val assetsRes = fetchWebPage(expandedAssetsUrl)
            val assetsHtml = assetsRes.body.orEmpty()

            parseWebReleaseAndCompare(
                tagName = tagName,
                expandedAssetsHtml = assetsHtml,
                currentVersionName = currentVersionName
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractTagFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val marker = "/releases/tag/"
        val idx = url.indexOf(marker)
        if (idx == -1) return null
        return url.substring(idx + marker.length)
            .substringBefore("?")
            .substringBefore("#")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    internal fun extractFirstTagFromReleasesHtml(html: String): String? {
        val regex = Regex("""href="/$REPO_PATH/releases/tag/([^"?#]+)"""")
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    internal fun parseWebReleaseAndCompare(
        tagName: String,
        expandedAssetsHtml: String,
        currentVersionName: String
    ): UpdateCheckResult {
        val apkRegex = Regex("""href="(/$REPO_PATH/releases/download/[^"]+\.apk)"""", RegexOption.IGNORE_CASE)
        val relativeHref = apkRegex.find(expandedAssetsHtml)?.groupValues?.getOrNull(1)
        val apkName = relativeHref?.substringAfterLast("/")?.let { Uri.decode(it) }

        val candidateVersion = resolveBestVersionCandidate(
            tagName = tagName,
            releaseTitle = "",
            apkName = apkName.orEmpty()
        )

        if (candidateVersion.isBlank()) {
            return UpdateCheckResult.Error("Could not parse version from release tag: $tagName")
        }

        if (!isNewerVersion(candidateVersion, currentVersionName)) {
            return UpdateCheckResult.UpToDate(latestTag = tagName)
        }

        if (relativeHref.isNullOrBlank() || apkName.isNullOrBlank()) {
            return UpdateCheckResult.Error(
                "Release $tagName is available, but no .apk asset was found."
            )
        }

        return UpdateCheckResult.UpdateAvailable(
            GitHubReleaseInfo(
                tagName = tagName,
                normalizedVersion = candidateVersion,
                releaseNotes = "New update $tagName is available for DNS Pulse.",
                apkAssetName = apkName,
                apkDownloadUrl = "https://github.com$relativeHref",
                apkSizeBytes = 0L
            )
        )
    }

    internal fun parseAndCompareRelease(
        jsonString: String,
        currentVersionName: String
    ): UpdateCheckResult {
        return try {
            val json = JSONObject(jsonString)
            parseSingleReleaseObject(json, currentVersionName)
        } catch (_: Exception) {
            UpdateCheckResult.Error("Invalid update metadata from GitHub.")
        }
    }

    internal fun parseReleaseListAndCompare(
        jsonArrayString: String,
        currentVersionName: String
    ): UpdateCheckResult {
        return try {
            val array = JSONArray(jsonArrayString)
            var bestAvailable: UpdateCheckResult.UpdateAvailable? = null
            var highestTagSeen: String? = null
            var highestVersionSeen = ""
            var lastError: UpdateCheckResult.Error? = null

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optBoolean("draft", false)) continue

                val tagName = obj.optNonNullString("tag_name")
                val title = obj.optNonNullString("name")
                val apkAsset = findApkAsset(obj.optJSONArray("assets"))
                val candidateVersion = resolveBestVersionCandidate(
                    tagName = tagName,
                    releaseTitle = title,
                    apkName = apkAsset?.first.orEmpty()
                )

                if (candidateVersion.isNotBlank() &&
                    (highestVersionSeen.isBlank() || isNewerVersion(candidateVersion, highestVersionSeen))
                ) {
                    highestVersionSeen = candidateVersion
                    highestTagSeen = tagName.ifBlank { candidateVersion }
                }

                when (val result = parseSingleReleaseObject(obj, currentVersionName)) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        val currentBest = bestAvailable
                        if (currentBest == null || isNewerVersion(
                                result.release.normalizedVersion,
                                currentBest.release.normalizedVersion
                            )
                        ) {
                            bestAvailable = result
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        if (lastError == null) lastError = result
                    }
                    is UpdateCheckResult.UpToDate -> Unit
                }
            }

            when {
                bestAvailable != null -> bestAvailable
                highestTagSeen != null -> UpdateCheckResult.UpToDate(latestTag = highestTagSeen)
                lastError != null -> lastError
                else -> UpdateCheckResult.Error("No published releases found on GitHub yet.")
            }
        } catch (_: Exception) {
            UpdateCheckResult.Error("Invalid releases list metadata from GitHub.")
        }
    }

    private fun parseSingleReleaseObject(
        json: JSONObject,
        currentVersionName: String
    ): UpdateCheckResult {
        val tagName = json.optNonNullString("tag_name")
        val releaseTitle = json.optNonNullString("name")
        val apkAsset = findApkAsset(json.optJSONArray("assets"))

        if (tagName.isBlank() && releaseTitle.isBlank()) {
            return UpdateCheckResult.Error("Release tag is missing in GitHub response.")
        }

        val remoteNormalized = resolveBestVersionCandidate(
            tagName = tagName,
            releaseTitle = releaseTitle,
            apkName = apkAsset?.first.orEmpty()
        )

        if (remoteNormalized.isBlank()) {
            return UpdateCheckResult.Error("Unable to parse version from release \"$tagName\".")
        }

        if (!isNewerVersion(remoteNormalized, currentVersionName)) {
            return UpdateCheckResult.UpToDate(latestTag = tagName.ifBlank { remoteNormalized })
        }

        if (apkAsset == null) {
            return UpdateCheckResult.Error(
                "Release ${tagName.ifBlank { remoteNormalized }} is available, but no .apk asset was found."
            )
        }

        val notes = json.optNonNullString("body").ifBlank {
            "Performance improvements, stability fixes, and latest DNS updates."
        }

        return UpdateCheckResult.UpdateAvailable(
            GitHubReleaseInfo(
                tagName = tagName.ifBlank { "v$remoteNormalized" },
                normalizedVersion = remoteNormalized,
                releaseNotes = notes,
                apkAssetName = apkAsset.first,
                apkDownloadUrl = apkAsset.second,
                apkSizeBytes = apkAsset.third
            )
        )
    }

    private fun findApkAsset(assets: JSONArray?): Triple<String, String, Long>? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optNonNullString("name")
            val downloadUrl = asset.optNonNullString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                val size = asset.optLong("size", 0L)
                return Triple(name, downloadUrl, size)
            }
        }
        return null
    }

    /**
     * Resolves the cleanest semantic version string from tag_name, release title, or APK filename.
     * Supports formats like: "v1.0.8", "1.0.8", "release-v1.0.8", "DNS Pulse.v1.0.8.apk", "v2".
     */
    internal fun resolveBestVersionCandidate(
        tagName: String,
        releaseTitle: String,
        apkName: String
    ): String {
        val tagVer = normalizeVersion(tagName)
        if (tagVer.isNotBlank()) return tagVer

        val titleVer = normalizeVersion(releaseTitle)
        if (titleVer.isNotBlank()) return titleVer

        return normalizeVersion(apkName)
    }

    /**
     * Extracts numeric dotted version segments from arbitrary version/tag strings
     * (e.g. "v1.2.0-beta" -> "1.2.0", "release-1.0.8" -> "1.0.8", "DNS Pulse.v1.0.12.apk" -> "1.0.12").
     */
    fun normalizeVersion(version: String): String {
        val trimmed = version.trim()
        if (trimmed.isBlank()) return ""

        // Prefer multi-segment dotted version first (e.g. 1.0.8 or 2.1)
        val multiSegmentMatch = Regex("""(\d+(?:\.\d+)+)""").find(trimmed)
        if (multiSegmentMatch != null) {
            return multiSegmentMatch.groupValues[1]
        }

        // Fallback to single integer version (e.g. "v2" -> "2")
        val singleSegmentMatch = Regex("""(\d+)""").find(trimmed)
        return singleSegmentMatch?.groupValues?.getOrNull(1).orEmpty()
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

    private fun JSONObject.optNonNullString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key, "").trim()
        return if (value.equals("null", ignoreCase = true)) "" else value
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
