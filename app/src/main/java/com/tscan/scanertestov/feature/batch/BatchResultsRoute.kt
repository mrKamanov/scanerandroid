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
    val run = remember { BatchLastRunStore.current }
    // Критерии теста, с которыми проверяли; если у теста своих нет — общие из Настроек.
    val gradingCriteria = remember(criteriaReload, context, run?.runId) {
        run?.gradingCriteriaJson?.let { GradingCriteriaConfig.fromPrefsString(it) }
            ?: AppSettingsStore.getGradingCriteria(context)
    }
    val runItems = run?.items.orEmpty()
    val runId = run?.runId.orEmpty()
    val exportedIndices = remember { mutableStateMapOf<Int, Boolean>() }
    // Очередь повторов: (номер работы, её снимок для отчёта, уже добавленная работа).
    val dupQueue = remember { androidx.compose.runtime.mutableStateListOf<Triple<Int, ReportsWorkSnapshot, ReportsWorkSnapshot>>() }
    var assignRequest by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    fun sendOrQueue(index: Int, snapshot: ReportsWorkSnapshot) {
        val dup = com.tscan.scanertestov.feature.reports.ReportDuplicates.findFor(snapshot, ReportsSelectedWorksStore.works)
        if (dup == null) {
            ReportsSelectedWorksStore.upsert(snapshot)
            exportedIndices[index] = true
        } else if (dupQueue.none { it.first == index }) {
            dupQueue += Triple(index, snapshot, dup)
        }
    }
    // Решения по спорным ячейкам хранятся вместе с прогоном на диске: не теряются
    // при переходе в «Отчёты» и обратно и при выгрузке приложения.
    val fixedDecisions = remember(runId) {
        mutableStateMapOf<String, Boolean>().apply { putAll(run?.decisions.orEmpty()) }
    }
    // Очевидные исправления (зачеркнул и закрасил другой) решаются сами; решение учителя главнее.
    val autoDecisions = remember(runId) { autoFixedDecisions(runItems) }
    // «Чья работа?»: имена, которые учитель указал вручную, и список учеников из журнала.
    val manualNames = remember(runId) {
        mutableStateMapOf<Int, BatchManualName>().apply { putAll(run?.manualNames.orEmpty()) }
    }
    val roster = remember { com.tscan.scanertestov.feature.journals.JournalRepository(context).loadAll() }
    // Вариант, выбранный учителем для работы, где номер варианта на бланке не прочитался.
    val manualVariants = remember(runId) {
        mutableStateMapOf<Int, Int>().apply { putAll(run?.manualVariants.orEmpty()) }
    }
    val variantKeys = run?.variantKeys.orEmpty()
    val effectiveDecisions: Map<String, Boolean> = autoDecisions + fixedDecisions
    val fixedUiItems = buildFixedUiItems(
        runItems = runItems,
        decisions = fixedDecisions,
        autoDecisions = autoDecisions,
        showOnlyUnresolved = true,
    )
    val entries = if (runItems.isNotEmpty()) {
        buildResultEntries(
            runItems = runItems,
            decisions = effectiveDecisions,
            explicitDecisions = fixedDecisions,
            manualNames = manualNames,
            gradingCriteria = gradingCriteria,
            manualVariants = manualVariants,
            variantKeys = variantKeys,
        )
    } else {
        listOf(
            BatchResultEntryState(
                title = "Пакетная обработка",
                status = "Пока нет сохранённого результата. Запустите обработку на экране «Пакетная обработка».",
            ),
        )
    }
    if (exportedIndices.isEmpty() && runItems.isNotEmpty()) {
        val sentKeys = ReportsSelectedWorksStore.works.mapTo(HashSet()) { it.sourceKey }
        entries.indices.forEach { index ->
            if (reportSourceKey(runId, index) in sentKeys) exportedIndices[index] = true
        }
    }
    com.tscan.scanertestov.ui.components.ScreenTour(
        "batch_results",
        com.tscan.scanertestov.ui.components.ScreenTours.BatchResults,
        enabled = runItems.isNotEmpty(),
    ) {
    BatchResultsScreen(
        state = BatchResultsState(
            resultEntries = entries,
            summary = if (runItems.isNotEmpty()) buildSummary(entries) else null,
            fixedItems = fixedUiItems,
            exportedToReportsIndices = exportedIndices.keys,
            roster = roster,
            duplicatePrompt = dupQueue.firstOrNull()?.let { (index, snap, old) ->
                val e = entries.getOrNull(index)
                BatchDuplicatePrompt(
                    itemIndex = index,
                    reportTitle = snap.testTitle ?: "проверка",
                    studentLabel = old.fullName ?: snap.fullName ?: "без имени",
                    existingTitle = old.title,
                    existingSheetPath = old.sheetPath,
                    existingGrade = old.grade,
                    existingPercent = old.percent,
                    newTitle = snap.title,
                    newSheetPath = e?.sheetImagePath ?: snap.sheetPath,
                    newGrade = snap.grade,
                    newPercent = snap.percent,
                    differingQuestions = com.tscan.scanertestov.feature.reports.ReportDuplicates.differingQuestions(snap, old),
                    sameStudentByName = snap.fullName != null && old.fullName != null,
                    remaining = dupQueue.size - 1,
                )
            },
            assignRequestIndex = assignRequest,
        ),
        onAction = { action ->
            when (action) {
                BatchResultsAction.Back,
                BatchResultsAction.OpenReports -> handleBatchResultsAction(navController, action)
                is BatchResultsAction.SendWorkToReports -> {
                    val entry = entries.getOrNull(action.itemIndex)
                    entry?.toReportsSnapshot(run, action.itemIndex)?.let { sendOrQueue(action.itemIndex, it) }
                }
                BatchResultsAction.AssignRequestHandled -> assignRequest = null
                BatchResultsAction.DuplicateKeepExisting -> {
                    dupQueue.removeFirstOrNull()?.second?.sheetPath?.let { runCatching { java.io.File(it).delete() } }
                }
                BatchResultsAction.DuplicateReplace -> {
                    dupQueue.removeFirstOrNull()?.let { (index, snap, old) ->
                        ReportsSelectedWorksStore.remove(old.sourceKey)
                        if (old.runId == runId) {
                            old.sourceKey.substringAfter('#').toIntOrNull()?.let { exportedIndices.remove(it) }
                        }
                        ReportsSelectedWorksStore.upsert(snap)
                        exportedIndices[index] = true
                    }
                }
                BatchResultsAction.DuplicateOtherStudent -> {
                    dupQueue.removeFirstOrNull()?.let { (index, snap, _) ->
                        snap.sheetPath?.let { runCatching { java.io.File(it).delete() } }
                        assignRequest = index
                    }
                }
                is BatchResultsAction.RemoveWorkFromReports -> {
                    ReportsSelectedWorksStore.remove(reportSourceKey(runId, action.itemIndex))
                    exportedIndices.remove(action.itemIndex)
                }
                BatchResultsAction.SendAllToReports -> {
                    entries.forEachIndexed { index, entry ->
                        if (exportedIndices[index] == true) return@forEachIndexed
                        entry.toReportsSnapshot(run, index)?.let { sendOrQueue(index, it) }
                    }
                }
                is BatchResultsAction.MarkFixedAsAnswered -> {
                    fixedDecisions[action.key] = true
                    BatchLastRunStore.setDecision(context.applicationContext, action.key, true)
                }
                is BatchResultsAction.AssignStudent -> {
                    val value = BatchManualName(action.surname.trim(), action.name.trim(), action.className)
                    manualNames[action.itemIndex] = value
                    BatchLastRunStore.setManualName(context.applicationContext, action.itemIndex, value)
                    // Если работа уже в отчёте — обновляем там имя.
                    if (exportedIndices[action.itemIndex] == true) {
                        val e = buildResultEntries(runItems, effectiveDecisions, fixedDecisions, manualNames, gradingCriteria, manualVariants, variantKeys)
                        e.getOrNull(action.itemIndex)?.toReportsSnapshot(run, action.itemIndex)?.let { ReportsSelectedWorksStore.upsert(it) }
                    }
                }
                is BatchResultsAction.SetVariant -> {
                    manualVariants[action.itemIndex] = action.variant
                    BatchLastRunStore.setManualVariant(context.applicationContext, action.itemIndex, action.variant)
                    // Если работа уже в отчёте — там обновляются вариант, баллы и оценка.
                    if (exportedIndices[action.itemIndex] == true) {
                        val e = buildResultEntries(runItems, effectiveDecisions, fixedDecisions, manualNames, gradingCriteria, manualVariants, variantKeys)
                        e.getOrNull(action.itemIndex)?.toReportsSnapshot(run, action.itemIndex)?.let { ReportsSelectedWorksStore.upsert(it) }
                    }
                }
                is BatchResultsAction.MarkFixedAsEmpty -> {
                    fixedDecisions[action.key] = false
                    BatchLastRunStore.setDecision(context.applicationContext, action.key, false)
                }
            }
        }
    )
    }
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
    explicitDecisions: Map<String, Boolean>,
    manualNames: Map<Int, BatchManualName>,
    gradingCriteria: GradingCriteriaConfig,
    manualVariants: Map<Int, Int> = emptyMap(),
    variantKeys: Map<Int, List<Set<Int>>> = emptyMap(),
): List<BatchResultEntryState> = runItems.mapIndexed { itemIndex, runItem ->
    val manual = manualNames[itemIndex]
    // Вариант выбран учителем — проверяем по ключу этого варианта (кружки уже распознаны, переснимать не нужно).
    val chosenVariant = manualVariants[itemIndex]?.takeIf { it in variantKeys }
    val item = if (chosenVariant != null) {
        runItem.copy(config = runItem.config.copy(answerKey = variantKeys.getValue(chosenVariant)), detectedVariant = chosenVariant)
    } else runItem
    val variantUnread = runItem.variantUnread && chosenVariant == null
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
            previewBitmap = item.thumbnail,
            sheetImagePath = item.sheetImagePath,
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
    val autoResolved = omr.fixedCells.count {
        val k = fixedCellKey(itemIndex, it)
        explicitDecisions[k] == null && decisions[k] != null
    }
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
        previewBitmap = item.thumbnail,
        sheetImagePath = item.sheetImagePath,
        grade = grade,
        percent = percent,
        scoreCorrect = correct,
        scoreTotal = item.config.questionsCount,
        correctionsTotal = omr.fixedCells.size,
        correctionsUnresolved = unresolved,
        correctionsAuto = autoResolved,
        detailRows = detailRows,
        questionWrongTotal = questionWrongTotal,
        sheetMarkerPredictions = adjustedPredictions,
        sheetMarkerConfig = item.config,
        sheetMarkerColumnFrames = omr.columnFrames,
        reportQuestionScores = scores,
        reportVariant = item.detectedVariant,
        reportStudentSurname = manual?.surname?.ifBlank { null } ?: item.detectedStudentSurname,
        reportStudentName = manual?.name?.ifBlank { null } ?: item.detectedStudentName,
        reportJournalClassName = manual?.className ?: item.detectedJournalClassName,
        nameCropPath = item.nameCropPath,
        nameAssignedManually = manual != null,
        studentNote = if (manual != null) null else item.studentNote,
        warning = if (manual != null) item.warning?.takeIf { it.startsWith("Выдан") } else item.warning,
        variantUnread = variantUnread,
        variantManual = chosenVariant != null,
        variantChoices = if (runItem.variantUnread) variantKeys.size else 0,
        reportStrictScoring = item.config.strictScoring,
        reportPartialCompletedCount = partialCompletedCount,
        reportHasMultiChoiceQuestions = hasMultiChoiceQuestions,
        reportAnswerKey = item.config.answerKey,
        reportChoicesCount = item.config.choicesCount,
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

/**
 * Ячейки для блока «Исправления». Решённые учителем скрываются; решённые автоматически показываются
 * с пометкой ✎, чтобы учитель видел их и мог изменить одним нажатием.
 */
private fun buildFixedUiItems(
    runItems: List<BatchRunItemResult>,
    decisions: Map<String, Boolean>,
    autoDecisions: Map<String, Boolean>,
    showOnlyUnresolved: Boolean,
): List<BatchFixedCellUiItem> = runItems.flatMapIndexed { itemIndex, item ->
    val omr = item.omr ?: return@flatMapIndexed emptyList()
    omr.fixedCells.mapNotNull { fixed ->
        val key = fixedCellKey(itemIndex, fixed)
        val currentDecision = decisions[key]
        if (showOnlyUnresolved && currentDecision != null) return@mapNotNull null
        val auto = currentDecision == null && autoDecisions[key] != null
        BatchFixedCellUiItem(
            key = key,
            itemIndex = itemIndex,
            itemTitle = item.title,
            questionIndex = fixed.questionIndex,
            choiceIndex = fixed.choiceIndex,
            includeAsAnswer = currentDecision ?: autoDecisions[key],
            autoDecided = auto,
        )
    }
}

/** Ключ уникален в пределах прогона: работы разных прогонов не перезаписывают друг друга в отчётах. */
private fun reportSourceKey(runId: String, itemIndex: Int): String = "${runId}#${itemIndex}"

private fun BatchResultEntryState.toReportsSnapshot(run: BatchRun?, itemIndex: Int): ReportsWorkSnapshot? {
    val runId = run?.runId.orEmpty()
    val gradeValue = grade ?: return null
    val percentValue = percent ?: return null
    val correct = scoreCorrect ?: return null
    val total = scoreTotal ?: return null
    if (reportQuestionScores.isEmpty()) return null
    val key = reportSourceKey(runId, itemIndex)
    return ReportsWorkSnapshot(
        sourceKey = key,
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
        testId = run?.testId,
        testTitle = run?.testTitle,
        runId = runId,
        checkedAtMs = run?.createdAtMs ?: System.currentTimeMillis(),
        answerKey = reportAnswerKey,
        choicesCount = reportChoicesCount,
        sheetPath = ReportsSelectedWorksStore.copySheet(sheetImagePath, key),
    )
}

