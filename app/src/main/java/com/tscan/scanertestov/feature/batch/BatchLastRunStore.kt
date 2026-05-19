package com.tscan.scanertestov.feature.batch

/**
 * Описание: последние строки результата пакетного прогона (до появления полноценного state/navigation args).
 */
object BatchLastRunStore {
    @Volatile
    var resultLines: List<String> = emptyList()

    @Volatile
    var resultItems: List<BatchRunItemResult> = emptyList()
}
