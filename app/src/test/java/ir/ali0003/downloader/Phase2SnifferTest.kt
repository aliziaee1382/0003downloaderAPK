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

        val videoVariants = qualities.filter { !it.formatTag.contains("AUDIO", ignoreCase = true) }
        assertEquals(3, videoVariants.size)

        // Sorted descending by bandwidth
        val highest = videoVariants[0]
        assertEquals("1920x1080", highest.resolution)
        assertEquals(5000000L, highest.bandwidthBps)
        assertEquals("https://cdn.example.com/video/1080p.m3u8", highest.url)
        assertTrue(highest.isHlsVariant)
        assertTrue(highest.label.contains("1080p"))
        assertEquals(0L, highest.estimatedSizeBytes)
        assertEquals("Adaptive HLS", highest.formattedSize)

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
        assertTrue(item.cleanFileName.endsWith(".mp4"))
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

    @Test
    fun testHlsManifestGenuineVariants() {
        val multiResManifest = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=15000000,RESOLUTION=3840x2160
            4k_stream.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=2560x1440
            2k_stream.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            1080p_high.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3500000,RESOLUTION=1920x1080
            1080p_low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
            480p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=600000,RESOLUTION=640x360
            360p.m3u8
        """.trimIndent()

        val baseUrl = "https://cdn.example.com/master.m3u8"
        val parsed = HlsManifestParser.parseManifestContent(multiResManifest, baseUrl)

        // All 7 declared variants are preserved without dropping valid video tracks
        assertEquals(7, parsed.size)

        // Both 1080p variants (5Mbps and 3.5Mbps) are preserved
        val variants1080 = parsed.filter { it.resolution == "1920x1080" }
        assertEquals(2, variants1080.size)
        assertEquals(5000000L, variants1080[0].bandwidthBps)
        assertEquals(3500000L, variants1080[1].bandwidthBps)

        // Highest bandwidth is 4K
        val highest = parsed[0]
        assertEquals("3840x2160", highest.resolution)
        assertEquals(15000000L, highest.bandwidthBps)
    }

    @Test
    fun testSingleStreamMediaPlaylistDoesNotManufactureFakeTiers() {
        val singleStreamPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.009,
            segment1.ts
            #EXTINF:9.009,
            segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = "https://cdn.example.com/video/hls_250p.m3u8"
        val parsed = HlsManifestParser.parseManifestContent(singleStreamPlaylist, baseUrl)

        // Must NOT manufacture fake 1080p or 720p tiers
        assertEquals(1, parsed.size)
        val single = parsed[0]
        assertEquals("250p", single.cleanResolutionBadge)
        assertEquals(baseUrl, single.url)
    }

    @Test
    fun testNormalizeAndBucketQualitiesAggressiveDeduplication() {
        val engine = ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine {}

        val raw = listOf(
            VideoQualityOption(
                label = "1080p",
                resolution = "1920x1080",
                bandwidthBps = 4000000L,
                url = "https://cdn.example.com/video_1080.mp4?token=abc&session=1",
                isHlsVariant = false,
                estimatedSizeBytes = 50 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            // Duplicate URL with different query parameters
            VideoQualityOption(
                label = "1080p",
                resolution = "1920x1080",
                bandwidthBps = 4000000L,
                url = "https://cdn.example.com/video_1080.mp4?token=xyz",
                isHlsVariant = false,
                estimatedSizeBytes = 50 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            // Duplicate resolution and identical size
            VideoQualityOption(
                label = "1080p FHD",
                resolution = "1920x1080",
                bandwidthBps = 4000000L,
                url = "https://cdn.example.com/video_1080_alt.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 50 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "720p",
                resolution = "1280x720",
                bandwidthBps = 2000000L,
                url = "https://cdn.example.com/video_720.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 25 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "Audio Only",
                resolution = "Audio",
                bandwidthBps = 128000L,
                url = "https://cdn.example.com/audio.m4a",
                isHlsVariant = false,
                estimatedSizeBytes = 4 * 1024 * 1024L,
                formatTag = "AUDIO"
            )
        )

        val bucketed = engine.normalizeAndBucketQualities(
            rawQualities = raw,
            durationSeconds = 120.0,
            baseFileSizeBytes = 50 * 1024 * 1024L,
            isHls = false,
            fallbackUrl = "https://cdn.example.com/video_1080.mp4"
        )

        // Only one 1080p option must remain, followed by 720p, followed by Audio track at the bottom
        assertEquals(3, bucketed.size)
        assertTrue(bucketed[0].cleanResolutionBadge.contains("1080p"))
        assertTrue(bucketed[1].cleanResolutionBadge.contains("720p"))
        assertTrue(bucketed[2].cleanResolutionBadge.contains("Audio", ignoreCase = true))
    }

    @Test
    fun testNormalizeAndBucketQualitiesFiltersMicroClipsAndPreviews() {
        val engine = ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine {}

        val raw = listOf(
            // Full feature video (50 MB)
            VideoQualityOption(
                label = "720p",
                resolution = "1280x720",
                bandwidthBps = 2500000L,
                url = "https://cdn.example.com/main_video.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 50 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            // Background preview MP4 under 1.5 MB
            VideoQualityOption(
                label = "preview",
                resolution = "480x270",
                bandwidthBps = 300000L,
                url = "https://cdn.example.com/thumb_preview.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 800 * 1024L,
                formatTag = "MP4"
            ),
            // Micro clip under 1.5 MB
            VideoQualityOption(
                label = "teaser",
                resolution = "640x360",
                bandwidthBps = 500000L,
                url = "https://cdn.example.com/teaser_clip.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 1200 * 1024L,
                formatTag = "MP4"
            )
        )

        val bucketed = engine.normalizeAndBucketQualities(
            rawQualities = raw,
            durationSeconds = 180.0,
            baseFileSizeBytes = 50 * 1024 * 1024L,
            isHls = false,
            fallbackUrl = "https://cdn.example.com/main_video.mp4"
        )

        // Micro-clips and previews must be completely discarded
        assertEquals(1, bucketed.size)
        assertEquals("https://cdn.example.com/main_video.mp4", bucketed[0].url)
    }

    @Test
    fun testNormalizeAndBucketQualitiesPrioritizesProgressiveMp4OverVagueHlsDirectStream() {
        val engine = ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine {}

        val raw = listOf(
            // Vague Direct Stream HLS sub-variant
            VideoQualityOption(
                label = "Direct Stream",
                resolution = "",
                bandwidthBps = 0L,
                url = "https://cdn.example.com/hls/index.m3u8",
                isHlsVariant = true,
                estimatedSizeBytes = 0L,
                formatTag = "HLS M3U8"
            ),
            // Clean progressive MP4 options discovered from player
            VideoQualityOption(
                label = "1080p FHD",
                resolution = "1920x1080",
                bandwidthBps = 5000000L,
                url = "https://cdn.example.com/video_1080p.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 100 * 1024 * 1024L,
                formatTag = "MP4"
            ),
            VideoQualityOption(
                label = "720p HD",
                resolution = "1280x720",
                bandwidthBps = 2500000L,
                url = "https://cdn.example.com/video_720p.mp4",
                isHlsVariant = false,
                estimatedSizeBytes = 50 * 1024 * 1024L,
                formatTag = "MP4"
            )
        )

        val bucketed = engine.normalizeAndBucketQualities(
            rawQualities = raw,
            durationSeconds = 160.0,
            baseFileSizeBytes = 100 * 1024 * 1024L,
            isHls = false,
            fallbackUrl = "https://cdn.example.com/video_1080p.mp4"
        )

        // Vague HLS "Direct Stream" must be suppressed in favor of clean progressive MP4s
        assertEquals(2, bucketed.size)
        assertFalse(bucketed.any { it.label == "Direct Stream" })
        assertTrue(bucketed[0].cleanResolutionBadge.contains("1080p"))
        assertTrue(bucketed[1].cleanResolutionBadge.contains("720p"))
    }

    @Test
    fun testCandidateMasterUrlsGeneratedFromNetworkLogsAndPathStripping() {
        val networkLogs = listOf(
            "https://cdn.example.com/assets/player.js",
            "https://cdn.example.com/videos/master.m3u8?token=sec123",
            "https://other.com/ad.m3u8"
        )
        val subVariantUrl = "https://cdn.example.com/videos/720p/index.m3u8?rendition=720p&token=sec123"

        val candidates = HlsManifestParser.generateCandidateMasterUrls(subVariantUrl, networkLogs)

        // 1. Should prioritize candidate from same-host network logs
        assertTrue(candidates.contains("https://cdn.example.com/videos/master.m3u8?token=sec123"))

        // 2. Should strip rendition path segment (/720p/)
        assertTrue(candidates.any { it.startsWith("https://cdn.example.com/videos/index.m3u8") })

        // 3. Should strip rendition query param (?rendition=720p)
        assertTrue(candidates.any { !it.contains("rendition=720p") })

        // 4. Should include parent directory master.m3u8
        assertTrue(candidates.any { it.contains("videos/master.m3u8") || it.contains("videos/playlist.m3u8") })
    }

    @Test
    fun testAllHlsResolutionsRetainedWithoutDropping() {
        val manifestWithAllTiers = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080
            1080p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=854x480
            480p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=350000,RESOLUTION=426x240
            240p.m3u8
        """.trimIndent()

        val parsed = HlsManifestParser.parseManifestContent(manifestWithAllTiers, "https://cdn.example.com/master.m3u8")
        assertEquals(4, parsed.size)
        assertEquals("1920x1080", parsed[0].resolution)
        assertEquals("1280x720", parsed[1].resolution)
        assertEquals("854x480", parsed[2].resolution)
        assertEquals("426x240", parsed[3].resolution)
        assertEquals("240p", parsed[3].cleanResolutionBadge)

        // Ensure normalizeAndBucketQualities preserves all 4 tiers for HLS
        val engine = ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine {}
        val bucketed = engine.normalizeAndBucketQualities(
            rawQualities = parsed,
            durationSeconds = 120.0,
            baseFileSizeBytes = 0L,
            isHls = true,
            fallbackUrl = "https://cdn.example.com/master.m3u8"
        )
        assertEquals(4, bucketed.size)
        assertTrue(bucketed.any { it.cleanResolutionBadge == "1080p FHD" })
        assertTrue(bucketed.any { it.cleanResolutionBadge == "720p HD" })
        assertTrue(bucketed.any { it.cleanResolutionBadge == "480p SD" })
        assertTrue(bucketed.any { it.cleanResolutionBadge == "240p" })
    }

    @Test
    fun testNonFluctuatingTotalBytesFormula() {
        val bitrateBps = 3_000_000L
        val durationSeconds = 120.0
        val fixedTotalBytes = ((bitrateBps * durationSeconds) / 8.0).toLong()

        // 3 Mbps for 120 seconds = 45,000,000 bytes (~42.9 MB)
        assertEquals(45_000_000L, fixedTotalBytes)

        // Progress calculation does NOT jump or dynamically scale with segment bytes
        val seg1Downloaded = 450_000L
        val seg1Ratio = 1f / 100f
        val progress1 = ir.ali0003.downloader.downloader.model.DownloadProgress(
            taskId = 1L,
            downloadedBytes = seg1Downloaded,
            totalBytes = fixedTotalBytes,
            speedBps = 1_000_000L,
            etaSeconds = 44L,
            explicitProgress = seg1Ratio,
            currentSegment = 1,
            totalSegments = 100
        )

        assertEquals(fixedTotalBytes, progress1.totalBytes)
        assertEquals(0.01f, progress1.progress, 0.001f)

        // Segment 50: totalBytes remains fixedTotalBytes
        val seg50Downloaded = 22_500_000L
        val progress50 = ir.ali0003.downloader.downloader.model.DownloadProgress(
            taskId = 1L,
            downloadedBytes = seg50Downloaded,
            totalBytes = fixedTotalBytes,
            speedBps = 1_000_000L,
            etaSeconds = 22L,
            explicitProgress = 0.5f,
            currentSegment = 50,
            totalSegments = 100
        )
        assertEquals(fixedTotalBytes, progress50.totalBytes)
        assertEquals(0.5f, progress50.progress, 0.001f)
    }

    @Test
    fun testAverageBandwidthAndAudioSummation() {
        val manifestWithAudio = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="English",DEFAULT=YES,AUTOSELECT=YES,BANDWIDTH=128000,URI="audio_en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,AVERAGE-BANDWIDTH=2500000,RESOLUTION=1280x720,AUDIO="audio-aac"
            720p_video.m3u8
        """.trimIndent()

        val parsed = HlsManifestParser.parseManifestContent(manifestWithAudio, "https://cdn.example.com/master.m3u8")
        val videoOption = parsed.firstOrNull { !it.formatTag.contains("AUDIO", ignoreCase = true) }
        assertNotNull(videoOption)

        // AVERAGE-BANDWIDTH (2,500,000) + audio bandwidth (128,000) = 2,628,000
        assertEquals(2_628_000L, videoOption!!.bandwidthBps)
        assertTrue(videoOption.isHlsVariant)
    }

    @Test
    fun testTildeSizePrefixForEstimatedHlsSizes() {
        val estimatedOption = VideoQualityOption(
            label = "720p HD",
            resolution = "1280x720",
            bandwidthBps = 2_000_000L,
            url = "https://cdn.example.com/720p.m3u8",
            isHlsVariant = true,
            estimatedSizeBytes = 47_185_920L, // ~45 MB
            isExactSize = false
        )
        assertEquals("~45 MB", estimatedOption.formattedSize)

        val exactOption = VideoQualityOption(
            label = "1080p MP4",
            resolution = "1920x1080",
            bandwidthBps = 0L,
            url = "https://cdn.example.com/video.mp4",
            isHlsVariant = false,
            estimatedSizeBytes = 104_857_600L, // 100 MB
            isExactSize = true
        )
        assertEquals("100 MB", exactOption.formattedSize)
    }

    @Test
    fun testPornhubMediaDefinitionsParsingExtractsMultiQualityTiers() {
        val sampleJson = """
            [
                {
                    "defaultQuality": false,
                    "format": "hls",
                    "videoUrl": "https://cdn.example.com/hls/master.m3u8",
                    "quality": ["1080", "720", "480", "240"]
                },
                {
                    "defaultQuality": false,
                    "format": "mp4",
                    "videoUrl": "https://cdn.example.com/videos/1080p.mp4",
                    "quality": "1080"
                },
                {
                    "defaultQuality": true,
                    "format": "mp4",
                    "videoUrl": "https://cdn.example.com/videos/720p.mp4",
                    "quality": "720"
                },
                {
                    "defaultQuality": false,
                    "format": "mp4",
                    "videoUrl": "https://cdn.example.com/videos/480p.mp4",
                    "quality": "480"
                },
                {
                    "defaultQuality": false,
                    "format": "mp4",
                    "videoUrl": "https://cdn.example.com/videos/240p.mp4",
                    "quality": "240"
                }
            ]
        """.trimIndent()

        var detectedItem: SniffedMediaItem? = null
        val engine = ir.ali0003.downloader.browser.sniffer.VideoSnifferEngine { item ->
            detectedItem = item
        }

        engine.parseAndProcessMediaDefinitions(
            json = sampleJson,
            pageUrl = "https://example.com/view_video.php?viewkey=ph123",
            pageTitle = "Sample Streaming Video",
            userAgent = "Mozilla/5.0"
        )

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val primary = engine.getPrimaryVideoItem()
        assertNotNull(primary)

        val tiers = primary!!.qualities
        assertTrue(tiers.isNotEmpty())

        val heights = tiers.map { it.getResolutionHeight() }.toSet()
        assertTrue("Expected 1080 in tiers, found: $heights", heights.contains(1080))
        assertTrue("Expected 720 in tiers, found: $heights", heights.contains(720))
        assertTrue("Expected 480 in tiers, found: $heights", heights.contains(480))
        assertTrue("Expected 240 in tiers, found: $heights", heights.contains(240))
    }
}
