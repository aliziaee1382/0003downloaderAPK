package ir.ali0003.downloader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.ali0003.downloader.browser.viewmodel.BrowserViewModel
import ir.ali0003.downloader.ui.navigation.MainAppNavigation
import ir.ali0003.downloader.ui.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge system window rendering
        enableEdgeToEdge()

        // Configure system bars behavior for swipe-to-show navigation
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Hide system navigation bar safely
        hideSystemNavigationBar()

        // Auto-lock vault on background lifecycle event & re-enforce immersive mode on resume
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.lockVault()
                }
                Lifecycle.Event.ON_RESUME -> {
                    hideSystemNavigationBar()
                }
                else -> {}
            }
        })

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MainAppNavigation(
                    viewModel = viewModel,
                    browserViewModel = browserViewModel,
                    onReEnforceImmersive = { hideSystemNavigationBar() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemNavigationBar()
        }
    }

    fun hideSystemNavigationBar() {
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                val insets = ViewCompat.getRootWindowInsets(window.decorView)
                val isImeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                // Prevent interfering with active keyboard insets animations or SurfaceSyncGroup
                if (!isImeVisible) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
    }
}
