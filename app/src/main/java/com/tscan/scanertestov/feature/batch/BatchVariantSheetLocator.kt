package com.tscan.scanertestov.feature.batch

/**
 * Описание: локальный поиск маркера варианта (шестиугольник) рядом с блоком ответов.
 */
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_HEX_SIZE = 36

data class BatchVariantLocatorDebugPreview(
    val sheetOverlayBitmap: Bitmap,
    val cropBitmap: Bitmap,
)

private data class LocatorSnapshot(
    val imageBgr: Mat,
    val answerRect: Rect?,
    val cornerFrameRect: Rect?,
    val searchRect: Rect,
    val detectedHexRect: Rect?,
    val cropRect: Rect,
)

private data class CandidateRect(
    val rect: Rect,
    val score: Double,
)

private data class FloatPoint(val x: Float, val y: Float)
private data class CornerPoint(val x: Int, val y: Int, val score: Double)

fun extractVariantCropFromPhoto(
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
): Mat {
    val snapshot = buildLocatorSnapshot(sourceBitmap)
    try {
        return Mat(snapshot.imageBgr, snapshot.cropRect).clone()
    } finally {
        snapshot.imageBgr.release()
    }
}

fun extractStudentNameCropFromPhoto(
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
): Mat {
    val snapshot = buildLocatorSnapshot(sourceBitmap)
    try {
        val nameRect = buildStudentNameRect(
            hexRect = snapshot.detectedHexRect,
            answerRect = snapshot.answerRect,
            width = snapshot.imageBgr.cols(),
            height = snapshot.imageBgr.rows(),
        )
        return Mat(snapshot.imageBgr, nameRect).clone()
    } finally {
        snapshot.imageBgr.release()
    }
}

fun extractVariantAndNameCropsSingleSnapshot(sourceBitmap: Bitmap): Pair<Mat, Mat> {
    val snapshot = buildLocatorSnapshot(sourceBitmap)
    try {
        val variantCrop = Mat(snapshot.imageBgr, snapshot.cropRect).clone()
        val nameRect = buildStudentNameRect(
            hexRect = snapshot.detectedHexRect,
            answerRect = snapshot.answerRect,
            width = snapshot.imageBgr.cols(),
            height = snapshot.imageBgr.rows(),
        )
        val nameCrop = Mat(snapshot.imageBgr, nameRect).clone()
        return variantCrop to nameCrop
    } finally {
        snapshot.imageBgr.release()
    }
}

fun buildVariantLocatorDebugPreview(
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
): BatchVariantLocatorDebugPreview {
    val snapshot = buildLocatorSnapshot(sourceBitmap)
    try {
        val overlay = snapshot.imageBgr.clone()
        val crop = Mat(snapshot.imageBgr, snapshot.cropRect).clone()
        try {
            snapshot.answerRect?.let { rect ->
                Imgproc.rectangle(
                    overlay,
                    Point(rect.x.toDouble(), rect.y.toDouble()),
                    Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                    Scalar(255.0, 0.0, 255.0),
                    2,
                )
            }
            Imgproc.rectangle(
                overlay,
                Point(snapshot.searchRect.x.toDouble(), snapshot.searchRect.y.toDouble()),
                Point(
                    (snapshot.searchRect.x + snapshot.searchRect.width).toDouble(),
                    (snapshot.searchRect.y + snapshot.searchRect.height).toDouble(),
                ),
                Scalar(0.0, 255.0, 255.0),
                2,
            )
            snapshot.detectedHexRect?.let { rect ->
                Imgproc.rectangle(
                    overlay,
                    Point(rect.x.toDouble(), rect.y.toDouble()),
                    Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()),
                    Scalar(0.0, 255.0, 0.0),
                    2,
                )
            }
            Imgproc.rectangle(
                overlay,
                Point(snapshot.cropRect.x.toDouble(), snapshot.cropRect.y.toDouble()),
                Point(
                    (snapshot.cropRect.x + snapshot.cropRect.width).toDouble(),
                    (snapshot.cropRect.y + snapshot.cropRect.height).toDouble(),
                ),
                Scalar(255.0, 0.0, 0.0),
                2,
            )
            return BatchVariantLocatorDebugPreview(
                sheetOverlayBitmap = matBgrToBitmap(overlay),
                cropBitmap = matBgrToBitmap(crop),
            )
        } finally {
            overlay.release()
            crop.release()
        }
    } finally {
        snapshot.imageBgr.release()
    }
}

private fun buildLocatorSnapshot(sourceBitmap: Bitmap): LocatorSnapshot {
    val imageBgr = bitmapToOrientedBgr(sourceBitmap)
    val answerRect = detectAnswerBlockRect(imageBgr)
    val cornerFrameRect = detectLCornerFrameRect(imageBgr)
    val anchorRect = answerRect ?: cornerFrameRect
    val searchRect = buildHexSearchRect(anchorRect, imageBgr.cols(), imageBgr.rows())
    val hexRect = detectHexagonRect(imageBgr, searchRect, anchorRect)
        ?: run {
            val h = imageBgr.rows()
            val w = imageBgr.cols()
            val yMax = anchorRect?.let { ar ->
                (ar.y + ar.height * 0.26).toInt().coerceIn(h / 8, h - 8)
            } ?: (h * 0.52).toInt().coerceAtLeast(h / 5)
            val band = Rect(0, 0, w, yMax)
            detectHexagonRect(imageBgr, band, anchorRect)
        }

    val cropRect = when {
        hexRect != null -> innerRect(hexRect, imageBgr.cols(), imageBgr.rows(), shrink = 0.18f)
        anchorRect != null -> fallbackCropFromAnswerBlock(anchorRect, imageBgr.cols(), imageBgr.rows())
        else -> centerFallbackCrop(imageBgr.cols(), imageBgr.rows())
    }
    return LocatorSnapshot(
        imageBgr = imageBgr,
        answerRect = answerRect,
        cornerFrameRect = cornerFrameRect,
        searchRect = searchRect,
        detectedHexRect = hexRect,
        cropRect = cropRect,
    )
}

private fun bitmapToOrientedBgr(sourceBitmap: Bitmap): Mat {
    val src = Mat()
    Utils.bitmapToMat(sourceBitmap, src)
    val bgr = Mat()
    try {
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR)
        } else {
            src.copyTo(bgr)
        }
        if (bgr.cols() > bgr.rows()) {
            val rotated = Mat()
            Core.rotate(bgr, rotated, Core.ROTATE_90_CLOCKWISE)
            bgr.release()
            return rotated
        }
        return bgr
    } finally {
        src.release()
    }
}

private fun detectAnswerBlockRect(imageBgr: Mat): Rect? {
    val gray = Mat()
    val claheOut = Mat()
    val edges = Mat()
    val contours = mutableListOf<MatOfPoint>()
    try {
        Imgproc.cvtColor(imageBgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.createCLAHE(2.4, Size(8.0, 8.0)).apply(gray, claheOut)
        Imgproc.GaussianBlur(claheOut, claheOut, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(claheOut, edges, 60.0, 180.0)
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val imageArea = imageBgr.cols().toDouble() * imageBgr.rows().toDouble()
        var best: CandidateRect? = null
        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area < imageArea * 0.02 || area > imageArea * 0.9) return@forEach

            val c2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size !in 4..6) {
                c2f.release()
                approx.release()
                return@forEach
            }
            val approxInt = MatOfPoint(*pts)
            if (!Imgproc.isContourConvex(approxInt)) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            val rect = Imgproc.boundingRect(approxInt)
            val ratio = rect.width.toDouble() / max(rect.height, 1)
            if (ratio !in 0.35..2.6) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            if (rect.y < (imageBgr.rows() * 0.20).toInt()) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            val rectArea = rect.width.toDouble() * rect.height.toDouble()
            val fill = area / max(rectArea, 1.0)
            val bottomFrac = (rect.y + rect.height).toDouble() / max(imageBgr.rows(), 1).toDouble()
            if (bottomFrac < 0.36) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            val verticalBias = bottomFrac * area * 0.06
            val score = area * fill + verticalBias
            if (best == null || score > best!!.score) {
                best = CandidateRect(rect, score)
            }
            c2f.release()
            approx.release()
            approxInt.release()
        }
        return best?.rect
    } finally {
        gray.release()
        claheOut.release()
        edges.release()
        contours.forEach { it.release() }
    }
}

private fun detectLCornerFrameRect(imageBgr: Mat): Rect? {
    val gray = Mat()
    val blur = Mat()
    val binary = Mat()
    val contours = mutableListOf<MatOfPoint>()
    try {
        Imgproc.cvtColor(imageBgr, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blur, Size(3.0, 3.0), 0.0)
        Imgproc.threshold(blur, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val w = imageBgr.cols()
        val h = imageBgr.rows()
        val imageArea = w.toDouble() * h.toDouble()
        val minArea = imageArea * 0.00012
        val maxArea = imageArea * 0.02

        val candidates = contours.mapNotNull { c ->
            val area = Imgproc.contourArea(c)
            if (area < minArea || area > maxArea) return@mapNotNull null
            val rect = Imgproc.boundingRect(c)
            if (rect.width < 8 || rect.height < 8) return@mapNotNull null
            val ratio = rect.width.toDouble() / max(rect.height, 1).toDouble()
            if (ratio !in 0.45..2.2) return@mapNotNull null
            val rectArea = rect.width.toDouble() * rect.height.toDouble()
            val fill = area / max(rectArea, 1.0)
            if (fill > 0.72 || fill < 0.12) return@mapNotNull null
            val centerX = rect.x + rect.width / 2
            val centerY = rect.y + rect.height / 2
            val sizeScore = (area / max(minArea, 1.0)).coerceAtMost(6.0)
            CornerPoint(centerX, centerY, sizeScore)
        }
        if (candidates.isEmpty()) return null

        val cx = w / 2.0
        val cy = h / 2.0
        fun pick(quadrant: (CornerPoint) -> Boolean): CornerPoint? {
            return candidates
                .asSequence()
                .filter(quadrant)
                .maxByOrNull { point ->
                    val dx = kotlin.math.abs(point.x - cx) / max(w.toDouble(), 1.0)
                    val dy = kotlin.math.abs(point.y - cy) / max(h.toDouble(), 1.0)
                    point.score + dx + dy
                }
        }

        val tl = pick { it.x < cx && it.y < cy } ?: return null
        val tr = pick { it.x > cx && it.y < cy } ?: return null
        val bl = pick { it.x < cx && it.y > cy } ?: return null
        val br = pick { it.x > cx && it.y > cy } ?: return null

        val minX = listOf(tl.x, tr.x, bl.x, br.x).minOrNull() ?: return null
        val maxX = listOf(tl.x, tr.x, bl.x, br.x).maxOrNull() ?: return null
        val minY = listOf(tl.y, tr.y, bl.y, br.y).minOrNull() ?: return null
        val maxY = listOf(tl.y, tr.y, bl.y, br.y).maxOrNull() ?: return null
        val boxW = maxX - minX
        val boxH = maxY - minY
        if (boxW < w * 0.18 || boxH < h * 0.18) return null

        val padX = (boxW * 0.04).toInt()
        val padY = (boxH * 0.04).toInt()
        val x = (minX + padX).coerceIn(0, w - 2)
        val y = (minY + padY).coerceIn(0, h - 2)
        val rw = (boxW - padX * 2).coerceIn(2, w - x)
        val rh = (boxH - padY * 2).coerceIn(2, h - y)
        return Rect(x, y, rw, rh)
    } finally {
        gray.release()
        blur.release()
        binary.release()
        contours.forEach { it.release() }
    }
}

private fun buildHexSearchRect(answerRect: Rect?, width: Int, height: Int): Rect {
    if (answerRect == null) return Rect(0, 0, width, height)
    val x1 = max(0, answerRect.x - (answerRect.width * 0.30).toInt())
    val x2 = min(width, answerRect.x + (answerRect.width * 0.70).toInt())
    val y1 = max(0, answerRect.y - (answerRect.height * 0.90).toInt())
    val y2 = min(height, answerRect.y + (answerRect.height * 0.12).toInt())
    if (x2 <= x1 + 8 || y2 <= y1 + 8) return Rect(0, 0, width, height)
    return Rect(x1, y1, x2 - x1, y2 - y1)
}

private fun detectHexagonRect(imageBgr: Mat, searchRect: Rect, answerRect: Rect?): Rect? {
    val roi = Mat(imageBgr, searchRect)
    val gray = Mat()
    val claheOut = Mat()
    val adaptive = Mat()
    val edges = Mat()
    val contours = mutableListOf<MatOfPoint>()
    try {
        Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.createCLAHE(2.2, Size(8.0, 8.0)).apply(gray, claheOut)
        Imgproc.adaptiveThreshold(
            claheOut,
            adaptive,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            33,
            9.0,
        )
        Imgproc.Canny(claheOut, edges, 70.0, 190.0)
        Core.bitwise_or(adaptive, edges, adaptive)
        Imgproc.findContours(adaptive, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val regionArea = searchRect.width.toDouble() * searchRect.height.toDouble()
        val minArea = max(120.0, regionArea * 0.0006)
        val maxArea = max(2200.0, regionArea * 0.25)
        var best = matchTemplateHexagon(claheOut, answerRect, searchRect)

        contours.forEach { contour ->
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) return@forEach

            val c2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            val pts = approx.toArray()
            if (pts.size !in 5..7) {
                c2f.release()
                approx.release()
                return@forEach
            }
            val approxInt = MatOfPoint(*pts)
            if (!Imgproc.isContourConvex(approxInt)) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            val rect = Imgproc.boundingRect(approxInt)
            val ratio = rect.width.toDouble() / max(rect.height, 1)
            val solidity = area / max(rect.width.toDouble() * rect.height.toDouble(), 1.0)
            if (ratio !in 0.60..1.70 || solidity < 0.16) {
                c2f.release()
                approx.release()
                approxInt.release()
                return@forEach
            }
            val absRect = Rect(rect.x + searchRect.x, rect.y + searchRect.y, rect.width, rect.height)
            val vertexPenalty = abs(pts.size - 6) * 0.22
            val ratioPenalty = abs(ratio - 1.05) * 0.35
            val areaScore = min(1.0, area / max(minArea, 1.0))
            val score = areaScore - vertexPenalty - ratioPenalty
            if (best == null || score > best!!.score) {
                best = CandidateRect(absRect, score)
            }
            c2f.release()
            approx.release()
            approxInt.release()
        }
        return best?.rect
    } finally {
        roi.release()
        gray.release()
        claheOut.release()
        adaptive.release()
        edges.release()
        contours.forEach { it.release() }
    }
}

private fun matchTemplateHexagon(grayRegion: Mat, answerRect: Rect?, searchRect: Rect): CandidateRect? {
    val hexSize = estimateHexSize(answerRect).coerceIn(20, 80)
    if (grayRegion.cols() <= hexSize + 4 || grayRegion.rows() <= hexSize + 4) return null
    val template = Mat.zeros(hexSize, hexSize, grayRegion.type())
    val points = hexagonPoints(hexSize.toFloat())
    val contour = MatOfPoint(*points.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
    try {
        Imgproc.polylines(template, listOf(contour), true, Scalar(255.0), 2)
        val result = Mat()
        try {
            Imgproc.matchTemplate(grayRegion, template, result, Imgproc.TM_CCOEFF_NORMED)
            val mmr = Core.minMaxLoc(result)
            if (mmr.maxVal < 0.32) return null
            val x = (mmr.maxLoc.x.toInt() + searchRect.x).coerceAtLeast(0)
            val y = (mmr.maxLoc.y.toInt() + searchRect.y).coerceAtLeast(0)
            return CandidateRect(Rect(x, y, hexSize, hexSize), mmr.maxVal)
        } finally {
            result.release()
        }
    } finally {
        contour.release()
        template.release()
    }
}

private fun hexagonPoints(size: Float): List<FloatPoint> {
    val r = size / 2f
    val cx = r
    val cy = r
    val k = 0.8660254f
    return listOf(
        FloatPoint(cx, cy - r),
        FloatPoint(cx + k * r, cy - r * 0.5f),
        FloatPoint(cx + k * r, cy + r * 0.5f),
        FloatPoint(cx, cy + r),
        FloatPoint(cx - k * r, cy + r * 0.5f),
        FloatPoint(cx - k * r, cy - r * 0.5f),
    )
}

private fun estimateHexSize(answerRect: Rect?): Int {
    if (answerRect == null) return DEFAULT_HEX_SIZE
    return max(24, (answerRect.height * 0.12f).toInt())
}

private fun fallbackCropFromAnswerBlock(answerRect: Rect, width: Int, height: Int): Rect {
    val size = estimateHexSize(answerRect)
    val x = (answerRect.x + answerRect.width * 0.02f).toInt().coerceIn(0, width - 2)
    val y = (answerRect.y - size - 10).coerceIn(0, height - 2)
    val w = size.coerceAtMost(width - x).coerceAtLeast(2)
    val h = size.coerceAtMost(height - y).coerceAtLeast(2)
    return Rect(x, y, w, h)
}

private fun centerFallbackCrop(width: Int, height: Int): Rect {
    val size = (min(width, height) / 8).coerceAtLeast(20)
    val x = ((width - size) / 2).coerceAtLeast(0)
    val y = ((height - size) / 5).coerceAtLeast(0)
    return Rect(x, y, size, size)
}

private fun innerRect(rect: Rect, width: Int, height: Int, shrink: Float): Rect {
    val dx = (rect.width * shrink).toInt()
    val dy = (rect.height * shrink).toInt()
    val x = (rect.x + dx).coerceIn(0, width - 2)
    val y = (rect.y + dy).coerceIn(0, height - 2)
    val w = (rect.width - dx * 2).coerceAtLeast(2).coerceAtMost(width - x)
    val h = (rect.height - dy * 2).coerceAtLeast(2).coerceAtMost(height - y)
    return Rect(x, y, w, h)
}

private fun buildStudentNameRect(
    hexRect: Rect?,
    answerRect: Rect?,
    width: Int,
    height: Int,
): Rect {
    if (hexRect != null) {
        val nameHeight = max(18, hexRect.height)
        val nameWidth = max(80, (hexRect.width * 6.18f).toInt())
        val gap = max(4, (hexRect.height * 0.30f).toInt())
        val x = hexRect.x.coerceIn(0, width - 2)
        val y = (hexRect.y - nameHeight - gap).coerceIn(0, height - 2)
        val w = nameWidth.coerceAtMost(width - x).coerceAtLeast(2)
        val h = nameHeight.coerceAtMost(height - y).coerceAtLeast(2)
        return Rect(x, y, w, h)
    }
    if (answerRect != null) {
        val h = max(20, (answerRect.height * 0.08f).toInt())
        val w = max(90, (answerRect.width * 0.55f).toInt())
        val x = answerRect.x.coerceIn(0, width - 2)
        val y = (answerRect.y - h - 10).coerceIn(0, height - 2)
        return Rect(
            x,
            y,
            w.coerceAtMost(width - x).coerceAtLeast(2),
            h.coerceAtMost(height - y).coerceAtLeast(2),
        )
    }
    val w = (width * 0.45f).toInt().coerceAtLeast(90).coerceAtMost(width - 2)
    val h = (height * 0.06f).toInt().coerceAtLeast(20).coerceAtMost(height - 2)
    val x = ((width - w) / 2).coerceAtLeast(0)
    val y = ((height - h) / 6).coerceAtLeast(0)
    return Rect(x, y, w, h)
}

private fun matBgrToBitmap(srcBgr: Mat): Bitmap {
    val rgba = Mat()
    try {
        Imgproc.cvtColor(srcBgr, rgba, Imgproc.COLOR_BGR2RGBA)
        val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        return bmp
    } finally {
        rgba.release()
    }
}

