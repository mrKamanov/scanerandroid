package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: вырезка увеличенного фрагмента ячейки бланка для панели уточнения исправлений.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchSheetGridLayout

internal object RealtimeFixedCellCrop {
    fun cropAroundCell(
        bitmap: Bitmap,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
        questionIndex: Int,
        choiceIndex: Int,
        paddingRatio: Float = 0.5f,
    ): Bitmap? {
        val rect = BatchSheetGridLayout.findCell(
            bitmap = bitmap,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
            columnCount = columnCount,
            questionIndex = questionIndex,
            choiceIndex = choiceIndex,
        ) ?: return null
        val cellW = (rect.right - rect.left).coerceAtLeast(1f)
        val cellH = (rect.bottom - rect.top).coerceAtLeast(1f)
        val padX = cellW * paddingRatio
        val padY = cellH * paddingRatio
        val left = (rect.left - padX).toInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top - padY).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rect.right + padX).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (rect.bottom + padY).toInt().coerceIn(top + 1, bitmap.height)
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }
}
