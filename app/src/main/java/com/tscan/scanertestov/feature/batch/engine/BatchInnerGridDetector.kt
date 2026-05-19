package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: определение области равномерной сетки OMR на выпрямленном кропе бланка.
 */
import android.graphics.Bitmap

internal object BatchInnerGridDetector {

    fun detectInnerRect(bitmap: Bitmap): BatchInnerGridRect {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        return BatchInnerGridRect(
            left = 0,
            top = 0,
            rightEx = w,
            bottomEx = h,
        )
    }
}
