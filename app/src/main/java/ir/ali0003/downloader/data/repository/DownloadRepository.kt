package ir.ali0003.downloader.data.repository

import ir.ali0003.downloader.data.local.DownloadDao
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    val allPublicDownloads: Flow<List<DownloadTaskEntity>>
    val activeDownloads: Flow<List<DownloadTaskEntity>>
    val completedDownloads: Flow<List<DownloadTaskEntity>>
    val vaultDownloads: Flow<List<DownloadTaskEntity>>
    val publicCount: Flow<Int>
    val vaultCount: Flow<Int>
    val totalDownloadedBytes: Flow<Long>

    fun getDownloadById(id: Long): Flow<DownloadTaskEntity?>
    suspend fun findDownloadById(id: Long): DownloadTaskEntity?
    suspend fun findDownloadByUrl(url: String): DownloadTaskEntity?

    suspend fun enqueueDownload(
        url: String,
        websiteUrl: String,
        fileName: String,
        mimeType: String = "video/mp4",
        totalBytes: Long = 0L,
        isM3u8: Boolean = false,
        headersJson: String = "{}",
        isHidden: Boolean = false
    ): Long

    suspend fun insertTask(task: DownloadTaskEntity): Long
    suspend fun insertTasks(tasks: List<DownloadTaskEntity>): List<Long>
    suspend fun updateTask(task: DownloadTaskEntity)
    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long, speedBps: Long)
    suspend fun pauseDownload(id: Long)
    suspend fun resumeDownload(id: Long)
    suspend fun retryDownload(id: Long)
    suspend fun markCompleted(id: Long)
    suspend fun markFailed(id: Long)
    suspend fun setHidden(id: Long, isHidden: Boolean)
    suspend fun deleteDownload(id: Long)
    suspend fun deleteAllVaultDownloads()
}

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override val allPublicDownloads: Flow<List<DownloadTaskEntity>> =
        downloadDao.getAllPublicDownloads()

    override val activeDownloads: Flow<List<DownloadTaskEntity>> =
        downloadDao.getActiveDownloads()

    override val completedDownloads: Flow<List<DownloadTaskEntity>> =
        downloadDao.getCompletedDownloads()

    override val vaultDownloads: Flow<List<DownloadTaskEntity>> =
        downloadDao.getVaultDownloads()

    override val publicCount: Flow<Int> =
        downloadDao.getPublicCount()

    override val vaultCount: Flow<Int> =
        downloadDao.getVaultCount()

    override val totalDownloadedBytes: Flow<Long> =
        downloadDao.getTotalDownloadedBytes()

    override fun getDownloadById(id: Long): Flow<DownloadTaskEntity?> {
        return downloadDao.getDownloadById(id)
    }

    override suspend fun findDownloadById(id: Long): DownloadTaskEntity? {
        return downloadDao.findDownloadById(id)
    }

    override suspend fun findDownloadByUrl(url: String): DownloadTaskEntity? {
        return downloadDao.findDownloadByUrl(url)
    }

    override suspend fun enqueueDownload(
        url: String,
        websiteUrl: String,
        fileName: String,
        mimeType: String,
        totalBytes: Long,
        isM3u8: Boolean,
        headersJson: String,
        isHidden: Boolean
    ): Long {
        val task = DownloadTaskEntity(
            url = url,
            websiteUrl = websiteUrl,
            fileName = fileName,
            mimeType = mimeType,
            totalBytes = totalBytes,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            speedBps = 0L,
            isM3u8 = isM3u8,
            headersJson = headersJson,
            isHidden = isHidden,
            createdAt = System.currentTimeMillis()
        )
        return downloadDao.insertDownload(task)
    }

    override suspend fun insertTask(task: DownloadTaskEntity): Long {
        return downloadDao.insertDownload(task)
    }

    override suspend fun insertTasks(tasks: List<DownloadTaskEntity>): List<Long> {
        return downloadDao.insertDownloads(tasks)
    }

    override suspend fun updateTask(task: DownloadTaskEntity) {
        downloadDao.updateDownload(task)
    }

    override suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBps: Long
    ) {
        downloadDao.updateProgress(id, downloadedBytes, totalBytes, speedBps)
    }

    override suspend fun pauseDownload(id: Long) {
        downloadDao.updateStatus(id, DownloadStatus.PAUSED)
    }

    override suspend fun resumeDownload(id: Long) {
        downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
    }

    override suspend fun retryDownload(id: Long) {
        downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
    }

    override suspend fun markCompleted(id: Long) {
        downloadDao.markCompleted(id, System.currentTimeMillis())
    }

    override suspend fun markFailed(id: Long) {
        downloadDao.markFailed(id)
    }

    override suspend fun setHidden(id: Long, isHidden: Boolean) {
        downloadDao.updateHiddenStatus(id, isHidden)
    }

    override suspend fun deleteDownload(id: Long) {
        downloadDao.deleteDownloadById(id)
    }

    override suspend fun deleteAllVaultDownloads() {
        downloadDao.deleteAllVaultDownloads()
    }
}
