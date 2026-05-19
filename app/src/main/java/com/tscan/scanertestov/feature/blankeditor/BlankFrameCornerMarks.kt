package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: отрисовка L-ориентиров по контуру блока сетки бланка.
 */
import android.graphics.Canvas
import android.graphics.Paint

internal fun drawBlankBubbleLayoutCornerMarksOnCanvas(
    canvas: Canvas,
    layout: BlankBubbleLayout,
    paint: Paint,
) {
    val p = cornerMarkDrawParams(layout) ?: return
    val l = p.frameLeft
    val t = p.frameTop
    val r = p.frameRight
    val b = p.frameBottom
    val o = p.outwardGap
    val arm = p.arm

    canvas.drawLine(l - o - arm, t - o - arm, l - o, t - o - arm, paint)
    canvas.drawLine(l - o - arm, t - o - arm, l - o - arm, t - o, paint)
    canvas.drawLine(r + o + arm, t - o - arm, r + o, t - o - arm, paint)
    canvas.drawLine(r + o + arm, t - o - arm, r + o + arm, t - o, paint)
    canvas.drawLine(l - o - arm, b + o + arm, l - o, b + o + arm, paint)
    canvas.drawLine(l - o - arm, b + o + arm, l - o - arm, b + o, paint)
    canvas.drawLine(r + o + arm, b + o + arm, r + o, b + o + arm, paint)
    canvas.drawLine(r + o + arm, b + o + arm, r + o + arm, b + o, paint)
}
