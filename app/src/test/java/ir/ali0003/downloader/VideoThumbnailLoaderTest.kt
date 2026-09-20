package ir.ali0003.downloader

import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.ui.media.VideoThumbnailLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoThumbnailLoaderTest {

    @Test
    fun testResolutionTagResolution() {
        val fhdTask = DownloadTaskEntity(
            id = 1L,
            url = "https://example.com/video_1080p.mp4",
            websiteUrl = "https://example.com",
            fileName = "my_awesome_video_1080p_FHD.mp4",
            mimeType = "video/mp4",
            totalBytes = 35_000_000L
        )
        assertEquals("1080p", VideoThumbnailLoader.resolveResolutionTag(fhdTask, null))

        val fourKTask = DownloadTaskEntity(
            id = 2L,
            url = "https://example.com/video_2160p.mp4",
            websiteUrl = "https://example.com",
            fileName = "movie_trailer_4k.mp4",
            mimeType = "video/mp4",
            totalBytes = 120_000_000L
        )
        assertEquals("1080p", VideoThumbnailLoader.resolveResolutionTag(fourKTask, null))

        val audioTask = DownloadTaskEntity(
            id = 3L,
            url = "https://example.com/podcast.mp3",
            websiteUrl = "https://example.com",
            fileName = "podcast_episode.mp3",
            mimeType = "audio/mpeg",
            totalBytes = 12_000_000L
        )
        assertEquals("Audio", VideoThumbnailLoader.resolveResolutionTag(audioTask, null))

        val hlsTask = DownloadTaskEntity(
            id = 4L,
            url = "https://example.com/stream.m3u8",
            websiteUrl = "https://example.com",
            fileName = "live_stream.m3u8",
            mimeType = "application/x-mpegURL",
            isM3u8 = true,
            totalBytes = 0L
        )
        assertEquals("HLS", VideoThumbnailLoader.resolveResolutionTag(hlsTask, null))
    }
}
