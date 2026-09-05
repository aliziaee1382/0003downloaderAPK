package ir.ali0003.downloader

import ir.ali0003.downloader.browser.adblock.AdBlockEngine
import ir.ali0003.downloader.browser.model.SniffedMediaItem
import ir.ali0003.downloader.browser.model.VideoQualityOption
import ir.ali0003.downloader.browser.sniffer.HlsManifestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2SnifferTest {

    @Test
    fun testAdBlockEngineIdentifiesAdDomains() {
        assertTrue(AdBlockEngine.isAdUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockEngine.isAdUrl("https://sub.doubleclick.net/ad/banner.png"))
        assertTrue(AdBlockEngine.isAdUrl("https://popads.net/serve"))
        assertTrue(AdBlockEngine.isAdUrl("https://example.com/ads/tracking/pixel.gif"))

        // Legitimate video domains should NOT be blocked
        assertFalse(AdBlockEngine.isAdUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
        assertFalse(AdBlockEngine.isAdUrl("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"))
    }

    @Test
    fun testAdBlockEngineFiltersUnsafeSchemes() {
        assertTrue(AdBlockEngine.isDangerousOrUnsafeScheme("market://details?id=com.spam.app"))
        assertTrue(AdBlockEngine.isDangerousOrUnsafeScheme("intent://play.google.com/#Intent;package=com.spam.app;end"))
        assertFalse(AdBlockEngine.isDangerousOrUnsafeScheme("https://example.com/video.mp4"))
    }

    @Test
    fun testHlsManifestParserExtractsMultiVariants() {
        val sampleManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401f,mp4a.40.2"
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080p.m3u8
        """.trimIndent()

        val baseUrl = "https://cdn.example.com/video/master.m3u8"
        val qualities = HlsManifestParser.parseManifestContent(sampleManifest, baseUrl)

        assertEquals(3, qualities.size)

        // Sorted descending by bandwidth
        val highest = qualities[0]
        assertEquals("1920x1080", highest.resolution)
        assertEquals(5000000L, highest.bandwidthBps)
        assertEquals("https://cdn.example.com/video/1080p.m3u8", highest.url)
        assertTrue(highest.isHlsVariant)
        assertTrue(highest.label.contains("1080p"))

        val lowest = qualities[2]
        assertEquals("640x360", lowest.resolution)
        assertEquals(800000L, lowest.bandwidthBps)
        assertEquals("https://cdn.example.com/video/360p.m3u8", lowest.url)
    }

    @Test
    fun testSniffedMediaItemFormattingAndCleanNames() {
        val item = SniffedMediaItem(
            url = "https://cdn.example.com/video/stream.m3u8?token=xyz123",
            pageUrl = "https://example.com/watch?v=123",
            title = "My Cool Video: Episode 1 (Special Edition)",
            isM3u8 = true,
            headers = mapOf("Referer" to "https://example.com/watch", "Cookie" to "session=abc")
        )

        assertNotNull(item.cleanFileName)
        assertTrue(item.cleanFileName.endsWith(".m3u8"))
        assertFalse(item.cleanFileName.contains(":"))
        assertFalse(item.cleanFileName.contains("?"))
        assertTrue(item.headersJson.contains("session=abc"))
    }

    @Test
    fun testVideoQualityOptionSizeFormatting() {
        assertEquals("0 B", VideoQualityOption.formatFileSize(0))
        assertEquals("1.5 KB", VideoQualityOption.formatFileSize(1536))
        assertEquals("25 MB", VideoQualityOption.formatFileSize(26214400))
        assertEquals("1.2 GB", VideoQualityOption.formatFileSize(1288490188))
    }
}
