package com.example

import com.example.utils.UpdateCheckResult
import com.example.utils.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateManagerTest {

    @Test
    fun `semantic version comparison detects newer versions accurately`() {
        assertTrue(UpdateManager.isNewerVersion("v1.2.0", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("1.0.10", "1.0.2"))
        assertTrue(UpdateManager.isNewerVersion("V2.0.0", "1.9.9"))
        assertTrue(UpdateManager.isNewerVersion("1.1", "1.0.5"))
        assertTrue(UpdateManager.isNewerVersion("release-v1.0.8", "1.0.5"))
        assertTrue(UpdateManager.isNewerVersion("DNS-Pulse-1.0.6", "1.0"))

        assertFalse(UpdateManager.isNewerVersion("v1.0.5", "1.0.5"))
        assertFalse(UpdateManager.isNewerVersion("1.0.2", "1.0.10"))
        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0"))
    }

    @Test
    fun `parseAndCompareRelease extracts dynamic apk asset url when newer version exists`() {
        val sampleJson = """
            {
              "tag_name": "v1.0.12",
              "body": null,
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/charfeddine23/DNS/releases/download/v1.0.12/checksums.txt"
                },
                {
                  "name": "DNS Pulse.v1.0.12.apk",
                  "size": 15728640,
                  "browser_download_url": "https://github.com/charfeddine23/DNS/releases/download/v1.0.12/DNS.Pulse.v1.0.12.apk"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManager.parseAndCompareRelease(sampleJson, "1.0.5")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("v1.0.12", release.tagName)
        assertEquals("1.0.12", release.normalizedVersion)
        assertEquals("DNS Pulse.v1.0.12.apk", release.apkAssetName)
        assertEquals(
            "https://github.com/charfeddine23/DNS/releases/download/v1.0.12/DNS.Pulse.v1.0.12.apk",
            release.apkDownloadUrl
        )
        assertEquals(15728640L, release.apkSizeBytes)
        // Ensures JSON null body does not become literal "null"
        assertFalse(release.releaseNotes.equals("null", ignoreCase = true))
    }

    @Test
    fun `parseAndCompareRelease returns UpToDate when versions match`() {
        val sampleJson = """
            {
              "tag_name": "v1.0.5",
              "body": "Initial release",
              "assets": [
                {
                  "name": "DNS Pulse.v1.0.5.apk",
                  "size": 1000,
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManager.parseAndCompareRelease(sampleJson, "1.0.5")
        assertEquals(UpdateCheckResult.UpToDate("v1.0.5"), result)
    }

    @Test
    fun `parseReleaseListAndCompare detects prerelease and non-numeric tag using asset filename`() {
        val releasesArrayJson = """
            [
              {
                "tag_name": "draft-1.0.99",
                "draft": true,
                "assets": []
              },
              {
                "tag_name": "latest",
                "name": "DNS Pulse Update",
                "prerelease": true,
                "draft": false,
                "body": "New release!",
                "assets": [
                  {
                    "name": "DNS.Pulse.v1.0.9.apk",
                    "size": 123456,
                    "browser_download_url": "https://github.com/charfeddine23/DNS/releases/download/latest/DNS.Pulse.v1.0.9.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = UpdateManager.parseReleaseListAndCompare(releasesArrayJson, "1.0.5")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("1.0.9", release.normalizedVersion)
        assertEquals("DNS.Pulse.v1.0.9.apk", release.apkAssetName)
    }

    @Test
    fun `web fallback parses tag url and expanded_assets apk link`() {
        val tag = UpdateManager.extractTagFromUrl(
            "https://github.com/charfeddine23/DNS/releases/tag/v1.0.15"
        )
        assertEquals("v1.0.15", tag)

        val html = """
            <div class="Box">
              <a href="/charfeddine23/DNS/releases/download/v1.0.15/DNS.Pulse.v1.0.15.apk" rel="nofollow">
                <span>DNS.Pulse.v1.0.15.apk</span>
              </a>
            </div>
        """.trimIndent()

        val result = UpdateManager.parseWebReleaseAndCompare(tag!!, html, "1.0.5")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("1.0.15", release.normalizedVersion)
        assertEquals(
            "https://github.com/charfeddine23/DNS/releases/download/v1.0.15/DNS.Pulse.v1.0.15.apk",
            release.apkDownloadUrl
        )
    }

    @Test
    fun `parseAndCompareRelease returns Error when apk asset is missing`() {
        val sampleJson = """
            {
              "tag_name": "v2.0.0",
              "body": "Source only",
              "assets": [
                {
                  "name": "source.zip",
                  "size": 1000,
                  "browser_download_url": "https://example.com/source.zip"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateManager.parseAndCompareRelease(sampleJson, "1.0.0")
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue((result as UpdateCheckResult.Error).message.contains("no .apk asset"))
    }

    @Test
    fun `verifyApkForDirectUpdate rejects empty or non-apk html error files`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val htmlFile = java.io.File.createTempFile("bad_update", ".apk", context.cacheDir)
        try {
            htmlFile.writeText("<html><body>404 Not Found</body></html>")
            assertFalse(UpdateManager.isZipApkHeader(htmlFile))
            val res = UpdateManager.verifyApkForDirectUpdate(context, htmlFile)
            assertTrue(res is UpdateManager.ApkCompatibilityResult.Incompatible)
        } finally {
            htmlFile.delete()
        }
    }

    @Test
    fun `downloadApk succeeds across redirect and reports progress even without Content-Length`(): Unit = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val payload = ByteArray(150 * 1024) { index ->
            when (index) {
                0 -> 0x50 // 'P'
                1 -> 0x4B // 'K'
                2 -> 0x03
                3 -> 0x04
                else -> (index % 251).toByte()
            }
        }

        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                // Handle redirect request
                server.accept().use { sock ->
                    val reader = sock.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* drain headers */ }
                    val redirectHeaders = "HTTP/1.1 302 Found\r\nLocation: /cdn/app.apk\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().write(redirectHeaders.toByteArray())
                    sock.getOutputStream().flush()
                }
                // Handle actual APK stream WITHOUT Content-Length (tests anti-freeze progress reporting)
                server.accept().use { sock ->
                    val reader = sock.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* drain headers */ }
                    val okHeaders = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nConnection: close\r\n\r\n"
                    sock.getOutputStream().write(okHeaders.toByteArray())
                    sock.getOutputStream().write(payload)
                    sock.getOutputStream().flush()
                }
            }.apply { start() }

            val release = com.example.utils.GitHubReleaseInfo(
                tagName = "v1.0.99",
                normalizedVersion = "1.0.99",
                releaseNotes = "Test",
                apkAssetName = "DNS.Pulse.v1.0.99.apk",
                apkDownloadUrl = "http://127.0.0.1:$port/download/app.apk",
                apkSizeBytes = 0L
            )

            val progressEvents = mutableListOf<Triple<Int, Long, Long>>()
            val result = UpdateManager.downloadApk(context, release) { pct, downloaded, total ->
                progressEvents.add(Triple(pct, downloaded, total))
            }

            serverThread.join(5000)

            assertTrue("Expected download success, got: ${result.exceptionOrNull()}", result.isSuccess)
            val file = result.getOrThrow()
            assertTrue(file.exists())
            assertEquals(payload.size.toLong(), file.length())
            assertTrue("Expected multiple progress callbacks even when Content-Length is missing", progressEvents.size >= 3)
            assertEquals(100, progressEvents.last().first)
            file.delete()
        }
        Unit
    }

    @Test
    fun `downloadApk rejects html error responses cleanly`(): Unit = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                repeat(2) {
                    runCatching {
                        server.accept().use { sock ->
                            val reader = sock.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { /* drain */ }
                            val body = "<html><body>Rate limited</body></html>".toByteArray()
                            val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            sock.getOutputStream().write(headers.toByteArray())
                            sock.getOutputStream().write(body)
                            sock.getOutputStream().flush()
                        }
                    }
                }
            }.apply { start() }

            val release = com.example.utils.GitHubReleaseInfo(
                tagName = "v1.0.99",
                normalizedVersion = "1.0.99",
                releaseNotes = "Test",
                apkAssetName = "DNS.Pulse.v1.0.99.apk",
                apkDownloadUrl = "http://127.0.0.1:$port/bad.apk",
                apkSizeBytes = 0L
            )

            val result = UpdateManager.downloadApk(context, release) { _, _, _ -> }
            serverThread.join(5000)

            assertTrue(result.isFailure)
        }
        Unit
    }
}
