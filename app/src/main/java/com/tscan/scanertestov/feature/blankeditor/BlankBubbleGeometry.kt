package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: раскладка сетки бланка, панорама листа и hit-test по кружкам.
 */
import androidx.compose.ui.geometry.Offset

data class BlankFrameSpec(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class BlankBubbleSpec(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val label: String?,
    val isEmptyPlaceholder: Boolean,
)

data class BlankBubbleLayout(
    val frames: List<BlankFrameSpec>,
    val bubbles: List<BlankBubbleSpec>,
    val renderParams: BlankBubbleRenderParams,
)

private val OX = BlankBubbleDefaults.GRID_ORIGIN_X_PX
private val OY = BlankBubbleDefaults.GRID_ORIGIN_Y_PX
private val COL_GAP = BlankBubbleDefaults.COLUMN_GAP_PX

private fun innerWidthPx(options: Int, bubbleSizePx: Float, gapHorizontalPx: Float): Float =
    options * bubbleSizePx + (options - 1).coerceAtLeast(0) * gapHorizontalPx

private fun innerHeightPx(rows: Int, bubbleSizePx: Float, gapVerticalPx: Float): Float =
    if (rows <= 0) 0f
    else rows * bubbleSizePx + (rows - 1).coerceAtLeast(0) * gapVerticalPx

private fun outerWidthPx(innerW: Float, gridPaddingPx: Float, borderWidthPx: Float): Float =
    innerW + 2 * gridPaddingPx + 2 * borderWidthPx

private fun outerHeightPx(innerH: Float, gridPaddingPx: Float, borderWidthPx: Float): Float =
    innerH + 2 * gridPaddingPx + 2 * borderWidthPx

fun buildBlankBubbleLayout(
    config: BlankEditorLayoutConfig,
    renderParams: BlankBubbleRenderParams = BlankBubbleRenderParams.defaults(),
): BlankBubbleLayout {
    val questions = config.questionCount.coerceIn(1, 35)
    val options = config.optionCount.coerceIn(2, 9)
    val bubbleSizePx = clampBubbleSizePx(renderParams.bubbleSizePx)
    val fontSizePx = clampBubbleFontSizePx(renderParams.fontSizePx)
    val gapHorizontalPx = clampCellGapHorizontalPx(renderParams.cellGapHorizontalPx)
    val gapVerticalPx = clampCellGapVerticalPx(renderParams.cellGapVerticalPx)
    val gridPaddingPx = clampGridPaddingPx(renderParams.gridPaddingPx)
    val borderWidthPx = clampGridBorderWidthPx(renderParams.borderWidthPx)
    val normalizedParams = BlankBubbleRenderParams(
        bubbleSizePx = bubbleSizePx,
        fontSizePx = fontSizePx,
        cellGapHorizontalPx = gapHorizontalPx,
        cellGapVerticalPx = gapVerticalPx,
        gridPaddingPx = gridPaddingPx,
        borderWidthPx = borderWidthPx,
    )

    val frames = mutableListOf<BlankFrameSpec>()
    val bubbles = mutableListOf<BlankBubbleSpec>()

    val innerW = innerWidthPx(options, bubbleSizePx = bubbleSizePx, gapHorizontalPx = gapHorizontalPx)

    fun addNumberedBubbles(
        contentLeft: Float,
        contentTop: Float,
        rows: Int,
        startQuestionIndex1: Int,
    ) {
        for (q in 0 until rows) {
            for (o in 1..options) {
                val cx = contentLeft + (o - 1) * (bubbleSizePx + gapHorizontalPx) + bubbleSizePx / 2f
                val cy = contentTop + q * (bubbleSizePx + gapVerticalPx) + bubbleSizePx / 2f
                val qNum = startQuestionIndex1 + q
                val label = "$qNum.$o"
                bubbles.add(
                    BlankBubbleSpec(
                        id = label,
                        centerX = cx,
                        centerY = cy,
                        label = label,
                        isEmptyPlaceholder = false,
                    )
                )
            }
        }
    }

    fun addEmptyRow(contentLeft: Float, contentTop: Float, rowIndex: Int) {
        for (o in 1..options) {
            val cx = contentLeft + (o - 1) * (bubbleSizePx + gapHorizontalPx) + bubbleSizePx / 2f
            val cy = contentTop + rowIndex * (bubbleSizePx + gapVerticalPx) + bubbleSizePx / 2f
            bubbles.add(
                BlankBubbleSpec(
                    id = "empty:${cx.toInt()}:${cy.toInt()}:$o",
                    centerX = cx,
                    centerY = cy,
                    label = null,
                    isEmptyPlaceholder = true,
                )
            )
        }
    }

    if (config.columnCount == 1) {
        val innerH = innerHeightPx(questions, bubbleSizePx = bubbleSizePx, gapVerticalPx = gapVerticalPx)
        val outerW = outerWidthPx(innerW, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
        val outerH = outerHeightPx(innerH, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
        frames.add(BlankFrameSpec(OX, OY, outerW, outerH))
        val contentLeft = OX + borderWidthPx + gridPaddingPx
        val contentTop = OY + borderWidthPx + gridPaddingPx
        addNumberedBubbles(contentLeft, contentTop, questions, startQuestionIndex1 = 1)
    } else {
        val q1 = (questions + 1) / 2
        val q2 = questions - q1
        if (q2 == 0) {
            val innerH = innerHeightPx(q1, bubbleSizePx = bubbleSizePx, gapVerticalPx = gapVerticalPx)
            val outerW = outerWidthPx(innerW, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
            val outerH = outerHeightPx(innerH, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
            frames.add(BlankFrameSpec(OX, OY, outerW, outerH))
            val contentLeft = OX + borderWidthPx + gridPaddingPx
            val contentTop = OY + borderWidthPx + gridPaddingPx
            addNumberedBubbles(contentLeft, contentTop, rows = q1, startQuestionIndex1 = 1)
        } else {
            val extraRow = if (q2 < q1) 1 else 0
            val rows2 = q2 + extraRow

            val innerH1 = innerHeightPx(q1, bubbleSizePx = bubbleSizePx, gapVerticalPx = gapVerticalPx)
            val innerH2 = innerHeightPx(rows2, bubbleSizePx = bubbleSizePx, gapVerticalPx = gapVerticalPx)
            val outerW = outerWidthPx(innerW, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
            val outerH1 = outerHeightPx(innerH1, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)
            val outerH2 = outerHeightPx(innerH2, gridPaddingPx = gridPaddingPx, borderWidthPx = borderWidthPx)

            frames.add(BlankFrameSpec(OX, OY, outerW, outerH1))
            val x2 = OX + outerW + COL_GAP
            frames.add(BlankFrameSpec(x2, OY, outerW, outerH2))

            val c1Left = OX + borderWidthPx + gridPaddingPx
            val c1Top = OY + borderWidthPx + gridPaddingPx
            addNumberedBubbles(c1Left, c1Top, rows = q1, startQuestionIndex1 = 1)

            val c2Left = x2 + borderWidthPx + gridPaddingPx
            val c2Top = OY + borderWidthPx + gridPaddingPx
            addNumberedBubbles(c2Left, c2Top, rows = q2, startQuestionIndex1 = q1 + 1)
            if (extraRow == 1) {
                addEmptyRow(c2Left, c2Top, rowIndex = q2)
            }
        }
    }

    return BlankBubbleLayout(frames = frames, bubbles = bubbles, renderParams = normalizedParams)
}

fun blankBubbleLayoutBoundingRect(layout: BlankBubbleLayout): BlankLayoutRect {
    val frames = layout.frames
    if (frames.isEmpty()) {
        return BlankLayoutRect(0f, 0f, BlankSheetSpec.LOGICAL_WIDTH_PX, BlankSheetSpec.LOGICAL_HEIGHT_PX)
    }
    var minL = Float.MAX_VALUE
    var minT = Float.MAX_VALUE
    var maxR = Float.MIN_VALUE
    var maxB = Float.MIN_VALUE
    for (f in frames) {
        minL = minOf(minL, f.left)
        minT = minOf(minT, f.top)
        maxR = maxOf(maxR, f.left + f.width)
        maxB = maxOf(maxB, f.top + f.height)
    }
    return BlankLayoutRect(minL, minT, maxR, maxB)
}

data class BlankLayoutRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

fun blankLayoutCenter(layout: BlankBubbleLayout): Offset {
    val r = blankBubbleLayoutBoundingRect(layout)
    return Offset((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)
}

private fun transformedRect(layout: BlankBubbleLayout, pan: Offset, scale: Float): BlankLayoutRect {
    val r = blankBubbleLayoutBoundingRect(layout)
    val c = blankLayoutCenter(layout)
    fun tx(x: Float): Float = pan.x + c.x + (x - c.x) * scale
    fun ty(y: Float): Float = pan.y + c.y + (y - c.y) * scale
    return BlankLayoutRect(
        left = tx(r.left),
        top = ty(r.top),
        right = tx(r.right),
        bottom = ty(r.bottom),
    )
}

fun blankLayoutBoundingRectTransformed(
    layout: BlankBubbleLayout,
    pan: Offset,
    scale: Float,
): BlankLayoutRect = transformedRect(layout, pan = pan, scale = scale)

fun clampBlankPanOnSheet(
    pan: Offset,
    layout: BlankBubbleLayout,
    scale: Float,
    marginLogical: Float = 8f,
): Offset {
    val r = transformedRect(layout, pan = Offset.Zero, scale = scale)
    val minX = marginLogical - r.left
    val maxX = BlankSheetSpec.LOGICAL_WIDTH_PX - marginLogical - r.right
    val minY = marginLogical - r.top
    val maxY = BlankSheetSpec.LOGICAL_HEIGHT_PX - marginLogical - r.bottom
    return Offset(
        x = pan.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
        y = pan.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY)),
    )
}

fun clampBlankScale(scale: Float): Float = scale.coerceIn(0.65f, 1.75f)

private fun sheetToLayoutPoint(
    layout: BlankBubbleLayout,
    sheetPoint: Offset,
    pan: Offset,
    scale: Float,
): Offset {
    val c = blankLayoutCenter(layout)
    val local = sheetPoint - pan
    return Offset(
        x = c.x + (local.x - c.x) / scale,
        y = c.y + (local.y - c.y) / scale,
    )
}

fun hitTestBubbleId(layout: BlankBubbleLayout, logicalX: Float, logicalY: Float): String? {
    val r = layout.renderParams.bubbleSizePx / 2f
    val r2 = r * r
    for (b in layout.bubbles) {
        val dx = logicalX - b.centerX
        val dy = logicalY - b.centerY
        if (dx * dx + dy * dy <= r2) return b.id
    }
    return null
}

fun hitTestBubbleIdTransformed(
    layout: BlankBubbleLayout,
    sheetPoint: Offset,
    pan: Offset,
    scale: Float,
): String? {
    val p = sheetToLayoutPoint(layout, sheetPoint = sheetPoint, pan = pan, scale = scale)
    return hitTestBubbleId(layout, p.x, p.y)
}

fun hitTestBlankObjectTransformed(
    layout: BlankBubbleLayout,
    sheetPoint: Offset,
    pan: Offset,
    scale: Float,
): Boolean {
    val r = blankLayoutBoundingRectTransformed(layout, pan = pan, scale = scale)
    return sheetPoint.x in r.left..r.right && sheetPoint.y in r.top..r.bottom
}
