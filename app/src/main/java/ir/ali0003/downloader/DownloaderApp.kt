package ir.ali0003.downloader
 
import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import java.io.File

class DownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initWebViewEnvironment()
        Log.i(TAG, "DownloaderApp initialized with lean native pipeline")
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

    companion object {
        private const val TAG = "DownloaderApp"
    }
}
