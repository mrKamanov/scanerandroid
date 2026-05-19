package com.tscan.scanertestov.feature.realtime.engine

/**
 * Описание: превью кадра — контур бланка, выравнивание и масштаб под область камеры.
 */
import android.graphics.Bitmap
import android.util.Log
import com.tscan.scanertestov.feature.batch.engine.BatchSheetContourDetector
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal data class RealtimePreviewResult(
    val bitmap: Bitmap,
    val contourFound: Boolean,
    val sheetContour: Array<Point>?,
)

internal object RealtimePreviewPipeline {
    private const val TAG = "RealtimePreview"

    @Suppress("LongParameterList")
    fun processFrameWithOpenCV(
        inputBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        questionsCount: Int,
        choicesCount: Int,
        isGridVisible: Boolean,
        brightness: Int,
        contrast: Int,
        saturation: Int,
        sharpness: Int,
        sheetContour: Array<Point>? = null,
    ): RealtimePreviewResult {
        if (!RealtimeOpenCvBootstrap.ensureLoaded()) {
            Log.e(TAG, "OpenCV не готов, возвращаем исходный кадр")
            return RealtimePreviewResult(inputBitmap, false, null)
        }

        val inputMat = Mat()
        Utils.bitmapToMat(inputBitmap, inputMat)

        val alpha = contrast / 100.0
        val beta = brightness.toDouble()
        inputMat.convertTo(inputMat, -1, alpha, beta)

        if (saturation != 100) {
            val hsv = Mat()
            Imgproc.cvtColor(inputMat, hsv, Imgproc.COLOR_BGR2HSV)
            val channels = ArrayList<Mat>(3)
            Core.split(hsv, channels)
            channels[1].convertTo(channels[1], -1, saturation / 100.0, 0.0)
            Core.merge(channels, hsv)
            Imgproc.cvtColor(hsv, inputMat, Imgproc.COLOR_HSV2BGR)
            channels.forEach { it.release() }
            hsv.release()
        }

        if (sharpness != 50) {
            val kernelSize = 3
            val kernel = Mat(kernelSize, kernelSize, org.opencv.core.CvType.CV_32F)
            val k = (sharpness - 50) / 50.0
            val base = 1.0 + k * 2.0
            val arr = floatArrayOf(
                0f, -1f, 0f,
                -1f, base.toFloat(), -1f,
                0f, -1f, 0f,
            )
            kernel.put(0, 0, arr)
            Imgproc.filter2D(inputMat, inputMat, inputMat.depth(), kernel)
            kernel.release()
        }

        val inputWidth = inputMat.cols()
        val inputHeight = inputMat.rows()

        val sheetPts = sheetContour ?: BatchSheetContourDetector.findSheetContour(
            inputBgr = inputMat,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
        )

        if (sheetPts != null) {
            val sortedPts = sortPoints(sheetPts)
            val dstSize = minOf(inputWidth, inputHeight)
            val srcMat = MatOfPoint2f(*sortedPts)
            val dstMat = MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, 0.0),
                org.opencv.core.Point(dstSize - 1.0, dstSize - 1.0),
                org.opencv.core.Point(0.0, dstSize - 1.0),
            )
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            val warp = Mat()
            Imgproc.warpPerspective(inputMat, warp, perspectiveTransform, Size(dstSize.toDouble(), dstSize.toDouble()))

            if (isGridVisible) {
                drawGridOnWarp(warp, questionsCount, choicesCount)
            }

            val resizedWarp = Mat()
            Imgproc.resize(warp, resizedWarp, Size(inputWidth.toDouble(), inputHeight.toDouble()))
            resizedWarp.copyTo(inputMat)
            resizedWarp.release()
            warp.release()
            srcMat.release()
            dstMat.release()
            perspectiveTransform.release()
        }

        val finalMat = Mat()
        Imgproc.resize(inputMat, finalMat, Size(targetWidth.toDouble(), targetHeight.toDouble()))
        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(finalMat, outputBitmap)
        inputMat.release()
        finalMat.release()
        val contourFound = sheetPts != null
        return RealtimePreviewResult(outputBitmap, contourFound, sheetPts)
    }

    private fun drawGridOnWarp(warpMat: Mat, questionsCount: Int, choicesCount: Int) {
        val width = warpMat.cols()
        val height = warpMat.rows()
        val cellWidth = width / choicesCount
        val cellHeight = height / questionsCount
        for (i in 0..questionsCount) {
            val y = (i * cellHeight).toInt()
            Imgproc.line(
                warpMat,
                org.opencv.core.Point(0.0, y.toDouble()),
                org.opencv.core.Point(width.toDouble(), y.toDouble()),
                Scalar(255.0, 79.0, 0.0),
                2,
            )
        }
        for (i in 0..choicesCount) {
            val x = (i * cellWidth).toInt()
            Imgproc.line(
                warpMat,
                org.opencv.core.Point(x.toDouble(), 0.0),
                org.opencv.core.Point(x.toDouble(), height.toDouble()),
                Scalar(255.0, 79.0, 0.0),
                2,
            )
        }
    }

    private fun sortPoints(pts: Array<org.opencv.core.Point>): Array<org.opencv.core.Point> {
        val sorted = pts.sortedWith(compareBy({ it.y + it.x }, { it.y - it.x }))
        val result = Array(4) { org.opencv.core.Point() }
        result[0] = sorted[0]
        result[2] = sorted[3]
        val remain = sorted.subList(1, 3)
        if (remain[0].x > remain[1].x) {
            result[1] = remain[0]
            result[3] = remain[1]
        } else {
            result[1] = remain[1]
            result[3] = remain[0]
        }
        return result
    }
}
