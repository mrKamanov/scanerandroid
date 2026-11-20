package com.tscan.scanertestov.feature.realtime.engine

/**
 * Описание: поиск сетки бланка на кадре камеры для живого превью.
 * Использует тот же поиск по устройству бланка, что и пакетная проверка (быстрый режим);
 * контур показывается, только если сетка действительно узнана.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchGridLocator
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point

internal object RealtimeSheetContour {
    private const val LIVE_MIN_SCORE = 0.2f

    fun detect(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int = 1,
    ): Array<Point>? {
        if (!RealtimeOpenCvBootstrap.ensureLoaded()) return null
        val mat = Mat()
        return try {
            Utils.bitmapToMat(bitmap, mat)
            val found = BatchGridLocator.locateFast(mat, questionsCount, choicesCount, columnCount)
            // Порог для живого кадра мягче, чем для готового фото: превью мелкое и без точной
            // подгонки, строгий порог (0.6) на нём почти не достигался — кнопки «Стоп-кадр» и
            // «Проверить» оставались неактивными. Окончательное решение принимает полная проверка.
            found?.takeIf { it.second >= LIVE_MIN_SCORE }?.first
        } finally {
            mat.release()
        }
    }

    fun copy(contour: Array<Point>): Array<Point> =
        contour.map { Point(it.x, it.y) }.toTypedArray()
}
