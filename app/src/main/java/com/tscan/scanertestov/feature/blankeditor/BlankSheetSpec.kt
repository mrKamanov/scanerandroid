package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: логические размеры листа A4 и соотношение сторон.
 */
object BlankSheetSpec {
    const val LOGICAL_WIDTH_PX = 595f
    const val LOGICAL_HEIGHT_PX = 842f
    val aspectRatio: Float get() = LOGICAL_WIDTH_PX / LOGICAL_HEIGHT_PX
}
