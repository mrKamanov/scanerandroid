package com.tscan.scanertestov.feature.journals

/**
 * Описание: экран «Журналы». Классы — кнопками сверху; в выбранном классе быстрый ввод
 * («Фамилия Имя», можно вставить сразу список), выбор нескольких учеников галочками и действия
 * над ними: перевести в другой класс или удалить (с отменой). Поиск — сразу по всем классам.
 * Импорт из Excel/CSV и шаблон — внизу.
 */
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import java.util.Locale

private val ru = Locale("ru")

private fun classSort(a: String, b: String): Int {
    val na = a.takeWhile { it.isDigit() }.toIntOrNull()
    val nb = b.takeWhile { it.isDigit() }.toIntOrNull()
    return if (na != null && nb != null && na != nb) na.compareTo(nb) else a.compareTo(b, ignoreCase = true)
}

/** «Иванов Пётр Сергеевич» → фамилия, имя, отчество. Пустая строка — null. */
internal fun parseFullNameLine(line: String, className: String): JournalStudent? {
    val parts = line.trim().replace(',', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size < 2) return null
    return JournalStudent(className, parts[0], parts[1], parts.drop(2).joinToString(" ").ifBlank { null }).normalized()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun JournalsScreen(
    state: JournalsState = JournalsState(),
    onAction: (JournalsAction) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { JournalRepository(context.applicationContext) }
    val students = remember { mutableStateListOf<JournalStudent>() }
    LaunchedEffect(Unit) {
        val raw = repo.loadAll()
        val norm = raw.map { it.normalized() }.distinct()
        students.clear(); students.addAll(norm)
        if (norm != raw) repo.saveAll(norm)
    }
    fun save() = repo.saveAll(students.toList())

    var query by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<List<JournalStudent>?>(null) }
    val selected = remember { mutableStateListOf<JournalStudent>() }
    var quickAdd by rememberSaveable { mutableStateOf("") }
    var newClassDialog by remember { mutableStateOf(false) }
    var renameClass by remember { mutableStateOf<String?>(null) }
    var deleteClass by remember { mutableStateOf<String?>(null) }
    var moveDialog by remember { mutableStateOf<List<JournalStudent>?>(null) }
    var deleteDialog by remember { mutableStateOf<List<JournalStudent>?>(null) }
    var editStudent by remember { mutableStateOf<JournalStudent?>(null) }
    var emptyClasses by remember { mutableStateOf(setOf<String>()) }
    var pendingImport by remember { mutableStateOf<StudentsImport?>(null) }

    val classes = (students.map { it.className } + emptyClasses).distinct().sortedWith(::classSort)
    if (current == null || current !in classes) current = classes.firstOrNull()
    val cls = current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { parseStudentsImport(it.readBytes()) }
                ?: StudentsImport(emptyList())
        }.onSuccess { result ->
            // Сначала показываем, что нашлось, — добавляем только после «Добавить».
            if (result.students.isEmpty()) {
                status = buildString {
                    append("В файле не нашлось учеников. Скачайте шаблон — в нём лист «Как заполнять».")
                    if (result.skipped.isNotEmpty()) {
                        append(" Не прочитаны строки: ")
                        append(result.skipped.take(5).joinToString(", ") { (n, why) -> "$n ($why)" })
                        if (result.skipped.size > 5) append(" и ещё ${result.skipped.size - 5}")
                        append(".")
                    }
                }
            } else {
                pendingImport = result
            }
        }.onFailure { status = "Не удалось прочитать файл: ${it.message ?: "ошибка"}" }
    }
    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = buildJournalTemplateXlsx(context).readBytes()
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("нет доступа")
        }.onSuccess { status = "Шаблон сохранён." }.onFailure { status = "Не удалось сохранить шаблон: ${it.message}" }
    }

    fun addLines(text: String, target: String) {
        val parsed = text.lines().mapNotNull { parseFullNameLine(it, target) }
        var added = 0
        parsed.forEach { s -> if (students.none { it == s }) { students += s; added++ } }
        save()
        emptyClasses = emptyClasses - target
        status = when {
            parsed.isEmpty() -> "Напишите фамилию и имя через пробел, например: Иванов Пётр"
            added == parsed.size -> if (added == 1) "Добавлен: ${parsed.first().surname} ${parsed.first().name}" else "Добавлено учеников: $added"
            else -> "Добавлено $added, остальные уже есть в $target"
        }
        if (parsed.isNotEmpty()) quickAdd = ""
    }

    fun move(list: List<JournalStudent>, to: String) {
        val target = normalizeJournalClassName(to)
        var moved = 0
        list.forEach { s ->
            val i = students.indexOf(s)
            val updated = s.copy(className = target)
            if (i >= 0 && students.none { it == updated }) { students[i] = updated; moved++ }
        }
        save()
        selected.clear()
        emptyClasses = emptyClasses - target
        status = "Переведено в $target: $moved" + if (moved < list.size) " (остальные там уже есть)" else ""
    }

    fun remove(list: List<JournalStudent>) {
        students.removeAll(list.toSet())
        save()
        selected.clear()
        undo = list
        status = if (list.size == 1) "Удалён: ${list.first().surname} ${list.first().name}" else "Удалено учеников: ${list.size}"
    }

    ScreenScaffold(onBack = { onAction(JournalsAction.Back) }, title = state.title) { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            Text(
                "${ruCount(classes.size, "класс", "класса", "классов")} · ${studentsWord(students.size)}. Хранится только на этом телефоне.",
                style = MaterialTheme.typography.bodyMedium,
                color = Scrapbook.InkMuted,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по всем классам") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            status?.let { msg ->
                SettingsShellCard(paper = Scrapbook.Lemon) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        undo?.let { back ->
                            TextButton(onClick = {
                                back.forEach { s -> if (students.none { it == s }) students += s }
                                save(); undo = null; status = if (back.size == 1) "Ученик возвращён в журнал" else "Возвращено в журнал: ${studentsWord(back.size)}"
                            }) { Text("Вернуть") }
                        }
                    }
                }
            }

            val q = query.trim()
            if (q.isNotEmpty()) {
                val found = students.filter { it.matchesSearch(q) }
                    .sortedWith(compareBy({ it.surname.lowercase(ru) }, { it.name.lowercase(ru) }))
                SettingsShellCard {
                    Text("Найдено: ${found.size}", fontWeight = FontWeight.Bold)
                    found.forEach { s ->
                        StudentLine(
                            s, showClass = true, checked = s in selected, selecting = selected.isNotEmpty(),
                            onToggle = { toggle(selected, s) },
                            onEdit = { editStudent = s },
                            onMove = { moveDialog = listOf(s) },
                            onDelete = { deleteDialog = listOf(s) },
                        )
                    }
                    if (found.isEmpty()) Text("Никого не нашлось.", color = Scrapbook.InkMuted)
                }
            } else {
                // Классы — кнопками; «+ Класс» добавляет новый.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { c ->
                        val n = students.count { it.className == c }
                        ClassChip("$c · $n", active = c == cls) { current = c; selected.clear() }
                    }
                    ClassChip("+ Класс", active = false, dashed = true) { newClassDialog = true }
                }

                if (cls == null) {
                    SettingsShellCard {
                        Text(state.listEmptyText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Нажмите «+ Класс», чтобы создать класс, или загрузите список из Excel внизу.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Scrapbook.InkMuted,
                        )
                    }
                } else {
                    val list = students.filter { it.className == cls }
                        .sortedWith(compareBy({ it.surname.lowercase(ru) }, { it.name.lowercase(ru) }))
                    SettingsShellCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cls, style = MaterialTheme.typography.headlineSmall, fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(studentsWord(list.size), color = Scrapbook.InkMuted)
                            var menu by remember(cls) { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с классом") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Переименовать класс") }, onClick = { menu = false; renameClass = cls })
                                    DropdownMenuItem(text = { Text("Перевести весь класс…") }, enabled = list.isNotEmpty(), onClick = { menu = false; moveDialog = list })
                                    DropdownMenuItem(text = { Text("Удалить класс") }, onClick = { menu = false; deleteClass = cls })
                                }
                            }
                        }
                        // Быстрый ввод: одна строка — один ученик; можно вставить сразу весь список.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quickAdd,
                                onValueChange = { quickAdd = it },
                                label = { Text("Фамилия Имя", maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f),
                                maxLines = 6,
                            )
                            ShellBubblePrimaryButton(
                                text = "Добавить",
                                onClick = { addLines(quickAdd, cls) },
                                enabled = quickAdd.isNotBlank(),
                                paletteIndex = 1,
                                fillMaxWidth = false,
                            )
                        }
                        Text(
                            "Можно вставить сразу список — по одному ученику в строке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Scrapbook.InkMuted,
                        )
                        if (list.isEmpty()) {
                            Text("В классе пока никого нет.", color = Scrapbook.InkMuted, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Checkbox(
                                    checked = list.all { it in selected },
                                    onCheckedChange = { on -> if (on) list.forEach { if (it !in selected) selected += it } else selected.removeAll(list.toSet()) },
                                )
                                Text(if (selected.isEmpty()) "Отметить всех" else "Отмечено: ${selected.size}", fontWeight = FontWeight.SemiBold)
                            }
                            list.forEach { s ->
                                StudentLine(
                                    s, showClass = false, checked = s in selected, selecting = selected.isNotEmpty(),
                                    onToggle = { toggle(selected, s) },
                                    onEdit = { editStudent = s },
                                    onMove = { moveDialog = listOf(s) },
                                    onDelete = { deleteDialog = listOf(s) },
                                )
                            }
                        }
                    }
                }
            }

            if (selected.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShellBubblePrimaryButton("Перевести (${selected.size})", { moveDialog = selected.toList() }, Modifier.weight(1f), paletteIndex = 3)
                    ShellBubblePrimaryButton("Удалить (${selected.size})", { deleteDialog = selected.toList() }, Modifier.weight(1f), paletteIndex = 5)
                }
                ShellBubbleOutlinedButton(text = "Снять отметки", onClick = { selected.clear() })
            }

            SettingsShellCard {
                Text("Загрузить список", fontWeight = FontWeight.Bold)
                Text(state.importHint, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                ShellBubblePrimaryButton(text = state.importButtonText, onClick = { importLauncher.launch("*/*") }, paletteIndex = 2)
                ShellBubbleOutlinedButton(text = state.templateButtonText, onClick = { templateLauncher.launch("Шаблон_журнала.xlsx") })
            }
        }
    }

    if (newClassDialog) {
        TextDialog(
            title = "Новый класс",
            label = "Например: 7 А",
            initial = "",
            confirm = "Создать",
            onConfirm = { name ->
                val c = normalizeJournalClassName(name)
                if (c.isNotBlank()) { emptyClasses = emptyClasses + c; current = c; status = "Класс $c создан — добавьте учеников" }
                newClassDialog = false
            },
            onDismiss = { newClassDialog = false },
        )
    }
    renameClass?.let { old ->
        TextDialog(
            title = "Название класса",
            label = "Новое название",
            initial = old,
            confirm = "Сохранить",
            onConfirm = { name ->
                val c = normalizeJournalClassName(name)
                if (c.isNotBlank() && c != old) {
                    val merged = students.any { it.className == c }
                    for (i in students.indices) if (students[i].className == old) students[i] = students[i].copy(className = c)
                    val dedup = students.distinct(); students.clear(); students.addAll(dedup)
                    save(); emptyClasses = emptyClasses - old; current = c
                    status = if (merged) "Класс объединён с $c" else "Класс переименован: $c"
                }
                renameClass = null
            },
            onDismiss = { renameClass = null },
        )
    }
    pendingImport?.let { result ->
        val found = result.students.map { it.normalized() }
        val fresh = found.filter { s -> students.none { it == s } }
        val byClass = found.groupBy { it.className }.toSortedMap(::classSort)
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Загрузить ${studentsWord(found.size)}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(byClass.entries.joinToString(" · ") { (c, l) -> "$c — ${l.size}" }, fontWeight = FontWeight.Bold)
                    if (fresh.size < found.size) Text("Уже есть в журнале: ${found.size - fresh.size} — второй раз не добавятся.")
                    if (result.skipped.isNotEmpty()) {
                        Text(
                            "Не прочитаны строки: " + result.skipped.take(6).joinToString(", ") { (n, why) -> "$n ($why)" } +
                                if (result.skipped.size > 6) " и ещё ${result.skipped.size - 6}." else ".",
                            color = Scrapbook.InkMuted,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fresh.forEach { students += it }
                    save()
                    status = "Импорт: добавлено ${fresh.size}" +
                        (if (fresh.size < found.size) ", уже были в журнале: ${found.size - fresh.size}" else "") +
                        ". Всего в журнале ${students.size}."
                    pendingImport = null
                }) { Text(if (fresh.isEmpty()) "Понятно" else "Добавить") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } },
        )
    }
    deleteClass?.let { c ->
        val list = students.filter { it.className == c }
        AlertDialog(
            onDismissRequest = { deleteClass = null },
            title = { Text("Удалить класс $c?") },
            text = { Text(if (list.isEmpty()) "Класс пуст." else "Из журнала уйдут ${studentsWord(list.size)}. Это можно отменить кнопкой «Вернуть».") },
            confirmButton = { TextButton(onClick = { remove(list); emptyClasses = emptyClasses - c; deleteClass = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteClass = null }) { Text("Отмена") } },
        )
    }
    moveDialog?.let { list ->
        MoveDialog(
            count = list.size,
            from = list.map { it.className }.distinct().singleOrNull(),
            classes = classes,
            onMove = { to -> move(list, to); moveDialog = null },
            onDismiss = { moveDialog = null },
        )
    }
    deleteDialog?.let { list ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text(if (list.size == 1) "Удалить ученика?" else "Удалить ${studentsWord(list.size)}?") },
            text = { Text(list.take(5).joinToString("\n") { "${it.surname} ${it.name} (${it.className})" } + if (list.size > 5) "\n…" else "") },
            confirmButton = { TextButton(onClick = { remove(list); deleteDialog = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteDialog = null }) { Text("Отмена") } },
        )
    }
    editStudent?.let { s ->
        EditStudentDialog(
            s,
            onSave = { upd ->
                val n = upd.normalized()
                val i = students.indexOf(s)
                when {
                    n.className.isBlank() || n.surname.isBlank() || n.name.isBlank() -> status = state.editStudentValidationError
                    n != s && students.any { it == n } -> status = state.editStudentDuplicateError
                    i >= 0 -> { students[i] = n; save(); status = "Сохранено"; editStudent = null }
                }
            },
            onDismiss = { editStudent = null },
        )
    }
}

private fun toggle(list: SnapshotStateList<JournalStudent>, s: JournalStudent) {
    if (s in list) list.remove(s) else list.add(s)
}

@Composable
private fun ClassChip(text: String, active: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = if (active) Color.White else Scrapbook.Ink,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Scrapbook.Ink else if (dashed) Scrapbook.Mint else Scrapbook.Paper)
            .border(2.dp, Scrapbook.Ink, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudentLine(
    s: JournalStudent,
    showClass: Boolean,
    checked: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember(s) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) Scrapbook.Sky else Color.Transparent)
            .combinedClickable(onClick = { if (selecting) onToggle() else onEdit() }, onLongClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(s.surname, s.name, s.middleName).joinToString(" "),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showClass) Text(s.className, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Действия с учеником") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Изменить") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Перевести в другой класс") }, onClick = { menu = false; onMove() })
                DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveDialog(count: Int, from: String?, classes: List<String>, onMove: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Перевести в класс" else "Перевести ${studentsWord(count)} в класс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.filter { it != from }.forEach { c -> ClassChip(c, active = false) { onMove(c) } }
                }
                OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("или новый класс, например 8 А") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onMove(custom) }, enabled = custom.isNotBlank()) { Text("В новый класс") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TextDialog(title: String, label: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(v.trim()) }, enabled = v.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EditStudentDialog(s: JournalStudent, onSave: (JournalStudent) -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(s.className) }
    var sn by remember { mutableStateOf(s.surname) }
    var nm by remember { mutableStateOf(s.name) }
    var md by remember { mutableStateOf(s.middleName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Данные ученика") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = sn, onValueChange = { sn = it }, label = { Text("Фамилия") }, singleLine = true)
                OutlinedTextField(value = nm, onValueChange = { nm = it }, label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(value = md, onValueChange = { md = it }, label = { Text("Отчество (необязательно)") }, singleLine = true)
                OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("Класс") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(JournalStudent(c.trim(), sn.trim(), nm.trim(), md.trim().ifBlank { null })) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** «1 класс», «3 класса», «5 классов». */
private fun ruCount(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}

private fun studentsWord(n: Int) = ruCount(n, "ученик", "ученика", "учеников")
