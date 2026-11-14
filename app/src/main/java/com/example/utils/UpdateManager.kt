package com.example.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
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
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L
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
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Dedicated HTTP/1.1 streaming client for large binary APK downloads across GitHub CDN
     * redirects (avoids HTTP/2 stream stalls and sets explicit call timeouts).
     */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
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
            ?.replace("&amp;", "&")
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
     * Downloads the APK safely into a temp `.part` file in app internal cache (`updates/`),
     * reporting continuous progress (time + byte throttled even when Content-Length is unknown),
     * supporting coroutine cancellation, automatic retry, and HttpURLConnection fallback.
     */
    suspend fun downloadApk(
        context: Context,
        release: GitHubReleaseInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val updatesDir = resolveUpdatesDir(context)
        val safeFileName = release.apkAssetName
            .ifBlank { "DNS-Pulse-update.apk" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
        val targetFile = File(updatesDir, safeFileName)
        val partFile = File(updatesDir, "$safeFileName.part")

        var lastError: Exception? = null
        val urlsToTry = buildCandidateUrls(release)

        for (url in urlsToTry) {
            currentCoroutineContext().ensureActive()

            // Attempt 1: OkHttp HTTP/1.1 streaming download
            try {
                downloadWithOkHttp(
                    url = url,
                    fallbackTotalBytes = release.apkSizeBytes,
                    partFile = partFile,
                    onProgress = onProgress
                )
                return@withContext finalizeDownloadedApk(partFile, targetFile, onProgress)
            } catch (e: CancellationException) {
                partFile.delete()
                throw e
            } catch (e: Exception) {
                partFile.delete()
                currentCoroutineContext().ensureActive()
                lastError = e
            }

            // Attempt 2: HttpURLConnection fallback with manual redirect following
            try {
                downloadWithHttpUrlConnection(
                    initialUrl = url,
                    fallbackTotalBytes = release.apkSizeBytes,
                    partFile = partFile,
                    onProgress = onProgress
                )
                return@withContext finalizeDownloadedApk(partFile, targetFile, onProgress)
            } catch (e: CancellationException) {
                partFile.delete()
                throw e
            } catch (e: Exception) {
                partFile.delete()
                currentCoroutineContext().ensureActive()
                lastError = e
            }
        }

        val friendlyMessage = when (lastError) {
            is UnknownHostException -> "تعذر الاتصال بالخادم. تحقق من اتصال الإنترنت أو الـ DNS."
            is SocketTimeoutException -> "انتهت مهلة الاتصال أثناء تحميل التحديث. حاول مجدداً."
            is ConnectException -> "تعذر الوصول إلى خوادم التحميل."
            else -> lastError?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "فشل تحميل ملف التحديث."
        }
        Result.failure(IOException(friendlyMessage, lastError))
    }

    private fun resolveUpdatesDir(context: Context): File {
        val preferred = File(context.cacheDir, "updates")
        if ((preferred.exists() || preferred.mkdirs()) && preferred.canWrite()) {
            cleanOldUpdateFiles(preferred)
            return preferred
        }
        val fallback = File(context.externalCacheDir ?: context.cacheDir, "updates")
        if (!fallback.exists()) fallback.mkdirs()
        cleanOldUpdateFiles(fallback)
        return fallback
    }

    private fun cleanOldUpdateFiles(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".apk", ignoreCase = true) || file.name.endsWith(".part", ignoreCase = true))) {
                file.delete()
            }
        }
    }

    private fun buildCandidateUrls(release: GitHubReleaseInfo): List<String> {
        val primary = release.apkDownloadUrl.trim()
        val list = mutableListOf<String>()
        if (primary.isNotBlank()) list.add(primary)
        // If asset came from GitHub releases/download with unencoded spaces, add encoded form as backup
        if (primary.contains(" ")) {
            list.add(primary.replace(" ", "%20"))
        }
        return list.distinct()
    }

    private suspend fun downloadWithOkHttp(
        url: String,
        fallbackTotalBytes: Long,
        partFile: File,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", "DNSPulse-Android")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("Accept-Encoding", "identity")
            .get()
            .build()

        val call = downloadClient.newCall(request)
        val jobHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            call.cancel()
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.startsWith("text/html", ignoreCase = true)) {
                    throw IOException("Server returned HTML instead of APK package")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: fallbackTotalBytes

                body.byteStream().use { input ->
                    copyStreamWithProgress(
                        input = input,
                        partFile = partFile,
                        totalBytes = totalBytes,
                        onProgress = onProgress
                    )
                }
            }
        } finally {
            jobHandle?.dispose()
        }
    }

    private suspend fun downloadWithHttpUrlConnection(
        initialUrl: String,
        fallbackTotalBytes: Long,
        partFile: File,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        var currentUrl = initialUrl
        var redirects = 0
        var connection: HttpURLConnection? = null

        val jobHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }

        try {
            while (redirects < 8) {
                currentCoroutineContext().ensureActive()
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    requestMethod = "GET"
                    useCaches = false
                    setRequestProperty("User-Agent", "DNSPulse-Android")
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                connection = conn
                val code = conn.responseCode

                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")?.trim()
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        throw IOException("Redirect without location (HTTP $code)")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    continue
                }

                if (code !in 200..299) {
                    conn.disconnect()
                    throw IOException("HTTP $code")
                }

                val contentType = conn.contentType.orEmpty()
                if (contentType.startsWith("text/html", ignoreCase = true)) {
                    conn.disconnect()
                    throw IOException("Server returned HTML instead of APK package")
                }

                val headerLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    conn.contentLengthLong
                } else {
                    conn.contentLength.toLong()
                }
                val totalBytes = headerLength.takeIf { it > 0L } ?: fallbackTotalBytes

                conn.inputStream.use { input ->
                    copyStreamWithProgress(
                        input = input,
                        partFile = partFile,
                        totalBytes = totalBytes,
                        onProgress = onProgress
                    )
                }
                return
            }
            throw IOException("Too many redirects while downloading update")
        } finally {
            jobHandle?.dispose()
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun copyStreamWithProgress(
        input: InputStream,
        partFile: File,
        totalBytes: Long,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        var downloaded = 0L
        var lastReportedPercent = Int.MIN_VALUE
        var lastReportedBytes = -1L
        var lastReportTimeMs = 0L

        // Emit immediate initial state so UI stops showing "connecting" right when stream opens
        onProgress(if (totalBytes > 0L) 0 else -1, 0L, totalBytes)

        FileOutputStream(partFile).use { output ->
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                currentCoroutineContext().ensureActive()
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead

                val percent = if (totalBytes > 0L) {
                    ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
                val now = System.currentTimeMillis()
                val shouldReport = (percent >= 0 && percent != lastReportedPercent) ||
                    (now - lastReportTimeMs >= 120L) ||
                    (downloaded - lastReportedBytes >= 64 * 1024L)

                if (shouldReport) {
                    lastReportedPercent = percent
                    lastReportedBytes = downloaded
                    lastReportTimeMs = now
                    onProgress(percent, downloaded, totalBytes)
                }
            }
            try {
                output.fd.sync()
            } catch (_: Exception) {
            }
            output.flush()
        }
    }

    private fun finalizeDownloadedApk(
        partFile: File,
        targetFile: File,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        if (!partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            return Result.failure(IOException("Downloaded APK file is empty"))
        }

        if (!isZipApkHeader(partFile)) {
            partFile.delete()
            return Result.failure(IOException("Downloaded file is not a valid APK package"))
        }

        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }

        val finalSize = targetFile.length()
        onProgress(100, finalSize, finalSize)
        return Result.success(targetFile)
    }

    sealed interface ApkCompatibilityResult {
        data object Compatible : ApkCompatibilityResult
        data class Incompatible(val reason: String) : ApkCompatibilityResult
    }

    internal fun isZipApkHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.read()
                val b1 = raf.read()
                b0 == 0x50 && b1 == 0x4B // 'P' 'K'
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies that [apkFile] can be installed directly over the currently installed app
     * without uninstalling (matching packageName, >= versionCode, and matching signing certificate).
     */
    @Suppress("DEPRECATION")
    fun verifyApkForDirectUpdate(context: Context, apkFile: File): ApkCompatibilityResult {
        if (!apkFile.exists() || apkFile.length() == 0L || !isZipApkHeader(apkFile)) {
            return ApkCompatibilityResult.Incompatible("Downloaded APK file is corrupted or incomplete.")
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        } catch (_: Exception) {
            null
        } ?: return ApkCompatibilityResult.Compatible // Let Android PackageInstaller inspect if archive parser unavailable

        if (archiveInfo.packageName != context.packageName) {
            return ApkCompatibilityResult.Incompatible(
                "Package ID mismatch (${archiveInfo.packageName} != ${context.packageName})."
            )
        }

        val installedInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(context.packageName, flags)
            }
        } catch (_: Exception) {
            null
        }

        if (installedInfo != null) {
            val archiveCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
            val installedCode = PackageInfoCompat.getLongVersionCode(installedInfo)
            if (archiveCode < installedCode) {
                return ApkCompatibilityResult.Incompatible(
                    "Update versionCode ($archiveCode) is lower than installed ($installedCode)."
                )
            }

            val installedSigs = extractCertSha256Set(installedInfo)
            val archiveSigs = extractCertSha256Set(archiveInfo)
            if (installedSigs.isNotEmpty() && archiveSigs.isNotEmpty() && installedSigs.intersect(archiveSigs).isEmpty()) {
                return ApkCompatibilityResult.Incompatible(
                    "Signing key mismatch: the existing app was signed with a different certificate."
                )
            }
        }

        return ApkCompatibilityResult.Compatible
    }

    @Suppress("DEPRECATION")
    private fun extractCertSha256Set(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()

        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
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
     * Launches Android's package installer using FileProvider for direct in-place upgrade.
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
                clipData = ClipData.newRawUri("DNS Pulse Update", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
