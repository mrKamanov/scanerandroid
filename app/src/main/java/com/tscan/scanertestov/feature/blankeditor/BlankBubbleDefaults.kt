package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: значения по умолчанию и параметры отрисовки сетки кружков бланка.
 */
object BlankBubbleDefaults {
    const val BUBBLE_SIZE_PX = 30f
    const val FONT_SIZE_PX = 15f
    const val BUBBLE_FONT_SCALE = 0.7f
    const val CELL_GAP_HORIZONTAL_PX = 10f
    const val CELL_GAP_VERTICAL_PX = 10f
    const val GRID_PADDING_PX = 5f
    const val BORDER_WIDTH_PX = 7f
    const val GRID_ORIGIN_X_PX = 50f
    const val GRID_ORIGIN_Y_PX = 400f
    const val COLUMN_GAP_PX = 20f
    const val BUBBLE_STROKE_PX = 3f
}

data class BlankBubbleRenderParams(
    val bubbleSizePx: Float,
    val fontSizePx: Float,
    val cellGapHorizontalPx: Float,
    val cellGapVerticalPx: Float,
    val gridPaddingPx: Float,
    val borderWidthPx: Float,
) {
    companion object {
        fun defaults(): BlankBubbleRenderParams = BlankBubbleRenderParams(
            bubbleSizePx = BlankBubbleDefaults.BUBBLE_SIZE_PX,
            fontSizePx = BlankBubbleDefaults.FONT_SIZE_PX,
            cellGapHorizontalPx = BlankBubbleDefaults.CELL_GAP_HORIZONTAL_PX,
            cellGapVerticalPx = BlankBubbleDefaults.CELL_GAP_VERTICAL_PX,
            gridPaddingPx = BlankBubbleDefaults.GRID_PADDING_PX,
            borderWidthPx = BlankBubbleDefaults.BORDER_WIDTH_PX,
        )
    }
}

fun clampBubbleSizePx(value: Float): Float = value.coerceIn(20f, 80f)
fun clampBubbleFontSizePx(value: Float): Float = value.coerceIn(10f, 30f)
fun clampCellGapHorizontalPx(value: Float): Float = value.coerceIn(5f, 50f)
fun clampCellGapVerticalPx(value: Float): Float = value.coerceIn(5f, 50f)
fun clampGridPaddingPx(value: Float): Float = value.coerceIn(5f, 50f)
fun clampGridBorderWidthPx(value: Float): Float = value.coerceIn(1f, 20f)
