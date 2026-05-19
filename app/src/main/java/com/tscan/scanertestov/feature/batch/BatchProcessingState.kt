package com.tscan.scanertestov.feature.batch

import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset

/**
 * Описание: состояние экрана пакетной обработки и отображаемые подписи.
 */
enum class BatchWorkSource { Camera, Gallery }

enum class BatchWorkStatus { Queued, Ready, Error }

enum class BatchPageLayout(val label: String) {
    OneColumn("1 колонка"),
    TwoColumns("2 колонки")
}

data class BatchWorkItem(
    val id: String,
    val title: String,
    val displayName: String,
    val subtitle: String,
    val contentUri: String,
    val source: BatchWorkSource,
    val status: BatchWorkStatus,
    val autoRecognitionRequested: Boolean = false,
    val detectedVariant: Int? = null,
    val detectedVariantConfidence: Float? = null,
    val detectedVariantRawText: String? = null,
    val isVariantDetecting: Boolean = false,
    val variantDetectionError: String? = null,
    val variantOverride: Int? = null,
    val detectedStudentSurname: String? = null,
    val detectedStudentName: String? = null,
    val detectedStudentNameConfidence: Float? = null,
    val detectedStudentNameRawText: String? = null,
    val studentNameDetectionError: String? = null,
    val detectedJournalClassName: String? = null,
)

data class BatchProcessingState(
    val screenTitle: String = "Проверка работ по фото",
    val hintText: String = "Пройдите шаги ниже по порядку: добавьте фото, задайте ответы и нажмите «Проверить работы».",
    val captureButtonText: String = "Сделать фото",
    val loadButtonText: String = "Галерея",
    val capturePhotoGuideText: String =
        "«Сделать фото» открывает камеру приложения: в белый контур вместите чёрную рамку бланка, держите телефон параллельно листу.",
    val autoRecognitionToggleText: String = "Авто-распознавание",
    val runQueuedRecognitionButtonText: String = "Распознать уже загруженные",
    val startButtonText: String = "Проверить работы",
    val clearButtonText: String = "Очистить список",
    val queueSectionTitle: String = "Шаг 1. Добавьте работы",
    val queueEmptyText: String = "Пока нет ни одной работы. Добавьте фото камерой или выберите готовые снимки.",
    val parametersSectionTitle: String = "Шаг 2. Настройте проверку",
    val answerKeyTitle: String = "Варианты (если нужны)",
    val addVariantButtonText: String = "Добавить",
    val removeVariantButtonText: String = "Удалить",
    val noVariantText: String = "Варианты не добавлены. Можно работать и без них.",
    val answerGridTitle: String = "Правильные ответы",
    val answerGridHelpText: String = "Для каждого вопроса отметьте правильные варианты: минимум 1, но не все.",
    val criteriaNameTitle: String = "Название шаблона",
    val saveCriteriaButtonText: String = "Сохранить шаблон",
    val savedCriteriaTitle: String = "Сохранённые шаблоны",
    val savedCriteriaExpandText: String = "Развернуть",
    val savedCriteriaCollapseText: String = "Свернуть",
    val layoutTitle: String = "Расположение вопросов на бланке",
    val strictScoringTitle: String = "Проверять строго",
    val strictScoringSubtitle: String = "Включено — засчитываем только полное совпадение. Выключено — частичная проверка.",
    val runSectionTitle: String = "Шаг 3. Запустите проверку",
    val previewSectionTitle: String = "Предпросмотр",
    val previewSectionCollapsedHint: String = "Перед запуском можно открыть предпросмотр: проверить листы, вариант и ФИО.",
    val previewSectionExpandA11y: String = "Развернуть предпросмотр",
    val previewSectionCollapseA11y: String = "Свернуть предпросмотр",
    val previewPlaceholder: String = "Добавьте работы: выберите группу по варианту, лист в полосе ниже и при необходимости укажите номер варианта для проверки.",
    val workItems: List<BatchWorkItem> = emptyList(),
    val selectedWorkIds: Set<String> = emptySet(),
    val previewWorkId: String? = null,
    val answerKeyVariantsCount: Int = 0,
    val selectedAnswerKeyVariant: Int? = null,
    val questionsCount: Int = 5,
    val choicesCount: Int = 4,
    val correctAnswers: List<Set<Int>> = List(5) { emptySet() },
    val correctAnswersByVariant: Map<Int, List<Set<Int>>> = emptyMap(),
    val criteriaNameDraft: String = "",
    val savedCriteria: List<BatchCriteriaPreset> = emptyList(),
    val savedCriteriaExpanded: Boolean = false,
    val pageLayout: BatchPageLayout = BatchPageLayout.OneColumn,
    val strictScoring: Boolean = true,
    val autoRecognitionEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    val ocrWarmupInProgress: Boolean = false,
    val ocrWarmupReady: Boolean = false,
    val statusMessage: String? = null,
    val lastRunSummary: String? = null
)
