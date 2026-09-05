package ir.ali0003.downloader.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.ali0003.downloader.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks WHERE isHidden = 0 ORDER BY createdAt DESC")
    fun getAllPublicDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE isHidden = 0 AND status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE isHidden = 0 AND status = 'COMPLETED' ORDER BY completedAt DESC, createdAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE isHidden = 1 ORDER BY createdAt DESC")
    fun getVaultDownloads(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    fun getDownloadById(id: Long): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun findDownloadById(id: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE url = :url LIMIT 1")
    suspend fun findDownloadByUrl(url: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(task: DownloadTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloads(tasks: List<DownloadTaskEntity>): List<Long>

    @Update
    suspend fun updateDownload(task: DownloadTaskEntity)

    @Delete
    suspend fun deleteDownload(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM download_tasks WHERE isHidden = 1")
    suspend fun deleteAllVaultDownloads()

    @Query("UPDATE download_tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE download_tasks SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBps = :speedBps WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long, speedBps: Long)

    @Query("UPDATE download_tasks SET isHidden = :isHidden WHERE id = :id")
    suspend fun updateHiddenStatus(id: Long, isHidden: Boolean)

    @Query("UPDATE download_tasks SET status = 'COMPLETED', completedAt = :completedAt, downloadedBytes = totalBytes, speedBps = 0 WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET status = 'FAILED', speedBps = 0 WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("SELECT COUNT(*) FROM download_tasks WHERE isHidden = 0")
    fun getPublicCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE isHidden = 1")
    fun getVaultCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM download_tasks")
    fun getTotalDownloadedBytes(): Flow<Long>
}
