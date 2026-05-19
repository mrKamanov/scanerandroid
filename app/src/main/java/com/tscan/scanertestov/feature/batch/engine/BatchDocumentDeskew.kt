package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: выравнивание наклона кропа бланка после перспективного warp.
 */
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal object BatchDocumentDeskew {

    fun deskewIfNeeded(inputBgr: Mat, maxAbsDeg: Double = 5.5): Mat {
        if (inputBgr.empty()) return inputBgr
        val deg = estimateSkewDegrees(inputBgr) ?: return inputBgr
        if (abs(deg) < 0.35) return inputBgr
        val a = deg.coerceIn(-maxAbsDeg, maxAbsDeg)
        return rotateBgrExpand(inputBgr, a)
    }

    private fun estimateSkewDegrees(bgr: Mat): Double? {
        val maxSide = kotlin.math.max(bgr.cols(), bgr.rows()).coerceAtLeast(1)
        val scale = kotlin.math.min(1.0, 520.0 / maxSide)
        val sw = kotlin.math.max(1, (bgr.cols() * scale).roundToInt())
        val sh = kotlin.math.max(1, (bgr.rows() * scale).roundToInt())
        val small = Mat()
        Imgproc.resize(bgr, small, Size(sw.toDouble(), sh.toDouble()))
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
        small.release()
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 45.0, 135.0, 3, true)
        gray.release()
        val lines = Mat()
        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0,
            Math.PI / 180.0,
            28,
            25.0,
            8.0,
        )
        edges.release()
        val angles = mutableListOf<Double>()
        for (i in 0 until lines.rows()) {
            val v = lines.get(i, 0)
            val x1 = v[0]
            val y1 = v[1]
            val x2 = v[2]
            val y2 = v[3]
            val rad = kotlin.math.atan2(y2 - y1, x2 - x1)
            var d = Math.toDegrees(rad)
            while (d <= -90.0) d += 180.0
            while (d > 90.0) d -= 180.0
            if (abs(d) < 28.0) angles.add(d)
        }
        lines.release()
        if (angles.size < 6) return null
        angles.sort()
        return angles[angles.size / 2]
    }

    private fun rotateBgrExpand(src: Mat, angleDeg: Double): Mat {
        val w = src.cols()
        val h = src.rows()
        val center = Point(w / 2.0, h / 2.0)
        val rot = Imgproc.getRotationMatrix2D(center, angleDeg, 1.0)
        val cos = abs(rot.get(0, 0)[0])
        val sin = abs(rot.get(1, 0)[0])
        val newW = ceil(h * sin + w * cos).toInt().coerceAtLeast(1)
        val newH = ceil(h * cos + w * sin).toInt().coerceAtLeast(1)
        rot.put(0, 2, rot.get(0, 2)[0] + newW / 2.0 - center.x)
        rot.put(1, 2, rot.get(1, 2)[0] + newH / 2.0 - center.y)
        val dst = Mat()
        Imgproc.warpAffine(
            src,
            dst,
            rot,
            Size(newW.toDouble(), newH.toDouble()),
            Imgproc.INTER_LINEAR,
            Core.BORDER_REPLICATE,
            Scalar.all(0.0),
        )
        rot.release()
        return dst
    }
}
