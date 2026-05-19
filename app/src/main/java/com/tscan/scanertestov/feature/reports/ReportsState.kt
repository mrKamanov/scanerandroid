package com.tscan.scanertestov.feature.reports

/**
 * Описание: состояние экрана отчетов и агрегированных аналитических блоков.
 */
data class ReportsSummaryStats(
    val totalWorks: Int,
    val averageGrade: Double,
    val successRatePercent: Double,
    val qualityKnowledgePercent: Double,
)

data class ReportsGradeDistributionItem(
    val grade: Int,
    val count: Int,
    val percent: Double,
)

data class ReportsHardQuestionItem(
    val questionNumber: Int,
    val wrongCount: Int,
    val wrongPercent: Double,
)

data class ReportsQuestionHeatmapItem(
    val questionNumber: Int,
    val wrongCount: Int,
    val wrongPercent: Double,
)

data class ReportsRelatedErrorItem(
    val firstQuestion: Int,
    val secondQuestion: Int,
    val togetherCount: Int,
)

data class ReportsIdenticalWorksItem(
    val signature: String,
    val workTitles: List<String>,
)

data class ReportsVariantStatsItem(
    val variant: Int,
    val worksCount: Int,
    val averagePercent: Double,
)

data class ReportsVariantComparisonSummary(
    val bestVariant: Int,
    val bestPercent: Double,
    val worstVariant: Int,
    val worstPercent: Double,
    val gapPercent: Double,
)

data class ReportsClassStatsItem(
    val className: String,
    val worksCount: Int,
    val averagePercent: Double,
    val successRatePercent: Double,
)

data class ReportsWorkListItem(
    val title: String,
    val grade: Int?,
    val percent: Float?,
    val scoreText: String,
    val status: String,
    val correctQuestions: List<Int> = emptyList(),
    val wrongQuestions: List<Int> = emptyList(),
    val variant: Int? = null,
    val className: String? = null,
    val studentDisplayName: String? = null,
    val strictScoring: Boolean = true,
    val partialCompletedCount: Int = 0,
    val hasMultiChoiceQuestions: Boolean = false,
)

data class ReportsState(
    val title: String = "Отчеты",
    val emptyText: String = "Пока нет данных. Сначала выполните пакетную проверку работ.",
    val summary: ReportsSummaryStats? = null,
    val gradeDistribution: List<ReportsGradeDistributionItem> = emptyList(),
    val hardestQuestions: List<ReportsHardQuestionItem> = emptyList(),
    val heatmap: List<ReportsQuestionHeatmapItem> = emptyList(),
    val relatedErrors: List<ReportsRelatedErrorItem> = emptyList(),
    val identicalWorks: List<ReportsIdenticalWorksItem> = emptyList(),
    val variantStats: List<ReportsVariantStatsItem> = emptyList(),
    val variantComparison: ReportsVariantComparisonSummary? = null,
    val classStats: List<ReportsClassStatsItem> = emptyList(),
    val namedWorksCount: Int = 0,
    val unnamedWorksCount: Int = 0,
    val works: List<ReportsWorkListItem> = emptyList(),
)
