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

        assertFalse(UpdateManager.isNewerVersion("v1.0.5", "1.0.5"))
        assertFalse(UpdateManager.isNewerVersion("1.0.2", "1.0.10"))
        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0"))
    }

    @Test
    fun `parseAndCompareRelease extracts dynamic apk asset url when newer version exists`() {
        val sampleJson = """
            {
              "tag_name": "v1.0.12",
              "body": "### What's New\n- Faster DNS ping\n- Bug fixes",
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
        assertEquals(UpdateCheckResult.UpToDate, result)
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
