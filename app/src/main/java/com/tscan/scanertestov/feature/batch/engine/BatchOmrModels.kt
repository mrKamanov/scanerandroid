package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: модели данных OMR-движка для пакетной обработки.
 */
import android.graphics.Bitmap
enum class BatchCellClass { Yes, No, Fixed }

data class BatchCellPrediction(
    val questionIndex: Int,
    val choiceIndex: Int,
    val klass: BatchCellClass,
    val confidence: Float,
)

data class BatchOmrConfig(
    val questionsCount: Int,
    val choicesCount: Int,
    val columnCount: Int,
    val answerKey: List<Set<Int>>,
    val strictScoring: Boolean,
)

data class BatchQuestionScore(
    val questionIndex: Int,
    val selectedChoices: Set<Int>,
    val score: Float,
    val correct: Boolean,
    val hasFixed: Boolean,
)

data class BatchOmrResult(
    val predictions: List<BatchCellPrediction>,
    val questionScores: List<BatchQuestionScore>,
    val fixedCells: List<BatchCellPrediction>,
    val contourFound: Boolean,
    val sheetCropBitmap: Bitmap? = null,
) {
    val correctCount: Int get() = questionScores.count { it.correct }
    val incorrectCount: Int get() = questionScores.size - correctCount
    val scoreRaw: Float get() = questionScores.sumOf { it.score.toDouble() }.toFloat()
    val scorePercent: Float get() = if (questionScores.isEmpty()) 0f else (scoreRaw / questionScores.size) * 100f
}
