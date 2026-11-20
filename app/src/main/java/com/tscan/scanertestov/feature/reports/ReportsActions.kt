package com.tscan.scanertestov.feature.reports

/**
 * Описание: действия пользователя на экране отчётов.
 */
sealed interface ReportsAction {
    data object Back : ReportsAction
    data class OpenReport(val key: String) : ReportsAction
    data class SelectTab(val tab: ReportTab) : ReportsAction
    /** null — все классы. */
    data class FilterClass(val className: String?) : ReportsAction
    data class FocusQuestion(val number: Int) : ReportsAction
    data class VariantClass(val className: String) : ReportsAction
    data class StudentQuery(val query: String) : ReportsAction
    data class StudentFilterChanged(val filter: StudentFilter) : ReportsAction
    data class RemoveWork(val sourceKey: String) : ReportsAction
    data class ChangeStudent(val sourceKey: String, val surname: String, val name: String, val className: String?) : ReportsAction
    data class RenameReport(val key: String, val title: String) : ReportsAction
    data class DeleteReport(val key: String) : ReportsAction
    data class Export(val key: String, val options: ReportExportOptions) : ReportsAction
}

enum class ReportExportFormat { Excel, Pdf }

data class ReportExportOptions(
    val format: ReportExportFormat,
    /** null — все классы. */
    val className: String? = null,
    val summary: Boolean = true,
    val classes: Boolean = true,
    val variants: Boolean = true,
    val questions: Boolean = true,
    val students: Boolean = true,
    /** Ответ каждого ученика по каждому вопросу. */
    val answers: Boolean = false,
)
