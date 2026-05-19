package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: поиск внешнего четырёхугольного контура бланка на изображении.
 */
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal object BatchSheetContourDetector {

    private const val BATCH_IMAGE_SIZE = 800
    private const val MIN_AREA_RESIZED = 50.0
    private const val CANNY_LOW = 10.0
    private const val CANNY_HIGH = 70.0

    fun findSheetContour(
        inputBgr: Mat,
        questionsCount: Int,
        choicesCount: Int,
    ): Array<Point>? {
        if (inputBgr.empty()) return null
        if (questionsCount <= 0 || choicesCount <= 0) {
            return findSheetContourNoResize(inputBgr)
        }

        val qc = questionsCount.coerceAtLeast(1)
        val cc = choicesCount.coerceAtLeast(1)
        val newWidth = cc * (BATCH_IMAGE_SIZE / cc)
        val newHeight = qc * (BATCH_IMAGE_SIZE / qc)

        val resized = Mat()
        val gray = Mat()
        val edged = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val rectContours = mutableListOf<MatOfPoint>()
        return try {
            Imgproc.resize(inputBgr, resized, Size(newWidth.toDouble(), newHeight.toDouble()))

            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.0)
            Imgproc.Canny(gray, edged, CANNY_LOW, CANNY_HIGH)
            Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area <= MIN_AREA_RESIZED) continue
                val c2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(c2f, true)
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    if (approx.total() == 4L) {
                        rectContours.add(MatOfPoint(*approx.toArray()))
                    }
                } finally {
                    c2f.release()
                    approx.release()
                }
            }

            if (rectContours.isEmpty()) return null

            val sx = inputBgr.cols().toDouble() / resized.cols().toDouble().coerceAtLeast(1.0)
            val sy = inputBgr.rows().toDouble() / resized.rows().toDouble().coerceAtLeast(1.0)
            val scaledQuads = rectContours.map { mp ->
                mp.toArray().map { p -> Point(p.x * sx, p.y * sy) }.toTypedArray()
            }
            BatchSheetContourFilter.pickBest(scaledQuads, inputBgr.cols(), inputBgr.rows())
        } finally {
            resized.release()
            gray.release()
            edged.release()
            hierarchy.release()
            contours.forEach { it.release() }
            rectContours.forEach { it.release() }
        }
    }

    fun findSheetContourPairForTwoColumns(
        inputBgr: Mat,
        questionsCount: Int,
        choicesCount: Int,
    ): Pair<Array<Point>, Array<Point>>? {
        if (inputBgr.empty()) return null
        if (questionsCount <= 0 || choicesCount <= 0) return null

        val qc = questionsCount.coerceAtLeast(1)
        val cc = choicesCount.coerceAtLeast(1)
        val newWidth = cc * (BATCH_IMAGE_SIZE / cc)
        val newHeight = qc * (BATCH_IMAGE_SIZE / qc)

        val resized = Mat()
        val gray = Mat()
        val edged = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        val rectContours = mutableListOf<MatOfPoint>()

        return try {
            Imgproc.resize(inputBgr, resized, Size(newWidth.toDouble(), newHeight.toDouble()))

            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.0)
            Imgproc.Canny(gray, edged, CANNY_LOW, CANNY_HIGH)
            Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area <= MIN_AREA_RESIZED) continue
                val c2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(c2f, true)
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    if (approx.total() == 4L) {
                        rectContours.add(MatOfPoint(*approx.toArray()))
                    }
                } finally {
                    c2f.release()
                    approx.release()
                }
            }

            if (rectContours.size < 2) return null

            val sx = inputBgr.cols().toDouble() / resized.cols().toDouble().coerceAtLeast(1.0)
            val sy = inputBgr.rows().toDouble() / resized.rows().toDouble().coerceAtLeast(1.0)

            data class Cand(
                val pts: Array<Point>,
                val area: Double,
                val cx: Double,
                val cy: Double,
                val top: Double,
                val bottom: Double,
            )

            fun polygonArea(points: Array<Point>): Double {
                if (points.size < 3) return 0.0
                var sum = 0.0
                for (i in points.indices) {
                    val j = (i + 1) % points.size
                    sum += points[i].x * points[j].y - points[j].x * points[i].y
                }
                return kotlin.math.abs(sum) / 2.0
            }

            val candidates = rectContours.mapNotNull { mp ->
                val ptsResized = mp.toArray()
                val ptsScaled = ptsResized.map { p -> Point(p.x * sx, p.y * sy) }.toTypedArray()
                if (BatchSheetContourFilter.isLikelyStudentNameMarker(
                        ptsScaled,
                        inputBgr.cols(),
                        inputBgr.rows(),
                    )
                ) {
                    return@mapNotNull null
                }
                val area = polygonArea(ptsScaled)
                val cx = ptsScaled.map { it.x }.average()
                val cy = ptsScaled.map { it.y }.average()
                val top = ptsScaled.minOf { it.y }
                val bottom = ptsScaled.maxOf { it.y }
                Cand(
                    pts = ptsScaled,
                    area = area,
                    cx = cx,
                    cy = cy,
                    top = top,
                    bottom = bottom,
                )
            }.sortedByDescending { it.area }

            val topK = candidates.take(6)
            var bestPair: Pair<Cand, Cand>? = null
            var bestScore = Double.NEGATIVE_INFINITY

            for (i in 0 until topK.size) {
                for (j in i + 1 until topK.size) {
                    val a = topK[i]
                    val b = topK[j]
                    val xDiff = kotlin.math.abs(a.cx - b.cx)
                    if (xDiff < inputBgr.cols() * 0.15) continue

                    val yDiff = kotlin.math.abs(a.cy - b.cy)
                    val ySpan = kotlin.math.max(a.bottom - a.top, b.bottom - b.top).coerceAtLeast(1.0)
                    if (yDiff > ySpan * 0.35) continue

                    val areaRatio = (a.area / b.area).coerceAtLeast(b.area / a.area)
                    if (areaRatio > 2.0) continue

                    val score = xDiff - yDiff * 0.15
                    if (score > bestScore) {
                        bestScore = score
                        bestPair = a to b
                    }
                }
            }

            val pair = bestPair ?: return null
            val left = if (pair.first.cx <= pair.second.cx) pair.first else pair.second
            val right = if (left == pair.first) pair.second else pair.first
            left.pts to right.pts
        } finally {
            resized.release()
            gray.release()
            edged.release()
            hierarchy.release()
            contours.forEach { it.release() }
            rectContours.forEach { it.release() }
        }
    }

    private fun findSheetContourNoResize(inputBgr: Mat): Array<Point>? {
        val gray = Mat()
        val edges = Mat()
        val contours = ArrayList<MatOfPoint>()
        val rectContours = mutableListOf<MatOfPoint>()
        return try {
            Imgproc.cvtColor(inputBgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.0)
            Imgproc.Canny(gray, edges, CANNY_LOW, CANNY_HIGH)
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area <= MIN_AREA_RESIZED) continue
                val c2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(c2f, true)
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    if (approx.total() == 4L) {
                        rectContours.add(MatOfPoint(*approx.toArray()))
                    }
                } finally {
                    c2f.release()
                    approx.release()
                }
            }
            if (rectContours.isEmpty()) return null
            val quads = rectContours.map { it.toArray() }
            BatchSheetContourFilter.pickBest(quads, inputBgr.cols(), inputBgr.rows())
        } finally {
            gray.release()
            edges.release()
            contours.forEach { it.release() }
            rectContours.forEach { it.release() }
        }
    }

}

