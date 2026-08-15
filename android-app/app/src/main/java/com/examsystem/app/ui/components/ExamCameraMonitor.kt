package com.examsystem.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.examsystem.app.util.AntiCheatViolation

/**
 * Optional proctoring preview (front camera). Requires CAMERA permission.
 */
@Composable
fun ExamCameraMonitor(
    enabled: Boolean,
    onViolation: (AntiCheatViolation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraBound by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) onViolation(AntiCheatViolation.CAMERA_OFF)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .size(width = 100.dp, height = 76.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!hasPermission) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Camera required", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    if (cameraBound) return@AndroidView
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview
                            )
                            cameraBound = true
                        } catch (_: Exception) {
                            onViolation(AntiCheatViolation.CAMERA_OFF)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.White, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(4.dp))
                Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag to move",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(14.dp)
            )
        }
    }
}

private val CameraPreviewWidth = 100.dp
private val CameraPreviewHeight = 76.dp

/** Floating, draggable camera preview — student can move it away from buttons or questions. */
@Composable
fun DraggableExamCameraOverlay(
    enabled: Boolean,
    onViolation: (AntiCheatViolation) -> Unit
) {
    if (!enabled) return

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2f)
    ) {
        val marginPx = with(density) { 12.dp.toPx() }
        val topBarPx = with(density) { 64.dp.toPx() }
        val bottomBarPx = with(density) { 88.dp.toPx() }
        val cameraWidthPx = with(density) { CameraPreviewWidth.toPx() }
        val cameraHeightPx = with(density) { CameraPreviewHeight.toPx() }

        val maxX = (constraints.maxWidth - cameraWidthPx - marginPx).coerceAtLeast(0f)
        val minY = topBarPx
        val maxY = (constraints.maxHeight - cameraHeightPx - bottomBarPx).coerceAtLeast(minY)

        var offsetX by remember(constraints.maxWidth, constraints.maxHeight) {
            mutableFloatStateOf(maxX)
        }
        var offsetY by remember(constraints.maxWidth, constraints.maxHeight) {
            mutableFloatStateOf(minY + marginPx)
        }

        LaunchedEffect(maxX, minY) {
            offsetX = offsetX.coerceIn(0f, maxX)
            offsetY = offsetY.coerceIn(minY, maxY)
        }

        ExamCameraMonitor(
            enabled = true,
            onViolation = onViolation,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(maxX, maxY, minY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                    }
                }
        )
    }
}
