package io.github.kvmy666.duostatusbar.settings

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class UpdateMetadataDeepAuditTest {
    private val base="https://github.com/edugrepo1-jpg/duoStatusBar/releases"
    @Test fun `public stable title does not advertise the installed canvas fork as an update`() {
        assertFalse(UpdateChecker.isNewer("1.4.4","1.4.4-canvas-recreate"))
        assertFalse(UpdateChecker.isNewer("1.4.4-recreate","1.4.4-canvas-recreate"))
        assertTrue(UpdateChecker.isNewer("1.4.5","1.4.4-canvas-recreate"))
        assertFalse(UpdateChecker.isNewer("1.4.4-beta.2","1.4.4-canvas-recreate"))
    }
    @Test fun `newer fork release with normal public title can be downloaded`() {
        assertTrue(UpdateDownloadPolicy.compatibleChannel("1.4.4-canvas-recreate","1.4.5"))
        assertTrue(UpdateDownloadPolicy.downloadable(UpdateInfo("1.4.5","$base/tag/v1.4.5-recreate","$base/download/v1.4.5-recreate/DUO-Recreate-1.4.5.apk","a".repeat(64))))
    }
    @Test fun `digest and URL belong to the same usable apk asset`() {
        val body="""[{"tag_name":"v1.4.5-recreate","name":"DUO Recreate 1.4.5","html_url":"$base/tag/v1.4.5-recreate","assets":[
            {"name":"unsigned.apk","browser_download_url":"$base/download/v1.4.5-recreate/unsigned.apk","digest":""},
            {"name":"DUO-Recreate-1.4.5.apk","browser_download_url":"$base/download/v1.4.5-recreate/DUO-Recreate-1.4.5.apk","digest":"sha256:${"b".repeat(64)}"}
        ]}]"""
        val info=UpdateChecker.parseGithubReleases(body)!!.single()
        assertEquals("$base/download/v1.4.5-recreate/DUO-Recreate-1.4.5.apk",info.apkUrl)
        assertEquals("b".repeat(64),info.sha256)
    }
    @Test fun `empty release title falls back to its actual tag rather than unrelated title digits`() {
        val info=UpdateChecker.parseGithubReleases("""[{"tag_name":"v1.4.5-recreate","name":"Android 16 support 1.4.5","html_url":"$base/tag/v1.4.5-recreate","assets":[]}]""")!!.single()
        assertEquals("1.4.5-recreate",info.version)
    }
}
