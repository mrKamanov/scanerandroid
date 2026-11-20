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
import android.widget.Toast
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.view.Surface
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import com.tscan.scanertestov.ui.scanShellBackdrop
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
private const val TAG = "BatchDocumentCamera"
private const val BATCH_CAMERA_CROP_MAX_DIM = 4096

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
    var shotsAdded by remember { mutableIntStateOf(0) }
    // Обратная связь при съёмке: вспышка сразу после нажатия, счётчик «сохраняется»,
    // миниатюра последнего снимка и плашка «Снято — наводите на следующий».
    var shotsPending by remember { mutableIntStateOf(0) }
    var lastThumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var confirmTick by remember { mutableIntStateOf(0) }
    var confirmVisible by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(confirmTick) {
        if (confirmTick == 0) return@LaunchedEffect
        confirmVisible = true
        delay(1800)
        confirmVisible = false
    }
    var guideGeom by remember { mutableStateOf<IntArray?>(null) }

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
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val capture =
                    ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(rotation)
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
        BatchDocumentCameraFrameOverlay(
            modifier = Modifier.fillMaxSize(),
            onGuideGeometry = { vw, vh, mp -> guideGeom = intArrayOf(vw, vh, mp) },
        )
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
                text = buildString {
                    append("Вместите чёрную рамку бланка в белый контур. Держите телефон параллельно листу.")
                },
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    shotsPending++
                    scope.launch {
                        flash.snapTo(0.75f)
                        flash.animateTo(0f, tween(320))
                    }
                    // Запоминаем получателя сразу: если пользователь нажмёт «Назад», пока снимок
                    // ещё обрезается, previousBackStackEntry уже будет указывать на другой экран.
                    val targetHandle = navController.previousBackStackEntry?.savedStateHandle
                    val (file, uri) = createBatchCameraImageUri(context)
                    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                    cap.takePicture(
                        opts,
                        mainExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val geom = guideGeom
                                val worker = Thread {
                                    try {
                                        if (geom != null) {
                                            val bmp = decodeUprightSampled(file, BATCH_CAMERA_CROP_MAX_DIM)
                                            if (bmp != null) {
                                                val cropped = cropCameraShotByGuide(bmp, geom[0], geom[1], geom[2])
                                                if (cropped !== bmp) {
                                                    val ok = writeJpegOverwrite(file, cropped)
                                                    Log.i(
                                                        TAG,
                                                        "shot cropped ${bmp.width}x${bmp.height} -> " +
                                                            "${cropped.width}x${cropped.height} ok=$ok",
                                                    )
                                                }
                                                bmp.recycle()
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        Log.w(TAG, "crop skipped, keep full shot", t)
                                    }
                                    val thumb = runCatching { decodeUprightSampled(file, 320) }.getOrNull()
                                    Handler(Looper.getMainLooper()).post {
                                        val handle = targetHandle
                                        if (handle != null) {
                                            val prev = handle.get<ArrayList<String>>(BatchCaptureKeys.RESULT_URIS)
                                            val next = ArrayList<String>(prev?.size?.plus(1) ?: 1)
                                            if (prev != null) next.addAll(prev)
                                            next.add(uri.toString())
                                            handle[BatchCaptureKeys.RESULT_URIS] = next
                                        }
                                        shotsAdded++
                                        shotsPending = (shotsPending - 1).coerceAtLeast(0)
                                        if (thumb != null) lastThumb = thumb
                                        confirmTick++
                                    }
                                }
                                worker.isDaemon = true
                                worker.start()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Capture failed", exception)
                                file.delete()
                                shotsPending = (shotsPending - 1).coerceAtLeast(0)
                                // Раньше ошибка одного кадра закрывала камеру (а при запоздалом
                                // срабатывании — и сам экран пакета). Теперь просто сообщаем.
                                Toast.makeText(
                                    context,
                                    "Не удалось сделать снимок, попробуйте ещё раз",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
        // Счётчик и последний снимок — слева от кнопки; «Готово» — справа.
        if (shotsAdded > 0 || shotsPending > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 28.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 58.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val t = lastThumb
                    if (t != null) {
                        Image(
                            bitmap = t.asImageBitmap(),
                            contentDescription = "Последний снимок",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
                    }
                    if (shotsPending > 0) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text("$shotsAdded", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        if (shotsPending > 0) "сохраняю…" else "снято",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (shotsAdded > 0) {
            Surface(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 34.dp),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "Готово",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = confirmVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center),
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xE62F6B4F), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                Column {
                    Text("Снимок $shotsAdded сохранён", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Наводите на следующий бланк", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
        // Вспышка — сразу после нажатия, чтобы было видно, что кадр сделан.
        if (flash.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(flash.value)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun BatchDocumentCameraFrameOverlay(
    modifier: Modifier = Modifier,
    onGuideGeometry: (viewW: Int, viewH: Int, marginPx: Int) -> Unit = { _, _, _ -> },
) {
    val guide = Color.White.copy(alpha = 0.9f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val margin = 28.dp.toPx()
        onGuideGeometry(w.toInt(), h.toInt(), margin.toInt())
        val box = a4GuideBox(w, h, margin)
        val left = box[0]
        val top = box[1]
        val right = box[2]
        val bottom = box[3]

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
