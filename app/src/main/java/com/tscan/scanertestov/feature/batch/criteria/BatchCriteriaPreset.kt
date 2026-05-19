package com.tscan.scanertestov.feature.batch.criteria

/**
 * Описание: сохранённый пресет эталонных ответов для пакетной проверки.
 */
data class BatchCriteriaPreset(
    val id: String,
    val name: String,
    val questionsCount: Int,
    val choicesCount: Int,
    val columnCount: Int,
    val correctAnswers: List<List<Int>>,
    val variantAnswerKeys: List<BatchCriteriaVariantAnswers> = emptyList(),
)

data class BatchCriteriaVariantAnswers(
    val variant: Int,
    val correctAnswers: List<List<Int>>,
)
