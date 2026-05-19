package com.tscan.scanertestov.feature.batch

/**
 * Описание: структурированные данные последнего пакетного прогона, включая fixed-ячейки.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore

data class BatchRunItemResult(
    val title: String,
    val layoutLabel: String,
    val config: BatchOmrConfig,
    val detectedVariant: Int? = null,
    val detectedStudentSurname: String? = null,
    val detectedStudentName: String? = null,
    val detectedJournalClassName: String? = null,
    val omr: BatchOmrResult? = null,
    val error: String? = null,
)

data class BatchFixedCellUiItem(
    val key: String,
    val itemIndex: Int,
    val itemTitle: String,
    val questionIndex: Int,
    val choiceIndex: Int,
    val includeAsAnswer: Boolean?,
)

data class BatchResultDetailRow(
    val questionNumber: Int,
    val selectedText: String,
    val expectedText: String,
)

data class BatchResultEntryState(
    val title: String,
    val subtitle: String? = null,
    val status: String,
    val gradeLine: String? = null,
    val scoreLine: String? = null,
    val fixedLine: String? = null,
    val layoutLine: String? = null,
    val details: List<String> = emptyList(),
    val previewBitmap: Bitmap? = null,
    val grade: Int? = null,
    val percent: Float? = null,
    val scoreCorrect: Int? = null,
    val scoreTotal: Int? = null,
    val correctionsTotal: Int = 0,
    val correctionsUnresolved: Int = 0,
    val detailRows: List<BatchResultDetailRow> = emptyList(),
    val questionWrongTotal: Int = 0,
    val sheetMarkerPredictions: List<BatchCellPrediction> = emptyList(),
    val sheetMarkerConfig: BatchOmrConfig? = null,
    val reportQuestionScores: List<BatchQuestionScore> = emptyList(),
    val reportVariant: Int? = null,
    val reportStudentSurname: String? = null,
    val reportStudentName: String? = null,
    val reportJournalClassName: String? = null,
    val reportStrictScoring: Boolean = true,
    val reportPartialCompletedCount: Int = 0,
    val reportHasMultiChoiceQuestions: Boolean = false,
)

internal fun fixedCellKey(itemIndex: Int, prediction: BatchCellPrediction): String =
    "${itemIndex}_${prediction.questionIndex}_${prediction.choiceIndex}"
