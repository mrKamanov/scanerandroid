package com.tscan.scanertestov.feature.batch

/**
 * Описание: маршрут экрана результатов пакета с пересчетом после решений по fixed.
 */
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.feature.reports.ReportsSelectedWorksStore
import com.tscan.scanertestov.feature.reports.ReportsWorkSnapshot
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchScoringEngine
import java.util.Locale

@Composable
fun BatchResultsRoute(navController: NavHostController) {
    val context = LocalContext.current
    var criteriaReload by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) criteriaReload++
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val gradingCriteria = remember(criteriaReload, context) {
        AppSettingsStore.getGradingCriteria(context)
    }
    val runItems = BatchLastRunStore.resultItems
    val exportedIndices = remember { mutableStateMapOf<Int, Boolean>() }
    val fixedDecisions = remember { mutableStateMapOf<String, Boolean>() }
    val fixedUiItems = buildFixedUiItems(
        runItems = runItems,
        decisions = fixedDecisions,
        showOnlyUnresolved = true,
    )
    val entries = if (runItems.isNotEmpty()) {
        buildResultEntries(
            runItems = runItems,
            decisions = fixedDecisions,
            gradingCriteria = gradingCriteria,
        )
    } else {
        val lines = BatchLastRunStore.resultLines
        if (lines.isNotEmpty()) {
            lines.map { line ->
                BatchResultEntryState(
                    title = "Результат",
                    status = line,
                )
            }
        } else {
            listOf(
                BatchResultEntryState(
                    title = "Пакетная обработка",
                    status = "Пока нет сохранённого результата. Запустите обработку на экране «Пакетная обработка».",
                ),
            )
        }
    }
    if (exportedIndices.isEmpty()) {
        entries.forEachIndexed { index, entry ->
            val key = reportSourceKey(index, entry)
            if (ReportsSelectedWorksStore.works.any { it.sourceKey == key }) {
                exportedIndices[index] = true
            }
        }
    }
    BatchResultsScreen(
        state = BatchResultsState(
            resultEntries = entries,
            summary = if (runItems.isNotEmpty()) buildSummary(entries) else null,
            fixedItems = fixedUiItems,
            exportedToReportsIndices = exportedIndices.keys,
        ),
        onAction = { action ->
            when (action) {
                BatchResultsAction.Back,
                BatchResultsAction.OpenReports -> handleBatchResultsAction(navController, action)
                is BatchResultsAction.SendWorkToReports -> {
                    val entry = entries.getOrNull(action.itemIndex)
                    if (entry != null) {
                        entry.toReportsSnapshot(action.itemIndex)?.let { ReportsSelectedWorksStore.upsert(it) }
                        exportedIndices[action.itemIndex] = true
                    }
                }
                BatchResultsAction.SendAllToReports -> {
                    val snapshots = entries.mapIndexedNotNull { index, entry -> entry.toReportsSnapshot(index) }
                    ReportsSelectedWorksStore.upsertAll(snapshots)
                    entries.indices.forEach { index ->
                        if (entries[index].toReportsSnapshot(index) != null) exportedIndices[index] = true
                    }
                }
                is BatchResultsAction.MarkFixedAsAnswered -> fixedDecisions[action.key] = true
                is BatchResultsAction.MarkFixedAsEmpty -> fixedDecisions[action.key] = false
            }
        }
    )
}

private fun buildSummary(entries: List<BatchResultEntryState>): BatchResultsSummary {
    val total = entries.size
    fun countGrade(g: Int): Int = entries.count { it.grade == g }
    return BatchResultsSummary(
        totalWorks = total,
        countGrade5 = countGrade(5),
        countGrade4 = countGrade(4),
        countGrade3 = countGrade(3),
        countGrade2 = countGrade(2),
    )
}

private fun workCardSubtitle(cardIndexOneBased: Int, itemTitle: String): String? {
    val t = itemTitle.trim()
    if (t.isEmpty()) return null
    val cardTitle = "Работа $cardIndexOneBased"
    if (normalizeWorkLabel(cardTitle) == normalizeWorkLabel(t)) return null
    return t
}

private fun normalizeWorkLabel(s: String): String =
    s.lowercase(Locale.getDefault())
        .replace("\\s+".toRegex(), "")
        .replace("№", "")
        .replace("#", "")

private fun buildResultEntries(
    runItems: List<BatchRunItemResult>,
    decisions: Map<String, Boolean>,
    gradingCriteria: GradingCriteriaConfig,
): List<BatchResultEntryState> = runItems.mapIndexed { itemIndex, item ->
    val error = item.error
    if (error != null) {
        return@mapIndexed BatchResultEntryState(
            title = "Работа ${itemIndex + 1}",
            subtitle = workCardSubtitle(itemIndex + 1, item.title),
            status = "Ошибка",
            gradeLine = "Оценка: -",
            scoreLine = error,
            layoutLine = item.layoutLabel,
            details = emptyList(),
            previewBitmap = item.omr?.sheetCropBitmap,
            grade = null,
            percent = null,
            scoreCorrect = null,
            scoreTotal = null,
        )
    }
    val omr = item.omr
    if (omr == null || !omr.contourFound) {
        return@mapIndexed BatchResultEntryState(
            title = "Работа ${itemIndex + 1}",
            subtitle = workCardSubtitle(itemIndex + 1, item.title),
            status = "Контур бланка не найден",
            gradeLine = "Оценка: -",
            layoutLine = item.layoutLabel,
            details = emptyList(),
            previewBitmap = null,
            grade = null,
            percent = null,
            scoreCorrect = null,
            scoreTotal = null,
        )
    }
    val adjustedPredictions = omr.predictions.map { pred ->
        if (pred.klass != BatchCellClass.Fixed) {
            pred
        } else {
            val include = decisions[fixedCellKey(itemIndex, pred)]
            when (include) {
                true -> pred.copy(klass = BatchCellClass.Yes)
                false -> pred.copy(klass = BatchCellClass.No)
                null -> pred
            }
        }
    }
    val scores = BatchScoringEngine.score(adjustedPredictions, item.config)
    val correct = scores.count { it.correct }
    val percent = if (scores.isEmpty()) 0f else (scores.sumOf { it.score.toDouble() }.toFloat() / scores.size) * 100f
    val pointsSum = scores.sumOf { it.score.toDouble() }
    val unresolved = omr.fixedCells.count { decisions[fixedCellKey(itemIndex, it)] == null }
    val (detailRows, questionWrongTotal) = buildQuestionDetailRows(
        scores = scores,
        answerKey = item.config.answerKey,
    )
    val details = buildQuestionDetailsLines(detailRows, questionWrongTotal)
    val grade = gradingCriteria.resolveGrade(percent, pointsSum)
    val hasMultiChoiceQuestions = item.config.answerKey.any { it.size > 1 }
    val partialCompletedCount = if (!item.config.strictScoring && hasMultiChoiceQuestions) {
        scores.count { s -> s.score > 0f && s.score < 1f }
    } else {
        0
    }
    BatchResultEntryState(
        title = "Работа ${itemIndex + 1}",
        subtitle = workCardSubtitle(itemIndex + 1, item.title),
        status = when {
            unresolved > 0 -> "Обнаружены исправления"
            correct == item.config.questionsCount -> "Ошибок нет"
            else -> "Есть ошибки"
        },
        gradeLine = "Оценка: $grade",
        scoreLine = "$correct/${item.config.questionsCount} · ${"%.1f".format(Locale.US, percent)}%",
        layoutLine = item.layoutLabel,
        details = details,
        previewBitmap = omr.sheetCropBitmap,
        grade = grade,
        percent = percent,
        scoreCorrect = correct,
        scoreTotal = item.config.questionsCount,
        correctionsTotal = omr.fixedCells.size,
        correctionsUnresolved = unresolved,
        detailRows = detailRows,
        questionWrongTotal = questionWrongTotal,
        sheetMarkerPredictions = adjustedPredictions,
        sheetMarkerConfig = item.config,
        reportQuestionScores = scores,
        reportVariant = item.detectedVariant,
        reportStudentSurname = item.detectedStudentSurname,
        reportStudentName = item.detectedStudentName,
        reportJournalClassName = item.detectedJournalClassName,
        reportStrictScoring = item.config.strictScoring,
        reportPartialCompletedCount = partialCompletedCount,
        reportHasMultiChoiceQuestions = hasMultiChoiceQuestions,
    )
}

private fun buildQuestionDetailRows(
    scores: List<com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore>,
    answerKey: List<Set<Int>>,
): Pair<List<BatchResultDetailRow>, Int> {
    val wrong = scores.filter { !it.correct }
    val rows = wrong.map { q ->
        val selected = q.selectedChoices.toSortedSet().joinToString("+") { (it + 1).toString() }.ifBlank { "—" }
        val key = answerKey.getOrNull(q.questionIndex).orEmpty()
            .toSortedSet()
            .joinToString("+") { (it + 1).toString() }
            .ifBlank { "—" }
        BatchResultDetailRow(
            questionNumber = q.questionIndex + 1,
            selectedText = selected,
            expectedText = key,
        )
    }
    return rows to wrong.size
}

private fun buildQuestionDetailsLines(
    rows: List<BatchResultDetailRow>,
    questionWrongTotal: Int,
): List<String> {
    if (questionWrongTotal == 0) return listOf("Ошибок по вопросам нет")
    val head = listOf("Ошибки по вопросам: $questionWrongTotal")
    val lines = rows.map { r ->
        "Вопрос ${r.questionNumber}: выбрано [${r.selectedText}], верно [${r.expectedText}]"
    }
    return head + lines
}

private fun buildFixedUiItems(
    runItems: List<BatchRunItemResult>,
    decisions: Map<String, Boolean>,
    showOnlyUnresolved: Boolean,
): List<BatchFixedCellUiItem> = runItems.flatMapIndexed { itemIndex, item ->
    val omr = item.omr ?: return@flatMapIndexed emptyList()
    omr.fixedCells.mapNotNull { fixed ->
        val key = fixedCellKey(itemIndex, fixed)
        val currentDecision = decisions[key]
        if (showOnlyUnresolved && currentDecision != null) return@mapNotNull null
        BatchFixedCellUiItem(
            key = key,
            itemIndex = itemIndex,
            itemTitle = item.title,
            questionIndex = fixed.questionIndex,
            choiceIndex = fixed.choiceIndex,
            includeAsAnswer = currentDecision,
        )
    }
}

private fun reportSourceKey(itemIndex: Int, entry: BatchResultEntryState): String =
    "${itemIndex}_${entry.title}"

private fun BatchResultEntryState.toReportsSnapshot(itemIndex: Int): ReportsWorkSnapshot? {
    val gradeValue = grade ?: return null
    val percentValue = percent ?: return null
    val correct = scoreCorrect ?: return null
    val total = scoreTotal ?: return null
    if (reportQuestionScores.isEmpty()) return null
    return ReportsWorkSnapshot(
        sourceKey = reportSourceKey(itemIndex, this),
        title = title,
        grade = gradeValue,
        percent = percentValue,
        scoreCorrect = correct,
        scoreTotal = total,
        questionScores = reportQuestionScores,
        variant = reportVariant,
        studentSurname = reportStudentSurname,
        studentName = reportStudentName,
        journalClassName = reportJournalClassName,
        strictScoring = reportStrictScoring,
        partialCompletedCount = reportPartialCompletedCount,
        hasMultiChoiceQuestions = reportHasMultiChoiceQuestions,
    )
}

