package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: поиск рамки (внешнего контура) бланка на выпрямленном кропе.
 * Область сетки = прямоугольник от ВНУТРЕННЕГО края рамки (толщина рамки 7 лог. px
 * пересчитывается пропорционально размеру обнаруженной рамки в кропе),
 * делится на равные части без дополнительных отступов.
 */
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

internal object BatchInnerGridDetector {

    private const val TAG = "BatchInnerGrid"
    private const val BUBBLE_PX = 30.0
    private const val GAP_PX = 10.0
    private const val BORDER_PX = 7.0
    private const val PADDING_PX = 5.0
    private const val INSET_PX = BORDER_PX + PADDING_PX
    private const val DARK_THRESHOLD = 128.0
    private const val MAX_SAMPLES_PER_EDGE = 8

    fun detectInnerRect(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
    ): BatchInnerGridRect {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)

        val innerW = choicesCount * BUBBLE_PX + (choicesCount - 1).coerceAtLeast(0) * GAP_PX
        val innerH = questionsCount * BUBBLE_PX + (questionsCount - 1).coerceAtLeast(0) * GAP_PX
        val outerW = innerW + 2 * INSET_PX
        val outerH = innerH + 2 * INSET_PX
        val expectedAspect = (outerW / outerH).coerceIn(0.1, 10.0)

        val rect = findAnswerFrameRect(bitmap, expectedAspect)
        if (rect != null) {
            val dl = (BORDER_PX / outerW) * rect.width
            val dt = (BORDER_PX / outerH) * rect.height
            val inner = insetRectPx(rect, w, h, dl, dt)
            Log.d(
                TAG,
                "frame=$rect aspect=${"%.3f".format(rect.width.toDouble() / max(rect.height, 1))} " +
                    "borderPx=(${"%.1f".format(dl)},${"%.1f".format(dt)}) " +
                    "grid=$inner (inner edge, no padding)",
            )
            return inner
        }

        val dl = (BORDER_PX / outerW) * w
        val dt = (BORDER_PX / outerH) * h
        if (frameAtImageEdges(bitmap, dl.toInt(), dt.toInt())) {
            val inner = insetRectPx(Rect(0, 0, w, h), w, h, dl, dt)
            Log.d(
                TAG,
                "frame=whole crop (border at image edges), borderPx=(${"%.1f".format(dl)},${"%.1f".format(dt)}) " +
                    "grid=$inner (inner edge)",
            )
            return inner
        }
        // Рамка не найдена: никогда не привязываемся к внешнему краю кропа —
        // отступаем на пропорциональную толщину бордюра (внутренний край).
        val inner = insetRectPx(Rect(0, 0, w, h), w, h, dl, dt)
        Log.d(
            TAG,
            "frame=not found, grid=whole crop inset by proportional border " +
                "borderPx=(${"%.1f".format(dl)},${"%.1f".format(dt)}) grid=$inner (inner edge)",
        )
        return inner
    }

    private fun insetRectPx(
        rect: Rect,
        w: Int,
        h: Int,
        atLeft: Double,
        atTop: Double,
    ): BatchInnerGridRect {
        val dl = atLeft.toInt().coerceAtLeast(0)
        val dt = atTop.toInt().coerceAtLeast(0)
        val left = (rect.x + dl).coerceIn(0, w - 2)
        val top = (rect.y + dt).coerceIn(0, h - 2)
        val right = (rect.x + rect.width - dl).coerceIn(left + 2, w)
        val bottom = (rect.y + rect.height - dt).coerceIn(top + 2, h)
        return BatchInnerGridRect(left, top, right, bottom)
    }

    private fun frameAtImageEdges(bitmap: Bitmap, dl: Int, dt: Int): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 40 || h < 40 || dl <= 0 || dt <= 0) return false
        val src = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            when (src.channels()) {
                4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
                else -> src.copyTo(gray)
            }
            val bandL = minOf(dl, w / 2).coerceAtLeast(1)
            val bandT = minOf(dt, h / 2).coerceAtLeast(1)
            var dark = 0
            var total = 0
            val stepY = max(1, h / 64)
            val stepX = max(1, w / 64)
            for (y in 0 until bandT step stepY) {
                for (x in 0 until w step stepX) {
                    total++
                    val v = gray.get(y, x)
                    if (v != null && v.isNotEmpty() && v[0] < DARK_THRESHOLD) dark++
                }
            }
            for (y in (h - bandT) until h step stepY) {
                for (x in 0 until w step stepX) {
                    total++
                    val v = gray.get(y, x)
                    if (v != null && v.isNotEmpty() && v[0] < DARK_THRESHOLD) dark++
                }
            }
            for (x in 0 until bandL step stepX) {
                for (y in 0 until h step stepY) {
                    total++
                    val v = gray.get(y, x)
                    if (v != null && v.isNotEmpty() && v[0] < DARK_THRESHOLD) dark++
                }
            }
            for (x in (w - bandL) until w step stepX) {
                for (y in 0 until h step stepY) {
                    total++
                    val v = gray.get(y, x)
                    if (v != null && v.isNotEmpty() && v[0] < DARK_THRESHOLD) dark++
                }
            }
            if (total == 0) return false
            return dark.toDouble() / total >= 0.4
        } finally {
            src.release()
            gray.release()
        }
    }

    private fun findAnswerFrameRect(bitmap: Bitmap, expectedAspect: Double): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 40 || h < 40) return null

        val src = Mat()
        val gray = Mat()
        val inv = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap, src)
            when (src.channels()) {
                4 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
                else -> src.copyTo(gray)
            }
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                gray,
                inv,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                41,
                12.0,
            )
            Imgproc.findContours(inv, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val imageArea = w.toDouble() * h.toDouble()
            val minArea = imageArea * 0.008
            val maxArea = imageArea * 0.97
            var best: Rect? = null
            var bestScore = Double.NEGATIVE_INFINITY

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area < minArea || area > maxArea) continue
                val c2f = MatOfPoint2f(*c.toArray())
                val approx = MatOfPoint2f()
                try {
                    val peri = Imgproc.arcLength(c2f, true)
                    if (peri <= 0.0) continue
                    Imgproc.approxPolyDP(c2f, approx, 0.022 * peri, true)
                    val pts = approx.toArray()
                    if (pts.size !in 4..6) continue
                    val poly = MatOfPoint(*pts)
                    val convex = Imgproc.isContourConvex(poly)
                    val rect = Imgproc.boundingRect(poly)
                    poly.release()
                    if (!convex) continue
                    if (rect.width < 16 || rect.height < 16) continue

                    val ratio = rect.width.toDouble() / max(rect.height, 1)
                    val minRatio = expectedAspect * 0.55
                    val maxRatio = expectedAspect * 1.7
                    if (ratio < minRatio || ratio > maxRatio) continue

                    val rectArea = rect.width.toDouble() * rect.height.toDouble()
                    val fill = area / max(rectArea, 1.0)
                    if (fill < 0.08 || fill > 1.05) continue

                    val darkRatio = borderDarkRatio(gray, rect)
                    if (darkRatio < 0.45) continue

                    val aspectBonus = 1.0 / (1.0 + 3.0 * abs(ratio - expectedAspect) / expectedAspect)
                    val score = area * aspectBonus * (0.3 + 0.7 * darkRatio)
                    if (score > bestScore) {
                        best = rect
                        bestScore = score
                    }
                } finally {
                    c2f.release()
                    approx.release()
                }
            }
            return best
        } finally {
            src.release()
            gray.release()
            inv.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun borderDarkRatio(gray: Mat, rect: Rect): Double {
        val x1 = rect.x
        val y1 = rect.y
        val x2 = rect.x + rect.width
        val y2 = rect.y + rect.height
        val w = gray.cols()
        val h = gray.rows()

        fun darkAny(x: Int, y: Int, vertical: Boolean): Boolean {
            val steps = 8
            for (d in 1..steps) {
                val px = if (vertical) x else x + d
                val py = if (vertical) y + d else y
                if (px < 0 || px >= w || py < 0 || py >= h) continue
                val v = gray.get(py, px)
                if (v != null && v.isNotEmpty() && v[0] < DARK_THRESHOLD) return true
            }
            return false
        }

        var darkCount = 0
        var total = 0
        val edgeW = x2 - x1
        val edgeH = y2 - y1
        for (i in 0 until MAX_SAMPLES_PER_EDGE) {
            val fx = x1 + (i * edgeW) / (MAX_SAMPLES_PER_EDGE - 1)
            val fy = y1 + (i * edgeH) / (MAX_SAMPLES_PER_EDGE - 1)
            if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue
            total++
            if (darkAny(fx, y1, vertical = true) || darkAny(fx, y2 - 1, vertical = true)) darkCount++
            total++
            if (darkAny(x1, fy, vertical = false) || darkAny(x2 - 1, fy, vertical = false)) darkCount++
        }
        if (total == 0) return 0.0
        return darkCount.toDouble() / total
    }
}