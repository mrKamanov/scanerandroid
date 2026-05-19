package com.tscan.scanertestov.feature.reports

/**
 * Описание: маршрут экрана отчётов — состояние из выбранных работ, очистка, экспорт Excel и навигация назад.
 */
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.blankeditor.shareExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReportsRoute(navController: NavHostController) {
    var reloadToken by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(reloadToken) {
        buildReportsState(ReportsSelectedWorksStore.works)
    }
    ReportsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ReportsAction.Clear -> {
                    ReportsSelectedWorksStore.clear()
                    reloadToken++
                }
                ReportsAction.ExportExcel -> {
                    val exportState = buildReportsState(ReportsSelectedWorksStore.works)
                    if (exportState.summary != null) {
                        scope.launch(Dispatchers.IO) {
                            val file =
                                ReportsExcelExporter(context.applicationContext).exportToExcel(exportState)
                            withContext(Dispatchers.Main) {
                                if (file != null) {
                                    shareExportedFile(context.applicationContext, file)
                                } else {
                                    Toast.makeText(
                                            context,
                                            "Не удалось создать Excel",
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                            }
                        }
                    }
                }
                ReportsAction.Back -> navController.popBackStack()
                ReportsAction.ExportPdf -> {
                    val exportState = buildReportsState(ReportsSelectedWorksStore.works)
                    if (exportState.summary != null) {
                        scope.launch(Dispatchers.IO) {
                            val file =
                                ReportsPdfExporter(context.applicationContext).exportToPdf(exportState)
                            withContext(Dispatchers.Main) {
                                if (file != null) {
                                    shareExportedFile(context.applicationContext, file)
                                } else {
                                    Toast.makeText(
                                            context,
                                            "Не удалось создать PDF",
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun buildReportsState(items: List<ReportsWorkSnapshot>): ReportsState {
    if (items.isEmpty()) {
        return ReportsState(
            emptyText = "Отчет пуст. Добавьте работы кнопками «В отчет» на экране результатов пакетной проверки.",
        )
    }

    val workItems = items.map { item ->
        val correctQuestions = item.questionScores.filter { it.correct }.map { it.questionIndex + 1 }
        val wrongQuestions = item.questionScores.filter { !it.correct }.map { it.questionIndex + 1 }
        val fullName = listOfNotNull(item.studentSurname, item.studentName)
            .joinToString(" ")
            .trim()
            .ifBlank { null }
        ReportsWorkListItem(
            title = item.title,
            grade = item.grade,
            percent = item.percent,
            scoreText = "${item.scoreCorrect}/${item.scoreTotal}",
            status = "Проверено",
            correctQuestions = correctQuestions,
            wrongQuestions = wrongQuestions,
            variant = item.variant,
            className = item.journalClassName,
            studentDisplayName = fullName,
            strictScoring = item.strictScoring,
            partialCompletedCount = item.partialCompletedCount,
            hasMultiChoiceQuestions = item.hasMultiChoiceQuestions,
        )
    }

    val totalWorks = items.size
    val averageGrade = items
        .map { it.grade.toDouble() }
        .average()
        .takeIf { !it.isNaN() } ?: 0.0
    val gradeDistributionRaw = (2..5).associateWith { grade ->
        items.count { it.grade == grade }
    }
    val successCount = items.count { it.grade >= 3 }
    val qualityCount = items.count { it.grade >= 4 }
    val summary = ReportsSummaryStats(
        totalWorks = totalWorks,
        averageGrade = averageGrade,
        successRatePercent = successCount * 100.0 / totalWorks,
        qualityKnowledgePercent = qualityCount * 100.0 / totalWorks,
    )

    val gradeDistribution = (2..5).map { grade ->
        val count = gradeDistributionRaw[grade] ?: 0
        ReportsGradeDistributionItem(
            grade = grade,
            count = count,
            percent = if (totalWorks == 0) 0.0 else count * 100.0 / totalWorks,
        )
    }

    val wrongByQuestion = mutableMapOf<Int, Int>()
    val questionSeenCount = mutableMapOf<Int, Int>()
    val wrongSets = mutableListOf<Set<Int>>()
    items.forEach { item ->
        val questionScores = item.questionScores
        val wrongSet = mutableSetOf<Int>()
        questionScores.forEach { score ->
            val q = score.questionIndex + 1
            questionSeenCount[q] = (questionSeenCount[q] ?: 0) + 1
            if (!score.correct) {
                wrongByQuestion[q] = (wrongByQuestion[q] ?: 0) + 1
                wrongSet += q
            }
        }
        if (wrongSet.isNotEmpty()) wrongSets += wrongSet
    }

    val hardestQuestions = wrongByQuestion.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .take(5)
        .map { (q, wrongCount) ->
            val seen = questionSeenCount[q] ?: totalWorks
            ReportsHardQuestionItem(
                questionNumber = q,
                wrongCount = wrongCount,
                wrongPercent = if (seen == 0) 0.0 else wrongCount * 100.0 / seen,
            )
        }

    val maxQuestion = questionSeenCount.keys.maxOrNull() ?: 0
    val heatmap = (1..maxQuestion).map { q ->
        val wrongCount = wrongByQuestion[q] ?: 0
        val seen = questionSeenCount[q] ?: totalWorks
        ReportsQuestionHeatmapItem(
            questionNumber = q,
            wrongCount = wrongCount,
            wrongPercent = if (seen == 0) 0.0 else wrongCount * 100.0 / seen,
        )
    }

    val pairCounts = mutableMapOf<Pair<Int, Int>, Int>()
    wrongSets.forEach { wrongSet ->
        val sorted = wrongSet.sorted()
        for (i in 0 until sorted.size) {
            for (j in i + 1 until sorted.size) {
                val pair = sorted[i] to sorted[j]
                pairCounts[pair] = (pairCounts[pair] ?: 0) + 1
            }
        }
    }
    val relatedErrors = pairCounts.entries
        .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, Int>> { it.value }.thenBy { it.key.first }.thenBy { it.key.second })
        .take(5)
        .map { (pair, count) ->
            ReportsRelatedErrorItem(
                firstQuestion = pair.first,
                secondQuestion = pair.second,
                togetherCount = count,
            )
        }

    val groupedBySignature = items.groupBy { item ->
        val signature = item.questionScores
            .joinToString("|") { score ->
                val selected = score.selectedChoices.sorted().joinToString(",")
                "${score.questionIndex + 1}=$selected"
            }
        val classKey = item.journalClassName?.trim()?.ifBlank { "no-class" } ?: "no-class"
        val variantKey = item.variant?.toString() ?: "no-variant"
        "class=$classKey;variant=$variantKey;$signature"
    }
    val identicalWorks = groupedBySignature.values
        .filter { it.size >= 2 }
        .sortedByDescending { it.size }
        .take(5)
        .map { group ->
            val classLabel = group.firstOrNull()?.journalClassName?.trim().orEmpty()
            val variantLabel = group.firstOrNull()?.variant
            val scopeLabel = buildString {
                if (classLabel.isNotBlank()) append("класс $classLabel")
                if (variantLabel != null) {
                    if (isNotBlank()) append(", ")
                    append("вариант $variantLabel")
                }
            }.ifBlank { "без привязки к классу/варианту" }
            ReportsIdenticalWorksItem(
                signature = "Совпадение ответов: ${group.size} работ ($scopeLabel)",
                workTitles = group.map { it.title },
            )
        }

    val variantStats = items
        .filter { it.variant != null }
        .groupBy { it.variant!! }
        .entries
        .sortedBy { it.key }
        .map { (variant, works) ->
            ReportsVariantStatsItem(
                variant = variant,
                worksCount = works.size,
                averagePercent = works.map { it.percent.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
            )
        }

    val namedWorksCount = items.count {
        !listOfNotNull(it.studentSurname, it.studentName).joinToString(" ").trim().isBlank()
    }
    val unnamedWorksCount = items.size - namedWorksCount

    val variantComparison = if (variantStats.size >= 2) {
        val best = variantStats.maxByOrNull { it.averagePercent }!!
        val worst = variantStats.minByOrNull { it.averagePercent }!!
        ReportsVariantComparisonSummary(
            bestVariant = best.variant,
            bestPercent = best.averagePercent,
            worstVariant = worst.variant,
            worstPercent = worst.averagePercent,
            gapPercent = (best.averagePercent - worst.averagePercent).coerceAtLeast(0.0),
        )
    } else {
        null
    }

    val classStats = items
        .filter { !it.journalClassName.isNullOrBlank() }
        .groupBy { it.journalClassName!!.trim() }
        .entries
        .sortedBy { it.key }
        .map { (cls, works) ->
            ReportsClassStatsItem(
                className = cls,
                worksCount = works.size,
                averagePercent = works.map { it.percent.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                successRatePercent = works.count { it.grade >= 3 } * 100.0 / works.size,
            )
        }

    return ReportsState(
        summary = summary,
        gradeDistribution = gradeDistribution,
        hardestQuestions = hardestQuestions,
        heatmap = heatmap,
        relatedErrors = relatedErrors,
        identicalWorks = identicalWorks,
        variantStats = variantStats,
        variantComparison = variantComparison,
        classStats = classStats,
        namedWorksCount = namedWorksCount,
        unnamedWorksCount = unnamedWorksCount,
        works = workItems.sortedByDescending { (it.percent ?: 0f).roundToInt() },
    )
}
