package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: отбор контура рамки бланка среди кандидатов (исключая поле ФИО).
 */
import com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec
import org.opencv.core.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object BatchSheetContourFilter {

    private const val A4_WIDTH_OVER_HEIGHT = BlankSheetSpec.LOGICAL_WIDTH_PX / BlankSheetSpec.LOGICAL_HEIGHT_PX
    private const val NAME_MARKER_MIN_WIDTH_OVER_HEIGHT = 2.4
    private const val FLAT_STRIP_MIN_WIDTH_OVER_HEIGHT = 1.75
    private const val FLAT_STRIP_MAX_AREA_FRACTION = 0.24
    private const val MIN_SHEET_AREA_FRACTION = 0.07

    fun isLikelyStudentNameMarker(pts: Array<Point>, imageW: Int, imageH: Int): Boolean {
        if (pts.size < 4 || imageW <= 0 || imageH <= 0) return true
        val spanW = pts.maxOf { it.x } - pts.minOf { it.x }
        val spanH = pts.maxOf { it.y } - pts.minOf { it.y }
        if (spanW < 8.0 || spanH < 8.0) return true

        val widthOverHeight = spanW / spanH
        val areaFrac = polygonArea(pts) / (imageW * imageH.toDouble())

        if (widthOverHeight >= NAME_MARKER_MIN_WIDTH_OVER_HEIGHT) return true
        if (widthOverHeight >= FLAT_STRIP_MIN_WIDTH_OVER_HEIGHT && areaFrac <= FLAT_STRIP_MAX_AREA_FRACTION) {
            return true
        }
        return false
    }

    fun sheetContourScore(pts: Array<Point>, imageW: Int, imageH: Int): Double {
        if (isLikelyStudentNameMarker(pts, imageW, imageH)) return Double.NEGATIVE_INFINITY

        val spanW = pts.maxOf { it.x } - pts.minOf { it.x }
        val spanH = pts.maxOf { it.y } - pts.minOf { it.y }
        val shortSide = min(spanW, spanH).coerceAtLeast(1.0)
        val longSide = max(spanW, spanH)
        val portraitAspect = shortSide / longSide

        val area = polygonArea(pts)
        val areaFrac = area / (imageW * imageH.toDouble())
        if (areaFrac < MIN_SHEET_AREA_FRACTION) return Double.NEGATIVE_INFINITY

        val a4Deviation = abs(portraitAspect - A4_WIDTH_OVER_HEIGHT)
        val aspectBonus = (1.0 - a4Deviation.coerceAtMost(0.45)) * 0.35 + 0.65
        return area * aspectBonus
    }

    fun pickBest(candidates: List<Array<Point>>, imageW: Int, imageH: Int): Array<Point>? =
        candidates
            .asSequence()
            .map { pts -> pts to sheetContourScore(pts, imageW, imageH) }
            .filter { it.second > Double.NEGATIVE_INFINITY }
            .maxByOrNull { it.second }
            ?.first

    private fun polygonArea(points: Array<Point>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            sum += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return abs(sum) / 2.0
    }
}
