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
}
