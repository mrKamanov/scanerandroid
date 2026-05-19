package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: преобразование координат bitmap в область отрисовки при ContentScale.Fit.
 */
internal data class RealtimeSheetImageFitTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val drawWidthPx: Float,
    val drawHeightPx: Float,
)

internal object RealtimeSheetImageFit {
    fun compute(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        bitmapWidthPx: Float,
        bitmapHeightPx: Float,
    ): RealtimeSheetImageFitTransform {
        val bw = bitmapWidthPx.coerceAtLeast(1f)
        val bh = bitmapHeightPx.coerceAtLeast(1f)
        val cw = viewportWidthPx.coerceAtLeast(1f)
        val ch = viewportHeightPx.coerceAtLeast(1f)
        val scale = minOf(cw / bw, ch / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        return RealtimeSheetImageFitTransform(
            scale = scale,
            offsetX = (cw - drawW) / 2f,
            offsetY = (ch - drawH) / 2f,
            drawWidthPx = drawW,
            drawHeightPx = drawH,
        )
    }
}
