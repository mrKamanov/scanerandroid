package com.tscan.scanertestov.feature.realtime.engine

/**
 * Описание: поиск контура бланка на кадре камеры для превью и OMR.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchSheetContourDetector
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point

internal object RealtimeSheetContour {

    fun detect(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
    ): Array<Point>? {
        if (!RealtimeOpenCvBootstrap.ensureLoaded()) return null
        val mat = Mat()
        return try {
            Utils.bitmapToMat(bitmap, mat)
            BatchSheetContourDetector.findSheetContour(
                inputBgr = mat,
                questionsCount = questionsCount,
                choicesCount = choicesCount,
            )
        } finally {
            mat.release()
        }
    }

    fun copy(contour: Array<Point>): Array<Point> =
        contour.map { Point(it.x, it.y) }.toTypedArray()
}
