package com.tscan.scanertestov.feature.reports

/**
 * Описание: отчёт по тесту — все классы вместе. Считается из работ, отправленных «В отчёт».
 *
 * Отчёт = тест (а если работу проверяли без сохранённого теста — сама проверка). Внутри:
 * сводка, сравнение классов, вариантов (в целом, по классам и внутри класса), вопросы
 * (доля верных, что выбирали, частая ошибка, ошибки вместе) и список учеников.
 * Повторы одной работы (тот же ученик или тот же бланк ещё раз) в расчёт идут один раз.
 *
 * Ответы называем так же, как на бланке: «14.2» — второй кружок 14-го вопроса.
 */
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val NO_CLASS = "Без класса"

data class ReportKpi(
    val works: Int,
    val avgGrade: Double,
    val avgPercent: Double,
    val successPct: Double,
    val qualityPct: Double,
    val souPct: Double,
    /** Оценка → число работ (5, 4, 3, 2). */
    val grades: Map<Int, Int>,
)

data class ReportClassRow(val className: String, val kpi: ReportKpi)

data class ReportVariantCell(val variant: Int, val works: Int, val avgPercent: Double, val avgGrade: Double)

data class ReportClassVariantRow(val className: String, val cells: Map<Int, ReportVariantCell>)

/**
 * Сколько раз выбрали ответ. [right] — у скольких из них он верный: при разных вариантах
 * один и тот же кружок («14.2») в одном варианте верный, в другом — нет.
 */
data class ReportAnswerCount(val label: String, val count: Int, val right: Int) {
    /** Верный у всех, кто его выбрал. */
    val correct: Boolean get() = count > 0 && right == count
    val wrong: Int get() = count - right
}

data class ReportQuestionStat(
    /** Номер вопроса с 1. */
    val number: Int,
    val answered: Int,
    val correctPct: Double,
    val correctByClass: Map<String, Double?>,
    val correctByVariant: Map<Int, Double?>,
    /** Верный ответ по вариантам: вариант (или 0, если вариантов нет) → «14.2». */
    val keyLabels: Map<Int, String>,
    /** Что выбирали (по всем работам), по убыванию. «не ответил» — пустой ответ. */
    val answers: List<ReportAnswerCount>,
    val topWrong: ReportAnswerCount?,
    val noAnswer: Int,
    val multiMarked: Int,
    val withFixed: Int,
)

data class ReportPairError(val a: Int, val b: Int, val together: Int)

data class ReportStudentRow(
    val work: ReportsWorkSnapshot,
    val className: String,
    val displayName: String,
    /** Повтор другой работы (в расчёт не идёт). */
    val duplicateOf: ReportsWorkSnapshot?,
)

/** Внутри класса: вопросы, где варианты разошлись сильнее всего. */
data class ReportVariantGap(val question: Int, val byVariant: Map<Int, Double>)

data class TestReport(
    val key: String,
    val title: String,
    val questionCount: Int,
    val variants: List<Int>,
    val classes: List<String>,
    val dateRange: String,
    val totalWorks: Int,
    /** Фильтр по классу (null — все классы). */
    val classFilter: String?,
    val kpi: ReportKpi,
    val classRows: List<ReportClassRow>,
    val variantCells: List<ReportVariantCell>,
    val classVariantRows: List<ReportClassVariantRow>,
    val questions: List<ReportQuestionStat>,
    val pairs: List<ReportPairError>,
    val students: List<ReportStudentRow>,
    val duplicateCount: Int,
    val unnamedCount: Int,
) {
    val hardest: List<ReportQuestionStat> get() = questions.filter { it.answered > 0 }.sortedBy { it.correctPct }.take(5)

    /** Варианты внутри класса: вопросы с наибольшей разницей. */
    fun variantGapsInClass(className: String, top: Int = 3): List<ReportVariantGap> {
        if (variants.size < 2) return emptyList()
        val works = students.filter { it.duplicateOf == null && it.className == className }.map { it.work }
        val qs = (0 until questionCount).mapNotNull { qi ->
            val by = variants.mapNotNull { v ->
                val ws = works.filter { it.variant == v }
                val sc = ws.mapNotNull { w -> w.questionScores.firstOrNull { it.questionIndex == qi } }
                if (sc.isEmpty()) null else v to sc.count { it.correct } * 100.0 / sc.size
            }.toMap()
            if (by.size < 2) null else ReportVariantGap(qi + 1, by)
        }
        return qs.sortedByDescending { g -> g.byVariant.values.max() - g.byVariant.values.min() }.take(top)
    }
}

/** Краткая строка для списка отчётов. */
data class ReportListItem(
    val key: String,
    val title: String,
    val meta: String,
    val classes: List<String>,
    val kpi: ReportKpi,
    val lastCheckedMs: Long,
)

object TestReportBuilder {

    fun titleOf(works: List<ReportsWorkSnapshot>, overrides: Map<String, String>): String {
        val w = works.firstOrNull() ?: return "Отчёт"
        overrides[w.reportKey]?.let { return it }
        return w.testTitle ?: "Проверка от ${dateFmt.format(Date(w.checkedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis()))}"
    }

    fun list(all: List<ReportsWorkSnapshot>, overrides: Map<String, String>): List<ReportListItem> =
        all.groupBy { it.reportKey }.map { (key, works) ->
            val counted = countedWorks(works)
            val classes = works.map { classOf(it) }.distinct().sorted()
            val variants = works.mapNotNull { it.variant }.distinct().size
            ReportListItem(
                key = key,
                title = titleOf(works, overrides),
                meta = buildString {
                    append(worksWord(counted.size))
                    if (variants > 1) append(" · ").append(variants).append(" вар.")
                    dateRange(works).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                classes = classes,
                kpi = kpi(counted),
                lastCheckedMs = works.maxOf { it.checkedAtMs },
            )
        }.sortedByDescending { it.lastCheckedMs }

    fun build(all: List<ReportsWorkSnapshot>, key: String, classFilter: String?, overrides: Map<String, String>): TestReport? {
        val works = all.filter { it.reportKey == key }
        if (works.isEmpty()) return null
        val dupGroups = ReportDuplicates.groups(works)
        val dupOf = HashMap<String, ReportsWorkSnapshot>()
        dupGroups.forEach { g -> g.drop(1).forEach { dupOf[it.sourceKey] = g.first() } }
        val classes = works.map { classOf(it) }.distinct().sortedWith(classOrder)
        val filter = classFilter?.takeIf { it in classes }
        val scoped = works.filter { filter == null || classOf(it) == filter }
        val counted = scoped.filter { it.sourceKey !in dupOf }
        val variants = works.mapNotNull { it.variant }.distinct().sorted()
        val qCount = works.maxOf { w -> (w.questionScores.maxOfOrNull { it.questionIndex } ?: -1) + 1 }

        val classRows = classes.map { c -> ReportClassRow(c, kpi(works.filter { classOf(it) == c && it.sourceKey !in dupOf })) }
            .filter { it.kpi.works > 0 }
        val variantCells = variants.map { v -> variantCell(v, counted.filter { it.variant == v }) }
        val classVariantRows = if (variants.size >= 2) {
            classes.map { c ->
                val cw = works.filter { classOf(it) == c && it.sourceKey !in dupOf }
                ReportClassVariantRow(c, variants.associateWith { v -> variantCell(v, cw.filter { it.variant == v }) })
            }.filter { r -> r.cells.values.any { it.works > 0 } }
        } else emptyList()

        val questions = (0 until qCount).map { qi -> questionStat(qi, counted, classes, variants) }
        val pairs = pairErrors(counted)
        val students = scoped.map { w ->
            ReportStudentRow(w, classOf(w), w.fullName ?: "Без имени (${w.title})", dupOf[w.sourceKey])
        }.sortedWith(compareBy<ReportStudentRow>({ classOrderIndex(it.className, classes) }, { -it.work.grade }, { it.displayName }))

        return TestReport(
            key = key,
            title = titleOf(works, overrides),
            questionCount = qCount,
            variants = variants,
            classes = classes,
            dateRange = dateRange(works),
            totalWorks = counted.size,
            classFilter = filter,
            kpi = kpi(counted),
            classRows = classRows,
            variantCells = variantCells,
            classVariantRows = classVariantRows,
            questions = questions,
            pairs = pairs,
            students = students,
            duplicateCount = scoped.count { it.sourceKey in dupOf },
            unnamedCount = counted.count { it.fullName == null },
        )
    }

    // region расчёты

    fun classOf(w: ReportsWorkSnapshot): String = w.journalClassName?.trim()?.ifBlank { null } ?: NO_CLASS

    private val classOrder = Comparator<String> { a, b ->
        when {
            a == b -> 0
            a == NO_CLASS -> 1
            b == NO_CLASS -> -1
            else -> {
                val na = a.takeWhile { it.isDigit() }.toIntOrNull()
                val nb = b.takeWhile { it.isDigit() }.toIntOrNull()
                if (na != null && nb != null && na != nb) na.compareTo(nb) else a.compareTo(b, ignoreCase = true)
            }
        }
    }

    private fun classOrderIndex(c: String, classes: List<String>) = classes.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it }

    fun countedWorks(works: List<ReportsWorkSnapshot>): List<ReportsWorkSnapshot> {
        val dup = ReportDuplicates.groups(works).flatMap { it.drop(1) }.map { it.sourceKey }.toSet()
        return works.filter { it.sourceKey !in dup }
    }

    fun kpi(works: List<ReportsWorkSnapshot>): ReportKpi {
        val n = works.size
        val grades = (2..5).associateWith { g -> works.count { it.grade == g } }
        fun pct(c: Int) = if (n == 0) 0.0 else c * 100.0 / n
        val sou = if (n == 0) 0.0 else ((grades[5] ?: 0) * 1.0 + (grades[4] ?: 0) * 0.64 + (grades[3] ?: 0) * 0.36 + (grades[2] ?: 0) * 0.16) / n * 100
        return ReportKpi(
            works = n,
            avgGrade = if (n == 0) 0.0 else works.map { it.grade }.average(),
            avgPercent = if (n == 0) 0.0 else works.map { it.percent.toDouble() }.average(),
            successPct = pct(works.count { it.grade >= 3 }),
            qualityPct = pct(works.count { it.grade >= 4 }),
            souPct = sou,
            grades = grades,
        )
    }

    private fun variantCell(v: Int, ws: List<ReportsWorkSnapshot>) = ReportVariantCell(
        variant = v,
        works = ws.size,
        avgPercent = if (ws.isEmpty()) 0.0 else ws.map { it.percent.toDouble() }.average(),
        avgGrade = if (ws.isEmpty()) 0.0 else ws.map { it.grade }.average(),
    )

    fun answerLabel(question: Int, choices: Set<Int>): String =
        if (choices.isEmpty()) "не ответил" else choices.sorted().joinToString(", ") { "$question.${it + 1}" }

    private fun questionStat(qi: Int, works: List<ReportsWorkSnapshot>, classes: List<String>, variants: List<Int>): ReportQuestionStat {
        val q = qi + 1
        val scores = works.mapNotNull { w -> w.questionScores.firstOrNull { it.questionIndex == qi }?.let { w to it } }
        val answered = scores.size
        fun pctOf(list: List<Pair<ReportsWorkSnapshot, com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore>>): Double? =
            if (list.isEmpty()) null else list.count { it.second.correct } * 100.0 / list.size
        val counts = scores.groupBy { it.second.selectedChoices }.map { (sel, list) ->
            ReportAnswerCount(answerLabel(q, sel), list.size, if (sel.isEmpty()) 0 else list.count { it.second.correct })
        }.sortedByDescending { it.count }
        // Частая ошибка — по числу НЕВЕРНЫХ выборов (с учётом варианта), а не по числу выборов.
        val topWrong = counts.filter { it.wrong > 0 && it.label != "не ответил" }
            .maxByOrNull { it.wrong }
            ?.let { it.copy(count = it.wrong, right = 0) }
        val keyLabels = works.groupBy { it.variant ?: 0 }.mapNotNull { (v, ws) ->
            ws.firstNotNullOfOrNull { w -> w.answerKey.getOrNull(qi)?.takeIf { it.isNotEmpty() } }?.let { v to answerLabel(q, it) }
        }.toMap()
        return ReportQuestionStat(
            number = q,
            answered = answered,
            correctPct = pctOf(scores) ?: 0.0,
            correctByClass = classes.associateWith { c -> pctOf(scores.filter { classOf(it.first) == c }) },
            correctByVariant = variants.associateWith { v -> pctOf(scores.filter { it.first.variant == v }) },
            keyLabels = keyLabels,
            answers = counts,
            topWrong = topWrong,
            noAnswer = scores.count { it.second.selectedChoices.isEmpty() },
            multiMarked = scores.count { it.second.selectedChoices.size > 1 && (it.first.answerKey.getOrNull(qi)?.size ?: 1) <= 1 },
            withFixed = scores.count { it.second.hasFixed },
        )
    }

    private fun pairErrors(works: List<ReportsWorkSnapshot>): List<ReportPairError> {
        val counts = HashMap<Pair<Int, Int>, Int>()
        works.forEach { w ->
            val wrong = w.questionScores.filter { !it.correct }.map { it.questionIndex + 1 }.sorted()
            for (i in wrong.indices) for (j in i + 1 until wrong.size) {
                val k = wrong[i] to wrong[j]
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        val minTogether = maxOf(3, works.size / 5)
        return counts.filter { it.value >= minTogether }.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, Int>> { it.value }.thenBy { it.key.first })
            .take(5).map { ReportPairError(it.key.first, it.key.second, it.value) }
    }

    private val dateFmt = SimpleDateFormat("d MMMM", Locale("ru"))

    fun dateRange(works: List<ReportsWorkSnapshot>): String {
        val ts = works.map { it.checkedAtMs }.filter { it > 0 }
        if (ts.isEmpty()) return ""
        val a = dateFmt.format(Date(ts.min()))
        val b = dateFmt.format(Date(ts.max()))
        return if (a == b) a else "$a – $b"
    }

    /** Меньше стольких работ в варианте — не говорим, что он сложнее или проще. */
    const val MIN_WORKS_FOR_VERDICT = 5

    /** Вывод о вариантах (один для экрана и PDF); null — вариант один. */
    fun variantVerdict(cells: List<ReportVariantCell>): String? {
        val c = cells.filter { it.works > 0 }
        if (c.size < 2) return null
        val worst = c.minBy { it.avgPercent }
        val gap = c.maxOf { it.avgPercent } - worst.avgPercent
        val g = String.format(Locale("ru"), "%.0f", gap)
        // По двум-трём работам вывод о сложности варианта делать рано.
        val few = c.filter { it.works < MIN_WORKS_FOR_VERDICT }
        if (few.isNotEmpty()) {
            return "Разница $g%, но в " + few.joinToString(", ") { "варианте ${it.variant} — ${worksWord(it.works)}" } +
                ": этого мало, чтобы судить, какой вариант сложнее (нужно хотя бы $MIN_WORKS_FOR_VERDICT)."
        }
        return if (gap >= 10) "Вариант ${worst.variant} заметно сложнее (на $g%) — варианты неравноценны."
        else "Варианты примерно равны: разница $g%."
    }

    fun worksWord(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        val w = when {
            m10 == 1 && m100 != 11 -> "работа"
            m10 in 2..4 && m100 !in 12..14 -> "работы"
            else -> "работ"
        }
        return "$n $w"
    }

    // endregion
}
