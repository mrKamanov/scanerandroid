package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: оркестратор OMR-пайплайна пакетной проверки и быстрой проверки по контуру.
 */
import android.graphics.Bitmap
import android.content.Context
import android.util.Log
import com.tscan.scanertestov.feature.blankeditor.BlankBubbleRenderParams
import com.tscan.scanertestov.feature.blankeditor.BlankEditorLayoutConfig
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.cornerMarkTipQuadLogical
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

internal object BatchOmrEngine {
    private const val PERF_TAG = "BatchOmrPerf"
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

            val contourFull = if (betweenLMat == null) {
                BatchSheetContourDetector.findSheetContour(
                    inputBgr = bgr,
                    questionsCount = config.questionsCount,
                    choicesCount = config.choicesCount,
                )
            } else {
                null
            }
            val contourMs = lapMs()

            var warpMat: Mat = if (betweenLMat != null) {
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
            } else {
                when {
                config.columnCount == 2 -> {
                    val pair = BatchSheetContourDetector.findSheetContourPairForTwoColumns(
                        inputBgr = bgr,
                        questionsCount = config.questionsCount,
                        choicesCount = config.choicesCount,
                    )
                    if (pair == null) {
                        if (contourFull == null) return BatchOmrResult(
                            predictions = emptyList(),
                            questionScores = emptyList(),
                            fixedCells = emptyList(),
                            contourFound = false,
                            sheetCropBitmap = null,
                        )
                        BatchSheetWarper.warpByContour(bgr, contourFull)
                    } else {
                        val inW = bgr.cols()
                        val inH = bgr.rows()
                        val baseW = minOf(inW, inH).coerceIn(280, 1600)
                        val outW = baseW
                        val outH = (baseW * com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec.LOGICAL_HEIGHT_PX /
                            com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec.LOGICAL_WIDTH_PX)
                            .toDouble()
                            .toInt()
                            .coerceAtLeast(1)

                        val leftW = outW / 2
                        val rightW = outW - leftW

                        val leftWarp = BatchSheetWarper.warpByContourToSize(
                            inputBgr = bgr,
                            contour = pair.first,
                            outW = leftW.coerceAtLeast(1),
                            outH = outH,
                        )
                        val rightWarp = BatchSheetWarper.warpByContourToSize(
                            inputBgr = bgr,
                            contour = pair.second,
                            outW = rightW.coerceAtLeast(1),
                            outH = outH,
                        )

                        val combined = Mat(outH, outW, leftWarp.type())
                        try {
                            leftWarp.copyTo(combined.submat(Rect(0, 0, leftW, outH)))
                            rightWarp.copyTo(combined.submat(Rect(leftW, 0, rightW, outH)))
                            combined
                        } finally {
                            leftWarp.release()
                            rightWarp.release()
                        }
                    }
                }

                else -> {
                    if (contourFull == null) return BatchOmrResult(
                        predictions = emptyList(),
                        questionScores = emptyList(),
                        fixedCells = emptyList(),
                        contourFound = false,
                        sheetCropBitmap = null,
                    )
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
            val result = classifyWarpedSheet(context, bitmap, config, warpMat)
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
            )
            sheetCropRaw.recycle()
            val cells = BatchCellGridExtractor.extractCells(
                warpBgr = warpMat,
                questionsCount = config.questionsCount,
                choicesCount = config.choicesCount,
                columnCount = config.columnCount,
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
            )
        } finally {
            warpMat.release()
        }
    }

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
