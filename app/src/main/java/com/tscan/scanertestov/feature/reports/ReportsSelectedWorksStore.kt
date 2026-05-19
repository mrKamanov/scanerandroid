package com.tscan.scanertestov.feature.reports

/**
 * Описание: снимки работ для отчётов (источник — экран результатов пакета) и объект-хранилище списка.
 */
import com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore

data class ReportsWorkSnapshot(
    val sourceKey: String,
    val title: String,
    val grade: Int,
    val percent: Float,
    val scoreCorrect: Int,
    val scoreTotal: Int,
    val questionScores: List<BatchQuestionScore>,
    val variant: Int? = null,
    val studentSurname: String? = null,
    val studentName: String? = null,
    val journalClassName: String? = null,
    val strictScoring: Boolean = true,
    val partialCompletedCount: Int = 0,
    val hasMultiChoiceQuestions: Boolean = false,
)

object ReportsSelectedWorksStore {
    @Volatile
    var works: List<ReportsWorkSnapshot> = emptyList()
        private set

    fun upsert(work: ReportsWorkSnapshot) {
        val current = works.toMutableList()
        val idx = current.indexOfFirst { it.sourceKey == work.sourceKey }
        if (idx >= 0) current[idx] = work else current += work
        works = current
    }

    fun upsertAll(items: List<ReportsWorkSnapshot>) {
        items.forEach { upsert(it) }
    }

    fun clear() {
        works = emptyList()
    }
}
