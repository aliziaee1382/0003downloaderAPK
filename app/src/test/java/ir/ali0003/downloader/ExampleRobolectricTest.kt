package ir.ali0003.downloader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.ali0003.downloader.data.local.AppDatabase
import ir.ali0003.downloader.data.local.DownloadTaskEntity
import ir.ali0003.downloader.data.model.DownloadStatus
import ir.ali0003.downloader.data.repository.DownloadRepositoryImpl
import ir.ali0003.downloader.data.vault.VaultManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DownloadRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DownloadRepositoryImpl(db.downloadDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("0003 Downloader", appName)
    }

    @Test
    fun `test download repository CRUD and flows`() = runBlocking {
        val id = repository.enqueueDownload(
            url = "https://cdn.example.com/test.mp4",
            websiteUrl = "https://example.com",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            totalBytes = 1000L,
            isHidden = false
        )

        val item = repository.findDownloadById(id)
        assertNotNull(item)
        assertEquals("test.mp4", item?.fileName)
        assertEquals(DownloadStatus.QUEUED, item?.status)

        repository.updateProgress(id, 500L, 1000L, 100L)
        val updated = repository.findDownloadById(id)
        assertEquals(500L, updated?.downloadedBytes)

        val activeList = repository.activeDownloads.first()
        assertEquals(1, activeList.size)

        repository.setHidden(id, true)
        val publicList = repository.allPublicDownloads.first()
        assertEquals(0, publicList.size)

        val vaultList = repository.vaultDownloads.first()
        assertEquals(1, vaultList.size)
    }

    @Test
    fun `test vault manager pin authentication`() {
        val vaultManager = VaultManager(context)
        vaultManager.resetVault()
        assertFalse(vaultManager.isVaultConfigured())

        assertTrue(vaultManager.setupPin("1234"))
        assertTrue(vaultManager.isVaultConfigured())
        assertTrue(vaultManager.verifyPin("1234"))
        assertFalse(vaultManager.verifyPin("9999"))
    }
}

