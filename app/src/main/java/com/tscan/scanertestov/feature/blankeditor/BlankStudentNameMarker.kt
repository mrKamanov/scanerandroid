package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: вычисление прямоугольника зоны "ФИО ученика" относительно контура бланка.
 */
data class BlankStudentNameMarkerRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

fun computeBlankStudentNameMarkerRect(
    layout: BlankBubbleLayout,
    blankPanLogical: androidx.compose.ui.geometry.Offset,
    blankScale: Float,
    markerWidth: Float = 210f,
    markerHeight: Float = 34f,
    gapFromVariant: Float = 10f,
): BlankStudentNameMarkerRect {
    val scale = clampBlankScale(blankScale)
    val localBounds = blankBubbleLayoutBoundingRect(layout)
    val localVariantTop = localBounds.top - 34f - 12f
    val localLeft = localBounds.left
    val localTop = localVariantTop - markerHeight - gapFromVariant
    val center = blankLayoutCenter(layout)
    val left = blankPanLogical.x + center.x + (localLeft - center.x) * scale
    val top = blankPanLogical.y + center.y + (localTop - center.y) * scale
    return BlankStudentNameMarkerRect(
        left = left,
        top = top,
        width = markerWidth * scale,
        height = markerHeight * scale,
    )
}

