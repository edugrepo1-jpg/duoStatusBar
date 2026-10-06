package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update check must not nag: it only fires when the release is genuinely newer. The repo tags
 * releases as `6-1.1.0` while the display name is `v1.1.0`, so the version has to be pulled out of either,
 * and compared numerically (1.10 > 1.9) and pre-release-aware (`1.4.0-beta.2 > 1.4.0-beta.1`). Parsing
 * uses `org.json`, so these run under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckerTest {

    @Test
    fun `a version is pulled out of the name and out of the version-code tag`() {
        assertEquals("1.1.0", UpdateChecker.parseVersion("v1.1.0"))
        assertEquals("1.1.0", UpdateChecker.parseVersion("6-1.1.0"))
        assertEquals("1.2", UpdateChecker.parseVersion("Duo Status Bar 1.2"))
        assertNull(UpdateChecker.parseVersion("no version here"))
    }

    @Test
    fun `a genuinely newer release is detected`() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.1.1", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        // Numeric, not lexicographic: 1.10 is after 1.9.
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
    }

    @Test
    fun `the same or an older release is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("v1.1.0", "1.1.0"))
    }

    @Test
    fun `a version with a pre-release suffix is parsed whole`() {
        assertEquals("1.4.0-beta.1", UpdateChecker.parseVersion("v1.4.0-beta.1"))
        assertEquals("1.4.0-beta.1", UpdateChecker.parseVersion("16-1.4.0-beta.1"))
    }

    @Test
    fun `pre-releases order correctly, and a release beats its own betas`() {
        assertTrue(UpdateChecker.isNewer("1.4.0-beta.2", "1.4.0-beta.1"))
        assertFalse(UpdateChecker.isNewer("1.4.0-beta.1", "1.4.0-beta.2"))
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.4.0-beta.2"))
        assertFalse(UpdateChecker.isNewer("1.4.0-beta.2", "1.4.0"))
        assertTrue(UpdateChecker.isNewer("1.4.0-rc.1", "1.4.0-beta.5"))
    }

    @Test
    fun `github releases map the apk asset and its digest, skipping drafts`() {
        val sha = "a".repeat(64)
        val body = """
            [
              {"tag_name":"16-1.4.0-beta.1","name":"v1.4.0-beta.1","prerelease":true,"draft":false,
               "html_url":"https://github.com/edugrepo1-jpg/duoStatusBar/releases/tag/v1.4.0-beta.1","assets":[{"name":"DuoStatusBar-1.4.0-beta.1.apk",
               "browser_download_url":"https://github.com/edugrepo1-jpg/duoStatusBar/releases/download/v1.4.0-beta.1/a.apk","digest":"sha256:$sha"}]},
              {"tag_name":"15-1.3.2","name":"v1.3.2","prerelease":false,"draft":true,"assets":[]}
            ]
        """.trimIndent()
        val list = UpdateChecker.parseGithubReleases(body)!!
        assertEquals(1, list.size)
        assertEquals("1.4.0-beta.1", list[0].version)
        assertTrue(list[0].prerelease)
        assertEquals("https://github.com/edugrepo1-jpg/duoStatusBar/releases/download/v1.4.0-beta.1/a.apk", list[0].apkUrl)
        assertEquals(sha, list[0].sha256)
    }

    @Test
    fun `the relay body yields its named latest, including betas`() {
        val body = """{"ok":true,"latest":{"version":"1.4.0-beta.2","pageUrl":"https://p",
            "apkUrl":"https://a","sha256":"b","prerelease":true},"releases":[]}"""
        val info = UpdateChecker.parseRelay(body)!!
        assertEquals("1.4.0-beta.2", info.version)
        assertTrue(info.prerelease)
        assertEquals("https://a", info.apkUrl)
    }

    @Test
    fun `a relay body without latest falls back to the highest release`() {
        val body = """{"ok":true,"releases":[
            {"version":"1.4.0-beta.1","pageUrl":"p"},
            {"version":"1.4.0-beta.3","pageUrl":"p"},
            {"version":"1.3.2","pageUrl":"p"}]}"""
        assertEquals("1.4.0-beta.3", UpdateChecker.parseRelay(body)!!.version)
    }
}
