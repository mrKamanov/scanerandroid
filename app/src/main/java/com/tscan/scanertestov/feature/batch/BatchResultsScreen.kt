package com.tscan.scanertestov.feature.batch

/**
 * Описание: экран итогов пакетной обработки с переходом в отчеты.
 */
import androidx.compose.foundation.Image
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

private fun BatchResultEntryState.canExportToReports(): Boolean =
    grade != null && percent != null && scoreCorrect != null && scoreTotal != null &&
        reportQuestionScores.isNotEmpty()

private val EntryCardShape = RoundedCornerShape(12.dp)
private val FrostedEntryBg = Color.White.copy(alpha = 0.08f)
private val FrostedEntryBorder = Color.White.copy(alpha = 0.16f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchResultsScreen(
    state: BatchResultsState = BatchResultsState(),
    onAction: (BatchResultsAction) -> Unit
) {
    ScreenScaffold(onBack = { onAction(BatchResultsAction.Back) }) { padding ->
        var detailsIndex by remember { mutableStateOf<Int?>(null) }
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

        ScreenContentColumn(padding = padding, verticalScroll = true) {
            state.summary?.let { BatchResultsSummaryBlock(it) }

            SettingsShellCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.resultEntries.forEachIndexed { index, entry ->
                        key(index) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(EntryCardShape)
                                    .background(FrostedEntryBg)
                                    .border(1.dp, FrostedEntryBorder, EntryCardShape)
                                    .heightIn(min = 168.dp)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .heightIn(min = 72.dp)
                                            .background(statusAccentColor(entry.status), RoundedCornerShape(8.dp)),
                                    )
                                    if (entry.previewBitmap != null) {
                                        Image(
                                            bitmap = entry.previewBitmap.asImageBitmap(),
                                            contentDescription = "Миниатюра работы",
                                            modifier = Modifier.size(72.dp),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Text(
                                            text = "Без превью",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.size(72.dp),
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                                        val sub = entry.subtitle?.trim().orEmpty()
                                        if (sub.isNotEmpty() && normalizeWorkCardSubtitle(sub) != normalizeWorkCardSubtitle(entry.title)) {
                                            Text(
                                                sub,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            StatusIcon(entry.status)
                                            StatusBadge(entry.status)
                                        }
                                    }
                                }

                                EntryGradeScoreBlock(entry)
                                CorrectionsSummaryLine(entry)
                                entry.layoutLine?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val canExport = entry.canExportToReports()
                                    val alreadyInReports = index in state.exportedToReportsIndices
                                    val sentToReports = canExport && alreadyInReports
                                    val sendIx = remember(index, "send") { MutableInteractionSource() }
                                    val detailIx = remember(index, "detail") { MutableInteractionSource() }
                                    val sendPal = when {
                                        sentToReports -> mainMenuBubbleGradient(1)
                                        !canExport -> mainMenuBubbleGradient(4)
                                        else -> mainMenuBubbleGradient(2)
                                    }
                                    val detailPal = mainMenuBubbleGradient(0)
                                    RealtimeBubbleIconButton(
                                        onClick = { onAction(BatchResultsAction.SendWorkToReports(index)) },
                                        contentDescription = when {
                                            sentToReports -> state.inReportsButtonText
                                            !canExport -> "Недоступно для отчёта"
                                            else -> state.sendToReportsButtonText
                                        },
                                        topColor = sendPal.first,
                                        bottomColor = sendPal.second,
                                        size = 52.dp,
                                        enabled = canExport && !alreadyInReports,
                                        interactionSource = sendIx,
                                    ) {
                                        Icon(
                                            imageVector = if (sentToReports) {
                                                Icons.Filled.CheckCircle
                                            } else {
                                                Icons.AutoMirrored.Filled.SendToMobile
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                    RealtimeBubbleIconButton(
                                        onClick = { detailsIndex = index },
                                        contentDescription = "Подробнее",
                                        topColor = detailPal.first,
                                        bottomColor = detailPal.second,
                                        size = 52.dp,
                                        interactionSource = detailIx,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                                Text(
                                    "Результат проверки",
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
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color.White.copy(alpha = 0.14f),
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                                    .verticalScroll(dialogScroll),
                            ) {
                                Text(selected.title, style = MaterialTheme.typography.titleMedium)
                                StatusBadge(selected.status)

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
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                        val sheetWithMarkers = remember(
                                            showMarkers,
                                            selected.previewBitmap,
                                            selected.sheetMarkerConfig,
                                            selected.sheetMarkerPredictions,
                                        ) {
                                            val base = selected.previewBitmap ?: return@remember null
                                            val cfg = selected.sheetMarkerConfig
                                            val preds = selected.sheetMarkerPredictions
                                            if (!showMarkers || cfg == null || preds.isEmpty()) {
                                                base
                                            } else {
                                                BatchSheetAnswerMarkersOverlay.renderMarkersOnCrop(base, cfg, preds)
                                            }
                                        }
                                        sheetWithMarkers?.let { bmp ->
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Бланк",
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } ?: Text(
                                            "Изображение недоступно",
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchResultsSummaryBlock(summary: BatchResultsSummary) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Итоги проверки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStatPill("Всего работ", summary.totalWorks, Modifier.weight(1f))
                SummaryStatPill("Пятерок", summary.countGrade5, Modifier.weight(1f))
                SummaryStatPill("Четверок", summary.countGrade4, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStatPill("Троек", summary.countGrade3, Modifier.weight(1f))
                SummaryStatPill("Двоек", summary.countGrade2, Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryStatPill(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.10f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
        text = "Исправления: ${entry.correctionsTotal}",
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
                color = Color.White.copy(alpha = 0.08f),
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
                    Text(
                        "Как засчитать эту отметку?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                                tint = Color.White,
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
                                tint = Color.White,
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
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.10f)) {
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
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
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
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.12f)) {
                Text(
                    "Выбрано: ${row.selectedText}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.12f)) {
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
    5 -> Color(0xFF79A985)
    4 -> Color(0xFF7A99C2)
    3 -> Color(0xFFC1A56E)
    2 -> Color(0xFFC17B7B)
    else -> Color(0xFFB86F6F)
}

private fun percentTextColor(percent: Float): Color = when {
    percent < 50f -> Color(0xFFC17B7B)
    percent < 70f -> Color(0xFFC1A56E)
    percent < 90f -> Color(0xFF7A99C2)
    else -> Color(0xFF79A985)
}
