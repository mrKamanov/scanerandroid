package com.tscan.scanertestov.feature.reports

/**
 * Описание: состояние экрана отчётов — список отчётов по тестам или открытый отчёт с вкладками.
 */
enum class ReportTab(val title: String) {
    Overview("Обзор"),
    Classes("Классы"),
    Variants("Варианты"),
    Questions("Вопросы"),
    Students("Ученики"),
}

/** Фильтр списка учеников. */
enum class StudentFilter(val title: String) { All("Все"), Duplicates("Повторы"), Unnamed("Без имени"), Twos("«2»") }

data class ReportsState(
    val list: List<ReportListItem> = emptyList(),
    /** Открытый отчёт; null — показываем список. */
    val report: TestReport? = null,
    val tab: ReportTab = ReportTab.Overview,
    /** Вопрос, открытый в разборе на вкладке «Вопросы» (с 1). */
    val questionFocus: Int? = null,
    /** Для «Варианты»: класс, внутри которого сравниваем варианты. */
    val variantClass: String? = null,
    val studentQuery: String = "",
    val studentFilter: StudentFilter = StudentFilter.All,
    val roster: List<com.tscan.scanertestov.feature.journals.JournalStudent> = emptyList(),
)
