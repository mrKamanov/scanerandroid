package com.tscan.scanertestov.feature.batch

/**
 * Описание: вырезка шестиугольника варианта и поля имени по найденной сетке бланка.
 * Их место над сеткой задано конструктором (computeBlankVariantMarkerRect /
 * computeBlankStudentNameMarkerRect), поэтому после поиска сетки они известны точно —
 * в том числе на листе, снятом под углом или вверх ногами.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchGridLocator
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

internal fun extractVariantAndNameCropsByGrid(
    context: android.content.Context?,
    sourceBitmap: Bitmap,
    questionCount: Int,
    optionCount: Int,
    columnCount: Int,
): Pair<Mat, Mat>? {
    if (questionCount <= 0 || optionCount <= 0) return null
    if (!OpenCVLoader.initLocal()) return null
    val src = Mat()
    val bgr = Mat()
    try {
        Utils.bitmapToMat(sourceBitmap, src)
        if (src.channels() == 4) Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR) else src.copyTo(bgr)
        if (bgr.cols() > bgr.rows()) Core.rotate(bgr, bgr, Core.ROTATE_90_CLOCKWISE)
        val alternatives = context?.let { com.tscan.scanertestov.data.PrintedBlankParamsStore.load(it) }.orEmpty()
        val loc = BatchGridLocator.locateAny(bgr, questionCount, optionCount, columnCount, alternatives) ?: return null
        try {
            if (loc.score < BatchGridLocator.MIN_SCORE) return null
            // Шестиугольник 34×34 над левым верхним углом сетки (зазор 12); берём середину —
            // как раньше, без контура, чтобы OCR видел только цифру.
            val variant = BatchGridLocator.warpRegion(bgr, loc, 7.0, -40.0, 27.0, -18.0)
            // Поле имени 210×34 над шестиугольником (зазор 10), с запасом 6 ед. вокруг и в крупном
            // масштабе: по точному размеру распознавание узнаёт, что вырезка сделана по сетке.
            val name = BatchGridLocator.warpRegion(
                bgr, loc,
                -GRID_NAME_MARGIN, -90.0 - GRID_NAME_MARGIN, 210.0 + GRID_NAME_MARGIN, -56.0 + GRID_NAME_MARGIN,
                scale = GRID_NAME_SCALE,
            )
            return variant to name
        } finally {
            loc.homography.release()
        }
    } catch (e: Exception) {
        return null
    } finally {
        src.release()
        bgr.release()
    }
}

internal const val GRID_NAME_SCALE = 6.0
internal const val GRID_NAME_MARGIN = 6.0
private val GRID_NAME_W = ((210.0 + 2 * GRID_NAME_MARGIN) * GRID_NAME_SCALE).toInt()
private val GRID_NAME_H = ((34.0 + 2 * GRID_NAME_MARGIN) * GRID_NAME_SCALE).toInt()

/** Вырезка поля имени сделана по найденной сетке (точные границы ячеек «Фамилия» и «Имя»). */
internal fun isGridNameCrop(crop: Mat): Boolean =
    kotlin.math.abs(crop.cols() - GRID_NAME_W) <= 1 && kotlin.math.abs(crop.rows() - GRID_NAME_H) <= 1

/** Ячейки «Фамилия» и «Имя» без рамки поля. Вызывающий освобождает оба Mat. */
internal fun splitGridNameCrop(crop: Mat): Pair<Mat, Mat> {
    val s = GRID_NAME_SCALE
    val x0 = (GRID_NAME_MARGIN * s).toInt()
    val x1 = ((GRID_NAME_MARGIN + 210.0) * s).toInt().coerceAtMost(crop.cols())
    val y0 = (GRID_NAME_MARGIN * s).toInt()
    val y1 = ((GRID_NAME_MARGIN + 34.0) * s).toInt().coerceAtMost(crop.rows())
    val mid = (x0 + x1) / 2
    val m = (2.5 * s).toInt() // отступ от линий рамки
    val left = Mat(crop, org.opencv.core.Rect(x0 + m, y0 + m, mid - x0 - 2 * m, y1 - y0 - 2 * m)).clone()
    val right = Mat(crop, org.opencv.core.Rect(mid + m, y0 + m, x1 - mid - 2 * m, y1 - y0 - 2 * m)).clone()
    return left to right
}
