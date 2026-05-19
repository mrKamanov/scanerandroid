package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: вычисление позиции и размеров маркера "Вариант" (шестиугольник) относительно контура бланка.
 */
import androidx.compose.ui.geometry.Offset

data class BlankVariantMarkerRect(
    val left: Float,
    val top: Float,
    val size: Float,
)

fun computeBlankVariantMarkerRect(
    layout: BlankBubbleLayout,
    blankPanLogical: Offset,
    blankScale: Float,
    markerSize: Float = 34f,
    gapFromTop: Float = 12f,
): BlankVariantMarkerRect {
    val scale = clampBlankScale(blankScale)
    val localBounds = blankBubbleLayoutBoundingRect(layout)
    val localLeft = localBounds.left
    val localTop = localBounds.top - markerSize - gapFromTop
    val center = blankLayoutCenter(layout)
    val left = blankPanLogical.x + center.x + (localLeft - center.x) * scale
    val top = blankPanLogical.y + center.y + (localTop - center.y) * scale
    return BlankVariantMarkerRect(left = left, top = top, size = markerSize * scale)
}

fun buildVariantHexagonPoints(left: Float, top: Float, size: Float): List<Offset> {
    val r = size / 2f
    val cx = left + r
    val cy = top + r
    val k = 0.8660254f // sin(60°)
    return listOf(
        Offset(cx, cy - r),
        Offset(cx + k * r, cy - r * 0.5f),
        Offset(cx + k * r, cy + r * 0.5f),
        Offset(cx, cy + r),
        Offset(cx - k * r, cy + r * 0.5f),
        Offset(cx - k * r, cy - r * 0.5f),
    )
}

fun buildVariantHexagonPoints(size: Float): List<Offset> = buildVariantHexagonPoints(
    left = 0f,
    top = 0f,
    size = size,
)

