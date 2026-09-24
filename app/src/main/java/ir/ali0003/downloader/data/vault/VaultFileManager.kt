package ir.ali0003.downloader.data.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import ir.ali0003.downloader.data.local.DownloadDao
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Smart Stealth File-Hider Engine:
 * - Hides videos from gallery and system scanners using dot-notation obfuscation (.filename.mp4.vault)
 *   and scoped private app storage (filesDir/vault_media/).
 * - Handles MediaScanner un-indexing on hide and re-indexing on unhide.
 * - Supports seamless in-memory file retrieval for ExoPlayer playback without unhiding.
 */
class VaultFileManager(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "VaultFileManager"
        private const val VAULT_DIR_NAME = "vault_media"
        private const val VAULT_EXTENSION = ".vault"
    }

    private val vaultDir: File
        get() {
            val dir = File(context.filesDir, VAULT_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
                // Place .nomedia file in vault directory to prevent system gallery scanners
                File(dir, ".nomedia").createNewFile()
            }
            return dir
        }

    private val publicDownloadsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Moves a completed video to the stealth vault by:
     * 1. Renaming to dot-notation (.filename.mp4.vault) inside isolated vault directory.
     * 2. Un-indexing the old file path from MediaStore.
     * 3. Updating Room database entity.
     */
    suspend fun moveToVault(task: DownloadTaskEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentFile = resolveTaskFile(task)
            val cleanName = task.fileName.trimStart('.')
            val hiddenFileName = ".$cleanName$VAULT_EXTENSION"
            val targetVaultFile = File(vaultDir, hiddenFileName)

            if (currentFile != null && currentFile.exists() && currentFile.absolutePath != targetVaultFile.absolutePath) {
                currentFile.copyTo(targetVaultFile, overwrite = true)
                currentFile.delete()

                // Request MediaStore scan on deleted path to un-index it
                notifyMediaStore(currentFile.absolutePath)
            }

            // Update entity in Room
            downloadDao.updateHiddenStatus(task.id, isHidden = true)
            Log.d(TAG, "Successfully moved task ${task.id} (${task.fileName}) to Vault: ${targetVaultFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error moving task ${task.id} to vault: ${e.message}", e)
            false
        }
    }

    /**
     * Unhides a video from the vault back to the public media library:
     * 1. Moves file to public downloads directory with standard extension.
     * 2. Requests MediaScanner to index the restored video.
     * 3. Updates Room database entity.
     */
    suspend fun unhideFromVault(task: DownloadTaskEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val vaultFile = resolveTaskFile(task)
            var cleanName = task.fileName.trimStart('.')
            if (cleanName.endsWith(VAULT_EXTENSION)) {
                cleanName = cleanName.removeSuffix(VAULT_EXTENSION)
            }
            if (!cleanName.contains(".")) {
                cleanName = "$cleanName.mp4"
            }

            val targetPublicFile = File(publicDownloadsDir, cleanName)

            if (vaultFile != null && vaultFile.exists()) {
                vaultFile.copyTo(targetPublicFile, overwrite = true)
                vaultFile.delete()

                // Notify MediaStore to index new public video
                notifyMediaStore(targetPublicFile.absolutePath)
            }

            downloadDao.updateHiddenStatus(task.id, isHidden = false)
            Log.d(TAG, "Successfully unhidden task ${task.id} to: ${targetPublicFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unhiding task ${task.id}: ${e.message}", e)
            false
        }
    }

    /**
     * Resolves the actual physical file for a given task, whether public or hidden in the vault.
     * Also seamlessly resolves legacy tasks that were saved with .m3u8 instead of .mp4 (or vice versa).
     */
    fun resolveTaskFile(task: DownloadTaskEntity): File? {
        val cleanName = task.fileName.trimStart('.')
        val sanitizedName = cleanName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val hiddenName = ".$cleanName$VAULT_EXTENSION"
        val hiddenSanitizedName = ".$sanitizedName$VAULT_EXTENSION"

        val altCleanName = when {
            cleanName.endsWith(".m3u8", ignoreCase = true) -> cleanName.removeSuffix(".m3u8") + ".mp4"
            cleanName.endsWith(".mp4", ignoreCase = true) -> cleanName.removeSuffix(".mp4") + ".m3u8"
            else -> null
        }
        val altHiddenName = altCleanName?.let { ".$it$VAULT_EXTENSION" }
        val altSanitizedName = altCleanName?.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val altHiddenSanitizedName = altSanitizedName?.let { ".$it$VAULT_EXTENSION" }

        val possiblePaths = mutableListOf(
            File(vaultDir, hiddenName),
            File(vaultDir, hiddenSanitizedName),
            File(vaultDir, cleanName),
            File(vaultDir, sanitizedName),
            File(publicDownloadsDir, cleanName),
            File(publicDownloadsDir, sanitizedName),
            File(context.filesDir, "vault_media/$cleanName"),
            File(context.filesDir, "vault_media/$sanitizedName"),
            File(context.filesDir, "vault_media/$hiddenName"),
            File(context.filesDir, "vault_media/$hiddenSanitizedName"),
            File(context.filesDir, cleanName),
            File(context.filesDir, sanitizedName)
        )

        if (altCleanName != null) {
            possiblePaths.add(File(publicDownloadsDir, altCleanName))
            if (altSanitizedName != null) possiblePaths.add(File(publicDownloadsDir, altSanitizedName))
            possiblePaths.add(File(vaultDir, altCleanName))
            if (altSanitizedName != null) possiblePaths.add(File(vaultDir, altSanitizedName))
            possiblePaths.add(File(context.filesDir, altCleanName))
            if (altSanitizedName != null) possiblePaths.add(File(context.filesDir, altSanitizedName))
            possiblePaths.add(File(context.filesDir, "vault_media/$altCleanName"))
            if (altSanitizedName != null) possiblePaths.add(File(context.filesDir, "vault_media/$altSanitizedName"))
        }
        if (altHiddenName != null) {
            possiblePaths.add(File(vaultDir, altHiddenName))
            if (altHiddenSanitizedName != null) possiblePaths.add(File(vaultDir, altHiddenSanitizedName))
            possiblePaths.add(File(context.filesDir, "vault_media/$altHiddenName"))
            if (altHiddenSanitizedName != null) possiblePaths.add(File(context.filesDir, "vault_media/$altHiddenSanitizedName"))
        }

        return possiblePaths.firstOrNull { it.exists() } ?: if (task.isHidden) {
            File(vaultDir, hiddenSanitizedName)
        } else {
            File(publicDownloadsDir, sanitizedName)
        }
    }

    /**
     * Deletes physical file and database entry.
     */
    suspend fun deleteFileAndTask(task: DownloadTaskEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = resolveTaskFile(task)
            if (file != null && file.exists()) {
                val path = file.absolutePath
                file.delete()
                notifyMediaStore(path)
            }
            downloadDao.deleteDownloadById(task.id)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting task ${task.id}: ${e.message}", e)
            false
        }
    }

    private fun notifyMediaStore(filePath: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                null
            ) { path, uri ->
                Log.d(TAG, "MediaScanner finished for $path -> $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner notification error: ${e.message}")
        }
    }
}
