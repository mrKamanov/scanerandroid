package com.tscan.scanertestov.feature.batch

/**
 * Описание: состояние экрана итогов пакетной обработки.
 */
data class BatchResultsSummary(
    val totalWorks: Int,
    val countGrade5: Int,
    val countGrade4: Int,
    val countGrade3: Int,
    val countGrade2: Int,
)

data class BatchResultsState(
    val resultEntries: List<BatchResultEntryState> = listOf(
        BatchResultEntryState(
            title = "Бланк #1",
            subtitle = "Иванов И. — вариант 1",
            status = "Ошибок нет",
            grade = 5,
            percent = 83.3f,
            scoreCorrect = 10,
            scoreTotal = 12,
            correctionsTotal = 0,
            correctionsUnresolved = 0,
            layoutLine = "Один столбец",
        )
    ),
    val toReportsButtonText: String = "Перейти в отчеты",
    val sendAllToReportsButtonText: String = "Отправить все в отчет",
    val sendToReportsButtonText: String = "В отчет",
    val inReportsButtonText: String = "В отчете",
    val summary: BatchResultsSummary? = BatchResultsSummary(
        totalWorks = 1,
        countGrade5 = 1,
        countGrade4 = 0,
        countGrade3 = 0,
        countGrade2 = 0,
    ),
    val fixedItems: List<BatchFixedCellUiItem> = emptyList(),
    val exportedToReportsIndices: Set<Int> = emptySet(),
)
