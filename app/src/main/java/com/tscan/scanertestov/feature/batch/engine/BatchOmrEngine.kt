package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: оркестратор OMR-пайплайна пакетной проверки и быстрой проверки по контуру.
 */
import android.graphics.Bitmap
import android.content.Context
import android.util.Log
import com.tscan.scanertestov.data.PrintedBlankParamsStore
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams
import com.tscan.scanertestov.feature.blankeditor.BlankEditorLayoutConfig
import com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.cornerMarkTipQuadLogical
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

internal object BatchOmrEngine {
    private const val PERF_TAG = "BatchOmrPerf"
    private const val TAG_FRAMES = "BatchOmrFrames"
    @Volatile
    private var openCvReady: Boolean = false

    fun processWithSheetContour(
        context: Context,
        bitmap: Bitmap,
        config: BatchOmrConfig,
        sheetContour: Array<Point>,
    ): BatchOmrResult {
        if (!ensureOpenCvLoaded()) {
            return emptyContourResult()
        }
        if (sheetContour.size != 4) {
            Log.e(PERF_TAG, "processWithSheetContour: need 4 points, got ${sheetContour.size}")
            return emptyContourResult()
        }
        if (config.columnCount == 2) {
            Log.w(PERF_TAG, "processWithSheetContour: 2 columns — fallback to auto contour")
            return process(context, bitmap, config)
        }
        val src = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)
            } else {
                src.copyTo(bgr)
            }
            val contourInBgr = BatchOmrOrientation.normalizeLikeEngine(bgr, sheetContour)
            var warpMat = BatchSheetWarper.warpByContour(bgr, contourInBgr)
            val deskewed = BatchDocumentDeskew.deskewIfNeeded(warpMat)
            if (deskewed !== warpMat) {
                warpMat.release()
                warpMat = deskewed
            }
            return classifyWarpedSheet(context, bitmap, config, warpMat)
        } catch (e: Exception) {
            Log.e(PERF_TAG, "processWithSheetContour failed", e)
            return emptyContourResult()
        } finally {
            src.release()
            bgr.release()
        }
    }

    fun process(
        context: Context,
        bitmap: Bitmap,
        config: BatchOmrConfig,
        requireLCornerWarp: Boolean = false,
        useLegacyFallback: Boolean = false,
        debugOverlay: Boolean = false,
    ): BatchOmrResult {
        if (!ensureOpenCvLoaded()) {
            Log.e(PERF_TAG, "OpenCV не инициализирован, OMR остановлен")
            return BatchOmrResult(
                predictions = emptyList(),
                questionScores = emptyList(),
                fixedCells = emptyList(),
                contourFound = false,
                sheetCropBitmap = null,
            )
        }
        val tWall0 = System.nanoTime()
        var tLap = tWall0
        fun lapMs(): Long {
            val now = System.nanoTime()
            val ms = (now - tLap) / 1_000_000L
            tLap = now
            return ms
        }
        val src = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)
            } else {
                src.copyTo(bgr)
            }
            if (bgr.cols() > bgr.rows()) {
                Core.rotate(bgr, bgr, Core.ROTATE_90_CLOCKWISE)
            }
            val prepMs = lapMs()
            // Новый поиск сетки по устройству бланка. Если он не уверен — бланк не принимаем:
            // лучше «переснимите», чем уверенно неверная оценка.
            val loc = BatchGridLocator.locateAny(
                bgr, config.questionsCount, config.choicesCount, config.columnCount,
                PrintedBlankParamsStore.load(context), config.hiddenBubbleIds,
            )
            val locateMs = lapMs()
            if (loc != null && loc.score >= BatchGridLocator.MIN_SCORE) {
                try {
                    val r = classifyByGrid(context, bgr, config, loc, debugOverlay)
                    val classifyMs = lapMs()
                    Log.i(PERF_TAG, "OMR grid ok: total=${(System.nanoTime() - tWall0) / 1_000_000L}ms prep=${prepMs} locate=${locateMs} classify=${classifyMs} score=${"%.2f".format(loc.score)} flipped=${loc.flipped}")
                    return r
                } finally {
                    loc.homography.release()
                }
            }
            loc?.homography?.release()
            if (!useLegacyFallback) {
                Log.i(PERF_TAG, "OMR: сетка не найдена (score=${loc?.score})")
                return emptyContourResult()
            }
            val bubbleLayoutForL = buildBlankBubbleLayout(
                BlankEditorLayoutConfig(
                    questionCount = config.questionsCount,
                    optionCount = config.choicesCount,
                    columnCount = config.columnCount,
                ),
                BlankBubbleRenderParams.defaults(),
            )
            val tipTemplate = cornerMarkTipQuadLogical(bubbleLayoutForL)
            val betweenLMat = tipTemplate?.let { BatchCornerMarkWarp.tryWarpByCornerMarks(bgr, it) }

            if (requireLCornerWarp && betweenLMat == null) {
                val totalMs = (System.nanoTime() - tWall0) / 1_000_000L
                Log.i(
                    PERF_TAG,
                    "OMR skip: L-corners not found (requireLCornerWarp=true) total=${totalMs}ms " +
                        "bitmap=${bitmap.width}x${bitmap.height} Q=${config.questionsCount} " +
                        "choices=${config.choicesCount} cols=${config.columnCount}",
                )
                return BatchOmrResult(
                    predictions = emptyList(),
                    questionScores = emptyList(),
                    fixedCells = emptyList(),
                    contourFound = false,
                    sheetCropBitmap = null,
                )
            }

            val twoColumns = config.columnCount == 2
            // Для двух колонок контур листа нужен как резерв: варп по L-ориентирам может
            // «прижаться» как к кваду уголков, так и к кваду страницы, поэтому рамки
            // ищутся не по логической раскладке, а прямо в самом варпе.
            val contourFull = if (betweenLMat == null || twoColumns) {
                BatchSheetContourDetector.findSheetContour(
                    inputBgr = bgr,
                    questionsCount = config.questionsCount,
                    choicesCount = config.choicesCount,
                )
            } else {
                null
            }
            val contourMs = lapMs()

            var columnFrames: List<BatchColumnFrameGrid>? = null

            var warpMat: Mat = if (betweenLMat != null) {
                if (twoColumns) {
                    val framesFromL = computeTwoColumnFramesFromWarp(
                        warp = betweenLMat,
                        questionsCount = config.questionsCount,
                        choicesCount = config.choicesCount,
                    )
                    if (framesFromL != null) {
                        columnFrames = framesFromL
                        betweenLMat
                    } else if (contourFull != null) {
                        val w = BatchSheetWarper.warpByContour(bgr, contourFull)
                        val framesFromPage = computeTwoColumnFramesFromWarp(
                            warp = w,
                            questionsCount = config.questionsCount,
                            choicesCount = config.choicesCount,
                        )
                        if (framesFromPage != null) {
                            columnFrames = framesFromPage
                            betweenLMat.release()
                            w
                        } else {
                            val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
                                inputBgr = bgr,
                                questionsCount = config.questionsCount,
                                choicesCount = config.choicesCount,
                            )
                            if (pair != null) {
                                val combined = buildLegacyPairWarp(bgr, pair)
                                betweenLMat.release()
                                w.release()
                                combined
                            } else {
                                betweenLMat.release()
                                w
                            }
                        }
                    } else {
                        val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
                            inputBgr = bgr,
                            questionsCount = config.questionsCount,
                            choicesCount = config.choicesCount,
                        )
                        if (pair == null) {
                            betweenLMat.release()
                            return emptyContourResult()
                        }
                        betweenLMat.release()
                        buildLegacyPairWarp(bgr, pair)
                    }
                } else {
                    val contourOnCrop = BatchSheetContourDetector.findSheetContour(
                        inputBgr = betweenLMat,
                        questionsCount = config.questionsCount,
                        choicesCount = config.choicesCount,
                    )
                    if (contourOnCrop != null) {
                        val w = BatchSheetWarper.warpByContour(betweenLMat, contourOnCrop)
                        betweenLMat.release()
                        w
                    } else {
                        betweenLMat
                    }
                }
            } else {
                when {
                    twoColumns -> {
                        if (contourFull == null) {
                            // Последний резерв: попарный поиск колонок.
                            val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
                                inputBgr = bgr,
                                questionsCount = config.questionsCount,
                                choicesCount = config.choicesCount,
                            )
                            if (pair == null) return emptyContourResult()
                            buildLegacyPairWarp(bgr, pair)
                        } else {
                            val w = BatchSheetWarper.warpByContour(bgr, contourFull)
                            val framesFromPage = computeTwoColumnFramesFromWarp(
                                warp = w,
                                questionsCount = config.questionsCount,
                                choicesCount = config.choicesCount,
                            )
                            if (framesFromPage != null) {
                                columnFrames = framesFromPage
                                w
                            } else {
                                val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
                                    inputBgr = bgr,
                                    questionsCount = config.questionsCount,
                                    choicesCount = config.choicesCount,
                                )
                                if (pair != null) {
                                    val combined = buildLegacyPairWarp(bgr, pair)
                                    w.release()
                                    combined
                                } else {
                                    w
                                }
                            }
                        }
                    }

                    else -> {
                        if (contourFull == null) return emptyContourResult()
                        BatchSheetWarper.warpByContour(bgr, contourFull)
                    }
                }
            }

            val deskewed = BatchDocumentDeskew.deskewIfNeeded(warpMat)
            if (deskewed !== warpMat) {
                warpMat.release()
                warpMat = deskewed
            }

            val warpMs = lapMs()
            val result = classifyWarpedSheet(context, bitmap, config, warpMat, columnFrames)
            val totalMs = (System.nanoTime() - tWall0) / 1_000_000L
            Log.i(
                PERF_TAG,
                "OMR ok: total=${totalMs}ms prep=${prepMs}ms contour=${contourMs}ms warp=${warpMs}ms " +
                    "bitmap=${bitmap.width}x${bitmap.height} Q=${config.questionsCount}",
            )
            return result
        } finally {
            src.release()
            bgr.release()
        }
    }

    /** Рамки сетки в пикселях канона: по одной на столбец, границы — по шагу кружков. */
    private fun gridFrames(g: GridGeometry, s: Double): List<BatchColumnFrameGrid> {
        val choices = (g.bubbles.maxOfOrNull { it.choice } ?: 0) + 1
        return g.frames.mapNotNull { f ->
            val inFrame = g.bubbles.filter { it.cx >= f[0] && it.cx <= f[2] && it.cy >= f[1] && it.cy <= f[3] }
            val real = inFrame.filter { !it.placeholder }
            if (real.isEmpty()) return@mapNotNull null
            val left = (inFrame.minOf { it.cx } - g.pitchX / 2) * s
            val top = (inFrame.minOf { it.cy } - g.pitchY / 2) * s
            val rowsInFrame = inFrame.map { it.cy }.distinct().size
            BatchColumnFrameGrid(
                questionStart = real.minOf { it.question },
                questionCount = real.map { it.question }.distinct().size,
                frameBubbleRows = rowsInFrame,
                innerLeft = left.roundToInt(),
                innerTop = top.roundToInt(),
                innerRight = (left + choices * g.pitchX * s).roundToInt(),
                innerBottom = (top + rowsInFrame * g.pitchY * s).roundToInt(),
            )
        }
    }

    /** Ячейки вырезаются по центрам кружков из выпрямленного канона — шаг сетки известен точно. */
    private fun classifyByGrid(
        context: Context,
        bgr: Mat,
        config: BatchOmrConfig,
        loc: GridLocation,
        debugOverlay: Boolean,
    ): BatchOmrResult {
        val t0 = System.nanoTime()
        val canon = BatchGridLocator.warpCanon(bgr, loc)
        BatchGridLocator.normalizeContrast(canon)
        val g0 = loc.geometry
        // Изогнутый лист: центры кружков уточняются по самим кольцам.
        val centers = BatchGridLocator.refineBubbleCenters(canon, g0)
        val g = g0.copy(bubbles = g0.bubbles.mapIndexed { i, b -> b.copy(cx = centers[i].first, cy = centers[i].second) })
        val s = BatchGridLocator.SCALE
        val cells = ArrayList<Triple<Int, Int, Mat>>()
        try {
            val cw = (g.pitchX * s).roundToInt()
            val ch = (g.pitchY * s).roundToInt()
            for (b in g.bubbles) {
                if (b.placeholder || b.question !in 0 until config.questionsCount) continue
                val x0 = (b.cx * s - cw / 2.0).roundToInt().coerceIn(0, canon.cols() - cw)
                val y0 = (b.cy * s - ch / 2.0).roundToInt().coerceIn(0, canon.rows() - ch)
                cells += Triple(b.question, b.choice, Mat(canon, Rect(x0, y0, cw, ch)).clone())
            }
            val tCells = System.nanoTime()
            val rawPredictions = try {
                BatchCellClassifier.classifyCells(cells, context)
            } finally {
                cells.forEach { it.third.release() }
            }
            Log.i("BatchGridPerf", "classify prep=${(tCells - t0) / 1_000_000} nn=${(System.nanoTime() - tCells) / 1_000_000} ms")
            val predictions = promoteDarkBubbles(context, canon, g, rawPredictions, s)
            val scores = BatchScoringEngine.score(predictions, config)
            // Экраны результатов сами подсвечивают клетки, поэтому отдаём чистый выпрямленный
            // бланк и точные рамки сетки; картинка с кружками — только для отладочного прогона.
            val overlay = if (debugOverlay) {
                BatchSheetGridOverlay.renderPredictions(canon, g, predictions, s)
            } else {
                BatchSheetGridOverlay.canonToBitmap(canon)
            }
            val frames = gridFrames(g0, s)
            return BatchOmrResult(
                predictions = predictions,
                questionScores = scores,
                fixedCells = predictions.filter { it.klass == BatchCellClass.Fixed },
                contourFound = true,
                sheetCropBitmap = overlay,
                columnFrames = frames,
                gridScore = loc.score,
                flipped = loc.flipped,
            )
        } finally {
            canon.release()
        }
    }

    /**
     * Живая проверка: сетка уже найдена (обычно по меткам — [BatchGridLocator.locateByMarkers]).
     * [useModel] = true — нейросеть, как в пакетной проверке; false — чистый OpenCV по доле чернил в кружке.
     * [loc] освобождает вызывающий.
     */
    internal fun classifyLocated(
        context: Context,
        bgr: Mat,
        config: BatchOmrConfig,
        loc: GridLocation,
        useModel: Boolean,
    ): BatchOmrResult =
        if (useModel) classifyHybrid(context, bgr, config, loc)
        else classifyByInk(bgr, config, loc)

    /**
     * Чистый OpenCV: после выравнивания яркости бумага белая, чернила чёрные; считаем долю тёмных точек
     * во внутреннем круге (кольцо кружка не попадает). Закрашен — много, перечёркнут/исправлен — средне.
     */
    /** Выпрямленный канон с уточнёнными центрами и доля чернил во внутреннем круге каждого кружка. */
    private class InkSheet(val canon: Mat, val g: GridGeometry, val fills: Map<Pair<Int, Int>, Float>)

    private fun inkSheet(bgr: Mat, config: BatchOmrConfig, loc: GridLocation): InkSheet {
        val canon = BatchGridLocator.warpCanon(bgr, loc)
        val gray = Mat()
        val bin = Mat()
        try {
            BatchGridLocator.normalizeContrast(canon)
            val g0 = loc.geometry
            val centers = BatchGridLocator.refineBubbleCenters(canon, g0)
            val g = g0.copy(bubbles = g0.bubbles.mapIndexed { i, b -> b.copy(cx = centers[i].first, cy = centers[i].second) })
            val s = BatchGridLocator.SCALE
            Imgproc.cvtColor(canon, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.threshold(gray, bin, INK_LEVEL, 255.0, Imgproc.THRESH_BINARY_INV)
            val r = (g.bubbleSize / 2 * s * 0.55).roundToInt().coerceAtLeast(2)
            val w = bin.cols()
            val h = bin.rows()
            val px = ByteArray(w * h)
            bin.get(0, 0, px)
            val fills = HashMap<Pair<Int, Int>, Float>()
            for (b in g.bubbles) {
                if (b.placeholder || b.question !in 0 until config.questionsCount) continue
                val cx = (b.cx * s).roundToInt()
                val cy = (b.cy * s).roundToInt()
                var all = 0
                var ink = 0
                for (y in (cy - r).coerceAtLeast(0)..(cy + r).coerceAtMost(h - 1)) {
                    for (x in (cx - r).coerceAtLeast(0)..(cx + r).coerceAtMost(w - 1)) {
                        val dx = x - cx
                        val dy = y - cy
                        if (dx * dx + dy * dy > r * r) continue
                        all++
                        if (px[y * w + x].toInt() != 0) ink++
                    }
                }
                fills[b.question to b.choice] = if (all == 0) 0f else ink.toFloat() / all
            }
            return InkSheet(canon, g, fills)
        } catch (e: Throwable) {
            canon.release()
            throw e
        } finally {
            gray.release(); bin.release()
        }
    }

    private fun liveResult(sheet: InkSheet, loc: GridLocation, config: BatchOmrConfig, predictions: List<BatchCellPrediction>): BatchOmrResult {
        val scores = BatchScoringEngine.score(predictions, config)
        return BatchOmrResult(
            predictions = predictions,
            questionScores = scores,
            fixedCells = predictions.filter { it.klass == BatchCellClass.Fixed },
            contourFound = true,
            sheetCropBitmap = BatchSheetGridOverlay.canonToBitmap(sheet.canon),
            columnFrames = gridFrames(loc.geometry, BatchGridLocator.SCALE),
            gridScore = loc.score,
            flipped = loc.flipped,
        )
    }

    /**
     * Чистый OpenCV: после выравнивания яркости бумага белая, чернила чёрные; считаем долю тёмных точек
     * во внутреннем круге (кольцо кружка не попадает). Закрашен — много, перечёркнут/исправлен — средне.
     */
    private fun classifyByInk(bgr: Mat, config: BatchOmrConfig, loc: GridLocation): BatchOmrResult {
        val sheet = inkSheet(bgr, config, loc)
        try {
            val predictions = sheet.fills.map { (k, fill) ->
                val klass = when {
                    fill >= INK_FILLED -> BatchCellClass.Yes
                    fill >= INK_PARTIAL -> BatchCellClass.Fixed
                    else -> BatchCellClass.No
                }
                // «Уверенность» — насколько далеко от ближайшего порога.
                val conf = when (klass) {
                    BatchCellClass.Yes -> (0.5f + (fill - INK_FILLED)).coerceIn(0.5f, 1f)
                    BatchCellClass.No -> (0.5f + (INK_PARTIAL - fill) * 2f).coerceIn(0.5f, 1f)
                    BatchCellClass.Fixed -> 0.5f
                }
                BatchCellPrediction(k.first, k.second, klass, conf)
            }.sortedWith(compareBy({ it.questionIndex }, { it.choiceIndex }))
            return liveResult(sheet, loc, config, predictions)
        } finally {
            sheet.canon.release()
        }
    }

    /**
     * Нейросеть для живой проверки: явно пустые кружки (чернил меньше [INK_EMPTY] — с большим запасом, слабая
     * штриховка даёт больше) решаются сразу, нейросеть смотрит только остальные. ~4× быстрее, чем все 80.
     */
    private fun classifyHybrid(context: Context, bgr: Mat, config: BatchOmrConfig, loc: GridLocation): BatchOmrResult {
        val sheet = inkSheet(bgr, config, loc)
        val s = BatchGridLocator.SCALE
        val cells = ArrayList<Triple<Int, Int, Mat>>()
        try {
            val cw = (sheet.g.pitchX * s).roundToInt()
            val ch = (sheet.g.pitchY * s).roundToInt()
            val canon = sheet.canon
            for (b in sheet.g.bubbles) {
                if (b.placeholder || b.question !in 0 until config.questionsCount) continue
                if ((sheet.fills[b.question to b.choice] ?: 1f) < INK_EMPTY) continue
                val x0 = (b.cx * s - cw / 2.0).roundToInt().coerceIn(0, canon.cols() - cw)
                val y0 = (b.cy * s - ch / 2.0).roundToInt().coerceIn(0, canon.rows() - ch)
                cells += Triple(b.question, b.choice, Mat(canon, Rect(x0, y0, cw, ch)).clone())
            }
            val nn = try { BatchCellClassifier.classifyCells(cells, context) } finally { cells.forEach { it.third.release() } }
            val byCell = nn.associateBy { it.questionIndex to it.choiceIndex }
            val raw = sheet.fills.keys.map { k ->
                byCell[k] ?: BatchCellPrediction(k.first, k.second, BatchCellClass.No, 0.99f)
            }.sortedWith(compareBy({ it.questionIndex }, { it.choiceIndex }))
            // «Второе мнение» по темноте — как в пакетной проверке ([classifyByGrid]).
            val predictions = promoteDarkBubbles(context, sheet.canon, sheet.g, raw, s)
            Log.i("BatchGridPerf", "гибрид: нейросеть по ${nn.size} из ${sheet.fills.size} кружков")
            return liveResult(sheet, loc, config, predictions)
        } finally {
            sheet.canon.release()
        }
    }

    private const val INK_LEVEL = 128.0
    private const val INK_FILLED = 0.45f
    private const val INK_PARTIAL = 0.22f
    private const val INK_EMPTY = 0.06f

    /**
     * Второе мнение для слабых отметок: кружок, заштрихованный редкими штрихами, нейросеть
     * иногда считает пустым. Если внутри он намного темнее пустых кружков этого же листа
     * (порог подобран на 1313 кадрах), считаем его отмеченным.
     */
    private fun promoteDarkBubbles(
        context: Context,
        canon: Mat,
        g: GridGeometry,
        predictions: List<BatchCellPrediction>,
        s: Double,
    ): List<BatchCellPrediction> {
        val gray = Mat()
        val mask = Mat()
        try {
            Imgproc.cvtColor(canon, gray, Imgproc.COLOR_BGR2GRAY)
            val r = (g.bubbleSize / 2 * s * 0.62).roundToInt()
            val darkness = HashMap<Pair<Int, Int>, Double>()
            mask.create(gray.rows(), gray.cols(), org.opencv.core.CvType.CV_8UC1)
            for (b in g.bubbles) {
                if (b.placeholder) continue
                mask.setTo(org.opencv.core.Scalar(0.0))
                Imgproc.circle(mask, Point(b.cx * s, b.cy * s), r, org.opencv.core.Scalar(255.0), -1)
                darkness[b.question to b.choice] = 255.0 - Core.mean(gray, mask).`val`[0]
            }
            val empty = predictions.filter { it.klass == BatchCellClass.No && it.confidence >= 0.9f }
                .mapNotNull { darkness[it.questionIndex to it.choiceIndex] }.sorted()
            if (empty.size < 10) return predictions
            val base = empty[empty.size / 2]
            val dev = empty.map { kotlin.math.abs(it - base) }.sorted()
            val mad = dev[dev.size / 2] + 1.0
            return predictions.map { p ->
                val d = darkness[p.questionIndex to p.choiceIndex] ?: return@map p
                val z = (d - base) / mad
                val pr = p.probs
                // Неуверенная клетка («исправлено» только из-за порога): решаем по чернильности.
                // Тёмный кружок, который нейросеть хоть немного допускает как отметку.
                val darkEnough = z >= DARK_Z || (pr != null && z >= DARK_Z_SOFT && pr[1] >= DARK_SOFT_P_YES)
                if (pr != null && p.klass == BatchCellClass.Fixed && pr.maxOrNull()!! < BatchCellMlClassifier.confidenceThreshold(context)) {
                    val top = pr.indices.maxByOrNull { pr[it] } ?: 2
                    when {
                        top == 0 && z < DARK_Z_EMPTY -> return@map p.copy(klass = BatchCellClass.No)
                        top != 2 && darkEnough -> return@map p.copy(klass = BatchCellClass.Yes)
                    }
                }
                if (p.klass == BatchCellClass.No && darkEnough) {
                    p.copy(klass = BatchCellClass.Yes, confidence = 0.5f)
                } else p
            }
        } finally {
            gray.release(); mask.release()
        }
    }

    // Пороги пересмотрены после дообучения модели 128 (13.11.2026, 9 200 кружков с пробных листов):
    // дообученная модель сама ловит слабую штриховку, а прежние 8 / 5 / 0.05 только добавляли ложные
    // «закрашено» на тёмных пустых кружках. Оставлены как страховка для совсем необычных снимков.
    private const val DARK_Z = 15.0
    private const val DARK_Z_EMPTY = 4.0
    private const val DARK_Z_SOFT = 10.0
    private const val DARK_SOFT_P_YES = 0.3f

    private fun classifyWarpedSheet(
        context: Context,
        sourceBitmap: Bitmap,
        config: BatchOmrConfig,
        warpMat: Mat,
        columnFrames: List<BatchColumnFrameGrid>? = null,
    ): BatchOmrResult {
        try {
            val sheetCropRaw = Bitmap.createBitmap(warpMat.cols(), warpMat.rows(), Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(warpMat, it)
            }
            val sheetCrop = BatchSheetGridOverlay.renderOverlay(
                source = sheetCropRaw,
                questionsCount = config.questionsCount,
                choicesCount = config.choicesCount,
                columnCount = config.columnCount,
                columnFrames = columnFrames,
            )
            sheetCropRaw.recycle()
            val cells = BatchCellGridExtractor.extractCells(
                warpBgr = warpMat,
                questionsCount = config.questionsCount,
                choicesCount = config.choicesCount,
                columnCount = config.columnCount,
                columnFrames = columnFrames,
            )
            val predictions = try {
                BatchCellClassifier.classifyCells(cells, context)
            } finally {
                cells.forEach { (_, _, cellMat) -> cellMat.release() }
            }
            val scores = BatchScoringEngine.score(predictions, config)
            return BatchOmrResult(
                predictions = predictions,
                questionScores = scores,
                fixedCells = predictions.filter { it.klass == BatchCellClass.Fixed },
                contourFound = true,
                sheetCropBitmap = sheetCrop,
                columnFrames = columnFrames,
            )
        } finally {
            warpMat.release()
        }
    }

    private fun buildLegacyPairWarp(
        bgr: Mat,
        pair: Pair<Array<Point>, Array<Point>>,
    ): Mat {
        val inW = bgr.cols()
        val inH = bgr.rows()
        val baseW = minOf(inW, inH).coerceIn(280, 1600)
        val outW = baseW
        val outH = (baseW * BlankSheetSpec.LOGICAL_HEIGHT_PX / BlankSheetSpec.LOGICAL_WIDTH_PX)
            .toDouble()
            .toInt()
            .coerceAtLeast(1)
        val leftW = outW / 2
        val rightW = outW - leftW

        val leftWarp = BatchSheetWarper.warpByContourToSize(bgr, pair.first, leftW.coerceAtLeast(1), outH)
        val rightWarp = BatchSheetWarper.warpByContourToSize(bgr, pair.second, rightW.coerceAtLeast(1), outH)

        val combined = Mat(outH, outW, leftWarp.type())
        try {
            leftWarp.copyTo(combined.submat(Rect(0, 0, leftW, outH)))
            rightWarp.copyTo(combined.submat(Rect(leftW, 0, rightW, outH)))
            return combined
        } finally {
            leftWarp.release()
            rightWarp.release()
        }
    }

    /**
     * Двухколоночная геометрия: рамки колонок ищутся прямо в [warp] как пара
     * одинаковых прямоугольников рядом. Это устойчиво к тому, к какому кваду
     * «прижался» варп (L-ориентиры или страница) — хрупкие аффинные допущения
     * о логической раскладке не используются.
     */
    private fun computeTwoColumnFramesFromWarp(
        warp: Mat,
        questionsCount: Int,
        choicesCount: Int,
    ): List<BatchColumnFrameGrid>? {
        val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
            inputBgr = warp,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
        ) ?: return null
        val warpW = warp.cols()
        val warpH = warp.rows()
        val warpBmp = Bitmap.createBitmap(warpW, warpH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warp, warpBmp)
        try {
            return buildFrameGridsFromPair(
                warpBmp = warpBmp,
                warpW = warpW,
                warpH = warpH,
                pair = pair,
                questionsCount = questionsCount,
                choicesCount = choicesCount,
            )
        } finally {
            warpBmp.recycle()
        }
    }

    private fun buildFrameGridsFromPair(
        warpBmp: Bitmap,
        warpW: Int,
        warpH: Int,
        pair: Pair<Array<Point>, Array<Point>>,
        questionsCount: Int,
        choicesCount: Int,
    ): List<BatchColumnFrameGrid> {
        val q1 = (questionsCount + 1) / 2
        val q2 = questionsCount - q1
        val left = if (pair.first.minOf { it.x } <= pair.second.minOf { it.x }) pair.first else pair.second
        val right = if (left === pair.first) pair.second else pair.first
        val sides = listOf(
            FrameSide(pts = left, questionStart = 0, questionCount = q1, frameBubbleRows = q1),
            FrameSide(
                pts = right,
                questionStart = q1,
                questionCount = q2,
                frameBubbleRows = if (q2 < q1) q2 + 1 else q2,
            ),
        )
        val out = ArrayList<BatchColumnFrameGrid>(sides.size)
        for ((index, side) in sides.withIndex()) {
            if (side.questionCount <= 0) continue

            val minX = side.pts.minOf { it.x }.roundToInt()
            val minY = side.pts.minOf { it.y }.roundToInt()
            val maxX = side.pts.maxOf { it.x }.roundToInt()
            val maxY = side.pts.maxOf { it.y }.roundToInt()
            val bw = (maxX - minX).coerceAtLeast(2)
            val bh = (maxY - minY).coerceAtLeast(2)
            val marginX = (bw * 0.04).roundToInt()
            val marginY = (bh * 0.04).roundToInt()
            val cx = (minX - marginX).coerceIn(0, (warpW - 2).coerceAtLeast(0))
            val cy = (minY - marginY).coerceIn(0, (warpH - 2).coerceAtLeast(0))
            val cw = (bw + 2 * marginX).coerceAtMost(warpW - cx)
            val ch = (bh + 2 * marginY).coerceAtMost(warpH - cy)

            val crop = Bitmap.createBitmap(warpBmp, cx, cy, cw, ch)
            val local = try {
                BatchInnerGridDetector.detectInnerRect(crop, side.questionCount, choicesCount)
            } finally {
                crop.recycle()
            }

            val found = BatchInnerGridRect(
                left = cx + local.left,
                top = cy + local.top,
                rightEx = cx + local.rightEx,
                bottomEx = cy + local.bottomEx,
            )

            val insetX = (cw * 0.04).roundToInt()
            val insetY = (ch * 0.04).roundToInt()
            val fallback = BatchInnerGridRect(
                left = (cx + insetX).coerceIn(0, warpW - 2),
                top = (cy + insetY).coerceIn(0, warpH - 2),
                rightEx = (cx + cw - insetX).coerceIn(2, warpW),
                bottomEx = (cy + ch - insetY).coerceIn(2, warpH),
            )

            val cropArea = cw.toDouble() * ch.toDouble()
            val foundArea = found.width.toDouble() * found.height.toDouble()
            val valid = found.width >= 10 && found.height >= 10 &&
                foundArea >= 0.45 * cropArea && foundArea <= 1.1 * cropArea

            val inner = if (valid) found else fallback
            out += BatchColumnFrameGrid(
                questionStart = side.questionStart,
                questionCount = side.questionCount,
                frameBubbleRows = side.frameBubbleRows,
                innerLeft = inner.left,
                innerTop = inner.top,
                innerRight = inner.rightEx,
                innerBottom = inner.bottomEx,
            )
            Log.d(
                TAG_FRAMES,
                "col$index q${side.questionStart}+${side.questionCount} bbox=[$minX,$minY,$bw,$bh] " +
                    "found=${if (valid) found else "rejected"} fallback=[${fallback.left},${fallback.top},${fallback.rightEx},${fallback.bottomEx}] " +
                    "inner=[${inner.left},${inner.top},${inner.rightEx},${inner.bottomEx}]",
            )
        }
        return out
    }

    private data class FrameSide(
        val pts: Array<Point>,
        val questionStart: Int,
        val questionCount: Int,
        val frameBubbleRows: Int,
    )

    private fun emptyContourResult() = BatchOmrResult(
        predictions = emptyList(),
        questionScores = emptyList(),
        fixedCells = emptyList(),
        contourFound = false,
        sheetCropBitmap = null,
    )

    private fun ensureOpenCvLoaded(): Boolean {
        if (openCvReady) return true
        synchronized(this) {
            if (openCvReady) return true
            openCvReady = OpenCVLoader.initLocal()
            return openCvReady
        }
    }
}
