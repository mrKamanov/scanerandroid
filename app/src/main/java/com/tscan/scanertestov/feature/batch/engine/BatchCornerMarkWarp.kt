package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: выравнивание бланка по L-ориентирам в углах рамки.
 */
import android.util.Log
import com.tscan.scanertestov.feature.blankeditor.CornerMarkTipQuadLogical
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object BatchCornerMarkWarp {
    private const val TAG = "BatchCornerMarkWarp"

    fun tryWarpByCornerMarks(inputBgr: Mat, tipTemplate: CornerMarkTipQuadLogical): Mat? {
        if (inputBgr.empty()) return null
        val expectedAspect = tipTemplate.aspectRatio().toDouble().coerceIn(0.2, 5.0)
        val grayFull = Mat()
        Imgproc.cvtColor(inputBgr, grayFull, Imgproc.COLOR_BGR2GRAY)
        val detected = detectTipQuadFromBinaryCorners(inputBgr, expectedAspect)
        if (detected == null) {
            grayFull.release()
            return null
        }
        val sorted = sortQuadPointsForWarp(detected)
        val refined = refineCornersSubPix(grayFull, sorted)
        grayFull.release()
        val maxDim = max(inputBgr.cols(), inputBgr.rows()).coerceAtLeast(1)
        val outW = min(maxDim, 1600).coerceIn(400, 1600)
        val outH = (outW / expectedAspect).roundToInt().coerceIn(200, 2400)
        val srcMat = MatOfPoint2f(*refined)
        val dstMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outW - 1.0, 0.0),
            Point(outW - 1.0, outH - 1.0),
            Point(0.0, outH - 1.0),
        )
        val perspective = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val warp = Mat()
        return try {
            Imgproc.warpPerspective(inputBgr, warp, perspective, Size(outW.toDouble(), outH.toDouble()))
            Log.i(TAG, "L-mark region warp ${inputBgr.cols()}x${inputBgr.rows()} -> ${outW}x${outH}")
            warp
        } catch (e: Exception) {
            Log.w(TAG, "warpPerspective: ${e.message}")
            warp.release()
            null
        } finally {
            srcMat.release()
            dstMat.release()
            perspective.release()
        }
    }

    private fun refineCornersSubPix(gray: Mat, pts: Array<Point>): Array<Point> {
        val m2 = MatOfPoint2f(*pts)
        val criteria = TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 40, 0.001)
        Imgproc.cornerSubPix(gray, m2, Size(11.0, 11.0), Size(-1.0, -1.0), criteria)
        val out = m2.toArray()
        m2.release()
        return out
    }

    private fun detectTipQuadFromBinaryCorners(bgr: Mat, expectedAspect: Double): Array<Point>? {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        val work = Mat()
        val scale = min(720.0 / bgr.cols().toDouble(), 720.0 / bgr.rows().toDouble()).coerceAtMost(1.0)
        val sw = max(1, (bgr.cols() * scale).roundToInt())
        val sh = max(1, (bgr.rows() * scale).roundToInt())
        Imgproc.resize(gray, work, Size(sw.toDouble(), sh.toDouble()))
        gray.release()
        Imgproc.GaussianBlur(work, work, Size(5.0, 5.0), 0.0)
        val bin = Mat()
        Imgproc.adaptiveThreshold(
            work,
            bin,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            35,
            10.0,
        )
        Imgproc.morphologyEx(
            bin,
            bin,
            Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )
        work.release()
        val minDist = max(12.0, min(sw, sh) * 0.028)
        val corners = MatOfPoint()
        Imgproc.goodFeaturesToTrack(
            bin,
            corners,
            120,
            0.04,
            minDist,
            Mat(),
            5,
            false,
            0.04,
        )
        bin.release()
        val pts = corners.toArray()
        corners.release()
        if (pts.size < 16) return null
        val invSx = bgr.cols().toDouble() / sw
        val invSy = bgr.rows().toDouble() / sh
        fun toFull(p: Point) = Point(p.x * invSx, p.y * invSy)
        val fullPts = pts.map { toFull(it) }
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in fullPts) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        val cx = (minX + maxX) * 0.5
        val cy = (minY + maxY) * 0.5
        val bins = Array(4) { mutableListOf<Point>() }
        for (p in fullPts) {
            val q = when {
                p.x <= cx && p.y <= cy -> 0
                p.x > cx && p.y <= cy -> 1
                p.x <= cx && p.y > cy -> 2
                else -> 3
            }
            bins[q].add(p)
        }
        fun outerCornerKey(bin: Int, p: Point): Double = when (bin) {
            0 -> p.x + p.y
            1 -> -p.x + p.y
            2 -> p.x - p.y
            else -> -p.x - p.y
        }
        val topPerBin = bins.mapIndexed { bin, list ->
            list.sortedBy { outerCornerKey(bin, it) }.take(10)
        }
        if (topPerBin.any { it.isEmpty() }) return null
        val imageArea = bgr.cols().toDouble() * bgr.rows().toDouble()
        val minQuadArea = imageArea * 0.045
        var bestPts: Array<Point>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAspectErr = 1.0
        for (a in topPerBin[0]) for (b in topPerBin[1]) for (c in topPerBin[2]) for (d in topPerBin[3]) {
            val quad = arrayOf(a, b, c, d)
            val sorted = sortQuadPointsForWarp(quad)
            val w = hypot(sorted[1].x - sorted[0].x, sorted[1].y - sorted[0].y) +
                hypot(sorted[2].x - sorted[3].x, sorted[2].y - sorted[3].y)
            val h = hypot(sorted[3].x - sorted[0].x, sorted[3].y - sorted[0].y) +
                hypot(sorted[2].x - sorted[1].x, sorted[2].y - sorted[1].y)
            val asp = (w / 2.0) / max(h / 2.0, 1.0)
            val aspectErr = abs(asp - expectedAspect) / expectedAspect
            if (aspectErr > 0.38) continue
            val area = quadPolygonArea(sorted)
            if (area < minQuadArea) continue
            val score = area / max(aspectErr, 0.06)
            if (score > bestScore || (abs(score - bestScore) < 1e-6 && aspectErr < bestAspectErr)) {
                bestScore = score
                bestAspectErr = aspectErr
                bestPts = arrayOf(
                    Point(sorted[0].x, sorted[0].y),
                    Point(sorted[1].x, sorted[1].y),
                    Point(sorted[2].x, sorted[2].y),
                    Point(sorted[3].x, sorted[3].y),
                )
            }
        }
        if (bestPts == null || bestAspectErr > 0.34) return null
        return bestPts
    }

    private fun quadPolygonArea(pts: Array<Point>): Double {
        var s = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            s += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(s) * 0.5
    }

    private fun sortQuadPointsForWarp(pts: Array<Point>): Array<Point> {
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
