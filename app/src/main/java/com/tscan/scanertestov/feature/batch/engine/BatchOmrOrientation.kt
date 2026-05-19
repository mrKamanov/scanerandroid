package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: согласование ориентации кадра и контура для OMR.
 */
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point

internal object BatchOmrOrientation {

    fun normalizeLikeEngine(bgr: Mat, contour: Array<Point>): Array<Point> {
        if (bgr.cols() <= bgr.rows()) return contour
        val oldW = bgr.cols()
        Core.rotate(bgr, bgr, Core.ROTATE_90_CLOCKWISE)
        return contour.map { p -> Point(p.y, oldW - 1.0 - p.x) }.toTypedArray()
    }
}
