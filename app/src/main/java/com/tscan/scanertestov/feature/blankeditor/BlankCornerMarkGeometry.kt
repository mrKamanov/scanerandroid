package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: геометрия L-ориентиров и четырёхугольника между их вершинами.
 */
import kotlin.math.max
import kotlin.math.min

internal data class CornerMarkDrawParams(
    val frameLeft: Float,
    val frameTop: Float,
    val frameRight: Float,
    val frameBottom: Float,
    val outwardGap: Float,
    val arm: Float,
)

internal fun cornerMarkDrawParams(layout: BlankBubbleLayout): CornerMarkDrawParams? {
    val br = blankBubbleLayoutBoundingRect(layout)
    val l = br.left
    val t = br.top
    val r = br.right
    val b = br.bottom
    val rp = layout.renderParams
    val o = max(6f, rp.borderWidthPx * 0.95f + 3f)
    val rawArm = cornerMarkArmForLayout(r - l, b - t, rp)
    val sheetW = BlankSheetSpec.LOGICAL_WIDTH_PX
    val sheetH = BlankSheetSpec.LOGICAL_HEIGHT_PX
    val cap = minOf(l - o - 2f, t - o - 2f, sheetW - r - o - 2f, sheetH - b - o - 2f)
    if (cap < 8f) return null
    val arm = min(rawArm, cap)
    return CornerMarkDrawParams(l, t, r, b, o, arm)
}

private fun cornerMarkArmForLayout(boundW: Float, boundH: Float, renderParams: BlankBubbleRenderParams): Float {
    val maxBy = min(boundW, boundH) * 0.2f
    val fromBubble = renderParams.bubbleSizePx * 0.4f
    return min(maxBy, fromBubble.coerceIn(16f, 56f))
}

data class CornerMarkTipQuadLogical(
    val tipTlX: Float,
    val tipTlY: Float,
    val tipTrX: Float,
    val tipTrY: Float,
    val tipBlX: Float,
    val tipBlY: Float,
    val tipBrX: Float,
    val tipBrY: Float,
) {
    fun quadWidth(): Float = tipTrX - tipTlX

    fun quadHeight(): Float = tipBlY - tipTlY

    fun aspectRatio(): Float {
        val h = quadHeight().coerceAtLeast(1f)
        return quadWidth() / h
    }
}

fun cornerMarkTipQuadLogical(layout: BlankBubbleLayout): CornerMarkTipQuadLogical? {
    val p = cornerMarkDrawParams(layout) ?: return null
    val l = p.frameLeft
    val t = p.frameTop
    val r = p.frameRight
    val b = p.frameBottom
    val o = p.outwardGap
    val arm = p.arm
    return CornerMarkTipQuadLogical(
        tipTlX = l - o - arm,
        tipTlY = t - o - arm,
        tipTrX = r + o + arm,
        tipTrY = t - o - arm,
        tipBlX = l - o - arm,
        tipBlY = b + o + arm,
        tipBrX = r + o + arm,
        tipBrY = b + o + arm,
    )
}
