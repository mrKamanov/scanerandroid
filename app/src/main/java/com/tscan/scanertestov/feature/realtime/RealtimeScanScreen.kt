package com.tscan.scanertestov.feature.realtime

/**
 * Описание: экран быстрой проверки — камера, стоп-кадр, OMR и исправления.
 */
import android.graphics.Bitmap
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BatchSheetAnswerMarkersOverlay
import com.tscan.scanertestov.ui.scanShellBackdrop
import com.tscan.scanertestov.feature.realtime.engine.RealtimeFrameConverters
import com.tscan.scanertestov.feature.realtime.engine.RealtimePreviewPipeline
import com.tscan.scanertestov.feature.realtime.engine.RealtimeSheetContour
import org.opencv.core.Point
import com.tscan.scanertestov.feature.realtime.recognition.RealtimeOmrRunner
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.feature.realtime.ui.RealtimeCameraSettingsPanel
import com.tscan.scanertestov.feature.realtime.ui.RealtimeCriteriaOverlay
import com.tscan.scanertestov.feature.realtime.ui.RealtimeEtalonGridOnPreview
import com.tscan.scanertestov.feature.realtime.ui.RealtimeEdgeDrawer
import com.tscan.scanertestov.feature.realtime.ui.RealtimeProcessingOverlay
import com.tscan.scanertestov.feature.realtime.ui.RealtimeScanBottomBar
import com.tscan.scanertestov.feature.realtime.ui.RealtimeScanActionButtons
import com.tscan.scanertestov.feature.realtime.RealtimeFixedCorrections
import com.tscan.scanertestov.feature.realtime.ui.RealtimeFixedCellHighlightOnPreview
import com.tscan.scanertestov.feature.realtime.ui.RealtimeFixedCorrectionsOverlay
import com.tscan.scanertestov.feature.realtime.ui.RealtimeScanResultOverlay
import com.tscan.scanertestov.feature.realtime.ui.padAnswerKey
import com.tscan.scanertestov.feature.realtime.ui.toggleChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "RealtimeScan"

private val RealtimeCameraWellShape = RoundedCornerShape(14.dp)
private val RealtimeCameraViewportShape = RoundedCornerShape(11.dp)

private val RealtimeChromePlatterHi = Color(0xFF10364E).copy(alpha = 0.48f)
private val RealtimeChromePlatterLo = Color(0xFF0B2738).copy(alpha = 0.42f)
private val RealtimeChromeDockHi = Color(0xFF123B58).copy(alpha = 0.50f)
private val RealtimeChromeDockLo = Color(0xFF0C2839).copy(alpha = 0.44f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeScanScreen(
    onAction: (RealtimeScanAction) -> Unit,
) {
    val context = LocalContext.current
    val gradingCriteria = remember(context) {
        AppSettingsStore.getGradingCriteria(context.applicationContext)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

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
                text = "Нужен доступ к камере",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var ui by remember { mutableStateOf(RealtimeScanUiState()) }
    val uiCurrent by rememberUpdatedState(ui)
    var rawOmrResult by remember { mutableStateOf<BatchOmrResult?>(null) }
    val fixedDecisions: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }
    var correctionsNotified by remember { mutableStateOf(false) }

    fun currentOmrConfig(): BatchOmrConfig = BatchOmrConfig(
        questionsCount = ui.questionsCount,
        choicesCount = ui.choicesCount,
        columnCount = ui.columnCount,
        answerKey = padAnswerKey(ui.answerKey, ui.questionsCount),
        strictScoring = ui.strictScoring,
    )

    val fixedDecisionsSnapshot = fixedDecisions.toMap()
    val displayResult = remember(
        rawOmrResult,
        fixedDecisionsSnapshot,
        ui.questionsCount,
        ui.choicesCount,
        ui.columnCount,
        ui.answerKey,
        ui.strictScoring,
    ) {
        val raw = rawOmrResult ?: return@remember null
        val config = BatchOmrConfig(
            questionsCount = ui.questionsCount,
            choicesCount = ui.choicesCount,
            columnCount = ui.columnCount,
            answerKey = padAnswerKey(ui.answerKey, ui.questionsCount),
            strictScoring = ui.strictScoring,
        )
        RealtimeFixedCorrections.applyDecisions(raw, config, fixedDecisionsSnapshot)
    }
    val pendingCorrectionsCount = rawOmrResult?.let {
        RealtimeFixedCorrections.unresolvedCount(it, fixedDecisionsSnapshot)
    } ?: 0
    val activeFixedCell = rawOmrResult?.fixedCells?.firstOrNull { fixed ->
        fixedDecisionsSnapshot[RealtimeFixedCorrections.keyFor(fixed)] == null
    }

    var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastSheetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastRawCameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frozenRawForOmr by remember { mutableStateOf<Bitmap?>(null) }
    var lastSheetContour by remember { mutableStateOf<Array<Point>?>(null) }
    var frozenSheetContour by remember { mutableStateOf<Array<Point>?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        var imageAnalysis: ImageAnalysis? = null
        var bound = false
        val listener = Runnable {
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis = analysis
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val s = uiCurrent
                        if (!s.cameraRunning || s.isProcessing || s.isPaused) {
                            return@setAnalyzer
                        }
                        val raw = RealtimeFrameConverters.imageProxyToBitmap(imageProxy)
                        val rotated = RealtimeFrameConverters.rotateBitmap(
                            raw,
                            imageProxy.imageInfo.rotationDegrees.toFloat(),
                        )
                        val w = previewView.width.coerceAtLeast(320)
                        val h = previewView.height.coerceAtLeast(240)
                        val sheetContour = RealtimeSheetContour.detect(
                            bitmap = rotated,
                            questionsCount = s.questionsCount,
                            choicesCount = s.choicesCount,
                        )
                        val preview = RealtimePreviewPipeline.processFrameWithOpenCV(
                            inputBitmap = rotated,
                            targetWidth = w,
                            targetHeight = h,
                            questionsCount = s.questionsCount,
                            choicesCount = s.choicesCount,
                            isGridVisible = false,
                            brightness = s.brightness,
                            contrast = s.contrast,
                            saturation = s.saturation,
                            sharpness = s.sharpness,
                            sheetContour = sheetContour,
                        )
                        val processed = preview.bitmap
                        val contourFound = preview.contourFound
                        if (rotated !== raw) {
                            raw.recycle()
                        }
                        mainExecutor.execute {
                            overlayBitmap = processed
                            ui = ui.copy(contourDetected = contourFound)
                            if (contourFound && sheetContour != null) {
                                lastSheetBitmap =
                                    processed.copy(processed.config ?: Bitmap.Config.ARGB_8888, false)
                                lastRawCameraBitmap?.recycle()
                                lastRawCameraBitmap =
                                    rotated.copy(rotated.config ?: Bitmap.Config.ARGB_8888, false)
                                lastSheetContour = RealtimeSheetContour.copy(sheetContour)
                            } else {
                                lastSheetBitmap = null
                                lastRawCameraBitmap?.recycle()
                                lastRawCameraBitmap = null
                                lastSheetContour = null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Кадр: ${e.message}", e)
                    } finally {
                        imageProxy.close()
                    }
                }
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider?.unbindAll()
                val cam = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    analysis,
                )
                camera = cam
                cam?.cameraControl?.enableTorch(ui.flashOn)
                bound = true
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            imageAnalysis?.clearAnalyzer()
            camera = null
            if (bound) {
                try {
                    cameraProvider?.unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(ui.flashOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(ui.flashOn)
        } catch (e: Exception) {
            Log.w(TAG, "Torch: ${e.message}")
        }
    }

    LaunchedEffect(pendingCorrectionsCount, rawOmrResult) {
        val raw = rawOmrResult
        if (
            raw != null &&
            raw.fixedCells.isNotEmpty() &&
            pendingCorrectionsCount == 0 &&
            !correctionsNotified
        ) {
            correctionsNotified = true
            Toast.makeText(context, "Проверка завершена", Toast.LENGTH_SHORT).show()
        }
    }

    fun runOmrOnFrozenSheet() {
        val frame = frozenRawForOmr ?: lastRawCameraBitmap
        val contour = frozenSheetContour ?: lastSheetContour
        if (frame == null || contour == null) {
            Toast.makeText(
                context,
                "Нет зафиксированного контура бланка. Сделайте стоп-кадр снова.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        scope.launch {
            ui = ui.copy(isProcessing = true, errorMessage = null, lastResult = null)
            fixedDecisions.clear()
            correctionsNotified = false
            try {
                val result = RealtimeOmrRunner.runOnCameraFrameWithContour(
                    context = context,
                    cameraFrame = frame,
                    sheetContour = contour,
                    questionsCount = ui.questionsCount,
                    choicesCount = ui.choicesCount,
                    columnCount = ui.columnCount,
                    answerKey = padAnswerKey(ui.answerKey, ui.questionsCount),
                    strictScoring = ui.strictScoring,
                )
                val crop = result.sheetCropBitmap
                if (crop != null) {
                    val previousOverlay = overlayBitmap
                    overlayBitmap = crop
                    if (previousOverlay != null &&
                        previousOverlay !== lastSheetBitmap &&
                        previousOverlay !== crop &&
                        !previousOverlay.isRecycled
                    ) {
                        previousOverlay.recycle()
                    }
                }
                rawOmrResult = result
                val initialDisplay = RealtimeFixedCorrections.applyDecisions(
                    result,
                    currentOmrConfig(),
                    fixedDecisions,
                )
                ui = ui.copy(isProcessing = false, lastResult = initialDisplay, isPaused = true)
                if (result.fixedCells.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "Обнаружены исправления на бланке — укажите, как их учесть.",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    correctionsNotified = true
                    Toast.makeText(
                        context,
                        "Проверка завершена",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "OMR", e)
                ui = ui.copy(
                    isProcessing = false,
                    isPaused = true,
                    errorMessage = e.message ?: "Ошибка OMR",
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scanShellBackdrop(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to RealtimeChromePlatterHi,
                                1f to RealtimeChromePlatterLo,
                            ),
                        ),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                IconButton(
                    onClick = { onAction(RealtimeScanAction.Back) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color(0xFFC8DCEB),
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.weight(1f))
                RealtimeBubbleIconButton(
                    onClick = { ui = ui.copy(flashOn = !ui.flashOn) },
                    contentDescription = if (ui.flashOn) "Выключить вспышку" else "Включить вспышку",
                    topColor = if (ui.flashOn) Color(0xFFFFD47F) else Color(0xFFA8B3C7),
                    bottomColor = if (ui.flashOn) Color(0xFFF5A93E) else Color(0xFF73829A),
                    size = 48.dp,
                ) {
                    Icon(
                        imageVector = if (ui.flashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                RealtimeBubbleIconButton(
                    onClick = { ui = ui.copy(cameraRunning = !ui.cameraRunning) },
                    contentDescription = if (ui.cameraRunning) "Остановить камеру" else "Включить камеру",
                    topColor = if (ui.cameraRunning) Color(0xFF8ADDFE) else Color(0xFFA8B3C7),
                    bottomColor = if (ui.cameraRunning) Color(0xFF2EA3F3) else Color(0xFF73829A),
                    size = 48.dp,
                ) {
                    Icon(
                        imageVector = if (ui.cameraRunning) Icons.Default.PhotoCamera else Icons.Default.NoPhotography,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
                }
            }

            /* Зона камеры: внешняя подложка + чуть темнее «окно» превью. */
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clip(RealtimeCameraWellShape)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to RealtimeChromePlatterHi,
                                1f to RealtimeChromePlatterLo,
                            ),
                        ),
                        shape = RealtimeCameraWellShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RealtimeCameraViewportShape)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color(0xFF0A273C).copy(alpha = 0.92f),
                                    1f to Color(0xFF051523).copy(alpha = 0.95f),
                                ),
                            ),
                            shape = RealtimeCameraViewportShape,
                        ),
                ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0f),
                )
                val baseBmp = overlayBitmap
                val previewBitmap = remember(
                    baseBmp,
                    displayResult,
                    ui.questionsCount,
                    ui.choicesCount,
                    ui.columnCount,
                    ui.answerKey,
                    ui.strictScoring,
                ) {
                    val bmp = baseBmp ?: return@remember null
                    val result = displayResult
                    if (result == null || result.questionScores.isEmpty()) {
                        bmp
                    } else {
                        val config = BatchOmrConfig(
                            questionsCount = ui.questionsCount,
                            choicesCount = ui.choicesCount,
                            columnCount = ui.columnCount,
                            answerKey = padAnswerKey(ui.answerKey, ui.questionsCount),
                            strictScoring = ui.strictScoring,
                        )
                        BatchSheetAnswerMarkersOverlay.renderMarkersOnCrop(
                            sourceWithGrid = bmp,
                            config = config,
                            predictions = result.predictions,
                        )
                    }
                }
                DisposableEffect(previewBitmap, baseBmp) {
                    onDispose {
                        val marked = previewBitmap
                        if (marked != null && marked !== baseBmp && !marked.isRecycled) {
                            marked.recycle()
                        }
                    }
                }
                previewBitmap?.let { shownBmp ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = shownBmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            /* Fit — весь бланк без обрезки; поля по краям остаются чёрными, без «живой» камеры. */
                            contentScale = ContentScale.Fit,
                        )
                        if (
                            pendingCorrectionsCount > 0 &&
                            activeFixedCell != null &&
                            baseBmp != null
                        ) {
                            RealtimeFixedCellHighlightOnPreview(
                                sheetBitmap = baseBmp,
                                questionsCount = ui.questionsCount,
                                choicesCount = ui.choicesCount,
                                columnCount = ui.columnCount,
                                questionIndex = activeFixedCell.questionIndex,
                                choiceIndex = activeFixedCell.choiceIndex,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (ui.gridVisibleOnWarp && baseBmp != null) {
                            RealtimeEtalonGridOnPreview(
                                bitmapWidthPx = baseBmp.width,
                                bitmapHeightPx = baseBmp.height,
                                questionsCount = ui.questionsCount,
                                choicesCount = ui.choicesCount,
                                answerKey = ui.answerKey,
                                onToggleCell = { q, c ->
                                    ui = ui.copy(
                                        answerKey = toggleChoice(
                                            ui.answerKey,
                                            q,
                                            c,
                                            ui.choicesCount,
                                            ui.questionsCount,
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        displayResult?.let { result ->
                            if (result.questionScores.isNotEmpty()) {
                                RealtimeScanResultOverlay(
                                    result = result,
                                    gradingCriteria = gradingCriteria,
                                    showGradeStamp = pendingCorrectionsCount == 0,
                                    pendingCorrectionsCount = pendingCorrectionsCount,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to RealtimeChromeDockHi,
                                1f to RealtimeChromeDockLo,
                            ),
                        ),
                    )
                    .padding(top = 10.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sheetReady = ui.contourDetected &&
                    lastSheetBitmap != null &&
                    lastRawCameraBitmap != null &&
                    lastSheetContour != null
                RealtimeScanActionButtons(
                    contourReady = sheetReady,
                    isPaused = ui.isPaused,
                    isProcessing = ui.isProcessing,
                    canRunCheck = ui.isPaused && frozenRawForOmr != null && frozenSheetContour != null,
                    onFreezeClick = {
                        when {
                            ui.isProcessing -> Unit
                            ui.isPaused -> {
                                ui = ui.copy(
                                    isPaused = false,
                                    lastResult = null,
                                    contourDetected = false,
                                )
                                rawOmrResult = null
                                fixedDecisions.clear()
                                correctionsNotified = false
                                frozenRawForOmr?.recycle()
                                frozenRawForOmr = null
                                frozenSheetContour = null
                                lastSheetBitmap = null
                                lastRawCameraBitmap?.recycle()
                                lastRawCameraBitmap = null
                                lastSheetContour = null
                                overlayBitmap?.recycle()
                                overlayBitmap = null
                                Toast.makeText(context, "Съёмка возобновлена", Toast.LENGTH_SHORT).show()
                            }
                            sheetReady -> {
                                frozenRawForOmr?.recycle()
                                val rawFrame = lastRawCameraBitmap
                                val contourFrame = lastSheetContour
                                frozenRawForOmr = rawFrame?.copy(
                                    rawFrame.config ?: Bitmap.Config.ARGB_8888,
                                    false,
                                )
                                frozenSheetContour = contourFrame?.let { RealtimeSheetContour.copy(it) }
                                ui = ui.copy(isPaused = true, lastResult = null)
                                Toast.makeText(
                                    context,
                                    "Кадр зафиксирован. Проверьте чёткость или нажмите «Проверить».",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    context,
                                    "Контур бланка не найден. Поднесите бланк к камере.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onRunCheckClick = {
                        if (!ui.isPaused) {
                            Toast.makeText(
                                context,
                                "Сначала зафиксируйте кадр.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else if (frozenRawForOmr != null && frozenSheetContour != null) {
                            runOmrOnFrozenSheet()
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                RealtimeScanBottomBar(
                    gridVisible = ui.gridVisibleOnWarp,
                    onUpdateAnswers = {
                        ui = ui.copy(
                            answerKey = padAnswerKey(ui.answerKey, ui.questionsCount),
                            showCriteriaPanel = true,
                        )
                        Toast.makeText(context, "Эталонные ответы обновлены", Toast.LENGTH_SHORT).show()
                    },
                    onOpenGridEtalon = {
                        ui = ui.copy(gridVisibleOnWarp = !ui.gridVisibleOnWarp)
                    },
                    onCameraSettings = {
                        ui = ui.copy(showEndDrawer = true)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        RealtimeEdgeDrawer(
            visible = ui.showEndDrawer,
            alignEnd = true,
            onDismiss = { ui = ui.copy(showEndDrawer = false) },
        ) {
            RealtimeCameraSettingsPanel(
                brightness = ui.brightness,
                contrast = ui.contrast,
                saturation = ui.saturation,
                sharpness = ui.sharpness,
                onBrightnessChange = { ui = ui.copy(brightness = it) },
                onContrastChange = { ui = ui.copy(contrast = it) },
                onSaturationChange = { ui = ui.copy(saturation = it) },
                onSharpnessChange = { ui = ui.copy(sharpness = it) },
                onReset = {
                    ui = ui.copy(
                        brightness = 0,
                        contrast = 100,
                        saturation = 100,
                        sharpness = 50,
                    )
                },
                headerClose = {
                    RealtimeBubbleIconButton(
                        onClick = { ui = ui.copy(showEndDrawer = false) },
                        contentDescription = "Закрыть настройки",
                        topColor = Color(0xFFA8B3C7),
                        bottomColor = Color(0xFF73829A),
                        size = 44.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        }

        if (ui.isProcessing) {
            RealtimeProcessingOverlay()
        }

        rawOmrResult?.let { raw ->
            if (raw.fixedCells.isNotEmpty() && pendingCorrectionsCount > 0) {
                (overlayBitmap ?: lastSheetBitmap)?.let { sheet ->
                    RealtimeFixedCorrectionsOverlay(
                        fixedCells = raw.fixedCells,
                        decisions = fixedDecisionsSnapshot,
                        sheetBitmap = sheet,
                        questionsCount = ui.questionsCount,
                        choicesCount = ui.choicesCount,
                        columnCount = ui.columnCount,
                        onMarkAnswered = { key -> fixedDecisions[key] = true },
                        onMarkEmpty = { key -> fixedDecisions[key] = false },
                    )
                }
            }
        }

        ui.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(12.dp),
            )
            LaunchedEffect(msg) {
                delay(3200)
                ui = ui.copy(errorMessage = null)
            }
        }

        RealtimeCriteriaOverlay(
            visible = ui.showCriteriaPanel,
            strictScoring = ui.strictScoring,
            questionsCount = ui.questionsCount,
            choicesCount = ui.choicesCount,
            onDismiss = { ui = ui.copy(showCriteriaPanel = false) },
            onStrictChange = { s -> ui = ui.copy(strictScoring = s) },
            onApply = { ui = ui.copy(showCriteriaPanel = false) },
            onReset = {
                ui = ui.copy(
                    answerKey = List(ui.questionsCount) { emptySet() },
                )
            },
            onDecQuestions = {
                val n = (ui.questionsCount - 1).coerceIn(2, 20)
                ui = ui.copy(
                    questionsCount = n,
                    answerKey = padAnswerKey(ui.answerKey, n),
                )
            },
            onIncQuestions = {
                val n = (ui.questionsCount + 1).coerceIn(2, 20)
                ui = ui.copy(
                    questionsCount = n,
                    answerKey = padAnswerKey(ui.answerKey, n),
                )
            },
            onDecChoices = {
                val n = (ui.choicesCount - 1).coerceIn(2, 5)
                ui = ui.copy(choicesCount = n)
            },
            onIncChoices = {
                val n = (ui.choicesCount + 1).coerceIn(2, 5)
                ui = ui.copy(choicesCount = n)
            },
        )
    }
}
