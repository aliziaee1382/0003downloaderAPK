package ir.ali0003.downloader.data.model

enum class DownloadStatus(val displayName: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED

    val isActive: Boolean
        get() = this == DOWNLOADING || this == QUEUED
}
