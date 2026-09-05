package ir.ali0003.downloader.data.repository

import ir.ali0003.downloader.data.local.ShortcutDao
import ir.ali0003.downloader.data.local.WebShortcutEntity
import kotlinx.coroutines.flow.Flow
import java.net.URI

interface ShortcutRepository {
    fun getAllShortcuts(): Flow<List<WebShortcutEntity>>
    suspend fun saveOrUpdateShortcut(slotIndex: Int, title: String, url: String, id: Long = 0L)
    suspend fun deleteShortcutBySlot(slotIndex: Int)
    suspend fun deleteShortcutById(id: Long)
    suspend fun initializeDefaultPresetsIfEmpty()
}

class ShortcutRepositoryImpl(
    private val shortcutDao: ShortcutDao
) : ShortcutRepository {

    override fun getAllShortcuts(): Flow<List<WebShortcutEntity>> =
        shortcutDao.getAllShortcuts()

    override suspend fun saveOrUpdateShortcut(
        slotIndex: Int,
        title: String,
        url: String,
        id: Long
    ) {
        val formattedUrl = normalizeUrl(url)
        val cleanTitle = if (title.isBlank()) extractDomainName(formattedUrl) else title.trim()
        val colorHex = resolveBrandColor(cleanTitle, formattedUrl)

        val entity = WebShortcutEntity(
            id = id,
            slotIndex = slotIndex,
            title = cleanTitle,
            url = formattedUrl,
            primaryColorHex = colorHex
        )
        shortcutDao.insertShortcut(entity)
    }

    override suspend fun deleteShortcutBySlot(slotIndex: Int) {
        shortcutDao.deleteShortcutBySlot(slotIndex)
    }

    override suspend fun deleteShortcutById(id: Long) {
        shortcutDao.deleteShortcutById(id)
    }

    override suspend fun initializeDefaultPresetsIfEmpty() {
        val count = shortcutDao.getShortcutCount()
        if (count == 0) {
            // Default Preset: Only "YouTube" is preloaded as the initial default shortcut
            val defaultPreset = WebShortcutEntity(
                id = 0L,
                slotIndex = 0,
                title = "YouTube",
                url = "https://www.youtube.com",
                primaryColorHex = 0xFFFF0000
            )
            shortcutDao.insertShortcut(defaultPreset)
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractDomainName(url: String): String {
        return try {
            val host = URI(url).host ?: url
            val clean = if (host.startsWith("www.")) host.substring(4) else host
            clean.substringBefore(".").replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Shortcut"
        }
    }

    private fun resolveBrandColor(title: String, url: String): Long {
        val lower = "$title $url".lowercase()
        return when {
            lower.contains("youtube") -> 0xFFFF0000
            lower.contains("instagram") -> 0xFFE1306C
            lower.contains("aparat") -> 0xFFED145B
            lower.contains("github") -> 0xFF24292E
            lower.contains("tiktok") -> 0xFF00F2FE
            lower.contains("twitter") || lower.contains(" x.com") -> 0xFF1DA1F2
            lower.contains("facebook") -> 0xFF1877F2
            lower.contains("reddit") -> 0xFFFF4500
            lower.contains("spotify") -> 0xFF1DB954
            lower.contains("twitch") -> 0xFF9146FF
            lower.contains("pinterest") -> 0xFFBD081C
            lower.contains("vimeo") -> 0xFF1AB7EA
            lower.contains("telegram") -> 0xFF229ED9
            else -> {
                // Generate a distinct vibrant color based on hash
                val hash = Math.abs(title.hashCode() + url.hashCode())
                val palette = listOf(
                    0xFF06B6D4, // Cyan
                    0xFF8B5CF6, // Purple
                    0xFFEC4899, // Pink
                    0xFFF59E0B, // Amber
                    0xFF10B981, // Emerald
                    0xFF3B82F6, // Blue
                    0xFF6366F1, // Indigo
                    0xFF14B8A6  // Teal
                )
                palette[hash % palette.size]
            }
        }
    }
}
