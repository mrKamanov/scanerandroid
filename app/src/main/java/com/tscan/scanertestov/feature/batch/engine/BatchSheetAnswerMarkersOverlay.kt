package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: полупрозрачная подсветка ячеек «верно / неверно / на проверке (fixed)» поверх кропа со сеткой.
 */
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object BatchSheetAnswerMarkersOverlay {
    private const val InsetPx = 2f

    fun renderMarkersOnCrop(
        sourceWithGrid: Bitmap,
        config: BatchOmrConfig,
        predictions: List<BatchCellPrediction>,
    ): Bitmap {
        val out = sourceWithGrid.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val predByCell = predictions.associateBy { it.questionIndex to it.choiceIndex }

        val paintCorrect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(88, 46, 160, 67)
        }
        val paintWrong = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(95, 211, 47, 47)
        }
        val paintFixed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(100, 245, 180, 0)
        }

        BatchSheetGridLayout.forEachCell(
            bitmap = out,
            questionsCount = config.questionsCount,
            choicesCount = config.choicesCount,
            columnCount = config.columnCount,
        ) { cell ->
            val pred = predByCell[cell.questionIndex to cell.choiceIndex]
            val klass = pred?.klass ?: BatchCellClass.No
            val inKey = config.answerKey
                .getOrNull(cell.questionIndex)
                .orEmpty()
                .contains(cell.choiceIndex)

            val left = cell.left + InsetPx
            val top = cell.top + InsetPx
            val right = cell.right - InsetPx
            val bottom = cell.bottom - InsetPx
            if (left >= right || top >= bottom) return@forEachCell

            when (klass) {
                BatchCellClass.Fixed -> canvas.drawRect(left, top, right, bottom, paintFixed)
                BatchCellClass.Yes -> {
                    val paint = if (inKey) paintCorrect else paintWrong
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                BatchCellClass.No -> {
                    if (inKey) {
                        canvas.drawRect(left, top, right, bottom, paintWrong)
                    }
                }
            }
        }
        return out
    }
}
