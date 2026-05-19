package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: нарезка как в `external/.../ImageProcessor.processTestSheet`:
 * `cellWidth = width/cols`, `cellHeight = height/rows` (целочисленное деление всего warp).
 */
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect

internal object BatchCellGridExtractor {
    fun extractCells(
        warpBgr: Mat,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
    ): List<Triple<Int, Int, Mat>> {
        if (questionsCount <= 0 || choicesCount <= 0) return emptyList()
        if (columnCount != 1 && columnCount != 2) return emptyList()
        val preview = Bitmap.createBitmap(warpBgr.cols(), warpBgr.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warpBgr, preview)

        val out = mutableListOf<Triple<Int, Int, Mat>>()
        if (columnCount == 1) {
            val inner = BatchInnerGridDetector.detectInnerRect(preview)
            appendUniformGridCells(
                out = out,
                warpBgr = warpBgr,
                questionStart = 0,
                rows = questionsCount,
                cols = choicesCount,
                left = inner.left,
                top = inner.top,
                rightEx = inner.rightEx,
                bottomEx = inner.bottomEx,
            )
            preview.recycle()
            return out
        }

        val halfW = preview.width / 2
        val leftW = halfW.coerceAtLeast(1)
        val rightW = (preview.width - leftW).coerceAtLeast(0)
        val qLeft = (questionsCount + 1) / 2
        val qRight = questionsCount - qLeft

        val leftPreview = Bitmap.createBitmap(preview, 0, 0, leftW, preview.height)
        val leftInnerRect = BatchInnerGridDetector.detectInnerRect(leftPreview)
        leftPreview.recycle()

        appendUniformGridCells(
            out = out,
            warpBgr = warpBgr,
            questionStart = 0,
            rows = qLeft.coerceAtLeast(1),
            cols = choicesCount,
            left = leftInnerRect.left,
            top = leftInnerRect.top,
            rightEx = leftInnerRect.rightEx,
            bottomEx = leftInnerRect.bottomEx,
        )

        if (rightW > 0 && qRight > 0) {
            val rightPreview = Bitmap.createBitmap(preview, leftW, 0, rightW, preview.height)
            val rightInnerRect = BatchInnerGridDetector.detectInnerRect(rightPreview)
            rightPreview.recycle()

            appendUniformGridCells(
                out = out,
                warpBgr = warpBgr,
                questionStart = qLeft,
                rows = qRight,
                cols = choicesCount,
                left = leftW + rightInnerRect.left,
                top = rightInnerRect.top,
                rightEx = leftW + rightInnerRect.rightEx,
                bottomEx = rightInnerRect.bottomEx,
            )
        }
        preview.recycle()
        return out
    }

    private fun appendUniformGridCells(
        out: MutableList<Triple<Int, Int, Mat>>,
        warpBgr: Mat,
        questionStart: Int,
        rows: Int,
        cols: Int,
        left: Int,
        top: Int,
        rightEx: Int,
        bottomEx: Int,
    ) {
        if (rows <= 0 || cols <= 0) return
        val width = (rightEx - left).coerceAtLeast(1)
        val height = (bottomEx - top).coerceAtLeast(1)
        val cellW = width / cols
        val cellH = height / rows
        if (cellW <= 0 || cellH <= 0) return
        for (r in 0 until rows) {
            val y0 = top + r * cellH
            for (c in 0 until cols) {
                val x0 = left + c * cellW
                val roi = Rect(x0, y0, cellW, cellH)
                out += Triple(questionStart + r, c, Mat(warpBgr, roi).clone())
            }
        }
    }
}
