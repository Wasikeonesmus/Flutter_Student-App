package com.examsystem.app.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Immersive fullscreen, FLAG_SECURE (screenshots), and lifecycle leave-app detection.
 */
@Composable
fun ExamAntiCheatEffect(
    config: AntiCheatConfig,
    onViolation: (AntiCheatViolation) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(config.fullscreen, config.blockScreenshot, activity) {
        val window = activity?.window
        if (window != null) {
            if (config.fullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            if (config.blockScreenshot) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
        }
        onDispose {
            window?.let { w ->
                if (config.blockScreenshot) {
                    w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (config.fullscreen) {
                    WindowCompat.setDecorFitsSystemWindows(w, true)
                    WindowInsetsControllerCompat(w, w.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, config.detectLeaveApp) {
        if (!config.detectLeaveApp) return@DisposableEffect onDispose {}
        // ON_STOP only — ON_PAUSE also fires for overlays and duplicates with ON_STOP.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onViolation(AntiCheatViolation.LEFT_APP)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity, config.detectLeaveApp) {
        if (!config.detectLeaveApp || activity == null) return@DisposableEffect onDispose {}
        if (activity.isInMultiWindowMode) {
            onViolation(AntiCheatViolation.SPLIT_SCREEN)
        }
        onDispose {}
    }
}
