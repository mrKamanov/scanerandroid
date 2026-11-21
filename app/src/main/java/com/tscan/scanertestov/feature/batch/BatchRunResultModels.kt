package com.tscan.scanertestov.feature.batch

/**
 * Описание: структурированные данные последнего пакетного прогона, включая fixed-ячейки.
 */
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchColumnFrameGrid
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
    /** Путь к JPEG выпрямленного бланка на диске (полный кадр грузится только при просмотре). */
    val sheetImagePath: String? = null,
    /** Миниатюра для списка результатов (~240 px), держится в памяти. */
    val thumbnail: Bitmap? = null,
    /** Картинка места для имени с бланка (подпись ученика). */
    val nameCropPath: String? = null,
    /** Откуда ученик: «7 И · по коду №2». */
    val studentNote: String? = null,
    /** Что не сходится: вариант не тот, что выдан; код не выдавался по тесту. */
    val warning: String? = null,
    /** У теста несколько вариантов, а на бланке номер варианта не прочитан — проверено по ключу варианта 1. */
    val variantUnread: Boolean = false,
)

data class BatchFixedCellUiItem(
    val key: String,
    val itemIndex: Int,
    val itemTitle: String,
    val questionIndex: Int,
    val choiceIndex: Int,
    val includeAsAnswer: Boolean?,
    /** Решено приложением (очевидный случай, ✎); учитель может изменить. */
    val autoDecided: Boolean = false,
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
    /** Полный кадр бланка на диске для детального просмотра. */
    val sheetImagePath: String? = null,
    val grade: Int? = null,
    val percent: Float? = null,
    val scoreCorrect: Int? = null,
    val scoreTotal: Int? = null,
    val correctionsTotal: Int = 0,
    val correctionsUnresolved: Int = 0,
    /** Сколько исправлений решено автоматически (✎). */
    val correctionsAuto: Int = 0,
    val detailRows: List<BatchResultDetailRow> = emptyList(),
    val questionWrongTotal: Int = 0,
    val sheetMarkerPredictions: List<BatchCellPrediction> = emptyList(),
    val sheetMarkerConfig: BatchOmrConfig? = null,
    val sheetMarkerColumnFrames: List<BatchColumnFrameGrid>? = null,
    val reportQuestionScores: List<BatchQuestionScore> = emptyList(),
    val reportVariant: Int? = null,
    val reportStudentSurname: String? = null,
    val reportStudentName: String? = null,
    val reportJournalClassName: String? = null,
    val reportStrictScoring: Boolean = true,
    val reportPartialCompletedCount: Int = 0,
    val reportHasMultiChoiceQuestions: Boolean = false,
    val reportAnswerKey: List<Set<Int>> = emptyList(),
    val reportChoicesCount: Int = 0,
    /** Картинка места для имени с бланка (подпись) — для «Чья работа?». */
    val nameCropPath: String? = null,
    /** Имя указал учитель вручную. */
    val nameAssignedManually: Boolean = false,
    val studentNote: String? = null,
    val warning: String? = null,
    /** Номер варианта не прочитан, учитель его ещё не выбрал: проверено по ключу варианта 1. */
    val variantUnread: Boolean = false,
    /** Вариант выбрал учитель (номер — в [reportVariant]). */
    val variantManual: Boolean = false,
    /** Сколько вариантов можно выбрать (0 — выбор не нужен). */
    val variantChoices: Int = 0,
)

internal fun fixedCellKey(itemIndex: Int, prediction: BatchCellPrediction): String =
    "${itemIndex}_${prediction.questionIndex}_${prediction.choiceIndex}"
