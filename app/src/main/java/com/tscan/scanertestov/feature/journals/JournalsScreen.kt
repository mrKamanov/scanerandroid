package com.tscan.scanertestov.feature.journals

/**
 * Описание: экран журналов — сводка, поиск, состав по классам (карточки), импорт и ручной ввод.
 */
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayInputStream
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private val JournalSectionGap = 20.dp
private val JournalCardShape = RoundedCornerShape(16.dp)
private val JournalTileShape = RoundedCornerShape(14.dp)
private val JournalTableHeaderShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val JournalInsetBg = Color.White.copy(alpha = 0.08f)
private val JournalInsetBorder = Color.White.copy(alpha = 0.16f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalsScreen(
    state: JournalsState = JournalsState(),
    onAction: (JournalsAction) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val journalRepository = remember(appContext) { JournalRepository(appContext) }
    val students = remember { mutableStateListOf<JournalStudent>() }

    fun persistStudents() {
        journalRepository.saveAll(students.toList())
    }

    LaunchedEffect(Unit) {
        val raw = journalRepository.loadAll()
        val normalized = raw.map { it.normalized() }.distinct()
        students.clear()
        students.addAll(normalized)
        if (normalized != raw) {
            journalRepository.saveAll(normalized)
        }
    }
    var className by rememberSaveable { mutableStateOf("") }
    var surname by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var middleName by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showDataEntry by rememberSaveable { mutableStateOf(false) }
    val classExpanded = remember { mutableStateMapOf<String, Boolean>() }

    val filteredStudents = remember(students.size, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) students.toList()
        else students.filter { it.matchesSearch(q) }
    }

    val byClassSorted = remember(filteredStudents.size, searchQuery) {
        filteredStudents
            .groupBy { normalizeJournalClassName(it.className) }
            .entries
            .sortedWith(compareBy({ it.key.lowercase(Locale("ru")) }))
    }

    val classCount = remember(students.size) {
        students.map { normalizeJournalClassName(it.className) }.distinct().size
    }

    var classRenameTarget by remember { mutableStateOf<String?>(null) }
    var classRenameDraft by remember { mutableStateOf("") }

    var studentPendingRemoval by remember { mutableStateOf<JournalStudent?>(null) }
    var studentBeingEdited by remember { mutableStateOf<JournalStudent?>(null) }
    var editDraftClass by remember { mutableStateOf("") }
    var editDraftSurname by remember { mutableStateOf("") }
    var editDraftName by remember { mutableStateOf("") }
    var editDraftMiddle by remember { mutableStateOf("") }

    fun removeStudent(student: JournalStudent) {
        val target = student.normalized()
        students.removeAll { it.normalized() == target }
        persistStudents()
        statusMessage = "Ученик удалён из журнала"
    }

    fun saveEditedStudent(original: JournalStudent) {
        val cls = editDraftClass.trim()
        val sn = editDraftSurname.trim()
        val nm = editDraftName.trim()
        val mn = editDraftMiddle.trim()
        if (cls.isBlank() || sn.isBlank() || nm.isBlank()) {
            statusMessage = state.editStudentValidationError
            return
        }
        val updated =
            JournalStudent(
                className = cls,
                surname = sn,
                name = nm,
                middleName = mn.ifBlank { null },
            ).normalized()
        val origNorm = original.normalized()
        val idx = students.indexOfFirst { it.normalized() == origNorm }
        if (idx < 0) {
            studentBeingEdited = null
            return
        }
        val duplicate =
            students.indices.any { i ->
                i != idx && students[i].normalized() == updated
            }
        if (duplicate) {
            statusMessage = state.editStudentDuplicateError
            return
        }
        students[idx] = updated
        persistStudents()
        studentBeingEdited = null
        statusMessage = "Данные ученика сохранены"
    }

    fun applyClassRename(oldName: String, newName: String) {
        val trimmed = normalizeJournalClassName(newName.trim())
        if (trimmed.isBlank()) {
            statusMessage = state.renameClassEmptyError
            return
        }
        val oldKey = normalizeJournalClassName(oldName.trim())
        if (oldKey == trimmed) {
            classRenameTarget = null
            return
        }
        val mergedWithExisting =
            students.any { normalizeJournalClassName(it.className) == trimmed }
        for (i in students.indices) {
            if (normalizeJournalClassName(students[i].className) == oldKey) {
                val s = students[i]
                students[i] = s.copy(className = trimmed)
            }
        }
        val wasExpanded = classExpanded[oldKey] ?: false
        classExpanded.remove(oldKey)
        classExpanded[trimmed] = wasExpanded || (classExpanded[trimmed] ?: false)
        persistStudents()
        classRenameTarget = null
        statusMessage =
            if (mergedWithExisting) {
                String.format(Locale.getDefault(), state.renameClassMergedHint, trimmed)
            } else {
                "Класс переименован: $trimmed"
            }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                parseStudentsFromBytes(input.readBytes())
            }.orEmpty()
        }.onSuccess { imported ->
            var added = 0
            imported.forEach { item ->
                val n = item.normalized()
                val alreadyExists = students.any { it.normalized() == n }
                if (!alreadyExists) {
                    students += n
                    added++
                }
            }
            persistStudents()
            statusMessage = "Импорт завершен: добавлено $added, всего ${students.size}"
        }.onFailure { error ->
            statusMessage = "Ошибка импорта: ${error.message ?: "неизвестная ошибка"}"
        }
    }

    fun addStudentManually() {
        val cls = className.trim()
        val sn = surname.trim()
        val nm = name.trim()
        val mn = middleName.trim()
        if (cls.isBlank() || sn.isBlank() || nm.isBlank()) {
            statusMessage = "Заполните обязательные поля: класс, фамилия, имя."
            return
        }
        val candidate =
            JournalStudent(
                className = cls,
                surname = sn,
                name = nm,
                middleName = mn.ifBlank { null },
            ).normalized()
        if (students.any { it.normalized() == candidate }) {
            statusMessage = state.addStudentDuplicateError
            return
        }
        students += candidate
        className = ""
        surname = ""
        name = ""
        middleName = ""
        persistStudents()
        statusMessage = "Ученик добавлен. Всего в журнале: ${students.size}"
    }

    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val file = buildJournalTemplateXlsx(context)
            val bytes = file.readBytes()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Не удалось открыть место сохранения")
            statusMessage = "Шаблон сохранен."
        }.onFailure { error ->
            statusMessage = "Не удалось сохранить шаблон: ${error.message ?: "ошибка"}"
        }
    }

    ScreenScaffold(
        onBack = { onAction(JournalsAction.Back) }
    ) { padding ->
        ScreenContentColumn(
            padding = padding,
            verticalScroll = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(JournalSectionGap),
            ) {
                JournalHeroHeader(state = state)

                SettingsShellCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        JournalSectionTitle(text = state.summarySectionTitle)
                        JournalSummaryStrip(
                            state = state,
                            classCount = classCount,
                            totalStudents = students.size,
                        )
                    }
                }

                SettingsShellCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        JournalSectionTitle(text = state.searchSectionTitle)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(state.searchLabel) },
                            singleLine = true,
                            shape = JournalCardShape,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JournalSectionTitle(text = state.rosterSectionTitle)
                when {
                    students.isEmpty() -> {
                        SettingsShellCard {
                            Text(
                                text = state.listEmptyText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    byClassSorted.isEmpty() -> {
                        SettingsShellCard {
                            Text(
                                text = state.searchNoResultsText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            byClassSorted.forEach { (cls, list) ->
                                key(cls) {
                                val expanded = classExpanded[cls] ?: false
                                JournalClassRosterCard(
                                    state = state,
                                    className = cls,
                                    students = list.sortedWith(
                                        compareBy(
                                            { it.surname.lowercase(Locale("ru")) },
                                            { it.name.lowercase(Locale("ru")) },
                                        ),
                                    ),
                                    expanded = expanded,
                                    onToggleExpand = {
                                        classExpanded[cls] = !(classExpanded[cls] ?: false)
                                    },
                                    onRenameClass = {
                                        classRenameTarget = cls
                                        classRenameDraft = cls
                                    },
                                    onEditStudent = { s ->
                                        studentBeingEdited = s
                                        editDraftClass = s.className
                                        editDraftSurname = s.surname
                                        editDraftName = s.name
                                        editDraftMiddle = s.middleName?.trim().orEmpty()
                                    },
                                    onAskRemoveStudent = { studentPendingRemoval = it },
                                )
                                }
                            }
                        }
                    }
                }
                }

                ShellBubbleOutlinedButton(
                    text = if (showDataEntry) state.dataEntryToggleHide else state.dataEntryToggleShow,
                    onClick = { showDataEntry = !showDataEntry },
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = showDataEntry,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    SettingsShellCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = state.dataEntrySectionTitle,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = state.importHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ShellBubblePrimaryButton(
                                    text = state.templateButtonText,
                                    onClick = { saveTemplateLauncher.launch("journal-template.xlsx") },
                                    paletteIndex = 0,
                                )
                                ShellBubbleOutlinedButton(
                                    text = state.importButtonText,
                                    onClick = { importLauncher.launch("*/*") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                            OutlinedTextField(
                                value = className,
                                onValueChange = { className = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.classLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = surname,
                                onValueChange = { surname = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.surnameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.nameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = middleName,
                                onValueChange = { middleName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.middleNameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            ShellBubblePrimaryButton(
                                text = state.addButtonText,
                                onClick = { addStudentManually() },
                                paletteIndex = 1,
                            )
                        }
                    }
                }

                statusMessage?.let { msg ->
                    SettingsShellCard {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }

        val renameKey = classRenameTarget
        if (renameKey != null) {
            val renameScrimIx = remember(renameKey) { MutableInteractionSource() }
            val renameCardIx = remember(renameKey) { MutableInteractionSource() }
            Dialog(
                onDismissRequest = { classRenameTarget = null },
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
                            interactionSource = renameScrimIx,
                        ) { classRenameTarget = null },
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsShellCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable(
                                indication = null,
                                interactionSource = renameCardIx,
                            ) { },
                    ) {
                        Text(
                            text = state.renameClassDialogTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = classRenameDraft,
                            onValueChange = { classRenameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(state.renameClassFieldLabel) },
                            singleLine = true,
                            shape = JournalCardShape,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            ShellBubbleOutlinedButton(
                                text = state.renameClassDialogCancel,
                                onClick = { classRenameTarget = null },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                            )
                            ShellBubblePrimaryButton(
                                text = state.renameClassDialogSave,
                                onClick = { applyClassRename(renameKey, classRenameDraft) },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                                paletteIndex = 1,
                            )
                        }
                    }
                }
            }
        }

        val removal = studentPendingRemoval
        if (removal != null) {
            val remScrimIx = remember(removal) { MutableInteractionSource() }
            val remCardIx = remember(removal) { MutableInteractionSource() }
            Dialog(
                onDismissRequest = { studentPendingRemoval = null },
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
                            interactionSource = remScrimIx,
                        ) { studentPendingRemoval = null },
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsShellCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable(
                                indication = null,
                                interactionSource = remCardIx,
                            ) { },
                    ) {
                        Text(
                            text = state.removeStudentConfirmTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                buildString {
                                    append(removal.surname)
                                    append(" ")
                                    append(removal.name)
                                    removal.middleName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                        append(" ")
                                        append(it)
                                    }
                                    append(", класс ")
                                    append(removal.className)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = state.removeStudentConfirmHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            ShellBubbleOutlinedButton(
                                text = state.removeStudentConfirmNegative,
                                onClick = { studentPendingRemoval = null },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                            )
                            ShellBubblePrimaryButton(
                                text = state.removeStudentConfirmPositive,
                                onClick = {
                                    removeStudent(removal)
                                    studentPendingRemoval = null
                                },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                                paletteIndex = 5,
                            )
                        }
                    }
                }
            }
        }

        val editing = studentBeingEdited
        if (editing != null) {
            val scroll = rememberScrollState()
            val editScrimIx = remember(editing) { MutableInteractionSource() }
            val editCardIx = remember(editing) { MutableInteractionSource() }
            Dialog(
                onDismissRequest = { studentBeingEdited = null },
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
                            interactionSource = editScrimIx,
                        ) { studentBeingEdited = null },
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsShellCard(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 520.dp)
                            .clickable(
                                indication = null,
                                interactionSource = editCardIx,
                            ) { },
                    ) {
                        Text(
                            text = state.editStudentDialogTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.White.copy(alpha = 0.14f),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = state.editStudentTransferHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = editDraftClass,
                                onValueChange = { editDraftClass = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.classLabel) },
                                placeholder = { Text(state.editStudentClassHint) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = editDraftSurname,
                                onValueChange = { editDraftSurname = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.surnameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = editDraftName,
                                onValueChange = { editDraftName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.nameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                            OutlinedTextField(
                                value = editDraftMiddle,
                                onValueChange = { editDraftMiddle = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(state.middleNameLabel) },
                                singleLine = true,
                                shape = JournalCardShape,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        ) {
                            ShellBubbleOutlinedButton(
                                text = state.editStudentCancelButton,
                                onClick = { studentBeingEdited = null },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                            )
                            ShellBubblePrimaryButton(
                                text = state.editStudentSaveButton,
                                onClick = { saveEditedStudent(editing) },
                                modifier = Modifier.weight(1f),
                                fillMaxWidth = false,
                                paletteIndex = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalHeroHeader(state: JournalsState) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JournalSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun JournalSummaryStrip(
    state: JournalsState,
    classCount: Int,
    totalStudents: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JournalStatTile(
            label = state.statClassesLabel,
            value = classCount.toString(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 92.dp)
                .fillMaxHeight(),
        )
        JournalStatTile(
            label = state.statStudentsLabel,
            value = totalStudents.toString(),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 92.dp)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun JournalStatTile(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(JournalTileShape)
            .background(JournalInsetBg)
            .border(1.dp, JournalInsetBorder, JournalTileShape)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JournalClassRosterCard(
    state: JournalsState,
    className: String,
    students: List<JournalStudent>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRenameClass: () -> Unit,
    onEditStudent: (JournalStudent) -> Unit,
    onAskRemoveStudent: (JournalStudent) -> Unit,
) {
    val renameIx = remember(className, "rename") { MutableInteractionSource() }
    val expandIx = remember(className, "expand") { MutableInteractionSource() }
    val renamePal = mainMenuBubbleGradient(0)
    val expandPal = mainMenuBubbleGradient(4)
    SettingsShellCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(JournalCardShape)
                    .background(JournalInsetBg)
                    .border(1.dp, JournalInsetBorder, JournalCardShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Класс $className",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${students.size} учеников",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeBubbleIconButton(
                        onClick = onRenameClass,
                        contentDescription = state.renameClassAction,
                        topColor = renamePal.first,
                        bottomColor = renamePal.second,
                        size = 48.dp,
                        interactionSource = renameIx,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    RealtimeBubbleIconButton(
                        onClick = onToggleExpand,
                        contentDescription = if (expanded) state.expandHide else state.expandShow,
                        topColor = expandPal.first,
                        bottomColor = expandPal.second,
                        size = 48.dp,
                        interactionSource = expandIx,
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(JournalTableHeaderShape)
                            .background(JournalInsetBg)
                            .border(1.dp, JournalInsetBorder, JournalTableHeaderShape)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.rosterFioHeader,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(96.dp))
                    }
                    students.forEachIndexed { index, item ->
                        val sk = "${item.className}_${item.surname}_${item.name}_${item.middleName.orEmpty()}"
                        key(sk, index) {
                            val editIx = remember(sk, "edit") { MutableInteractionSource() }
                            val delIx = remember(sk, "del") { MutableInteractionSource() }
                            val editPal = mainMenuBubbleGradient(3)
                            val delPal = mainMenuBubbleGradient(5)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp),
                                ) {
                                    Text(
                                        text = item.surname,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Clip,
                                    )
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Clip,
                                    )
                                    val patronymic = item.middleName?.trim().orEmpty()
                                    if (patronymic.isNotEmpty()) {
                                        Text(
                                            text = patronymic,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 6,
                                            overflow = TextOverflow.Clip,
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    RealtimeBubbleIconButton(
                                        onClick = { onEditStudent(item) },
                                        contentDescription = state.editStudentAction,
                                        topColor = editPal.first,
                                        bottomColor = editPal.second,
                                        size = 44.dp,
                                        interactionSource = editIx,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Create,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    RealtimeBubbleIconButton(
                                        onClick = { onAskRemoveStudent(item) },
                                        contentDescription = state.removeStudentAction,
                                        topColor = delPal.first,
                                        bottomColor = delPal.second,
                                        size = 44.dp,
                                        interactionSource = delIx,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            if (index < students.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun JournalStudent.matchesSearch(query: String): Boolean {
    val q = query.lowercase(Locale("ru"))
    return className.lowercase(Locale("ru")).contains(q) ||
        surname.lowercase(Locale("ru")).contains(q) ||
        name.lowercase(Locale("ru")).contains(q) ||
        (middleName?.lowercase(Locale("ru"))?.contains(q) == true)
}

private fun parseStudentsCsv(csvRaw: String): List<JournalStudent> {
    return parseStudentsCsvInternal(csvRaw)
}

private fun parseStudentsFromBytes(bytes: ByteArray): List<JournalStudent> {
    return parseStudentsFromBytesInternal(bytes)
}

private fun parseStudentsXlsx(bytes: ByteArray): List<JournalStudent> {
    return parseStudentsXlsxInternal(bytes)
}

private fun parseSharedStrings(xml: String): List<String> {
    return parseSharedStringsInternal(xml)
}

private fun parseStudentsFromSheetXml(sheetXml: String, sharedStrings: List<String>): List<JournalStudent> {
    return parseStudentsFromSheetXmlInternal(sheetXml, sharedStrings)
}

private fun buildJournalTemplateXlsx(context: android.content.Context): File {
    return buildJournalTemplateXlsxInternal(context)
}

private fun buildSharedStringsXml(values: List<String>): String {
    return buildSharedStringsXmlInternal(values)
}

private fun xmlEscape(value: String): String {
    return xmlEscapeInternal(value)
}

private fun putZipTextEntry(zip: ZipOutputStream, path: String, content: String) {
    putZipTextEntryInternal(zip, path, content)
}
