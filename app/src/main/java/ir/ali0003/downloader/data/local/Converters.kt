package ir.ali0003.downloader.data.local

import androidx.room.TypeConverter
import ir.ali0003.downloader.data.model.DownloadStatus

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus?): String {
        return status?.name ?: DownloadStatus.QUEUED.name
    }

    @TypeConverter
    fun toDownloadStatus(value: String?): DownloadStatus {
        return try {
            if (value != null) DownloadStatus.valueOf(value) else DownloadStatus.QUEUED
        } catch (e: IllegalArgumentException) {
            DownloadStatus.QUEUED
        }
    }
}
