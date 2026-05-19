package com.tscan.scanertestov.feature.batch

/**
 * Описание: съёмка бланка внутри приложения (CameraX) с оверлеем-ориентиром под пропорции A4.
 */
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tscan.scanertestov.ui.scanShellBackdrop
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
private const val TAG = "BatchDocumentCamera"

@Composable
fun BatchDocumentCameraRoute(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!permissionGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scanShellBackdrop(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Запрос доступа к камере…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var bound = false
        val listener = Runnable {
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.getSurfaceProvider())
                }
                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                imageCapture = capture
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture,
                )
                bound = true
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            imageCapture = null
            if (bound) {
                try {
                    cameraProvider?.unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        BatchDocumentCameraFrameOverlay(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), shape = CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = Color.White,
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 48.dp, end = 48.dp),
            color = Color.Black.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "Вместите чёрную рамку бланка в белый контур. Держите телефон параллельно листу.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        val shutterInteraction = remember { MutableInteractionSource() }
        val shutterPalette = mainMenuBubbleGradient(0)
        RealtimeBubbleIconButton(
            onClick = {
                imageCapture?.let { cap ->
                    val (file, uri) = createBatchCameraImageUri(context)
                    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                    cap.takePicture(
                        opts,
                        mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                Handler(Looper.getMainLooper()).post {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(BatchCaptureKeys.RESULT_URI, uri.toString())
                                    navController.popBackStack()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Capture failed", exception)
                                Handler(Looper.getMainLooper()).post {
                                    navController.popBackStack()
                                }
                            }
                        },
                    )
                }
            },
            contentDescription = "Снять",
            topColor = shutterPalette.first,
            bottomColor = shutterPalette.second,
            size = 72.dp,
            interactionSource = shutterInteraction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

private const val SHEET_W_TO_H = 595f / 842f

@Composable
private fun BatchDocumentCameraFrameOverlay(modifier: Modifier = Modifier) {
    val guide = Color.White.copy(alpha = 0.9f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val margin = 28.dp.toPx()
        val maxW = (w - margin * 2f).coerceAtLeast(40f)
        val maxH = (h - margin * 2f).coerceAtLeast(40f)
        var boxW = maxW
        var boxH = boxW / SHEET_W_TO_H
        if (boxH > maxH) {
            boxH = maxH
            boxW = boxH * SHEET_W_TO_H
        }
        val left = (w - boxW) / 2f
        val top = (h - boxH) / 2f
        val right = left + boxW
        val bottom = top + boxH

        val hole = RoundRect(
            rect = Rect(left, top, right, bottom),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        val path =
            Path().apply {
                addRect(Rect(0f, 0f, w, h))
                addRoundRect(hole)
                fillType = PathFillType.EvenOdd
            }
        drawPath(path = path, color = Color.Black.copy(alpha = 0.5f))

        val stroke = 4.dp.toPx()
        val arm = 36.dp.toPx()
        fun cornerL(x0: Float, y0: Float, dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
            drawLine(
                color = guide,
                start = Offset(x0, y0),
                end = Offset(x0 + dx1 * arm, y0 + dy1 * arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = guide,
                start = Offset(x0, y0),
                end = Offset(x0 + dx2 * arm, y0 + dy2 * arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        cornerL(left, top, 0f, 1f, 1f, 0f)
        cornerL(right, top, 0f, 1f, -1f, 0f)
        cornerL(right, bottom, 0f, -1f, -1f, 0f)
        cornerL(left, bottom, 0f, -1f, 1f, 0f)
    }
}
