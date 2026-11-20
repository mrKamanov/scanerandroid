package com.tscan.scanertestov.feature.reports

/**
 * Описание: экран отчётов. Список — отчёт на каждый тест; открытый отчёт — вкладки
 * «Обзор · Классы · Варианты · Вопросы · Ученики» и фильтр по классу.
 *
 * Вёрстка рассчитана на любую ширину: ряды с несколькими элементами переносятся (FlowRow)
 * или делятся долями (weight), вкладки прокручиваются вбок, длинные подписи переносятся.
 */
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import java.util.Locale

// region цвета и форматирование

internal fun gradeColor(grade: Int): Color = when (grade) {
    5 -> Color(0xFF2F6B4F)
    4 -> Color(0xFF1F5A8C)
    3 -> Color(0xFFD9A21B)
    else -> Color(0xFFB5452F)
}

/** Заливка «% верных»: от светло-зелёного (легко) до кирпичного (трудно). */
internal fun heatFill(pct: Double): Color = when {
    pct >= 80 -> Color(0xFFBFE8D2)
    pct >= 60 -> Color(0xFFFFE7A3)
    pct >= 40 -> Color(0xFFF6B26B)
    else -> Color(0xFFC0533D)
}

internal fun heatText(pct: Double): Color = if (pct < 40) Color.White else Scrapbook.Ink

private val classPalette = listOf(Color(0xFF2F6B4F), Color(0xFF1F5A8C), Color(0xFFD9822B), Color(0xFF7A4FA0), Color(0xFF3A8A8C), Color(0xFFB5452F))

internal fun f0(v: Double) = String.format(Locale.US, "%.0f", v)
internal fun f1(v: Double) = String.format(Locale("ru"), "%.1f", v)

// endregion

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    state: ReportsState,
    onAction: (ReportsAction) -> Unit,
    onOpenSheet: (sourceKey: String) -> Unit = {},
    onExportRequest: (key: String) -> Unit = {},
    onChangeStudentRequest: (sourceKey: String) -> Unit = {},
) {
    val report = state.report
    ScreenScaffold(
        onBack = { onAction(ReportsAction.Back) },
        title = if (report == null) "Отчёты" else null,
    ) { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            if (report == null) {
                ReportsList(state.list, onAction, onExportRequest)
            } else {
                ReportHeader(report, onExport = { onExportRequest(report.key) })
                Tabs(state.tab) { onAction(ReportsAction.SelectTab(it)) }
                if (report.classes.size > 1) {
                    ChipRow(
                        options = listOf<String?>(null) + report.classes,
                        selected = report.classFilter,
                        label = { it ?: "Все классы" },
                        onSelect = { onAction(ReportsAction.FilterClass(it)) },
                    )
                }
                if (report.duplicateCount > 0) {
                    DuplicateBanner(report.duplicateCount) {
                        onAction(ReportsAction.SelectTab(ReportTab.Students))
                        onAction(ReportsAction.StudentFilterChanged(StudentFilter.Duplicates))
                    }
                }
                when (state.tab) {
                    ReportTab.Overview -> OverviewTab(report, onAction)
                    ReportTab.Classes -> ClassesTab(report)
                    ReportTab.Variants -> VariantsTab(report, state.variantClass, onAction)
                    ReportTab.Questions -> QuestionsTab(report, state.questionFocus, onAction)
                    ReportTab.Students -> StudentsTab(report, state, onAction, onOpenSheet, onChangeStudentRequest)
                }
            }
        }
    }
}

// region список отчётов

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ReportsList(items: List<ReportListItem>, onAction: (ReportsAction) -> Unit, onExport: (String) -> Unit) {
    Text(
        "Отчёт заводится сам на каждый тест. Работы попадают в него кнопкой «В отчёт» на экране результатов.",
        style = MaterialTheme.typography.bodyMedium,
        color = Scrapbook.InkMuted,
    )
    if (items.isEmpty()) {
        SettingsShellCard {
            Text(
                "Отчётов пока нет. Проверьте работы и нажмите «В отчёт» на карточке работы или «Отправить все в отчёт».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    var query by remember { mutableStateOf("") }
    if (items.size > 3) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск по названию теста") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    var renameKey by remember { mutableStateOf<String?>(null) }
    var deleteKey by remember { mutableStateOf<String?>(null) }
    items.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }.forEach { item ->
        var menu by remember(item.key) { mutableStateOf(false) }
        SettingsShellCard(
            modifier = Modifier.combinedClickable(
                onClick = { onAction(ReportsAction.OpenReport(item.key)) },
                onLongClick = { menu = true },
            ),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    com.tscan.scanertestov.ui.components.FitWordsText(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.meta, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HandNumber(f1(item.kpi.avgGrade), 34)
                    Text("ср. оценка", style = MaterialTheme.typography.labelSmall, color = Scrapbook.InkMuted)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с отчётом") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Выгрузить (Excel или PDF)") }, onClick = { menu = false; onExport(item.key) })
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; renameKey = item.key })
                        DropdownMenuItem(text = { Text("Удалить отчёт") }, onClick = { menu = false; deleteKey = item.key })
                    }
                }
            }
            GradeStackBar(item.kpi.grades, height = 10, showCounts = false, modifier = Modifier.padding(top = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                item.classes.forEach { c -> SmallTag(c) }
            }
        }
    }
    renameKey?.let { key ->
        val current = items.firstOrNull { it.key == key }?.title.orEmpty()
        RenameDialog(current, onSave = { onAction(ReportsAction.RenameReport(key, it)); renameKey = null }, onDismiss = { renameKey = null })
    }
    deleteKey?.let { key ->
        val title = items.firstOrNull { it.key == key }?.title.orEmpty()
        ConfirmDialog(
            title = "Удалить отчёт?",
            text = "Отчёт «$title» будет удалён. Снимки и результаты проверки останутся, работы можно снова добавить кнопкой «В отчёт».",
            confirm = "Удалить",
            onConfirm = { onAction(ReportsAction.DeleteReport(key)); deleteKey = null },
            onDismiss = { deleteKey = null },
        )
    }
}

// endregion

// region шапка, вкладки, фильтры

@Composable
private fun ReportHeader(report: TestReport, onExport: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                report.title,
                fontFamily = Scrapbook.HandFont,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 38.sp,
                color = Scrapbook.Ink,
            )
            Text(
                buildString {
                    append(report.questionCount).append(" вопр.")
                    if (report.variants.size > 1) append(" · ").append(report.variants.size).append(" вар.")
                    append(" · ").append(report.classes.joinToString(", "))
                    append(" · ").append(TestReportBuilder.worksWord(report.totalWorks))
                    if (report.dateRange.isNotBlank()) append(" · ").append(report.dateRange)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
            )
        }
        Text(
            "Выгрузить",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Scrapbook.Ink,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Scrapbook.Lemon)
                .border(2.dp, Scrapbook.Ink, RoundedCornerShape(12.dp))
                .clickable(onClick = onExport)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Tabs(selected: ReportTab, onSelect: (ReportTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReportTab.entries.forEach { t ->
            Pill(t.title, active = t == selected, onClick = { onSelect(t) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> Chip(label(o), active = o == selected, onClick = { onSelect(o) }) }
    }
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (active) Color.White else Scrapbook.Ink,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Scrapbook.Ink else Scrapbook.Paper)
            .border(2.dp, Scrapbook.Ink, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (active) Color.White else Scrapbook.Ink,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Scrapbook.Ink else Scrapbook.Paper)
            .border(2.dp, Scrapbook.Ink, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun SmallTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.5.dp, Scrapbook.Ink, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DuplicateBanner(count: Int, onClick: () -> Unit) {
    SettingsShellCard(paper = Scrapbook.Lemon) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFF8A5A00))
            Column(Modifier.weight(1f)) {
                Text(
                    "Повторных работ: $count — тот же ученик или тот же бланк ещё раз. В расчёте учтена одна работа.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Посмотреть повторы ›",
                    color = Color(0xFF8A5A00),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onClick),
                )
            }
        }
    }
}

// endregion

// region общие элементы

@Composable
private fun HandNumber(text: String, size: Int) {
    Text(text, fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size + 2).sp, color = Scrapbook.Ink)
}

@Composable
private fun SectionTitle(title: String, hint: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F5A8C),
                modifier = Modifier.clickable(onClick = onAction).padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
            )
        }
    }
    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
}

@Composable
private fun KpiRow(kpi: ReportKpi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KpiCell(f1(kpi.avgGrade), "средняя оценка", Modifier.weight(1f))
        KpiCell("${f0(kpi.successPct)}%", "успеваемость", Modifier.weight(1f))
        KpiCell("${f0(kpi.qualityPct)}%", "качество", Modifier.weight(1f))
    }
    Text(
        "успеваемость — «3» и выше · качество — «4» и «5» · средний процент ${f0(kpi.avgPercent)}% · СОУ ${f0(kpi.souPct)}%",
        style = MaterialTheme.typography.bodySmall,
        color = Scrapbook.InkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun KpiCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HandNumber(value, 44)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Scrapbook.InkMuted, textAlign = TextAlign.Center)
    }
}

/** Полоса оценок: доли «5», «4», «3», «2» с числом работ. */
@Composable
internal fun GradeStackBar(grades: Map<Int, Int>, height: Int = 28, showCounts: Boolean = true, modifier: Modifier = Modifier) {
    val total = grades.values.sum()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(if (showCounts) 2.dp else 0.dp, Scrapbook.Ink, RoundedCornerShape(6.dp)),
    ) {
        if (total == 0) {
            Box(Modifier.weight(1f).height(height.dp).background(Scrapbook.Ink.copy(alpha = 0.08f)))
        } else {
            listOf(5, 4, 3, 2).forEach { g ->
                val n = grades[g] ?: 0
                if (n > 0) {
                    Box(
                        Modifier.weight(n.toFloat()).height(height.dp).background(gradeColor(g)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showCounts) Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeLegend(grades: Map<Int, Int>? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(5, 4, 3, 2).forEach { g ->
            Text(
                if (grades != null) "«$g» — ${grades[g] ?: 0}" else "■ «$g»",
                color = gradeColor(g),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Полоса «подпись — доля — значение»; подпись слева фиксированной ширины, полоса делит остаток. */
@Composable
private fun LabeledBar(label: String, pct: Double, valueText: String, color: Color, labelWidth: Int = 64, barHeight: Int = 14) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(labelWidth.dp), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .weight(1f)
                .height(barHeight.dp)
                .clip(RoundedCornerShape(barHeight.dp))
                .background(Scrapbook.Ink.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((pct / 100.0).toFloat().coerceIn(0.01f, 1f))
                    .height(barHeight.dp)
                    .background(color, RoundedCornerShape(barHeight.dp)),
            )
        }
        Text(valueText, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Таблица с долевыми столбцами: первая колонка шире, остальные делят ширину поровну. */
@Composable
private fun SimpleTable(header: List<String>, rows: List<List<Cell>>, firstWeight: Float = 1.3f) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            header.forEachIndexed { i, h ->
                Text(
                    h,
                    modifier = Modifier.weight(if (i == 0) firstWeight else 1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                r.forEachIndexed { i, c ->
                    Text(
                        c.text,
                        modifier = Modifier
                            .weight(if (i == 0) firstWeight else 1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.fill)
                            .then(if (c.outlined) Modifier.border(2.dp, Scrapbook.Ink, RoundedCornerShape(6.dp)) else Modifier)
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        color = c.color,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (i == 0 || c.bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class Cell(
    val text: String,
    val fill: Color = Color.Transparent,
    val color: Color = Scrapbook.Ink,
    val bold: Boolean = false,
    val outlined: Boolean = false,
)

private fun heatCell(pct: Double?, suffix: String = "") =
    if (pct == null) Cell("—") else Cell("${f0(pct)}%$suffix", heatFill(pct), heatText(pct))

// endregion

// region вкладки

@Composable
private fun OverviewTab(r: TestReport, onAction: (ReportsAction) -> Unit) {
    SettingsShellCard {
        KpiRow(r.kpi)
        Text("Оценки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        GradeStackBar(r.kpi.grades)
        Box(Modifier.padding(top = 6.dp)) { GradeLegend(r.kpi.grades) }
    }
    if (r.classFilter == null && r.classRows.size > 1) {
        SettingsShellCard {
            SectionTitle("Классы", "средний процент выполнения", "Подробно ›") { onAction(ReportsAction.SelectTab(ReportTab.Classes)) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                r.classRows.forEachIndexed { i, c ->
                    LabeledBar(c.className, c.kpi.avgPercent, "${f0(c.kpi.avgPercent)}%", classPalette[i % classPalette.size])
                }
            }
            val best = r.classRows.maxOf { it.kpi.avgPercent }
            val worst = r.classRows.minOf { it.kpi.avgPercent }
            Text(
                "Разница между лучшим и слабым классом — ${f0(best - worst)}%",
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (r.variantCells.size > 1) {
        SettingsShellCard {
            SectionTitle("Варианты", action = "Подробно ›") { onAction(ReportsAction.SelectTab(ReportTab.Variants)) }
            VariantTiles(r.variantCells)
            variantVerdict(r.variantCells)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    SettingsShellCard {
        SectionTitle("Трудные вопросы", "доля верных ответов", "Все ›") { onAction(ReportsAction.SelectTab(ReportTab.Questions)) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            r.hardest.take(3).forEach { q ->
                Box(Modifier.clickable { onAction(ReportsAction.SelectTab(ReportTab.Questions)); onAction(ReportsAction.FocusQuestion(q.number)) }) {
                    LabeledBar("№ ${q.number}", q.correctPct, "${f0(q.correctPct)}%", Color(0xFFD9822B), labelWidth = 52, barHeight = 12)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantTiles(cells: List<ReportVariantCell>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        cells.forEach { v ->
            Column(
                Modifier
                    .weight(1f)
                    .border(2.dp, Scrapbook.Ink, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Вариант ${v.variant}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(TestReportBuilder.worksWord(v.works), style = MaterialTheme.typography.labelMedium, color = Scrapbook.InkMuted, textAlign = TextAlign.Center)
                HandNumber("${f0(v.avgPercent)}%", 38)
                Text("оценка ${f1(v.avgGrade)}", style = MaterialTheme.typography.labelMedium, color = Scrapbook.InkMuted)
            }
        }
    }
}

private fun variantVerdict(cells: List<ReportVariantCell>): String? = TestReportBuilder.variantVerdict(cells)

@Composable
private fun ClassesTab(r: TestReport) {
    if (r.classRows.isEmpty()) {
        SettingsShellCard { Text("Нет данных по классам.") }
        return
    }
    SettingsShellCard {
        SectionTitle("Сравнение классов", "оценки в каждом классе")
        r.classRows.forEach { c ->
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(c.className, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(TestReportBuilder.worksWord(c.kpi.works), style = MaterialTheme.typography.labelMedium, color = Scrapbook.InkMuted)
                }
                GradeStackBar(c.kpi.grades, height = 22)
                Row(Modifier.fillMaxWidth()) {
                    MiniStat(f1(c.kpi.avgGrade), "оценка", Modifier.weight(1f))
                    MiniStat("${f0(c.kpi.avgPercent)}%", "процент", Modifier.weight(1f))
                    MiniStat("${f0(c.kpi.successPct)}%", "успев.", Modifier.weight(1f))
                    MiniStat("${f0(c.kpi.qualityPct)}%", "качество", Modifier.weight(1f))
                }
            }
        }
        Box(Modifier.padding(top = 8.dp)) { GradeLegend() }
    }
    if (r.classRows.size > 1) {
        val diverging = r.questions
            .filter { q -> q.correctByClass.values.count { it != null } >= 2 }
            .sortedByDescending { q -> q.correctByClass.values.filterNotNull().let { it.max() - it.min() } }
            .take(5)
        SettingsShellCard {
            SectionTitle("Где классы расходятся", "вопросы с самой большой разницей, % верных")
            Box(Modifier.padding(top = 8.dp)) {
                SimpleTable(
                    header = listOf("№") + r.classRows.map { it.className },
                    rows = diverging.map { q -> listOf(Cell("№ ${q.number}")) + r.classRows.map { heatCell(q.correctByClass[it.className]) } },
                    firstWeight = 0.8f,
                )
            }
            diverging.firstOrNull()?.let { q ->
                val vals = q.correctByClass.filterValues { it != null }.mapValues { it.value!! }
                val lo = vals.minBy { it.value }
                val hi = vals.maxBy { it.value }
                if (hi.value - lo.value >= 20) {
                    Text(
                        "№ ${q.number}: в ${lo.key} верно ${f0(lo.value)}%, в ${hi.key} — ${f0(hi.value)}%. Тему, вероятно, стоит повторить в ${lo.key}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Scrapbook.InkMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HandNumber(value, 26)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Scrapbook.InkMuted)
    }
}

@Composable
private fun VariantsTab(r: TestReport, variantClass: String?, onAction: (ReportsAction) -> Unit) {
    if (r.variants.size < 2) {
        SettingsShellCard {
            Text("В этом тесте один вариант — сравнивать нечего.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    SettingsShellCard {
        SectionTitle("Общее сравнение")
        VariantTiles(r.variantCells)
        variantVerdict(r.variantCells)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted, modifier = Modifier.padding(top = 6.dp))
        }
        val noVariant = r.totalWorks - r.variantCells.sumOf { it.works }
        if (noVariant > 0) {
            Text(
                "Ещё ${TestReportBuilder.worksWord(noVariant)} без варианта: на бланке не распознан номер варианта, " +
                    "поэтому в сравнение вариантов они не вошли (в общих итогах учтены).",
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (r.classVariantRows.isNotEmpty()) {
        SettingsShellCard {
            SectionTitle("Классы × варианты", "средний процент; в скобках — число работ; рамка — разница больше 10%")
            val rows = r.classVariantRows.map { row ->
                val vals = r.variants.map { v -> row.cells[v]?.takeIf { it.works > 0 } }
                val present = vals.filterNotNull()
                val gap = if (present.size >= 2) present.maxOf { it.avgPercent } - present.minOf { it.avgPercent } else null
                listOf(Cell(row.className)) +
                    vals.map { c -> if (c == null) Cell("—") else heatCell(c.avgPercent, " (${c.works})") } +
                    listOf(Cell(gap?.let { "${f0(it)}%" } ?: "—", if (gap != null && gap >= 10) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.06f), if (gap != null && gap >= 10) Color.White else Scrapbook.Ink, bold = true))
            }
            val total = listOf(Cell("Все")) + r.variantCells.map { heatCell(it.avgPercent, " (${it.works})") } + listOf(
                Cell(
                    r.variantCells.filter { it.works > 0 }.let { c -> if (c.size >= 2) "${f0(c.maxOf { it.avgPercent } - c.minOf { it.avgPercent })}%" else "—" },
                    Scrapbook.Ink.copy(alpha = 0.06f),
                    bold = true,
                ),
            )
            Box(Modifier.padding(top = 8.dp)) {
                SimpleTable(listOf("Класс") + r.variants.map { "Вар. $it" } + listOf("Разница"), rows + listOf(total), firstWeight = 1f)
            }
        }
        val classes = r.classVariantRows.map { it.className }
        val cls = variantClass?.takeIf { it in classes } ?: classes.first()
        SettingsShellCard {
            SectionTitle("Внутри класса: $cls", "вопросы, где варианты разошлись сильнее всего, % верных")
            ChipRow(classes, cls, { it }, { onAction(ReportsAction.VariantClass(it)) })
            val gaps = r.variantGapsInClass(cls)
            if (gaps.isEmpty()) {
                Text("Мало работ, чтобы сравнить.", style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
            }
            gaps.forEach { g ->
                Text("Вопрос № ${g.question}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                g.byVariant.toSortedMap().forEach { (v, pct) ->
                    LabeledBar("Вар. $v", pct, "${f0(pct)}%", if (v % 2 == 1) Color(0xFF1F5A8C) else Color(0xFFD9822B), labelWidth = 52, barHeight = 12)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionsTab(r: TestReport, focus: Int?, onAction: (ReportsAction) -> Unit) {
    val q = r.questions.firstOrNull { it.number == focus } ?: r.hardest.firstOrNull()
    SettingsShellCard {
        SectionTitle("Карта вопросов", "% верных; нажмите на вопрос — откроется разбор")
        // По 5 клеток в ряду: ширина делится поровну — на узком экране клетки просто уже.
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 8.dp)) {
            r.questions.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { s ->
                        val sel = s.number == q?.number
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (s.answered == 0) Scrapbook.Ink.copy(alpha = 0.06f) else heatFill(s.correctPct))
                                .border(if (sel) 3.dp else 1.5.dp, Scrapbook.Ink, RoundedCornerShape(8.dp))
                                .clickable { onAction(ReportsAction.FocusQuestion(s.number)) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tc = if (s.answered == 0) Scrapbook.InkMuted else heatText(s.correctPct)
                            Text("№ ${s.number}", fontSize = 11.sp, color = tc, maxLines = 1)
                            Text(if (s.answered == 0) "—" else "${f0(s.correctPct)}%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tc, maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(85.0 to "80% и выше", 70.0 to "60–79%", 50.0 to "40–59%", 20.0 to "ниже 40%").forEach { (p, t) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(heatFill(p)).border(1.dp, Scrapbook.Ink))
                    Text(" $t", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (q != null && q.answered > 0) {
        SettingsShellCard {
            val keyText = q.keyLabels.entries.sortedBy { it.key }.joinToString(" · ") { (v, l) -> if (v == 0 || q.keyLabels.size == 1) l else "вар. $v: $l" }
            SectionTitle("Разбор: вопрос № ${q.number}", if (keyText.isNotBlank()) "верный ответ — $keyText; что выбирали ученики" else "что выбирали ученики")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                q.answers.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            a.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (a.correct) Color.White else Scrapbook.Ink,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier
                                .widthIn(min = 56.dp, max = 110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (a.correct) gradeColor(5) else Color.Transparent)
                                .border(2.dp, Scrapbook.Ink, RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            // Зелёная часть — у кого этот ответ верный (по их варианту), оранжевая — у кого неверный.
                            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Scrapbook.Ink.copy(alpha = 0.08f))) {
                                val share = (a.count.toFloat() / q.answered).coerceIn(0.01f, 1f)
                                if (a.right > 0) Box(Modifier.weight(share * a.right / a.count).fillMaxHeight().background(gradeColor(5)))
                                if (a.wrong > 0) Box(Modifier.weight(share * a.wrong / a.count).fillMaxHeight().background(Color(0xFFD9822B)))
                                if (share < 1f) Spacer(Modifier.weight(1f - share))
                            }
                            if (a.right > 0 && a.wrong > 0) {
                                Text(
                                    "верно у ${a.right} (по их варианту), неверно у ${a.wrong}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Scrapbook.InkMuted,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Text("${a.count} чел.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val notes = buildList {
                q.topWrong?.let { add("Частая ошибка: ${it.label} (${it.count} чел.)") }
                if (q.multiMarked > 0) add("Отметили несколько кружков: ${q.multiMarked}")
                if (q.withFixed > 0) add("С исправлением: ${q.withFixed}")
            }
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(". ") + ".", style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted, modifier = Modifier.padding(top = 8.dp))
            }
            if (r.classFilter == null && r.classes.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                    r.classes.forEachIndexed { i, c ->
                        q.correctByClass[c]?.let { LabeledBar(c, it, "${f0(it)}%", classPalette[i % classPalette.size], barHeight = 10) }
                    }
                }
            }
        }
    }
    if (r.pairs.isNotEmpty()) {
        SettingsShellCard {
            SectionTitle("Ошибаются вместе", "если ошибся в одном, часто ошибается и в другом — похоже на одну тему")
            r.pairs.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("№ ${p.a} и № ${p.b}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${p.together} уч.", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StudentsTab(
    r: TestReport,
    state: ReportsState,
    onAction: (ReportsAction) -> Unit,
    onOpenSheet: (String) -> Unit,
    onChangeStudent: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.studentQuery,
        onValueChange = { onAction(ReportsAction.StudentQuery(it)) },
        label = { Text("Поиск по фамилии") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val counts = mapOf(
        StudentFilter.All to r.students.size,
        StudentFilter.Duplicates to r.students.count { it.duplicateOf != null },
        StudentFilter.Unnamed to r.students.count { it.work.fullName == null },
        StudentFilter.Twos to r.students.count { it.work.grade == 2 && it.duplicateOf == null },
    )
    ChipRow(
        options = StudentFilter.entries.filter { it == StudentFilter.All || (counts[it] ?: 0) > 0 },
        selected = state.studentFilter,
        label = { "${it.title} · ${counts[it]}" },
        onSelect = { onAction(ReportsAction.StudentFilterChanged(it)) },
    )
    val q = state.studentQuery.trim().lowercase()
    val filtered = r.students.filter { s ->
        (q.isEmpty() || s.displayName.lowercase().contains(q)) &&
            when (state.studentFilter) {
                StudentFilter.All -> true
                StudentFilter.Duplicates -> s.duplicateOf != null || r.students.any { it.duplicateOf?.sourceKey == s.work.sourceKey }
                StudentFilter.Unnamed -> s.work.fullName == null
                StudentFilter.Twos -> s.work.grade == 2 && s.duplicateOf == null
            }
    }
    // В «Повторах» — по ученику: сначала работа, которая идёт в расчёт, под ней её повторы.
    val shown = if (state.studentFilter == StudentFilter.Duplicates) {
        val order = filtered.withIndex().associate { (i, s) -> s.work.sourceKey to i }
        filtered.sortedWith(
            compareBy<ReportStudentRow>(
                { order[it.duplicateOf?.sourceKey ?: it.work.sourceKey] ?: Int.MAX_VALUE },
                { if (it.duplicateOf == null) 0 else 1 },
            ),
        )
    } else filtered
    if (shown.isEmpty()) {
        SettingsShellCard { Text("Никого не нашлось.", style = MaterialTheme.typography.bodyMedium) }
        return
    }
    var removeKey by remember { mutableStateOf<String?>(null) }
    shown.groupBy { it.className }.forEach { (cls, rows) ->
        SettingsShellCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(cls, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val dups = rows.count { it.duplicateOf != null }
                Text(
                    if (state.studentFilter == StudentFilter.Duplicates) "в расчёте ${rows.size - dups}, повторов $dups"
                    else TestReportBuilder.worksWord(rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Scrapbook.InkMuted,
                )
            }
            rows.forEach { s ->
                StudentRow(
                    s,
                    repeats = r.students.count { it.duplicateOf?.sourceKey == s.work.sourceKey },
                    onOpen = { onOpenSheet(s.work.sourceKey) },
                    onChange = { onChangeStudent(s.work.sourceKey) },
                    onRemove = { removeKey = s.work.sourceKey },
                )
            }
        }
    }
    removeKey?.let { key ->
        val name = r.students.firstOrNull { it.work.sourceKey == key }?.displayName.orEmpty()
        ConfirmDialog(
            title = "Убрать из отчёта?",
            text = "Работа «$name» уйдёт из отчёта. Снимок остаётся в результатах проверки, вернуть можно кнопкой «В отчёт».",
            confirm = "Убрать",
            onConfirm = { onAction(ReportsAction.RemoveWork(key)); removeKey = null },
            onDismiss = { removeKey = null },
        )
    }
}

@Composable
private fun StudentRow(s: ReportStudentRow, repeats: Int, onOpen: () -> Unit, onChange: () -> Unit, onRemove: () -> Unit) {
    var menu by remember(s.work.sourceKey) { mutableStateOf(false) }
    val dup = s.duplicateOf != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (dup) Scrapbook.Lemon.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(gradeColor(s.work.grade)),
            contentAlignment = Alignment.Center,
        ) {
            Text("${s.work.grade}", color = Color.White, fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(s.displayName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("${s.work.scoreCorrect} из ${s.work.scoreTotal} · ${f0(s.work.percent.toDouble())}%")
                    s.work.variant?.let { append(" · вар. $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
            )
            if (dup) {
                Text(
                    "Повтор работы «${s.duplicateOf?.fullName ?: s.duplicateOf?.title}» — в расчёт не идёт",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8A5A00),
                    fontWeight = FontWeight.Bold,
                )
            } else if (repeats > 0) {
                Text(
                    "Идёт в расчёт · повторов этой работы: $repeats",
                    style = MaterialTheme.typography.labelMedium,
                    color = gradeColor(5),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с работой") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Открыть бланк") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Сменить ученика или класс") }, onClick = { menu = false; onChange() })
                DropdownMenuItem(text = { Text("Убрать из отчёта") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

// endregion

// region диалоги

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название отчёта") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// endregion
