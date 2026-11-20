package com.tscan.scanertestov.feature.reports

/**
 * Описание: маршрут экрана отчётов — список отчётов по тестам, открытый отчёт, выгрузка
 * (окно «Выгрузить отчёт»), просмотр бланка ученика, смена ученика, удаление и переименование.
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.blankeditor.shareExportedFile
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Свои названия отчётов (переименование), по ключу отчёта. */
internal object ReportTitles {
    private const val PREFS = "report_titles"
    fun all(context: Context): Map<String, String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    fun set(context: Context, key: String, title: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (title.isNullOrBlank()) remove(key) else putString(key, title)
        }.apply()
    }
}

@Composable
fun ReportsRoute(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var classFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(ReportTab.Overview) }
    var focusQuestion by rememberSaveable { mutableStateOf<Int?>(null) }
    var variantClass by rememberSaveable { mutableStateOf<String?>(null) }
    var studentQuery by rememberSaveable { mutableStateOf("") }
    var studentFilter by rememberSaveable { mutableStateOf(StudentFilter.All) }
    var exportKey by remember { mutableStateOf<String?>(null) }
    var sheetKey by remember { mutableStateOf<String?>(null) }
    var changeKey by remember { mutableStateOf<String?>(null) }

    val roster = remember { com.tscan.scanertestov.feature.journals.JournalRepository(context).loadAll() }
    val works = remember(reload) { ReportsSelectedWorksStore.works }
    val titles = remember(reload) { ReportTitles.all(context) }
    val list = remember(works, titles) { TestReportBuilder.list(works, titles) }
    val report = remember(works, titles, openKey, classFilter) {
        openKey?.let { TestReportBuilder.build(works, it, classFilter, titles) }
    }
    if (openKey != null && report == null) openKey = null

    fun doExport(key: String, opts: ReportExportOptions) {
        val rep = TestReportBuilder.build(ReportsSelectedWorksStore.works, key, opts.className, titles) ?: return
        scope.launch(Dispatchers.IO) {
            val app = context.applicationContext
            val file = runCatching {
                when (opts.format) {
                    ReportExportFormat.Excel -> ReportsExcelExporter(app).export(rep, opts)
                    ReportExportFormat.Pdf -> ReportsPdfExporter(app).export(rep, opts)
                }
            }.onFailure { android.util.Log.e("Reports", "export failed", it) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null) shareExportedFile(app, file)
                else Toast.makeText(context, "Не удалось создать файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = openKey != null) { openKey = null }

    com.tscan.scanertestov.ui.components.ScreenTour(
        "reports_v2",
        com.tscan.scanertestov.ui.components.ScreenTours.Reports,
        enabled = report != null,
    ) {
        ReportsScreen(
            state = ReportsState(
                list = list,
                report = report,
                tab = tab,
                questionFocus = focusQuestion,
                variantClass = variantClass,
                studentQuery = studentQuery,
                studentFilter = studentFilter,
                roster = roster,
            ),
            onAction = { action ->
                when (action) {
                    ReportsAction.Back -> if (openKey != null) openKey = null else navController.popBackStack()
                    is ReportsAction.OpenReport -> {
                        openKey = action.key
                        classFilter = null; tab = ReportTab.Overview; focusQuestion = null
                        variantClass = null; studentQuery = ""; studentFilter = StudentFilter.All
                    }
                    is ReportsAction.SelectTab -> tab = action.tab
                    is ReportsAction.FilterClass -> classFilter = action.className
                    is ReportsAction.FocusQuestion -> focusQuestion = action.number
                    is ReportsAction.VariantClass -> variantClass = action.className
                    is ReportsAction.StudentQuery -> studentQuery = action.query
                    is ReportsAction.StudentFilterChanged -> studentFilter = action.filter
                    is ReportsAction.RemoveWork -> { ReportsSelectedWorksStore.remove(action.sourceKey); reload++ }
                    is ReportsAction.ChangeStudent -> {
                        ReportsSelectedWorksStore.works.firstOrNull { it.sourceKey == action.sourceKey }?.let { w ->
                            ReportsSelectedWorksStore.upsert(
                                w.copy(studentSurname = action.surname, studentName = action.name, journalClassName = action.className ?: w.journalClassName),
                            )
                        }
                        reload++
                    }
                    is ReportsAction.RenameReport -> { ReportTitles.set(context, action.key, action.title); reload++ }
                    is ReportsAction.DeleteReport -> {
                        ReportsSelectedWorksStore.removeReport(action.key)
                        ReportTitles.set(context, action.key, null)
                        if (openKey == action.key) openKey = null
                        reload++
                    }
                    is ReportsAction.Export -> doExport(action.key, action.options)
                }
            },
            onOpenSheet = { sheetKey = it },
            onExportRequest = { exportKey = it },
            onChangeStudentRequest = { changeKey = it },
        )
    }

    exportKey?.let { key ->
        val classes = remember(key, works) { works.filter { it.reportKey == key }.map { TestReportBuilder.classOf(it) }.distinct().sorted() }
        ExportSheet(
            title = list.firstOrNull { it.key == key }?.title.orEmpty(),
            classes = classes,
            initialClass = if (key == openKey) classFilter else null,
            onDismiss = { exportKey = null },
            onRun = { opts -> exportKey = null; doExport(key, opts) },
        )
    }

    sheetKey?.let { key ->
        val students = report?.students.orEmpty()
        val idx = students.indexOfFirst { it.work.sourceKey == key }
        SheetViewer(
            row = students.getOrNull(idx),
            position = idx,
            total = students.size,
            onPrev = { students.getOrNull(idx - 1)?.let { sheetKey = it.work.sourceKey } },
            onNext = { students.getOrNull(idx + 1)?.let { sheetKey = it.work.sourceKey } },
            onDismiss = { sheetKey = null },
        )
    }

    changeKey?.let { key ->
        val w = works.firstOrNull { it.sourceKey == key }
        if (w == null) {
            changeKey = null
        } else {
            val taken = works.filter { it.reportKey == w.reportKey && it.sourceKey != key }.mapNotNull { it.fullName }.toSet()
            com.tscan.scanertestov.feature.batch.StudentPickerDialog(
                workTitle = w.fullName ?: w.title,
                nameCropPath = null,
                roster = roster,
                preferredClass = w.journalClassName,
                alreadyAssigned = taken,
                onPick = { s, n, c ->
                    ReportsSelectedWorksStore.upsert(w.copy(studentSurname = s, studentName = n, journalClassName = c ?: w.journalClassName))
                    changeKey = null
                    reload++
                },
                onDismiss = { changeKey = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportSheet(
    title: String,
    classes: List<String>,
    initialClass: String?,
    onRun: (ReportExportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by remember { mutableStateOf(ReportExportFormat.Excel) }
    var cls by remember { mutableStateOf(initialClass) }
    var summary by remember { mutableStateOf(true) }
    var byClass by remember { mutableStateOf(true) }
    var variants by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf(true) }
    var students by remember { mutableStateOf(true) }
    var answers by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SettingsShellCard(modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выгрузить отчёт", fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                Text(title, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                Text("Формат", style = MaterialTheme.typography.labelLarge, color = Scrapbook.InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatTile("Excel", "таблицы: можно считать и сортировать", format == ReportExportFormat.Excel, Modifier.weight(1f)) { format = ReportExportFormat.Excel }
                    FormatTile("PDF", "для печати и отправки", format == ReportExportFormat.Pdf, Modifier.weight(1f)) { format = ReportExportFormat.Pdf }
                }
                if (classes.size > 1) {
                    Text("Классы", style = MaterialTheme.typography.labelLarge, color = Scrapbook.InkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf<String?>(null) + classes).forEach { c ->
                            val active = c == cls
                            Text(
                                c ?: "Все",
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else Scrapbook.Ink,
                                modifier = Modifier
                                    .background(if (active) Scrapbook.Ink else Scrapbook.Paper, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .clickable { cls = c }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                Text("Что включить", style = MaterialTheme.typography.labelLarge, color = Scrapbook.InkMuted)
                CheckLine("Сводка и оценки", summary) { summary = it }
                if (classes.size > 1 && cls == null) CheckLine("Сравнение классов", byClass) { byClass = it }
                CheckLine("Сравнение вариантов", variants) { variants = it }
                CheckLine("Вопросы: карта и разбор", questions) { questions = it }
                CheckLine("Список учеников", students) { students = it }
                CheckLine("Ответы каждого ученика по вопросам", answers) { answers = it }
                ShellBubblePrimaryButton(
                    text = "Выгрузить и отправить",
                    onClick = {
                        onRun(ReportExportOptions(format, cls, summary, byClass && cls == null, variants, questions, students, answers))
                    },
                    enabled = summary || byClass || variants || questions || students,
                    paletteIndex = 2,
                )
                Text(
                    "Файл можно отправить в мессенджер, на почту или сохранить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Scrapbook.InkMuted,
                )
            }
        }
    }
}

@Composable
private fun FormatTile(title: String, hint: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(if (active) Scrapbook.Mint else Scrapbook.Paper, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
    }
}

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Бланк ученика из отчёта с листанием «‹ / ›». */
@Composable
private fun SheetViewer(
    row: ReportStudentRow?,
    position: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (row == null) { onDismiss(); return }
    val bmp by produceState<Bitmap?>(null, row.work.sheetPath) {
        value = withContext(Dispatchers.IO) { row.work.sheetPath?.let { p -> runCatching { BitmapFactory.decodeFile(p) }.getOrNull() } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SettingsShellCard(modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${position + 1} из $total · ${row.className}", style = MaterialTheme.typography.labelLarge, color = Scrapbook.InkMuted)
                Text(row.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "«${row.work.grade}» · ${row.work.scoreCorrect} из ${row.work.scoreTotal} · ${f0(row.work.percent.toDouble())}%" +
                        (row.work.variant?.let { " · вар. $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b != null) {
                        Image(b.asImageBitmap(), contentDescription = "Бланк", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            if (row.work.sheetPath == null) "Снимок этой работы не сохранился (добавлена в отчёт в прежней версии)." else "Загрузка…",
                            color = Scrapbook.InkMuted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShellBubblePrimaryButton("‹ Предыдущая", onPrev, Modifier.weight(1f), enabled = position > 0, paletteIndex = 4)
                    ShellBubblePrimaryButton("Следующая ›", onNext, Modifier.weight(1f), enabled = position < total - 1, paletteIndex = 1)
                }
                ShellBubblePrimaryButton("Закрыть", onDismiss, paletteIndex = 5)
            }
        }
    }
}
