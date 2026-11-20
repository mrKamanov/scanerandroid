package com.tscan.scanertestov.feature.reports

/**
 * Описание: поиск повторов в отчёте по тесту. Повтор — та же работа ещё раз: тот же ученик
 * (совпали фамилия и имя) или тот же бланк, сфотографированный повторно (совпали все ответы
 * и вариант, а имени у одной из работ нет). В отчёт должна попасть одна работа ученика.
 */
object ReportDuplicates {

    /** Работа в отчёте того же теста, которую повторяет [candidate]; null — повтора нет. */
    fun findFor(candidate: ReportsWorkSnapshot, existing: List<ReportsWorkSnapshot>): ReportsWorkSnapshot? {
        val same = existing.filter { it.reportKey == candidate.reportKey && it.sourceKey != candidate.sourceKey }
        val name = candidate.fullName?.let(::norm)
        if (name != null) {
            same.firstOrNull { it.fullName?.let(::norm) == name }?.let { return it }
        }
        return same.firstOrNull { other ->
            (candidate.fullName == null || other.fullName == null) && sameAnswers(candidate, other)
        }
    }

    /** Номера вопросов (с 1), где ответы двух работ различаются. */
    fun differingQuestions(a: ReportsWorkSnapshot, b: ReportsWorkSnapshot): List<Int> {
        val ma = a.questionScores.associate { it.questionIndex to it.selectedChoices }
        val mb = b.questionScores.associate { it.questionIndex to it.selectedChoices }
        return (ma.keys + mb.keys).sorted().filter { ma[it].orEmpty() != mb[it].orEmpty() }.map { it + 1 }
    }

    fun sameAnswers(a: ReportsWorkSnapshot, b: ReportsWorkSnapshot): Boolean =
        a.variant == b.variant && a.questionScores.size == b.questionScores.size &&
            a.questionScores.isNotEmpty() && differingQuestions(a, b).isEmpty()

    /**
     * Группы повторов внутри отчёта (на случай старых данных или работ без имени):
     * в каждой группе первая работа считается, остальные — повторы.
     */
    fun groups(works: List<ReportsWorkSnapshot>): List<List<ReportsWorkSnapshot>> {
        val used = HashSet<String>()
        val out = ArrayList<List<ReportsWorkSnapshot>>()
        works.forEach { w ->
            if (w.sourceKey in used) return@forEach
            val group = listOf(w) + works.filter { o ->
                o.sourceKey != w.sourceKey && o.sourceKey !in used && o.reportKey == w.reportKey &&
                    ((w.fullName != null && o.fullName != null && norm(w.fullName!!) == norm(o.fullName!!)) ||
                        ((w.fullName == null || o.fullName == null) && sameAnswers(w, o)))
            }
            if (group.size > 1) {
                group.forEach { used += it.sourceKey }
                out += group
            }
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()
}
