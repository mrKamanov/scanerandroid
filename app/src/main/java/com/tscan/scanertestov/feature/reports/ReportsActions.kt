package com.tscan.scanertestov.feature.reports

/**
 * Описание: действия пользователя на экране отчетов.
 */
sealed interface ReportsAction {
    data object Back : ReportsAction
    data object Clear : ReportsAction
    data object ExportPdf : ReportsAction
    data object ExportExcel : ReportsAction
}
