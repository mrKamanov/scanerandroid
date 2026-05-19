package com.tscan.scanertestov.feature.batch

/**
 * Описание: действия пользователя на экране результатов пакетной обработки.
 */
sealed interface BatchResultsAction {
    data object Back : BatchResultsAction
    data object OpenReports : BatchResultsAction
    data class SendWorkToReports(val itemIndex: Int) : BatchResultsAction
    data object SendAllToReports : BatchResultsAction
    data class MarkFixedAsAnswered(val key: String) : BatchResultsAction
    data class MarkFixedAsEmpty(val key: String) : BatchResultsAction
}
