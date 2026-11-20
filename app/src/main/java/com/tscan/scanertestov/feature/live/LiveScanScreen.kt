package com.tscan.scanertestov.feature.live

/**
 * Описание: экран «Один бланк» — живая проверка. Навели камеру на бланк v2 → лист обводится рамкой,
 * замер на полсекунды → проверяется (OpenCV или нейросеть) → результат рисуется прямо на листе
 * («На листе») или выпрямленным бланком («Крупно»). Убрали лист и положили следующий — проверяется новый.
 */
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.ui.theme.Scrapbook
import com.tscan.scanertestov.ui.components.stickerShadow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchGridLocator
import com.tscan.scanertestov.feature.batch.engine.BlankV2Markers
import com.tscan.scanertestov.feature.realtime.engine.RealtimeFrameConverters
import com.tscan.scanertestov.feature.realtime.engine.RealtimeOpenCvBootstrap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as OcvPoint
import org.opencv.core.Size as OcvSize
import org.opencv.imgproc.Imgproc
import org.opencv.core.Mat
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val TAG = "LiveScan"

/**
 * Неподвижности не ждём: положение листа считается по меткам заново в каждом кадре, дрожь руки не мешает.
 * Отсекаем только сильное движение (смаз): углы сетки сдвинулись между кадрами больше этой доли кадра.
 */
private const val CALM_SHIFT = 0.02
/** Резкость кадра — не меньше этой доли от лучшей за последнее время ... */
private const val SHARP_SHARE = 0.75
/** ... «лучшая» забывается так (за кадр; ~1 с при 25 кадр/с). */
private const val SHARP_DECAY = 0.96
/** Не чаще одного прохода за это время (телефон не перегревается). */
private const val PASS_GAP_MS = 200L
/** Больше стольких проходов на лист не делаем, даже если не совпали. */
private const val MAX_PASSES = 4
/** Лист заменили на месте: отпечаток отличается хотя бы в стольких точках ... */
private const val CHANGE_POINTS = 3
/** ... столько кадров подряд (одиночный блик или тень не сбросят результат). */
private const val CHANGE_FRAMES = 3
/** Метки пропали дольше этого — лист убрали, ждём следующий. */
private const val LOST_MS = 700L
/** Углы прыгнули больше чем на эту долю — положили другой лист. */
private const val JUMP_SHIFT = 0.18

private enum class LiveView(val title: String) { OnSheet("На листе"), Large("Крупно") }

/** Кадр камеры (уже повёрнутый, уменьшенный для экрана) и где на нём лист — из ОДНОГО кадра. */
private class LiveFrame(val bitmap: Bitmap, val track: LiveTrack?)

/** Длинная сторона кадра, который показываем на экране и по которому следим за листом. */
private const val VIEW_SIDE = 960.0
/** Метки пропали ненадолго (смаз, палец) — рисунок держим на месте, а не прячем. */
private const val HOLD_MS = 300L


private class LiveController {
    @Volatile var preset: BatchCriteriaPreset? = null
    @Volatile var method: LiveMethod = LiveMethod.OpenCv
    @Volatile var gridHeight: Double = 0.0
    @Volatile var probes: List<Pair<Double, Double>> = emptyList()
    @Volatile var gridWidth: Double = com.tscan.scanertestov.feature.batch.engine.BlankV2Markers.PILOT_GRID_WIDTH
    /** Поправка «метки → сетка» для текущего теста (из памяти или с последней проверки). */
    @Volatile var correction: DoubleArray? = null
    var correctionFor: String? = null
    @Volatile var busy = false
    /** Пауза: кадры не обрабатываются, последний кадр и результат остаются на экране. */
    @Volatile var paused = false
    @Volatile var recheck = false
    /** Проходы текущего листа; лист «закреплён» — два последних совпали (или проходов достаточно). */
    val history = ArrayList<LiveResult>()
    @Volatile var locked = false
    var lastCorners: DoubleArray? = null
    var lostSince = 0L
    /** Сглаженные углы сетки (доли кадра) для рисунка. */
    var smooth: DoubleArray? = null
    /** Слежение: оптический поток + метки для поправки. Только в потоке анализа. */
    val follower = LiveSheetFollower()
    /** Фильтр «One Euro» для 8 координат углов сетки (доли кадра). */
    val euro = Array(8) { OneEuro(minCutoff = 1.2, beta = 25.0) }
    var lastDraw: LiveTrack? = null
    var lastDrawAt = 0L
    /** Кольцо кадров для экрана: пишем в тот, что показан два кадра назад. */
    val ring = arrayOfNulls<Bitmap>(3)
    var ringIndex = 0
    @Volatile var lastPassEnd = 0L
    /** Отпечаток кадра, по которому сделан первый удачный проход, и кадра текущей проверки. */
    @Volatile var refDark: Set<Int>? = null
    /** Отпечаток заметно изменился — нужен контрольный проход (результат пока не трогаем). */
    @Volatile var suspect = false
    @Volatile var passDark: Set<Int> = emptySet()
    var changeFrames = 0
    var statDiff = 0
    @Volatile var sharpNow = 0.0
    @Volatile var sharpMax = 0.0
    // Замер слежения для журнала.
    var statFrames = 0
    var statTracked = 0
    var statMs = 0L
    var statSince = 0L

    fun newSheet() {
        synchronized(history) { history.clear() }
        locked = false
        refDark = null
        changeFrames = 0
        suspect = false
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("live_scan", Context.MODE_PRIVATE)

@Composable
fun LiveScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    var permission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }

    val presets = remember { BatchCriteriaRepository(app).getAll() }
    var preset by remember {
        val savedId = prefs(app).getString("test", null)
        mutableStateOf(presets.firstOrNull { it.id == savedId } ?: presets.firstOrNull())
    }
    // Проверка — нейросетью: она различает исправления (OpenCV по доле чернил — нет).
    val method = LiveMethod.Model
    var view by remember { mutableStateOf(LiveView.OnSheet) }
    var paused by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<LiveFrame?>(null) }
    val track = frame?.track
    var result by remember { mutableStateOf<LiveResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    var testMenu by remember { mutableStateOf(false) }

    val controller = remember { LiveController() }
    controller.preset = preset
    controller.method = method
    controller.paused = paused
    preset?.let { p ->
        if (controller.correctionFor != p.id) {
            controller.correctionFor = p.id
            controller.correction = loadCorrection(app, p.id)
            controller.follower.correction = controller.correction
        }
    }
    // Высота сетки нужна слежению: по ней стоят нижние метки. После первой проверки берём точную.
    controller.gridHeight = result?.geometry?.height?.toDouble()
        ?: preset?.let { p -> p.liveGeometry().height.toDouble() } ?: 0.0
    val probeGeo = result?.geometry ?: preset?.let { p -> p.liveGeometry() }
    probeGeo?.let { controller.gridWidth = it.width.toDouble() }
    controller.probes = remember(probeGeo) {
        val g = probeGeo ?: return@remember emptyList()
        g.bubbles.filter { !it.placeholder }.map { it.cx.toDouble() to it.cy.toDouble() } +
            BlankV2Markers.VARIANT_CENTERS +
            (0 until BlankV2Markers.CODE_BITS).map { k -> BlankV2Markers.codeSquare(k).let { (it[0] + it[2]) / 2 to (it[1] + it[3]) / 2 } }
    }

    if (!permission) {
        Box(Modifier.fillMaxSize().background(Color(0xFF16222C)), contentAlignment = Alignment.Center) {
            Text("Нужен доступ к камере", color = Color.White)
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Проверка — фоном с пониженным приоритетом: картинка и слежение важнее.
    val recognizeExecutor = remember {
        Executors.newSingleThreadExecutor { r ->
            Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "live-recognize")
        }
    }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        future.addListener({
            try {
                if (!RealtimeOpenCvBootstrap.ensureLoaded()) {
                    Log.e(TAG, "OpenCV не загрузился")
                    return@addListener
                }
                provider = future.get()
                // Отдельного видео нет: показываем те же кадры, по которым ищем лист, — рисунок не может отстать.
                val aspect = AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                val a = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(aspect)
                            // Полный HD: метки находятся по уменьшенной копии, а для кружков нужна чёткость.
                            .setResolutionStrategy(ResolutionStrategy(AndroidSize(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = a
                a.setAnalyzer(analysisExecutor) { image ->
                    try {
                        val p = controller.preset
                        val gh = controller.gridHeight
                        if (p == null || gh <= 0.0 || controller.paused) return@setAnalyzer
                        val tf = SystemClock.uptimeMillis()
                        val plane = image.planes[0]
                        val rgba = Mat(image.height, image.width, CvType.CV_8UC4, plane.buffer, plane.rowStride.toLong())
                        val rotation = image.imageInfo.rotationDegrees
                        // Кадр для экрана и слежения: уменьшенный и повёрнутый «как держат телефон».
                        val disp = Mat()
                        val gray = Mat()
                        val k = min(1.0, VIEW_SIDE / max(image.width, image.height))
                        Imgproc.resize(rgba, disp, OcvSize(), k, k, Imgproc.INTER_AREA)
                        rotateUpright(disp, rotation)
                        Imgproc.cvtColor(disp, gray, Imgproc.COLOR_RGBA2GRAY)
                        val t = try {
                            controller.follower.step(gray, gh, controller.probes, controller.gridWidth).also { tr ->
                                // Резкость листа: проверяем только кадры не хуже самых резких за последнюю секунду.
                                val sh = tr?.let { sheetSharpness(gray, it) } ?: 0.0
                                controller.sharpNow = sh
                                controller.sharpMax = max(controller.sharpMax * SHARP_DECAY, sh)
                            }
                        } finally { gray.release() }
                        val bmpIdx = controller.ringIndex
                        controller.ringIndex = (bmpIdx + 1) % controller.ring.size
                        val bmp = controller.ring[bmpIdx]?.takeIf { it.width == disp.cols() && it.height == disp.rows() }
                            ?: Bitmap.createBitmap(disp.cols(), disp.rows(), Bitmap.Config.ARGB_8888).also { controller.ring[bmpIdx] = it }
                        Utils.matToBitmap(disp, bmp)
                        disp.release()
                        // Сглаженное положение для рисунка (на том же кадре).
                        val nowDraw = SystemClock.uptimeMillis()
                        val tDraw = if (t != null) {
                            smoothTrack(controller, t, p, controller.follower.gridHeightUsed.takeIf { h -> h > 0 } ?: gh)
                                .also { controller.lastDraw = it; controller.lastDrawAt = nowDraw }
                        } else if (nowDraw - controller.lastDrawAt <= HOLD_MS) {
                            controller.lastDraw
                        } else {
                            controller.smooth = null
                            controller.lastDraw = null
                            null
                        }
                        val now = SystemClock.uptimeMillis()
                        controller.statFrames++
                        if (t != null) controller.statTracked++
                        controller.statMs += now - tf
                        if (controller.statSince == 0L) controller.statSince = now
                        if (now - controller.statSince >= 2000) {
                            val sec = (now - controller.statSince) / 1000.0
                            logLine(app, "слежение: ${"%.1f".format(controller.statFrames / sec)} кадр/с, с метками ${controller.statTracked}/${controller.statFrames}, ${controller.statMs / controller.statFrames.coerceAtLeast(1)} мс/кадр, кадр ${image.width}x${image.height}, отличие отпечатка до ${controller.statDiff}, источник: ${controller.follower.lastSource}")
                            controller.statDiff = 0
                            controller.statFrames = 0; controller.statTracked = 0; controller.statMs = 0; controller.statSince = now
                        }
                        var clear = false
                        if (t == null) {
                            if (controller.lastCorners != null && controller.lostSince == 0L) controller.lostSince = now
                            if (controller.lostSince != 0L && now - controller.lostSince > LOST_MS) {
                                clear = true
                                controller.newSheet()
                                controller.lastCorners = null
                                controller.lostSince = 0L
                            }
                        } else {
                            controller.lostSince = 0L
                            val corners = gridCorners(t, p, controller.follower.gridHeightUsed.takeIf { it > 0 } ?: gh)
                            val prev = controller.lastCorners
                            val shift = if (prev == null || corners == null) 1.0 else corners.indices.maxOf { abs(corners[it] - prev[it]) }
                            if (prev != null && shift > JUMP_SHIFT) {
                                clear = true
                                controller.newSheet()
                            }
                            controller.lastCorners = corners
                            // Другой лист на том же месте (сняли верхний из стопки): отпечаток не тот.
                            val ref = controller.refDark
                            if (ref != null && !clear) {
                                val diff = (ref - t.dark).size + (t.dark - ref).size
                                if (diff > controller.statDiff) controller.statDiff = diff
                                controller.changeFrames = if (diff >= CHANGE_POINTS) controller.changeFrames + 1 else 0
                                if (controller.changeFrames >= CHANGE_FRAMES && !controller.suspect) {
                                    // Сам отпечаток шумит (тень руки, смаз) — результат не сбрасываем,
                                    // а проверяем лист ещё раз: новый он или тот же, решит распознавание.
                                    logLine(app, "похоже на другой лист: отличие отпечатка $diff — контрольная проверка")
                                    controller.suspect = true
                                    controller.changeFrames = 0
                                }
                            }
                            val sharp = controller.sharpNow >= controller.sharpMax * SHARP_SHARE
                            val calm = shift < CALM_SHIFT && sharp
                            val passes = synchronized(controller.history) { controller.history.size }
                            val want = controller.recheck || controller.suspect || (!controller.locked && passes < MAX_PASSES)
                            if (calm && want && !controller.busy && now - controller.lastPassEnd >= PASS_GAP_MS) {
                                controller.busy = true
                                if (controller.recheck) { controller.newSheet(); controller.recheck = false }
                                val verify = controller.suspect && passes > 0
                                controller.suspect = false
                                val frameDark = t.dark
                                // Полный кадр для проверки — сразу в цвете, без сжатия в JPEG.
                                val full = rgba.clone()
                                rotateUpright(full, rotation)
                                val shot = Bitmap.createBitmap(full.cols(), full.rows(), Bitmap.Config.ARGB_8888)
                                Utils.matToBitmap(full, shot)
                                full.release()
                                val m = controller.method
                                mainExecutor.execute { checking = true }
                                recognizeExecutor.execute {
                                    val r = LiveRecognizer.recognize(app, shot, p, m, controller.correction)
                                    r.markerCorrection?.let { d ->
                                        if (r.error == null && plausibleCorrection(d) && !sameCorrection(d, controller.correction)) {
                                            controller.correction = d
                                            controller.follower.correction = d
                                            saveCorrection(app, p.id, d)
                                            logLine(app, "поправка «метки → сетка» обновлена")
                                        }
                                    }
                                    if ((app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) saveDebugFrame(app, shot)
                                    shot.recycle()
                                    logLine(app, "проверка ${m.title}${if (verify) " (контрольная)" else ""}: ${r.ms} мс [${r.stages}], ${r.correct}/${r.total}, вар=${r.variant} код=${r.code} ${r.error ?: ""}")
                                    val shown: LiveResult? = if (r.error != null) {
                                        // Смазанный кадр: просто берём следующий. Ошибку показываем, только если результата ещё нет.
                                        synchronized(controller.history) { if (controller.history.isEmpty()) r else null }
                                    } else {
                                        val merged = synchronized(controller.history) {
                                            if (verify && controller.history.isNotEmpty()) {
                                                val cur = LiveRecognizer.merge(controller.history)
                                                if (isOtherSheet(cur, r)) {
                                                    logLine(app, "лист заменён (подтвердила проверка)")
                                                    controller.history.clear()
                                                    controller.locked = false
                                                } else {
                                                    logLine(app, "тот же лист — результат оставлен")
                                                }
                                                // Отпечаток — с этого кадра: так тот же шум не будет срабатывать снова.
                                                controller.refDark = frameDark
                                            }
                                            if (controller.refDark == null) controller.refDark = frameDark
                                            controller.history += r
                                            LiveRecognizer.merge(controller.history)
                                        }
                                        if (merged.confirmed || merged.passes >= MAX_PASSES) controller.locked = true
                                        merged.copy(confirmed = merged.confirmed || controller.locked)
                                    }
                                    controller.lastPassEnd = SystemClock.uptimeMillis()
                                    controller.busy = false
                                    mainExecutor.execute {
                                        checking = false
                                        if (shown != null) result = shown
                                    }
                                }
                            }
                        }
                        rgba.release()
                        val f = LiveFrame(bmp, tDraw)
                        mainExecutor.execute {
                            frame = f
                            if (clear) result = null
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "кадр: ${e.message}", e)
                    } finally {
                        image.close()
                    }
                }
                provider?.unbindAll()
                camera = provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, a)
            } catch (e: Throwable) {
                Log.e(TAG, "камера: ${e.message}", e)
            }
        }, mainExecutor)
        onDispose {
            analysis?.clearAnalyzer()
            runCatching { provider?.unbindAll() }
            analysisExecutor.shutdown()
            recognizeExecutor.shutdown()
            camera = null
        }
    }
    LaunchedEffect(flash, camera) { runCatching { camera?.cameraControl?.enableTorch(flash) } }

    val hand = remember { runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, com.tscan.scanertestov.R.font.amatic_sc_bold) }.getOrNull() }

    Box(Modifier.fillMaxSize().background(Scrapbook.Board)) {
        val r = result
        val fr = frame
        // ——— Кадр камеры и рисунок — в одном Canvas и из одного кадра.
        Canvas(Modifier.fillMaxSize()) {
            val img = fr?.bitmap ?: return@Canvas
            val fw = img.width.toFloat()
            val fh = img.height.toFloat()
            val sc = max(size.width / fw, size.height / fh)
            val ox = (size.width - fw * sc) / 2f
            val oy = (size.height - fh * sc) / 2f
            drawImage(
                img.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize((fw * sc).toInt(), (fh * sc).toInt()),
            )
            if (paused) drawRect(Color.White.copy(alpha = 0.25f))
            if (view == LiveView.OnSheet || r?.omr?.sheetCropBitmap == null) {
                val t = fr.track ?: return@Canvas
                val p = preset ?: return@Canvas
                val toScreen: (Double, Double) -> Offset? = { x, y ->
                    t.map(x, y)?.let { (nx, ny) -> Offset((nx * fw * sc + ox).toFloat(), (ny * fh * sc + oy).toFloat()) }
                }
                val geo = r?.geometry ?: p.liveGeometry()
                val stateColor = when {
                    r != null && r.error == null && r.confirmed -> LiveColors.Confirmed
                    r != null && r.error == null -> LiveColors.Refining
                    checking -> LiveColors.Checking
                    else -> LiveColors.Searching
                }
                drawSheetCorners(toScreen, geo.height.toDouble(), stateColor, geo.width.toDouble())
                if (r != null && r.error == null && r.omr != null) {
                    drawLiveMarks(r, geo, toScreen)
                    drawPenGrade(toScreen, r.grade, hand, geo.width.toDouble())
                    if (r.variant != null || r.variantGuessed) drawPenVariant(toScreen, r.variant, hand)
                }
            }
        }
        if (view == LiveView.Large && r?.omr?.sheetCropBitmap != null) LargeSheet(r, hand)

        // ——— Сверху: назад, тест, фонарик.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaperIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", Scrapbook.Paper, 42.dp, onBack)
            Box(Modifier.weight(1f)) {
                PaperButton({ if (presets.isNotEmpty()) testMenu = true }, Scrapbook.Paper, Modifier.fillMaxWidth().height(42.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset?.let { "Тест: ${it.name}" } ?: "Нет тестов",
                            fontFamily = Scrapbook.BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                            color = Scrapbook.Ink, maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        if (presets.isNotEmpty()) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать тест", tint = Scrapbook.Ink)
                    }
                }
                DropdownMenu(expanded = testMenu, onDismissRequest = { testMenu = false }) {
                    presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} · ${p.questionsCount} вопр.") },
                            onClick = {
                                testMenu = false
                                preset = p
                                result = null
                                controller.newSheet()
                                prefs(app).edit().putString("test", p.id).apply()
                            },
                        )
                    }
                }
            }
            PaperIconButton(
                if (flash) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                if (flash) "Выключить фонарик" else "Включить фонарик",
                if (flash) Scrapbook.Lemon else Scrapbook.Paper,
                42.dp,
            ) { flash = !flash }
        }

        if (paused) {
            Text(
                "Проверка на паузе",
                fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Scrapbook.Ink,
                // Сверху, под строкой с тестом: не закрывает ни бланк, ни результат.
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .stickerShadow(offset = 3.dp, cornerRadius = 6.dp)
                    .background(Scrapbook.Lemon, RoundedCornerShape(6.dp))
                    .border(2.dp, Scrapbook.Ink, RoundedCornerShape(6.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }

        // ——— Снизу: записка с итогом и кнопками.
        val status = when {
            preset == null -> "Сначала создайте тест в «Мои тесты» — из него берутся правильные ответы."
            paused -> "Проверка на паузе. Нажмите ▶, чтобы продолжить."
            r != null && r.error == null -> null
            checking -> "Нашёл бланк — проверяю…"
            r?.error != null -> r.error
            track == null -> "Наведите камеру на бланк: в кадре должны быть видны четыре чёрных квадрата по углам."
            else -> "Нашёл бланк — проверяю…"
        }
        LiveBottomNote(
            result = r,
            status = status,
            paused = paused,
            viewIndex = view.ordinal,
            onView = { view = LiveView.entries[it] },
            onPause = { paused = !paused },
            onRecheck = { controller.recheck = true },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

private fun Modifier.clip18() = this.clip(RoundedCornerShape(18.dp))

/** Четыре угла сетки в долях кадра — для слежения «лист неподвижен / сдвинулся». */
private fun gridCorners(t: LiveTrack, p: BatchCriteriaPreset, gridHeight: Double): DoubleArray? {
    val w = p.liveGeometry().width.toDouble()
    val pts = listOf(0.0 to 0.0, w to 0.0, w to gridHeight, 0.0 to gridHeight).map { (x, y) -> t.map(x, y) ?: return null }
    return pts.flatMap { listOf(it.first, it.second) }.toDoubleArray()
}

private fun DrawScope.drawQuad(map: (Double, Double) -> Offset?, l: Double, t: Double, r: Double, b: Double, color: Color, width: Float) {
    val a = map(l, t) ?: return
    val bb = map(r, t) ?: return
    val c = map(r, b) ?: return
    val d = map(l, b) ?: return
    val path = Path().apply { moveTo(a.x, a.y); lineTo(bb.x, bb.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    drawPath(path, color, style = Stroke(width))
}

/** «Крупно»: выпрямленный бланк с теми же пометками и оценкой. */
@Composable
private fun LargeSheet(r: LiveResult, hand: android.graphics.Typeface?) {
    val bmp: Bitmap = r.omr?.sheetCropBitmap ?: return
    val geo = r.geometry ?: return
    Box(
        Modifier.fillMaxSize().background(Scrapbook.Board).padding(top = 72.dp, bottom = 250.dp, start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(bmp.asImageBitmap(), contentDescription = "Выпрямленный бланк", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val s = BatchGridLocator.SCALE
                val k = min(size.width / bmp.width, size.height / bmp.height)
                val ox = (size.width - bmp.width * k) / 2f
                val oy = (size.height - bmp.height * k) / 2f
                val map: (Double, Double) -> Offset? = { x, y -> Offset((x * s * k + ox).toFloat(), (y * s * k + oy).toFloat()) }
                drawLiveMarks(r, geo, map)
            }
        }
    }
}

/** Журнал живой проверки: в logcat и в файл приложения (на этом телефоне logcat почти сразу затирается). */
private fun logLine(context: Context, msg: String) {
    Log.i(TAG, msg)
    runCatching {
        val f = java.io.File(context.filesDir, "live_log.txt")
        if (f.length() > 512_000) f.delete()
        f.appendText("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} $msg\n")
    }
}

/**
 * Контрольный проход показал другой лист: другой код или вариант (если оба прочитаны)
 * или отмечены другие кружки хотя бы в трёх местах.
 */
private fun isOtherSheet(cur: LiveResult, fresh: LiveResult): Boolean {
    if (cur.code != null && fresh.code != null && cur.code != fresh.code) return true
    if (cur.variant != null && fresh.variant != null && cur.variant != fresh.variant) return true
    val a = cur.marks()
    val b = fresh.marks()
    return (a - b).size + (b - a).size >= 3
}

private fun rotateUpright(m: Mat, rotation: Int) {
    when ((rotation % 360 + 360) % 360) {
        90 -> Core.rotate(m, m, Core.ROTATE_90_CLOCKWISE)
        180 -> Core.rotate(m, m, Core.ROTATE_180)
        270 -> Core.rotate(m, m, Core.ROTATE_90_COUNTERCLOCKWISE)
    }
}

/**
 * Положение листа для рисунка: углы сетки сглаживаются тем сильнее, чем меньше движение
 * (фильтр «One Euro»), и по ним строится перспектива «сетка → доли кадра».
 */
private fun smoothTrack(c: LiveController, t: LiveTrack, p: BatchCriteriaPreset, gridHeight: Double): LiveTrack {
    val w = p.liveGeometry().width.toDouble()
    val logical = listOf(0.0 to 0.0, w to 0.0, w to gridHeight, 0.0 to gridHeight)
    val raw = DoubleArray(8)
    logical.forEachIndexed { i, (x, y) ->
        val m = t.map(x, y) ?: return t
        raw[2 * i] = m.first; raw[2 * i + 1] = m.second
    }
    if (c.smooth == null) c.euro.forEach { it.reset() }
    val now = SystemClock.uptimeMillis()
    val out = DoubleArray(8) { c.euro[it].filter(raw[it], now) }
    c.smooth = out
    val src = MatOfPoint2f(*logical.map { OcvPoint(it.first, it.second) }.toTypedArray())
    val dst = MatOfPoint2f(*(0 until 4).map { OcvPoint(out[2 * it], out[2 * it + 1]) }.toTypedArray())
    val h = Imgproc.getPerspectiveTransform(src, dst)
    val m = DoubleArray(9)
    h.get(0, 0, m)
    src.release(); dst.release(); h.release()
    return LiveTrack(m, t.frameW, t.frameH, t.dark)
}

/** Только отладочная сборка: последние 8 кадров проверки — для прогона на стенде (files/live_frames). */
private fun saveDebugFrame(context: Context, bmp: Bitmap) {
    runCatching {
        val dir = java.io.File(context.filesDir, "live_frames").apply { mkdirs() }
        val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        files.dropLast(7).forEach { it.delete() }
        java.io.File(dir, "f_${System.currentTimeMillis()}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}

/** Поправка «метки → сетка» по тесту — в настройках экрана (бланки одной печати не меняются). */
private fun loadCorrection(context: Context, testId: String): DoubleArray? =
    prefs(context).getString("corr_$testId", null)
        ?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 9 }?.toDoubleArray()

private fun saveCorrection(context: Context, testId: String, d: DoubleArray) {
    prefs(context).edit().putString("corr_$testId", d.joinToString(",")).apply()
}

/** Почти та же поправка — не перезаписываем (иначе слежение каждый раз перепривязывается). */
private fun sameCorrection(a: DoubleArray, b: DoubleArray?): Boolean {
    if (b == null) return false
    // Сравниваем, куда поправка переносит углы сетки (логические единицы, ≈ 0,35 мм).
    val pts = listOf(0.0 to 0.0, 440.0 to 0.0, 440.0 to 600.0, 0.0 to 600.0)
    fun ap(m: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        val w = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / w to (m[3] * x + m[4] * y + m[5]) / w
    }
    return pts.all { (x, y) ->
        val (ax, ay) = ap(a, x, y)
        val (bx, by) = ap(b, x, y)
        kotlin.math.hypot(ax - bx, ay - by) < 1.0
    }
}

/** Правдоподобная поправка: сдвигает углы сетки не больше чем на 40 логических единиц (≈ 14 мм). */
private fun plausibleCorrection(d: DoubleArray): Boolean {
    val pts = listOf(0.0 to 0.0, 440.0 to 0.0, 440.0 to 600.0, 0.0 to 600.0)
    return pts.all { (x, y) ->
        val w = d[6] * x + d[7] * y + d[8]
        if (kotlin.math.abs(w) < 1e-9) return false
        val nx = (d[0] * x + d[1] * y + d[2]) / w
        val ny = (d[3] * x + d[4] * y + d[5]) / w
        kotlin.math.hypot(nx - x, ny - y) < 40.0
    }
}

/** Резкость листа: дисперсия лапласиана в прямоугольнике, охватывающем сетку (кадр «как на экране»). */
private fun sheetSharpness(gray: Mat, t: LiveTrack): Double {
    val pts = listOf(0.0 to 0.0, 440.0 to 0.0, 440.0 to 500.0, 0.0 to 500.0).mapNotNull { (x, y) -> t.map(x, y) }
    if (pts.size < 4) return 0.0
    val x0 = (pts.minOf { it.first } * gray.cols()).toInt().coerceIn(0, gray.cols() - 2)
    val x1 = (pts.maxOf { it.first } * gray.cols()).toInt().coerceIn(x0 + 1, gray.cols())
    val y0 = (pts.minOf { it.second } * gray.rows()).toInt().coerceIn(0, gray.rows() - 2)
    val y1 = (pts.maxOf { it.second } * gray.rows()).toInt().coerceIn(y0 + 1, gray.rows())
    val roi = gray.submat(y0, y1, x0, x1)
    val lap = Mat()
    val mean = org.opencv.core.MatOfDouble()
    val std = org.opencv.core.MatOfDouble()
    return try {
        Imgproc.Laplacian(roi, lap, CvType.CV_16S)
        Core.meanStdDev(lap, mean, std)
        val sd = std.toArray().firstOrNull() ?: 0.0
        sd * sd
    } finally {
        roi.release(); lap.release(); mean.release(); std.release()
    }
}
