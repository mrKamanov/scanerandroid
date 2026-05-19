package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: геометрия ячеек совпадает с [BatchSheetGridOverlay] и [BatchCellGridExtractor]
 * и с external `ImageProcessor` (целые cellWidth/cellHeight по всему warp).
 */
import android.graphics.Bitmap

internal data class BatchSheetGridCellRect(
    val questionIndex: Int,
    val choiceIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal object BatchSheetGridLayout {
    fun findCell(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
        questionIndex: Int,
        choiceIndex: Int,
    ): BatchSheetGridCellRect? {
        var found: BatchSheetGridCellRect? = null
        forEachCell(
            bitmap = bitmap,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
            columnCount = columnCount,
        ) { cell ->
            if (cell.questionIndex == questionIndex && cell.choiceIndex == choiceIndex) {
                found = cell
            }
        }
        return found
    }

    fun forEachCell(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
        block: (BatchSheetGridCellRect) -> Unit,
    ) {
        val rows = questionsCount.coerceAtLeast(1)
        val cols = choicesCount.coerceAtLeast(1)
        val useTwoColumns = columnCount == 2
        val inner = BatchInnerGridDetector.detectInnerRect(bitmap)
        val innerLeft = inner.left.toFloat()
        val innerTop = inner.top.toFloat()
        val innerRight = inner.rightEx.toFloat()
        val innerBottom = inner.bottomEx.toFloat()
        val innerW = innerRight - innerLeft
        val innerH = innerBottom - innerTop
        if (!useTwoColumns) {
            forEachUniformGrid(
                left = innerLeft,
                top = innerTop,
                width = innerW,
                height = innerH,
                questionStart = 0,
                rows = rows,
                cols = cols,
                block = block,
            )
            return
        }
        val halfW = bitmap.width / 2
        val leftW = halfW.coerceAtLeast(1)
        val rightW = (bitmap.width - leftW).coerceAtLeast(0)
        val qLeft = (rows + 1) / 2
        val qRight = rows - qLeft

        val leftHalf = Bitmap.createBitmap(bitmap, 0, 0, leftW, bitmap.height)
        val leftInnerRect = BatchInnerGridDetector.detectInnerRect(leftHalf)
        leftHalf.recycle()
        val leftInner = object {
            val left = leftInnerRect.left.toFloat()
            val top = leftInnerRect.top.toFloat()
            val width = (leftInnerRect.rightEx - leftInnerRect.left).toFloat()
            val height = (leftInnerRect.bottomEx - leftInnerRect.top).toFloat()
        }

        forEachUniformGrid(
            left = leftInner.left,
            top = leftInner.top,
            width = leftInner.width,
            height = leftInner.height,
            questionStart = 0,
            rows = qLeft.coerceAtLeast(1),
            cols = cols,
            block = block,
        )

        if (rightW > 0 && qRight > 0) {
            val rightHalf = Bitmap.createBitmap(bitmap, leftW, 0, rightW, bitmap.height)
            val rightInnerRect = BatchInnerGridDetector.detectInnerRect(rightHalf)
            rightHalf.recycle()
            val rightInner = object {
                val left = rightInnerRect.left.toFloat()
                val top = rightInnerRect.top.toFloat()
                val width = (rightInnerRect.rightEx - rightInnerRect.left).toFloat()
                val height = (rightInnerRect.bottomEx - rightInnerRect.top).toFloat()
            }

            forEachUniformGrid(
                left = leftW.toFloat() + rightInner.left,
                top = rightInner.top,
                width = rightInner.width,
                height = rightInner.height,
                questionStart = qLeft,
                rows = qRight,
                cols = cols,
                block = block,
            )
        }
    }

    private fun forEachUniformGrid(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        questionStart: Int,
        rows: Int,
        cols: Int,
        block: (BatchSheetGridCellRect) -> Unit,
    ) {
        val safeRows = rows.coerceAtLeast(1)
        val safeCols = cols.coerceAtLeast(1)
        val iw = width.toInt().coerceAtLeast(1)
        val ih = height.toInt().coerceAtLeast(1)
        val cellW = iw / safeCols
        val cellH = ih / safeRows
        if (cellW <= 0 || cellH <= 0) return
        for (r in 0 until safeRows) {
            for (c in 0 until safeCols) {
                val q = questionStart + r
                val li = left.toInt() + c * cellW
                val ti = top.toInt() + r * cellH
                block(
                    BatchSheetGridCellRect(
                        questionIndex = q,
                        choiceIndex = c,
                        left = li.toFloat(),
                        top = ti.toFloat(),
                        right = (li + cellW).toFloat(),
                        bottom = (ti + cellH).toFloat(),
                    ),
                )
            }
        }
    }
}
