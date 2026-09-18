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

/**
 * Описание: детерминированная геометрия одного столбца бланка после выпрямления.
 * [innerLeft]/[innerTop]/[innerRight]/[innerBottom] заданы в пикселях warp-изображения.
 * [questionCount] — сколько реальных вопросов в столбце, [frameBubbleRows] — сколько
 * строк кружков нарисовано в рамке (включая возможную пустую строку), это шаг сетки.
 */
data class BatchColumnFrameGrid(
    val questionStart: Int,
    val questionCount: Int,
    val frameBubbleRows: Int,
    val innerLeft: Int,
    val innerTop: Int,
    val innerRight: Int,
    val innerBottom: Int,
) {
    val width: Int get() = innerRight - innerLeft
    val height: Int get() = innerBottom - innerTop
}

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
    val columnFrames: List<BatchColumnFrameGrid>? = null,
) {
    val correctCount: Int get() = questionScores.count { it.correct }
    val incorrectCount: Int get() = questionScores.size - correctCount
    val scoreRaw: Float get() = questionScores.sumOf { it.score.toDouble() }.toFloat()
    val scorePercent: Float get() = if (questionScores.isEmpty()) 0f else (scoreRaw / questionScores.size) * 100f
}
