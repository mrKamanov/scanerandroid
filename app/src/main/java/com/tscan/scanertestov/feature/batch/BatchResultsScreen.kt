package com.tscan.scanertestov.feature.batch

/**
 * Описание: экран итогов пакетной обработки с переходом в отчеты.
 */
import com.tscan.scanertestov.ui.components.stickerShadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Image
import com.tscan.scanertestov.ui.theme.Scrapbook
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tscan.scanertestov.feature.batch.engine.BatchSheetAnswerMarkersOverlay
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import java.util.Locale
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import android.graphics.Bitmap

private fun BatchResultEntryState.canExportToReports(): Boolean =
    grade != null && percent != null && scoreCorrect != null && scoreTotal != null &&
        reportQuestionScores.isNotEmpty()

private val EntryCardShape = RoundedCornerShape(12.dp)
private val FrostedEntryBg = Scrapbook.Ink.copy(alpha = 0.08f)
private val FrostedEntryBorder = Scrapbook.Ink.copy(alpha = 0.16f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResultsScreen(
    state: BatchResultsState = BatchResultsState(),
    onAction: (BatchResultsAction) -> Unit
) {
    ScreenScaffold(onBack = { onAction(BatchResultsAction.Back) }, title = "Результаты") { padding ->
        var detailsIndex by remember { mutableStateOf<Int?>(null) }
        var assignIndex by remember { mutableStateOf<Int?>(null) }
        val exportableIndices = remember(state.resultEntries) {
            state.resultEntries.mapIndexedNotNull { idx, e ->
                if (e.canExportToReports()) idx else null
            }.toSet()
        }
        val allExportableInReports = remember(exportableIndices, state.exportedToReportsIndices) {
            exportableIndices.isNotEmpty() &&
                exportableIndices.all { it in state.exportedToReportsIndices }
        }
        val sendAllLabel = when {
            exportableIndices.isEmpty() -> "Нет работ для отчёта"
            allExportableInReports -> "Все работы уже в отчёте"
            else -> state.sendAllToReportsButtonText
        }

        LaunchedEffect(state.assignRequestIndex) {
            state.assignRequestIndex?.let {
                assignIndex = it
                onAction(BatchResultsAction.AssignRequestHandled)
            }
        }
        state.duplicatePrompt?.let { p -> DuplicateWorkDialog(p, onAction) }

        assignIndex?.let { idx ->
            val entry = state.resultEntries.getOrNull(idx)
            if (entry != null) {
                val taken = state.resultEntries.mapIndexedNotNull { i, e ->
                    if (i == idx) null else listOfNotNull(e.reportStudentSurname, e.reportStudentName).joinToString(" ").trim().ifBlank { null }
                }.toSet()
                StudentPickerDialog(
                    workTitle = entry.title,
                    nameCropPath = entry.nameCropPath,
                    roster = state.roster,
                    preferredClass = entry.reportJournalClassName,
                    alreadyAssigned = taken,
                    onPick = { surname, name, cls ->
                        onAction(BatchResultsAction.AssignStudent(idx, surname, name, cls))
                        assignIndex = null
                    },
                    onDismiss = { assignIndex = null },
                )
            }
        }

        ScreenContentColumn(padding = padding, verticalScroll = true) {
            state.summary?.let { BatchResultsSummaryBlock(it, notRecognized = state.resultEntries.count { e -> e.grade == null }) }

            // Каждая работа — отдельный листок: фамилия, результат и оценка «красной ручкой».
            // Нажатие на листок открывает разбор; работы, где нужна проверка учителя, помечены.
            state.resultEntries.forEachIndexed { index, entry ->
                key(index) {
                    ResultWorkCard(
                        index = index,
                        entry = entry,
                        canExport = entry.canExportToReports(),
                        sentToReports = index in state.exportedToReportsIndices,
                        sendText = state.sendToReportsButtonText,
                        sentText = state.inReportsButtonText,
                        onOpen = { detailsIndex = index },
                        onSend = { onAction(BatchResultsAction.SendWorkToReports(index)) },
                        onAssign = { assignIndex = index },
                        onUnsend = { onAction(BatchResultsAction.RemoveWorkFromReports(index)) },
                    )
                }
            }

            ShellBubblePrimaryButton(
                text = sendAllLabel,
                onClick = { onAction(BatchResultsAction.SendAllToReports) },
                enabled = exportableIndices.isNotEmpty() && !allExportableInReports,
                paletteIndex = if (allExportableInReports || exportableIndices.isEmpty()) 4 else 1,
            )

            ShellBubblePrimaryButton(
                text = state.toReportsButtonText,
                onClick = { onAction(BatchResultsAction.OpenReports) },
                paletteIndex = 3,
            )
        }

        val selected = detailsIndex?.let { idx -> state.resultEntries.getOrNull(idx) }
        val fixedForSelected = detailsIndex?.let { idx -> state.fixedItems.filter { it.itemIndex == idx } }.orEmpty()
        var showMarkers by remember(detailsIndex) { mutableStateOf(true) }
        if (selected != null) {
            val dialogScroll = rememberScrollState()
            val scrimInteraction = remember { MutableInteractionSource() }
            val cardInteraction = remember(detailsIndex) { MutableInteractionSource() }
            val closeInteraction = remember(detailsIndex) { MutableInteractionSource() }
            val markersInteraction = remember(detailsIndex, showMarkers) { MutableInteractionSource() }
            Dialog(
                onDismissRequest = { detailsIndex = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .clickable(
                            indication = null,
                            interactionSource = scrimInteraction,
                        ) { detailsIndex = null },
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsShellCard(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.88f)
                            .clickable(
                                indication = null,
                                interactionSource = cardInteraction,
                            ) { },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val total = state.resultEntries.size
                                val cur = detailsIndex ?: 0
                                Text(
                                    "Работа ${cur + 1} из $total",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                val closePal = mainMenuBubbleGradient(5)
                                RealtimeBubbleIconButton(
                                    onClick = { detailsIndex = null },
                                    contentDescription = "Закрыть",
                                    topColor = closePal.first,
                                    bottomColor = closePal.second,
                                    size = 48.dp,
                                    interactionSource = closeInteraction,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = Scrapbook.Ink,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Scrapbook.Ink.copy(alpha = 0.14f),
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(dialogScroll),
                            ) {
                                LaunchedEffect(detailsIndex) { dialogScroll.scrollTo(0) }
                                val selName = listOfNotNull(selected.reportStudentSurname, selected.reportStudentName).joinToString(" ").trim()
                                Text(selName.ifEmpty { selected.title }, style = MaterialTheme.typography.titleMedium)
                                if (selName.isNotEmpty()) {
                                    Text(selected.title, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                                }
                                StatusBadge(selected.status)
                                selected.studentNote?.let { StudentNoteTag(it) }
                                selected.warning?.let { WarningNote(it) }
                                if (selected.variantChoices > 1) {
                                    val vIdx = detailsIndex ?: 0
                                    VariantPickBlock(selected) { v -> onAction(BatchResultsAction.SetVariant(vIdx, v)) }
                                }
                                if (selected.grade != null) {
                                    if (selected.nameCropPath != null) {
                                        NameCropImage(selected.nameCropPath, Modifier.fillMaxWidth().height(40.dp))
                                    }
                                    val idx = detailsIndex ?: 0
                                    val inReport = idx in state.exportedToReportsIndices
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DialogChip(
                                            text = if (selName.isEmpty()) "Чья работа?" else "✎ выбрать ученика",
                                            background = if (selName.isEmpty()) Scrapbook.Lavender else Color.Transparent,
                                            onClick = { assignIndex = idx },
                                        )
                                        if (selected.canExportToReports()) {
                                            DialogChip(
                                                text = if (inReport) "В отчёте ✓ · убрать" else state.sendToReportsButtonText,
                                                background = if (inReport) Color.Transparent else Scrapbook.Mint,
                                                onClick = {
                                                    onAction(
                                                        if (inReport) BatchResultsAction.RemoveWorkFromReports(idx)
                                                        else BatchResultsAction.SendWorkToReports(idx),
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }

                                SettingsShellCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Статистика", style = MaterialTheme.typography.titleSmall)
                                        EntryGradeScoreBlock(selected)
                                        CorrectionsSummaryLine(selected)
                                        selected.layoutLine?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }

                                SettingsShellCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("Просмотр бланка", style = MaterialTheme.typography.titleSmall)
                                            val markPal = mainMenuBubbleGradient(4)
                                            RealtimeBubbleIconButton(
                                                onClick = { showMarkers = !showMarkers },
                                                contentDescription = if (showMarkers) {
                                                    "Скрыть маркеры"
                                                } else {
                                                    "Показать маркеры"
                                                },
                                                topColor = markPal.first,
                                                bottomColor = markPal.second,
                                                size = 48.dp,
                                                interactionSource = markersInteraction,
                                            ) {
                                                Icon(
                                                    imageVector = if (showMarkers) {
                                                        Icons.Filled.VisibilityOff
                                                    } else {
                                                        Icons.Filled.Visibility
                                                    },
                                                    contentDescription = null,
                                                    tint = Scrapbook.Ink,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                        // Полный кадр бланка читается с диска в фоне только для открытой работы.
                                        val fullSheet by produceState<Bitmap?>(null, selected.sheetImagePath) {
                                            value = withContext(Dispatchers.IO) {
                                                BatchLastRunStore.loadSheetBitmap(selected.sheetImagePath)
                                            }
                                        }
                                        val sheetWithMarkers = remember(
                                            showMarkers,
                                            fullSheet,
                                            selected.sheetMarkerConfig,
                                            selected.sheetMarkerPredictions,
                                            selected.sheetMarkerColumnFrames,
                                        ) {
                                            val base = fullSheet ?: return@remember null
                                            val cfg = selected.sheetMarkerConfig
                                            val preds = selected.sheetMarkerPredictions
                                            if (!showMarkers || cfg == null || preds.isEmpty()) {
                                                base
                                            } else {
                                                BatchSheetAnswerMarkersOverlay.renderMarkersOnCrop(
                                                    base,
                                                    cfg,
                                                    preds,
                                                    selected.sheetMarkerColumnFrames,
                                                )
                                            }
                                        }
                                        sheetWithMarkers?.let { bmp ->
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Бланк",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } ?: Text(
                                            if (selected.sheetImagePath != null) "Загрузка бланка…" else "Изображение недоступно",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }

                                SettingsShellCard {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Детали ошибок", style = MaterialTheme.typography.titleSmall)
                                        ErrorDetailsBlock(selected)
                                    }
                                }

                                if (fixedForSelected.isNotEmpty()) {
                                    SettingsShellCard {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Исправления", style = MaterialTheme.typography.titleSmall)
                                            WorkFixedActionsBlock(
                                                fixedForEntry = fixedForSelected,
                                                onAction = onAction,
                                                showDetailIntro = true,
                                            )
                                        }
                                    }
                                }
                            }
                            // Листание без закрытия окна: предыдущая / следующая работа.
                            val cur = detailsIndex ?: 0
                            val last = state.resultEntries.lastIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ShellBubblePrimaryButton(
                                    text = "‹ Предыдущая",
                                    onClick = { detailsIndex = (cur - 1).coerceAtLeast(0) },
                                    enabled = cur > 0,
                                    paletteIndex = 4,
                                    modifier = Modifier.weight(1f),
                                )
                                ShellBubblePrimaryButton(
                                    text = "Следующая ›",
                                    onClick = { detailsIndex = (cur + 1).coerceAtMost(last) },
                                    enabled = cur < last,
                                    paletteIndex = 1,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchResultsSummaryBlock(summary: BatchResultsSummary, notRecognized: Int = 0) {
    // Сводка как записка учителя: сколько работ и сколько каких оценок.
    SettingsShellCard(paper = Scrapbook.Lemon) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Проверено работ: ${summary.totalWorks}",
                style = MaterialTheme.typography.headlineSmall,
                color = Scrapbook.Ink,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryGradeCell(5, summary.countGrade5, Modifier.weight(1f))
                SummaryGradeCell(4, summary.countGrade4, Modifier.weight(1f))
                SummaryGradeCell(3, summary.countGrade3, Modifier.weight(1f))
                SummaryGradeCell(2, summary.countGrade2, Modifier.weight(1f))
            }
            if (notRecognized > 0) {
                Text(
                    text = "Не узнано бланков: $notRecognized — переснимите их ровнее, чтобы сетка была видна целиком",
                    style = MaterialTheme.typography.bodySmall,
                    color = Scrapbook.AccentInk,
                )
            }
        }
    }
}

@Composable
private fun SummaryGradeCell(grade: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = grade.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = gradeTextColor(grade).copy(alpha = if (count > 0) 1f else 0.4f),
        )
        Text(
            text = " × $count",
            style = MaterialTheme.typography.titleMedium,
            color = Scrapbook.Ink.copy(alpha = if (count > 0) 1f else 0.4f),
        )
    }
}

@Composable
private fun EntryGradeScoreBlock(entry: BatchResultEntryState) {
    val grade = entry.grade
    val pct = entry.percent
    val correct = entry.scoreCorrect
    val total = entry.scoreTotal
    if (grade != null && pct != null && correct != null && total != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Оценка: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = grade.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = gradeTextColor(grade),
            )
            Text(
                text = " · $correct/$total · ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${"%.1f".format(Locale.US, pct)}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = percentTextColor(pct),
            )
        }
    } else {
        entry.gradeLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        entry.scoreLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun CorrectionsSummaryLine(entry: BatchResultEntryState) {
    if (entry.correctionsTotal <= 0) return
    Text(
        text = if (entry.correctionsAuto > 0) {
            "Исправления: ${entry.correctionsTotal} · ✎ решено автоматически: ${entry.correctionsAuto}"
        } else {
            "Исправления: ${entry.correctionsTotal}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun normalizeWorkCardSubtitle(s: String): String =
    s.lowercase(Locale.getDefault())
        .replace("\\s+".toRegex(), "")
        .replace("№", "")
        .replace("#", "")

@Composable
private fun WorkFixedActionsBlock(
    fixedForEntry: List<BatchFixedCellUiItem>,
    onAction: (BatchResultsAction) -> Unit,
    showDetailIntro: Boolean = false,
) {
    if (fixedForEntry.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDetailIntro) {
            Text(
                "Как учесть исправления на бланке?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Модель пометила ячейку как исправление. Укажите, засчитывать ли ответ как отмеченный или считать ячейку пустой — от этого изменится оценка по работе.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (fixed in fixedForEntry) {
            key(fixed.key) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Scrapbook.Ink.copy(alpha = 0.08f),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Вопрос ${fixed.questionIndex + 1}, вариант ${fixed.choiceIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (fixed.autoDecided) {
                        Text(
                            "✎ Решено автоматически: «Пусто» — кружок зачёркнут, а в этом вопросе закрашен другой. " +
                                "Если это не так, выберите сами.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Как засчитать эту отметку?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val answeredPal = mainMenuBubbleGradient(1)
                        val emptyPal = mainMenuBubbleGradient(5)
                        val answeredIx = remember(fixed.key, "a") { MutableInteractionSource() }
                        val emptyIx = remember(fixed.key, "e") { MutableInteractionSource() }
                        RealtimeBubbleIconButton(
                            onClick = { onAction(BatchResultsAction.MarkFixedAsAnswered(fixed.key)) },
                            contentDescription = "Считать отмеченным",
                            topColor = answeredPal.first,
                            bottomColor = answeredPal.second,
                            size = 52.dp,
                            interactionSource = answeredIx,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Scrapbook.Ink,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        RealtimeBubbleIconButton(
                            onClick = { onAction(BatchResultsAction.MarkFixedAsEmpty(fixed.key)) },
                            contentDescription = "Считать пустым",
                            topColor = emptyPal.first,
                            bottomColor = emptyPal.second,
                            size = 52.dp,
                            interactionSource = emptyIx,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = Scrapbook.Ink,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ErrorDetailsBlock(entry: BatchResultEntryState) {
    if (entry.questionWrongTotal == 0) {
        Surface(shape = RoundedCornerShape(10.dp), color = Scrapbook.Ink.copy(alpha = 0.10f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    "По вопросам ошибок нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        return
    }

    Text(
        "Ошибок по вопросам: ${entry.questionWrongTotal}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    entry.detailRows.forEach { row -> QuestionErrorRow(row) }
}

@Composable
private fun QuestionErrorRow(row: BatchResultDetailRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Scrapbook.Ink.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Вопрос ${row.questionNumber}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = Scrapbook.Ink.copy(alpha = 0.12f)) {
                Text(
                    "Выбрано: ${row.selectedText}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Scrapbook.Ink.copy(alpha = 0.12f)) {
                Text(
                    "Верно: ${row.expectedText}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg) = statusColors()
    Text(
        text = status,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatusIcon(status: String) {
    val image = when {
        status.contains("ошибок нет", ignoreCase = true) -> Icons.Filled.CheckCircle
        status.contains("исправления", ignoreCase = true) -> Icons.Filled.Warning
        else -> Icons.Filled.Error
    }
    androidx.compose.material3.Icon(
        imageVector = image,
        contentDescription = null,
        tint = statusAccentColor(status),
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun statusColors(): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return cs.surfaceVariant to cs.onSurfaceVariant
}

@Composable
private fun statusAccentColor(status: String): Color {
    val cs = MaterialTheme.colorScheme
    return when {
        status.contains("ошибок нет", ignoreCase = true) -> cs.secondary
        status.contains("исправления", ignoreCase = true) -> cs.primary
        else -> cs.onSurfaceVariant
    }
}

private fun gradeTextColor(grade: Int): Color = when (grade) {
    5 -> Color(0xFF2E8B57)
    4 -> Color(0xFF2F6DB5)
    3 -> Color(0xFFC77A12)
    2 -> Color(0xFFC0392B)
    else -> Color(0xFFC0392B)
}

private fun percentTextColor(percent: Float): Color = when {
    percent < 50f -> Color(0xFFC0392B)
    percent < 70f -> Color(0xFFC77A12)
    percent < 90f -> Color(0xFF2F6DB5)
    else -> Color(0xFF2E8B57)
}

@Composable
private fun ResultWorkCard(
    index: Int,
    entry: BatchResultEntryState,
    canExport: Boolean,
    sentToReports: Boolean,
    sendText: String,
    sentText: String,
    onOpen: () -> Unit,
    onSend: () -> Unit,
    onAssign: () -> Unit = {},
    onUnsend: () -> Unit = {},
) {
    val studentName = listOfNotNull(entry.reportStudentSurname, entry.reportStudentName)
        .joinToString(" ").trim()
    val failed = entry.grade == null
    val needsReview = entry.correctionsUnresolved > 0
    val meta = buildString {
        if (studentName.isNotEmpty()) append(entry.title)
        entry.reportVariant?.let {
            if (isNotEmpty()) append(" · ")
            append("вариант ").append(it)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(if (index % 2 == 0) -0.4f else 0.35f)
            .stickerShadow(offset = 3.dp, cornerRadius = 3.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (failed) Scrapbook.Rose.copy(alpha = 0.35f).compositeOver(Scrapbook.Paper) else Scrapbook.Paper)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = entry.previewBitmap
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 50.dp, height = 78.dp)
                    .border(1.dp, Scrapbook.Ink.copy(alpha = 0.25f)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 78.dp)
                    .border(1.dp, Scrapbook.Ink.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", style = MaterialTheme.typography.headlineSmall, color = Scrapbook.InkMuted)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = studentName.ifEmpty { entry.title },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted, maxLines = 1)
            }
            entry.studentNote?.let { StudentNoteTag(it) }
            entry.warning?.let { WarningNote(it) }
            if (entry.variantUnread) {
                WarningNote("Вариант не прочитан — проверено по ключу варианта 1. Откройте работу и выберите вариант.")
            } else if (entry.variantManual) {
                StudentNoteTag("Вариант ${entry.reportVariant} — выбран вручную")
            }
            // Подпись с бланка показываем всегда: распознавание почерка может ошибиться,
            // учитель сверяет глазами и при необходимости выбирает ученика из журнала.
            // Ученик по коду с именного бланка — без сомнений, кнопку «не тот?» не показываем.
            if (!failed && entry.studentNote == null && (studentName.isEmpty() || entry.nameAssignedManually || entry.nameCropPath != null)) {
                if (entry.nameCropPath != null) {
                    NameCropImage(entry.nameCropPath, Modifier.fillMaxWidth().height(34.dp))
                }
                Text(
                    text = if (studentName.isEmpty()) "Чья работа?" else "✎ не тот? выбрать ученика",
                    style = MaterialTheme.typography.labelMedium,
                    color = Scrapbook.Ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (studentName.isEmpty()) Scrapbook.Lavender else Color.Transparent)
                        .border(1.5.dp, Scrapbook.Ink.copy(alpha = if (studentName.isEmpty()) 1f else 0.3f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onAssign)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (failed) {
                Text(
                    text = entry.scoreLine?.takeIf { it.isNotBlank() } ?: "Бланк не узнан — переснимите",
                    style = MaterialTheme.typography.bodySmall,
                    color = Scrapbook.AccentInk,
                    maxLines = 2,
                )
            } else {
                Text(
                    text = "Верно ${entry.scoreCorrect ?: 0} из ${entry.scoreTotal ?: 0} · ${"%.0f".format(entry.percent ?: 0f)}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (needsReview) {
                    Text(
                        text = "Проверьте: ${entry.correctionsUnresolved}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Scrapbook.Ink,
                        modifier = Modifier
                            .background(Scrapbook.Lemon, RoundedCornerShape(6.dp))
                            .border(1.5.dp, Scrapbook.Ink, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (canExport) {
                    Text(
                        text = if (sentToReports) "$sentText ✓ · убрать" else sendText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sentToReports) Scrapbook.InkMuted else Scrapbook.Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sentToReports) Color.Transparent else Scrapbook.Mint)
                            .border(1.5.dp, Scrapbook.Ink.copy(alpha = if (sentToReports) 0.3f else 1f), RoundedCornerShape(6.dp))
                            .clickable(onClick = if (sentToReports) onUnsend else onSend)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        GradeMark(entry.grade)
    }
}

/** Оценка как пометка учителя: крупная рукописная цифра в кружке. */
@Composable
private fun GradeMark(grade: Int?) {
    val color = if (grade != null) gradeTextColor(grade) else Scrapbook.InkMuted
    Box(
        modifier = Modifier
            .size(60.dp)
            .rotate(-6f)
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = grade?.toString() ?: "—",
            style = MaterialTheme.typography.displaySmall,
            color = color,
        )
    }
}

@Composable
private fun DialogChip(text: String, background: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Scrapbook.Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.5.dp, Scrapbook.Ink, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** «Такая работа уже есть»: два бланка рядом и выбор, какую работу оставить в отчёте. */
@Composable
private fun DuplicateWorkDialog(p: BatchDuplicatePrompt, onAction: (BatchResultsAction) -> Unit) {
    Dialog(
        onDismissRequest = { onAction(BatchResultsAction.DuplicateKeepExisting) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SettingsShellCard(modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text("Такая работа уже есть", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append("В отчёте «").append(p.reportTitle).append("» уже есть ")
                        if (p.sameStudentByName) append("работа ученика ").append(p.studentLabel).append(". ")
                        else append("такой же бланк. ")
                        if (p.differingQuestions.isEmpty()) append("Ответы совпадают во всех вопросах — похоже, бланк сфотографирован ещё раз.")
                        else append("Ответы различаются в вопросах: ").append(p.differingQuestions.joinToString(", ")).append('.')
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DuplicateSide("В отчёте", p.existingTitle, p.existingSheetPath, p.existingGrade, p.existingPercent, Modifier.weight(1f))
                    DuplicateSide("Новая", p.newTitle, p.newSheetPath, p.newGrade, p.newPercent, Modifier.weight(1f))
                }
                ShellBubblePrimaryButton(text = "Оставить прежнюю", onClick = { onAction(BatchResultsAction.DuplicateKeepExisting) }, paletteIndex = 1)
                ShellBubblePrimaryButton(text = "Заменить новой", onClick = { onAction(BatchResultsAction.DuplicateReplace) }, paletteIndex = 3)
                ShellBubblePrimaryButton(text = "Это другой ученик — выбрать", onClick = { onAction(BatchResultsAction.DuplicateOtherStudent) }, paletteIndex = 4)
                if (p.remaining > 0) {
                    Text("Ещё повторов: ${p.remaining}", style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun DuplicateSide(label: String, title: String, sheetPath: String?, grade: Int, percent: Float, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, sheetPath) {
        value = withContext(Dispatchers.IO) { BatchLastRunStore.loadSheetBitmap(sheetPath) }
    }
    Column(
        modifier = modifier
            .border(1.5.dp, Scrapbook.Ink, RoundedCornerShape(10.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Scrapbook.InkMuted)
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), contentDescription = label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text(title, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
            }
        }
        Text("«$grade» · ${"%.0f".format(percent)}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** «7 И · по коду №2» — ученик подставлен по коду с именного бланка. */
@Composable
private fun StudentNoteTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF085041),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFDDF3E9))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Что не сходится: вариант не тот, что выдан ученику, или код не выдавался по тесту. */
@Composable
private fun WarningNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Scrapbook.Ink,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Scrapbook.Rose.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

/**
 * Номер варианта на бланке не прочитан: показать, по какому ключу проверено, и дать выбрать вариант.
 * После выбора работа сразу пересчитывается по ключу выбранного варианта.
 */
@Composable
private fun VariantPickBlock(entry: BatchResultEntryState, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (entry.variantUnread) {
            WarningNote(
                "Номер варианта на бланке не прочитан: ученик не закрасил кружок варианта или закрасил несколько. " +
                    "Пока работа проверена по ключу варианта 1 — оценка может быть неверной. Посмотрите на бланк " +
                    "и выберите вариант, работа сразу перепроверится.",
            )
        } else {
            Text(
                "Вариант ${entry.reportVariant} выбран вручную — работа проверена по его ключу. Ошиблись — выберите другой.",
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Вариант:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            (1..entry.variantChoices).forEach { v ->
                val chosen = entry.variantManual && entry.reportVariant == v
                DialogChip(
                    text = if (chosen) "$v ✓" else "$v",
                    background = if (chosen) Scrapbook.Mint else Color.Transparent,
                    onClick = { if (!chosen) onPick(v) },
                )
            }
        }
    }
}
