package ir.ali0003.downloader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "web_shortcuts",
    indices = [
        Index(value = ["slotIndex"], unique = true)
    ]
)
data class WebShortcutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val slotIndex: Int, // 0 to 11 (up to 12 slots)
    val title: String,
    val url: String,
    val iconEmoji: String = "",
    val primaryColorHex: Long = 0xFFFF0000,
    val createdAt: Long = System.currentTimeMillis()
)
