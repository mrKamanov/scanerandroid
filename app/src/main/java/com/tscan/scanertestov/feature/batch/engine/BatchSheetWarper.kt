package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: перспективное выравнивание контура бланка в прямоугольник пропорций A4.
 */
import com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min
import kotlin.math.roundToInt

internal object BatchSheetWarper {
    fun warpByContour(inputBgr: Mat, contour: Array<Point>): Mat {
        val sorted = sortPoints(contour)
        val inW = inputBgr.cols()
        val inH = inputBgr.rows()
        val baseW = min(inW, inH).coerceIn(280, 1600)
        val outW = baseW
        val outH = (baseW * BlankSheetSpec.LOGICAL_HEIGHT_PX / BlankSheetSpec.LOGICAL_WIDTH_PX)
            .roundToInt()
            .coerceAtLeast(1)
        val srcMat = MatOfPoint2f(*sorted)
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )
        val perspective = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warp = Mat()
        try {
            Imgproc.warpPerspective(inputBgr, warp, perspective, Size(outW.toDouble(), outH.toDouble()))
            return warp
        } finally {
            srcMat.release()
            dstMat.release()
            perspective.release()
        }
    }

    fun warpByContourToSize(
        inputBgr: Mat,
        contour: Array<Point>,
        outW: Int,
        outH: Int,
    ): Mat {
        val sorted = sortPoints(contour)
        val srcMat = MatOfPoint2f(*sorted)
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )
        val perspective = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warp = Mat()
        try {
            Imgproc.warpPerspective(inputBgr, warp, perspective, Size(outW.toDouble(), outH.toDouble()))
            return warp
        } finally {
            srcMat.release()
            dstMat.release()
            perspective.release()
        }
    }

    private fun sortPoints(pts: Array<Point>): Array<Point> {
        val sorted = pts.sortedWith(compareBy({ it.y + it.x }, { it.y - it.x }))
        val out = Array(4) { Point() }
        out[0] = sorted[0]
        out[2] = sorted[3]
        val remain = sorted.subList(1, 3)
        if (remain[0].x > remain[1].x) {
            out[1] = remain[0]
            out[3] = remain[1]
        } else {
            out[1] = remain[1]
            out[3] = remain[0]
        }
        return out
    }
}
