package ir.ali0003.downloader.downloader.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object Media3DownloadManagerProvider {

    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    @Volatile
    private var downloadCache: Cache? = null

    @Volatile
    private var downloadManager: DownloadManager? = null

    // Ephemeral dynamic header storage mapped per URI or task key
    private val headersByUri = ConcurrentHashMap<String, Map<String, String>>()

    @Synchronized
    fun getDatabaseProvider(context: Context): DatabaseProvider {
        return databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
            databaseProvider = it
        }
    }

    @Synchronized
    fun getDownloadCache(context: Context): Cache {
        return downloadCache ?: run {
            val downloadContentDirectory = File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            SimpleCache(
                downloadContentDirectory,
                NoOpCacheEvictor(),
                getDatabaseProvider(context)
            ).also { downloadCache = it }
        }
    }

    @Synchronized
    fun getDownloadDirectory(context: Context): File {
        var downloadDirectory = context.getExternalFilesDir(null)
        if (downloadDirectory == null) {
            downloadDirectory = context.filesDir
        }
        return downloadDirectory
    }

    fun registerRequestHeaders(uriString: String, headers: Map<String, String>) {
        if (headers.isNotEmpty()) {
            headersByUri[uriString] = headers
        }
    }

    fun getRequestHeaders(uriString: String): Map<String, String> {
        return headersByUri[uriString] ?: emptyMap()
    }

    /**
     * Builds an HttpDataSource.Factory with default headers or custom session headers.
     */
    fun getHttpDataSourceFactory(
        context: Context,
        customHeaders: Map<String, String> = emptyMap()
    ): DefaultHttpDataSource.Factory {
        val userAgent = customHeaders["User-Agent"]
            ?: customHeaders["user-agent"]
            ?: DEFAULT_USER_AGENT

        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(25_000)
            .setReadTimeoutMs(35_000)
            .setAllowCrossProtocolRedirects(true)

        // Inject Referer, Cookie, and other standard headers to prevent CDN 403 Forbidden
        val defaultRequestProperties = mutableMapOf<String, String>()
        for ((key, value) in customHeaders) {
            if (!key.equals("User-Agent", ignoreCase = true)) {
                defaultRequestProperties[key] = value
            }
        }
        if (defaultRequestProperties.isNotEmpty()) {
            factory.setDefaultRequestProperties(defaultRequestProperties)
        }

        return factory
    }

    /**
     * Read-only DataSource.Factory backed by SimpleCache.
     */
    fun getCacheDataSourceFactory(
        context: Context,
        customHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        val upstreamFactory = getHttpDataSourceFactory(context, customHeaders)
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: run {
            val downloadExecutor = Executors.newFixedThreadPool(4)
            val httpDataSourceFactory = getHttpDataSourceFactory(context)

            DownloadManager(
                context.applicationContext,
                getDatabaseProvider(context),
                getDownloadCache(context),
                httpDataSourceFactory,
                downloadExecutor
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 3
            }.also {
                downloadManager = it
            }
        }
    }

    fun getDownloadIndex(context: Context): DownloadIndex {
        return getDownloadManager(context).downloadIndex
    }
}
