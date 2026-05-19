package com.tscan.scanertestov.feature.reports

/**
 * Описание: экран аналитических отчетов по последнему пакетному запуску.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsState = ReportsState(),
    onAction: (ReportsAction) -> Unit
) {
    val expandedWorks = remember { mutableStateMapOf<String, Boolean>() }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    ScreenScaffold(
        onBack = { onAction(ReportsAction.Back) }
    ) { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            if (state.summary == null) {
                SettingsShellCard {
                    Text(
                        text = state.emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@ScreenContentColumn
            }

            SettingsShellCard {
                Text(
                    text = "Статистика",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(label = "Работ", value = "${state.summary.totalWorks}", modifier = Modifier.fillMaxWidth())
                    StatTile(label = "Средний балл", value = format1(state.summary.averageGrade), modifier = Modifier.fillMaxWidth())
                    StatTile(label = "Успеваемость", value = "${format0(state.summary.successRatePercent)}%", modifier = Modifier.fillMaxWidth())
                    StatTile(label = "Качество знаний", value = "${format0(state.summary.qualityKnowledgePercent)}%", modifier = Modifier.fillMaxWidth())
                }
            }

            SettingsShellCard {
                Text("Распределение оценок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                GradeDistributionChart(items = state.gradeDistribution, modifier = Modifier.padding(top = 12.dp))
            }

            SettingsShellCard {
                Text("Топ 5 сложных вопросов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.hardestQuestions.isEmpty()) {
                        Text("Нет ошибок для анализа", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.hardestQuestions.forEach { item ->
                            HardQuestionCard(item = item)
                        }
                    }
                }
            }

            SettingsShellCard {
                Text("Тепловая карта вопросов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                QuestionHeatmap(items = state.heatmap.take(35))
            }

            SettingsShellCard {
                Text("Связанные ошибки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.relatedErrors.isEmpty()) {
                        Text("Недостаточно данных", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.relatedErrors.forEach { item ->
                            RelatedErrorCard(item)
                        }
                    }
                }
            }

            if (state.variantStats.isNotEmpty()) {
                SettingsShellCard {
                    Text("Анализ по вариантам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.variantStats.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ReportInsetShape)
                                    .background(ReportInsetBg)
                                    .border(1.dp, ReportInsetBorder, ReportInsetShape)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Вариант ${item.variant}", fontWeight = FontWeight.Medium)
                                Text("${item.worksCount} работ · ср. ${format1(item.averagePercent)}%")
                            }
                        }
                    }
                    state.variantComparison?.let { cmp ->
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .clip(ReportInsetShape)
                                .background(ReportInsetBg)
                                .border(1.dp, ReportInsetBorder, ReportInsetShape)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("Сравнение вариантов", fontWeight = FontWeight.SemiBold)
                            Text("Лидер: В${cmp.bestVariant} (${format1(cmp.bestPercent)}%)", style = MaterialTheme.typography.bodySmall)
                            Text("Минимум: В${cmp.worstVariant} (${format1(cmp.worstPercent)}%)", style = MaterialTheme.typography.bodySmall)
                            Text("Разница: ${format1(cmp.gapPercent)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (state.classStats.isNotEmpty()) {
                SettingsShellCard {
                    Text("Анализ по классам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.classStats.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ReportInsetShape)
                                    .background(ReportInsetBg)
                                    .border(1.dp, ReportInsetBorder, ReportInsetShape)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.className, fontWeight = FontWeight.Medium)
                                    Text("${item.worksCount} работ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text("Ср. ${format1(item.averagePercent)}%", style = MaterialTheme.typography.bodySmall)
                                    Text("Усп. ${format0(item.successRatePercent)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            SettingsShellCard {
                Text("Распознавание ФИО", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("С именем: ${state.namedWorksCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("Без имени: ${state.unnamedWorksCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SettingsShellCard {
                Text("Идентичные работы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.identicalWorks.isEmpty()) {
                        Text("Совпадающих работ не найдено", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.identicalWorks.forEach { item ->
                            IdenticalWorksCard(item)
                        }
                    }
                }
            }

            SettingsShellCard {
                Text("Список работ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.works.forEach { item ->
                        val expanded = expandedWorks[item.title] == true
                        WorkRowCard(
                            item = item,
                            expanded = expanded,
                            onToggle = { expandedWorks[item.title] = !expanded },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShellBubbleOutlinedButton(
                    text = "Очистить",
                    onClick = { onAction(ReportsAction.Clear) },
                    modifier = Modifier.weight(1f),
                    fillMaxWidth = false,
                )
                Box(modifier = Modifier.weight(1f)) {
                    ShellBubbleOutlinedButton(
                        text = "Экспорт",
                        onClick = { exportMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("PDF") },
                            onClick = {
                                exportMenuExpanded = false
                                onAction(ReportsAction.ExportPdf)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Excel (.xlsx)") },
                            onClick = {
                                exportMenuExpanded = false
                                onAction(ReportsAction.ExportExcel)
                            },
                        )
                    }
                }
            }
        }
    }
}

private val ReportInsetShape = RoundedCornerShape(12.dp)
private val ReportInsetBg = Color.White.copy(alpha = 0.08f)
private val ReportInsetBorder = Color.White.copy(alpha = 0.16f)

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ReportInsetShape)
            .background(ReportInsetBg)
            .border(1.dp, ReportInsetBorder, ReportInsetShape)
            .padding(10.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GradeDistributionChart(items: List<ReportsGradeDistributionItem>, modifier: Modifier = Modifier) {
    val axisLabels = listOf(100, 75, 50, 25, 0)
    Row(modifier = modifier.fillMaxWidth().height(200.dp)) {
        Column(
            modifier = Modifier.width(30.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            axisLabels.forEach { label ->
                Text("$label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.sortedBy { it.grade }.forEach { item ->
                val ratio = (item.percent / 100.0).toFloat().coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text("${item.count}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(ratio.coerceIn(0.04f, 1f))
                            .background(gradeColor(item.grade).copy(alpha = 0.78f), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                    )
                    Text("${item.grade}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun HardQuestionCard(item: ReportsHardQuestionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReportInsetShape)
            .background(ReportInsetBg)
            .border(1.dp, ReportInsetBorder, ReportInsetShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column {
            Text("Вопрос ${item.questionNumber}", fontWeight = FontWeight.SemiBold)
            Text("Ошибок: ${item.wrongCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("${format0(item.wrongPercent)}%", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RelatedErrorCard(item: ReportsRelatedErrorItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReportInsetShape)
            .background(ReportInsetBg)
            .border(1.dp, ReportInsetBorder, ReportInsetShape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("Вопросы ${item.firstQuestion} и ${item.secondQuestion}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text("вместе ошиблись: ${item.togetherCount}", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IdenticalWorksCard(item: ReportsIdenticalWorksItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReportInsetShape)
            .background(ReportInsetBg)
            .border(1.dp, ReportInsetBorder, ReportInsetShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.signature, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Text(item.workTitles.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun WorkRowCard(item: ReportsWorkListItem, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReportInsetShape)
            .background(ReportInsetBg)
            .border(1.dp, ReportInsetBorder, ReportInsetShape)
            .clickable(onClick = onToggle)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.Medium)
                Text("${item.scoreText} · ${item.percent?.let { "${format1(it.toDouble())}%" } ?: "-"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val meta = buildList {
                    item.variant?.let { add("Вариант $it") }
                    item.className?.takeIf { it.isNotBlank() }?.let { add(it) }
                    item.studentDisplayName?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!item.strictScoring && item.hasMultiChoiceQuestions) {
                    Text(
                        "Частично выполнено: ${item.partialCompletedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(gradeColor(item.grade ?: 2).copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Оценка ${item.grade ?: "-"}", style = MaterialTheme.typography.labelMedium)
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            AnswerGroups(item)
        }
    }
}

@Composable
private fun QuestionHeatmap(items: List<ReportsQuestionHeatmapItem>) {
    if (items.isEmpty()) {
        Text("Нет данных для тепловой карты", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        return
    }
    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(7).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(heatColor(item.wrongPercent), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            text = "${item.questionNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = heatTextColor(item.wrongPercent),
                        )
                    }
                }
                repeat(7 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private fun gradeColor(grade: Int): Color = when (grade) {
    5 -> Color(0xFF66BB6A)
    4 -> Color(0xFF5C8DDE)
    3 -> Color(0xFFF2C94C)
    else -> Color(0xFFE57373)
}

private fun heatColor(percent: Double): Color {
    val t = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    return lerp(Color(0xFF8BCF7A), Color(0xFFA44A59), t)
}

private fun heatTextColor(wrongPercent: Double): Color =
    if (wrongPercent >= 45.0) Color.White else Color(0xFF1E2A1F)

@Composable
private fun AnswerGroups(item: ReportsWorkListItem) {
    val correct = item.correctQuestions.joinToString(", ").ifBlank { "—" }
    val wrong = item.wrongQuestions.joinToString(", ").ifBlank { "—" }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnswerPill(
            title = "Правильные вопросы",
            value = correct,
            bg = Color(0x2266BB6A),
            textColor = MaterialTheme.colorScheme.onSurface,
        )
        AnswerPill(
            title = "Ошибки",
            value = wrong,
            bg = Color(0x22E57373),
            textColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AnswerPill(title: String, value: String, bg: Color, textColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = textColor)
        Text(value, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

private fun format0(value: Double): String = String.format("%.0f", value)
private fun format1(value: Double): String = String.format("%.1f", value)
