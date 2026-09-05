package ir.ali0003.downloader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM web_shortcuts ORDER BY slotIndex ASC")
    fun getAllShortcuts(): Flow<List<WebShortcutEntity>>

    @Query("SELECT * FROM web_shortcuts WHERE slotIndex = :slotIndex LIMIT 1")
    suspend fun getShortcutBySlot(slotIndex: Int): WebShortcutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: WebShortcutEntity): Long

    @Update
    suspend fun updateShortcut(shortcut: WebShortcutEntity)

    @Query("DELETE FROM web_shortcuts WHERE id = :id")
    suspend fun deleteShortcutById(id: Long)

    @Query("DELETE FROM web_shortcuts WHERE slotIndex = :slotIndex")
    suspend fun deleteShortcutBySlot(slotIndex: Int)

    @Query("SELECT COUNT(*) FROM web_shortcuts")
    suspend fun getShortcutCount(): Int
}
