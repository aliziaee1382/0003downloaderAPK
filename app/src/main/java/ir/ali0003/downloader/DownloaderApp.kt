package ir.ali0003.downloader
 
import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DownloaderApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initWebViewEnvironment()
        applicationScope.launch {
            initYoutubeDLEngine()
        }
    }

    private fun initWebViewEnvironment() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = Application.getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }

            // Ensure Chromium HTTP cache and Code Cache directories exist with valid hierarchy
            val cacheBase = File(cacheDir, "WebView/Default/HTTP Cache")
            val codeCacheDir = File(cacheBase, "Code Cache")
            val jsCacheDir = File(codeCacheDir, "js")
            val wasmCacheDir = File(codeCacheDir, "wasm")

            if (!cacheBase.exists()) cacheBase.mkdirs()
            if (!codeCacheDir.exists()) codeCacheDir.mkdirs()
            if (!jsCacheDir.exists()) jsCacheDir.mkdirs()
            if (!wasmCacheDir.exists()) wasmCacheDir.mkdirs()

            // Clean up any broken 0-byte or corrupted tmp cache nodes that trigger simple_file_enumerator failures
            try {
                jsCacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.length() == 0L || file.name.endsWith(".tmp"))) {
                        file.delete()
                    }
                }
                wasmCacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.length() == 0L || file.name.endsWith(".tmp"))) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "WebView environment initialization notice: ${e.message}")
        }
    }

    private fun initYoutubeDLEngine() {
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            Log.d(TAG, "YoutubeDL and FFmpeg successfully initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native libraries", e)
        }
    }

    companion object {
        private const val TAG = "YoutubeDL"
    }
}
