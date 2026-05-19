package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: отрисовка debug-сетки на warp — как в external `ImageProcessor.drawGridOnWarp`:
 * линии через равные интервалы по полной ширине и высоте (`cols()/choices`, `rows()/questions`, целые).
 */
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
internal object BatchSheetGridOverlay {
    fun renderOverlay(
        source: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val rows = questionsCount.coerceAtLeast(1)
        val cols = choicesCount.coerceAtLeast(1)
        val useTwoColumns = columnCount == 2

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 0, 200, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 64, 64)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val innerRect = BatchInnerGridDetector.detectInnerRect(out)
        val inner = FRect(
            left = innerRect.left.toFloat(),
            top = innerRect.top.toFloat(),
            right = innerRect.rightEx.toFloat(),
            bottom = innerRect.bottomEx.toFloat(),
        )
        if (useTwoColumns) {
            val halfW = out.width / 2
            val leftW = halfW.coerceAtLeast(1)
            val rightW = (out.width - leftW).coerceAtLeast(0)

            val leftHalf = Bitmap.createBitmap(out, 0, 0, leftW, out.height)
            val rightHalf = if (rightW > 0) Bitmap.createBitmap(out, leftW, 0, rightW, out.height) else null
            val qLeft = (rows + 1) / 2
            val qRight = rows - qLeft

            val leftInnerRect = BatchInnerGridDetector.detectInnerRect(leftHalf)
            val leftInner = FRect(
                left = leftInnerRect.left.toFloat(),
                top = leftInnerRect.top.toFloat(),
                right = leftInnerRect.rightEx.toFloat(),
                bottom = leftInnerRect.bottomEx.toFloat(),
            )
            canvas.drawRect(leftInner.left, leftInner.top, leftInner.right, leftInner.bottom, borderPaint)
            drawUniformGrid(
                canvas = canvas,
                left = leftInner.left,
                top = leftInner.top,
                width = leftInner.right - leftInner.left,
                height = leftInner.bottom - leftInner.top,
                rows = qLeft.coerceAtLeast(1),
                cols = cols,
                paint = gridPaint,
            )

            if (rightHalf != null && qRight > 0) {
                val rightInnerRect = BatchInnerGridDetector.detectInnerRect(rightHalf)
                val rightInner = FRect(
                    left = rightInnerRect.left.toFloat(),
                    top = rightInnerRect.top.toFloat(),
                    right = rightInnerRect.rightEx.toFloat(),
                    bottom = rightInnerRect.bottomEx.toFloat(),
                )
                val rx = leftW.toFloat() + rightInner.left
                val ry = rightInner.top
                canvas.drawRect(rx, ry, leftW.toFloat() + rightInner.right, ry + (rightInner.bottom - rightInner.top), borderPaint)

                drawUniformGrid(
                    canvas = canvas,
                    left = leftW.toFloat() + rightInner.left,
                    top = rightInner.top,
                    width = rightInner.right - rightInner.left,
                    height = rightInner.bottom - rightInner.top,
                    rows = qRight.coerceAtLeast(1),
                    cols = cols,
                    paint = gridPaint,
                )
            }

            leftHalf.recycle()
            rightHalf?.recycle()
            return out
        }

        canvas.drawRect(inner.left, inner.top, inner.right, inner.bottom, borderPaint)

        if (!useTwoColumns) {
            drawUniformGrid(
                canvas = canvas,
                left = inner.left,
                top = inner.top,
                width = inner.right - inner.left,
                height = inner.bottom - inner.top,
                rows = rows,
                cols = cols,
                paint = gridPaint,
            )
            return out
        }

        val fullW = inner.right - inner.left
        val leftW = fullW / 2f
        val rightW = fullW - leftW
        val qLeft = (rows + 1) / 2
        val qRight = rows - qLeft
        drawUniformGrid(
            canvas = canvas,
            left = inner.left,
            top = inner.top,
            width = leftW,
            height = inner.bottom - inner.top,
            rows = qLeft.coerceAtLeast(1),
            cols = cols,
            paint = gridPaint,
        )
        if (qRight > 0) {
            drawUniformGrid(
                canvas = canvas,
                left = inner.left + leftW,
                top = inner.top,
                width = rightW,
                height = inner.bottom - inner.top,
                rows = qRight,
                cols = cols,
                paint = gridPaint,
            )
        }
        return out
    }

    private data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun drawUniformGrid(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        rows: Int,
        cols: Int,
        paint: Paint,
    ) {
        val safeRows = rows.coerceAtLeast(1)
        val safeCols = cols.coerceAtLeast(1)
        val iw = width.toInt().coerceAtLeast(1)
        val ih = height.toInt().coerceAtLeast(1)
        val cellW = iw / safeCols
        val cellH = ih / safeRows
        if (cellW <= 0 || cellH <= 0) return
        val lf = left
        val tf = top
        for (r in 0..safeRows) {
            val y = tf + r * cellH
            canvas.drawLine(lf, y, lf + iw, y, paint)
        }
        for (c in 0..safeCols) {
            val x = lf + c * cellW
            canvas.drawLine(x, tf, x, tf + ih, paint)
        }
    }
}
