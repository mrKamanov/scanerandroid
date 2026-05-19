package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: внутренний прямоугольник сетки (без толщины внешнего контура рамки).
 */
internal data class BatchInnerGridRect(
    val left: Int,
    val top: Int,
    val rightEx: Int,
    val bottomEx: Int,
) {
    val width: Int get() = rightEx - left
    val height: Int get() = bottomEx - top
}
