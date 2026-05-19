package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: fallback-классификатор ячейки ответа по OpenCV-эвристике fill-ratio.
 */
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

internal object BatchCellHeuristicClassifier {
    fun classify(cellBgr: Mat): Pair<BatchCellClass, Float> {
        val gray = Mat()
        val binary = Mat()
        return try {
            Imgproc.cvtColor(cellBgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)
            val nonZero = Core.countNonZero(binary).toFloat()
            val area = (binary.rows() * binary.cols()).coerceAtLeast(1).toFloat()
            val fill = nonZero / area
            when {
                fill >= 0.22f -> BatchCellClass.Yes to fill
                fill <= 0.08f -> BatchCellClass.No to (1f - fill)
                else -> BatchCellClass.Fixed to (1f - kotlin.math.abs(fill - 0.15f) / 0.07f).coerceIn(0f, 1f)
            }
        } finally {
            gray.release()
            binary.release()
        }
    }
}
