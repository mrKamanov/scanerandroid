package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: оркестратор OMR-пайплайна пакетной проверки и быстрой проверки по контуру.
 */
import android.graphics.Bitmap
import android.content.Context
import android.util.Log
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
