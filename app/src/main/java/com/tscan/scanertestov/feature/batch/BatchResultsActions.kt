package com.tscan.scanertestov.feature.batch

/**
 * Описание: действия пользователя на экране результатов пакетной обработки.
 */
sealed interface BatchResultsAction {
    data object Back : BatchResultsAction
    data object OpenReports : BatchResultsAction
    data class SendWorkToReports(val itemIndex: Int) : BatchResultsAction
    data object SendAllToReports : BatchResultsAction
    /** Передумал: убрать работу из отчёта (снимок и результат проверки остаются). */
    data class RemoveWorkFromReports(val itemIndex: Int) : BatchResultsAction
    /** Повтор: оставить прежнюю работу в отчёте (новая не добавляется). */
    data object DuplicateKeepExisting : BatchResultsAction
    /** Повтор: заменить прежнюю работу новой. */
    data object DuplicateReplace : BatchResultsAction
    /** Повтор: это другой ученик — не добавлять, а открыть «Чья работа?». */
    data object DuplicateOtherStudent : BatchResultsAction
    data object AssignRequestHandled : BatchResultsAction
    data class MarkFixedAsAnswered(val key: String) : BatchResultsAction
    data class MarkFixedAsEmpty(val key: String) : BatchResultsAction
    /** «Чья работа?»: учитель выбрал ученика (или вписал вручную). */
    data class AssignStudent(val itemIndex: Int, val surname: String, val name: String, val className: String?) : BatchResultsAction
}
