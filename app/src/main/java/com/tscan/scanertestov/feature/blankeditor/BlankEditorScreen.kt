package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: главный экран конструктора бланков с параметрами, предпросмотром и экспортом.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.journals.JournalRepository
import com.tscan.scanertestov.feature.journals.JournalStudent
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.widget.Toast

@Composable
fun BlankEditorScreen(
    onAction: (BlankEditorAction) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var questionsText by rememberSaveable { mutableStateOf("10") }
    var optionsText by rememberSaveable { mutableStateOf("5") }
    var columnCount by rememberSaveable { mutableIntStateOf(1) }
    var bubbleSizeText by rememberSaveable { mutableStateOf("30") }
    var bubbleFontSizeText by rememberSaveable { mutableStateOf("15") }
    var gapHorizontalText by rememberSaveable { mutableStateOf("10") }
    var gapVerticalText by rememberSaveable { mutableStateOf("10") }
    var gridPaddingText by rememberSaveable { mutableStateOf("5") }
    var borderWidthText by rememberSaveable { mutableStateOf("7") }
    var variantMarkerEnabled by rememberSaveable { mutableStateOf(false) }
    var studentNameMarkerEnabled by rememberSaveable { mutableStateOf(false) }
    var studentNamePrintedMode by rememberSaveable { mutableStateOf(false) }
    var studentSurnameText by rememberSaveable { mutableStateOf("") }
    var studentNameText by rememberSaveable { mutableStateOf("") }
    var variantPrintedMode by rememberSaveable { mutableStateOf(false) }
    var variantNumberText by rememberSaveable { mutableStateOf("1") }
    var advancedParamsExpanded by rememberSaveable { mutableStateOf(false) }

    val questionCount = questionsText.toIntOrNull()?.coerceIn(1, 35) ?: 10
    val optionCount = optionsText.toIntOrNull()?.coerceIn(2, 9) ?: 5
    val bubbleRenderParams = remember(
        bubbleSizeText,
        bubbleFontSizeText,
        gapHorizontalText,
        gapVerticalText,
        gridPaddingText,
        borderWidthText,
    ) {
        BlankBubbleRenderParams(
            bubbleSizePx = clampBubbleSizePx(bubbleSizeText.toFloatOrNull() ?: BlankBubbleDefaults.BUBBLE_SIZE_PX),
            fontSizePx = clampBubbleFontSizePx(bubbleFontSizeText.toFloatOrNull() ?: BlankBubbleDefaults.FONT_SIZE_PX),
            cellGapHorizontalPx = clampCellGapHorizontalPx(
                gapHorizontalText.toFloatOrNull() ?: BlankBubbleDefaults.CELL_GAP_HORIZONTAL_PX
            ),
            cellGapVerticalPx = clampCellGapVerticalPx(
                gapVerticalText.toFloatOrNull() ?: BlankBubbleDefaults.CELL_GAP_VERTICAL_PX
            ),
            gridPaddingPx = clampGridPaddingPx(gridPaddingText.toFloatOrNull() ?: BlankBubbleDefaults.GRID_PADDING_PX),
            borderWidthPx = clampGridBorderWidthPx(borderWidthText.toFloatOrNull() ?: BlankBubbleDefaults.BORDER_WIDTH_PX),
        )
    }
    val layoutConfig = remember(questionCount, optionCount, columnCount) {
        BlankEditorLayoutConfig(
            questionCount = questionCount,
            optionCount = optionCount,
            columnCount = columnCount,
        )
    }

    val bubbleLayoutForPan = remember(layoutConfig, bubbleRenderParams) {
        buildBlankBubbleLayout(layoutConfig, bubbleRenderParams)
    }

    var selection by remember { mutableStateOf<BlankEditorSelection>(BlankEditorSelection.Blank) }
    var textBlocks by remember { mutableStateOf(defaultBlankTextBlocks()) }
    var alignmentGuides by remember { mutableStateOf(emptyList<AlignmentGuide>()) }
    var hiddenBubbleIds by remember { mutableStateOf(emptySet<String>()) }
    var blankPanLogical by remember { mutableStateOf(Offset.Zero) }
    var blankScale by remember { mutableStateOf(1f) }
    var panAtGestureStart by remember { mutableStateOf(Offset.Zero) }
    var isExporting by remember { mutableStateOf(false) }

    val journalRepository = remember(context) { JournalRepository(context.applicationContext) }
    var journalStudents by remember { mutableStateOf<List<JournalStudent>>(emptyList()) }
    LaunchedEffect(Unit) {
        journalStudents = withContext(Dispatchers.IO) { journalRepository.loadAll() }
    }
    val journalClasses = remember(journalStudents) { journalDistinctClasses(journalStudents) }

    var showJournalExportDialog by remember { mutableStateOf(false) }
    var exportFormatKey by rememberSaveable { mutableStateOf("pdf") }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    var journalExportSelectedClass by rememberSaveable { mutableStateOf("") }
    var journalVariantModeKey by rememberSaveable { mutableStateOf("single") }
    var journalPngOutputModeKey by rememberSaveable { mutableStateOf("zip") }
    var journalSingleVariantText by rememberSaveable { mutableStateOf("1") }
    var journalCycleVariantMaxText by rememberSaveable { mutableStateOf("4") }

    LaunchedEffect(journalClasses) {
        if (journalClasses.isNotEmpty() && journalExportSelectedClass !in journalClasses) {
            journalExportSelectedClass = journalClasses.first()
        }
    }

    val journalDialogScroll = rememberScrollState()

    fun journalVariantModeFromKey(): BlankJournalVariantExportMode = when (journalVariantModeKey) {
        "none" -> BlankJournalVariantExportMode.None
        "cycle" -> BlankJournalVariantExportMode.Cycle
        else -> BlankJournalVariantExportMode.Single
    }

    fun updateTextBlock(id: String, transform: (BlankTextBlock) -> BlankTextBlock) {
        textBlocks = textBlocks.map { if (it.id == id) transform(it) else it }
    }
    val showTextElements = textBlocks.any { it.isVisible }

    fun scaleMinus() {
        when (val s = selection) {
            BlankEditorSelection.Blank -> {
                val next = clampBlankScale(blankScale - 0.05f)
                blankScale = next
                blankPanLogical = clampBlankPanOnSheet(blankPanLogical, bubbleLayoutForPan, next)
            }
            is BlankEditorSelection.Text -> {
                updateTextBlock(s.id) { it.copy(scale = clampTextScale(it.scale - 0.05f)) }
            }
            BlankEditorSelection.None -> Unit
        }
    }

    fun scalePlus() {
        when (val s = selection) {
            BlankEditorSelection.Blank -> {
                val next = clampBlankScale(blankScale + 0.05f)
                blankScale = next
                blankPanLogical = clampBlankPanOnSheet(blankPanLogical, bubbleLayoutForPan, next)
            }
            is BlankEditorSelection.Text -> {
                updateTextBlock(s.id) { it.copy(scale = clampTextScale(it.scale + 0.05f)) }
            }
            BlankEditorSelection.None -> Unit
        }
    }

    fun buildSingleExportSnapshot(): BlankSheetExportSnapshot = BlankSheetExportSnapshot(
        layoutConfig = layoutConfig,
        renderParams = bubbleRenderParams,
        hiddenBubbleIds = hiddenBubbleIds,
        blankPanLogical = blankPanLogical,
        blankScale = blankScale,
        textBlocks = textBlocks,
        showVariantMarker = variantMarkerEnabled,
        showStudentNameMarker = studentNameMarkerEnabled,
        studentNamePrintedMode = studentNamePrintedMode,
        studentSurnameText = studentSurnameText,
        studentNameText = studentNameText,
        variantPrintedMode = variantPrintedMode,
        variantPrintedNumber = variantNumberText.ifBlank { "1" },
    )

    fun exportSingleSheet(format: String) {
        if (isExporting) return
        val snapshot = buildSingleExportSnapshot()
        isExporting = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (format) {
                        "png-bg" -> {
                            val bitmap = renderBlankSheetBitmap(snapshot, withBackground = true)
                            try {
                                saveBlankSheetPng(context, bitmap, withBackground = true)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        "png-transparent" -> {
                            val bitmap = renderBlankSheetBitmap(snapshot, withBackground = false)
                            try {
                                saveBlankSheetPng(context, bitmap, withBackground = false)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        "pdf" -> {
                            val bitmap = renderBlankSheetBitmap(snapshot, withBackground = true)
                            try {
                                saveBlankSheetPdf(context, bitmap)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        else -> {
                            val bitmap = renderBlankSheetBitmap(snapshot, withBackground = true)
                            try {
                                saveBlankSheetDocx(context, bitmap)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            isExporting = false
            result.onSuccess { file ->
                Toast
                    .makeText(
                        context,
                        "Файл подготовлен (${file.name}). Откроется окно «Отправить»…",
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                runCatching { shareExportedFile(context, file) }
            }.onFailure {
                Toast
                    .makeText(context, "Не удалось подготовить файл", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun requestExport() {
        if (isExporting) return
        pendingExportFormat = exportFormatKey
        if (journalVariantModeKey !in listOf("none", "single", "cycle")) {
            journalVariantModeKey = "single"
        }
        showJournalExportDialog = true
    }

    fun runJournalBatchExport(format: String, className: String) {
        if (isExporting) return
        val roster = studentsInClass(journalStudents, className)
        if (roster.isEmpty()) {
            Toast.makeText(context, "В выбранном классе нет учеников.", Toast.LENGTH_SHORT).show()
            return
        }
        val variantModeResolved = if (variantMarkerEnabled && variantPrintedMode) {
            journalVariantModeFromKey()
        } else {
            BlankJournalVariantExportMode.None
        }
        val cycleMax = journalCycleVariantMaxText.toIntOrNull()?.coerceIn(2, 4) ?: 4
        val safeClassPrefix = className.trim()
            .replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
            .take(24)
            .ifBlank { "class" }
        isExporting = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val rendered = ArrayList<Bitmap>(roster.size)
                    val zipEntries = ArrayList<Pair<String, Bitmap>>(roster.size)
                    try {
                        for ((index, student) in roster.withIndex()) {
                            val snap = buildJournalExportSnapshot(
                                layoutConfig = layoutConfig,
                                renderParams = bubbleRenderParams,
                                hiddenBubbleIds = hiddenBubbleIds,
                                blankPanLogical = blankPanLogical,
                                blankScale = blankScale,
                                textBlocks = textBlocks,
                                variantMarkerEnabled = variantMarkerEnabled,
                                variantPrintedMode = variantPrintedMode,
                                variantNumberText = variantNumberText,
                                studentNameMarkerEnabled = studentNameMarkerEnabled,
                                studentNamePrintedMode = studentNamePrintedMode,
                                studentSurnameFallback = studentSurnameText,
                                studentNameFallback = studentNameText,
                                student = student,
                                studentIndex = index,
                                variantMode = variantModeResolved,
                                singleVariantText = journalSingleVariantText,
                                cycleVariantMax = cycleMax,
                            )
                            val withBg = when (format) {
                                "png-transparent" -> false
                                else -> true
                            }
                            val bmp = renderBlankSheetBitmap(snap, withBackground = withBg)
                            rendered.add(bmp)
                            zipEntries.add(journalZipEntryBaseName(index, student) to bmp)
                        }
                        withContext(Dispatchers.IO) {
                            when (format) {
                                "png-bg" -> {
                                    val files = if (journalPngOutputModeKey == "files") {
                                        saveBlankSheetsPngSeparate(
                                            context,
                                            zipEntries,
                                            withBackground = true,
                                            filePrefix = "blank-class-$safeClassPrefix",
                                        )
                                    } else {
                                        listOf(
                                            saveBlankSheetsPngZip(
                                                context,
                                                zipEntries,
                                                withBackground = true,
                                                filePrefix = "blank-class-$safeClassPrefix",
                                            ),
                                        )
                                    }
                                    files
                                }
                                "png-transparent" -> {
                                    val files = if (journalPngOutputModeKey == "files") {
                                        saveBlankSheetsPngSeparate(
                                            context,
                                            zipEntries,
                                            withBackground = false,
                                            filePrefix = "blank-class-$safeClassPrefix",
                                        )
                                    } else {
                                        listOf(
                                            saveBlankSheetsPngZip(
                                                context,
                                                zipEntries,
                                                withBackground = false,
                                                filePrefix = "blank-class-$safeClassPrefix",
                                            ),
                                        )
                                    }
                                    files
                                }
                                "pdf" -> listOf(
                                    saveBlankSheetPdfMultiPage(
                                        context,
                                        rendered,
                                        filePrefix = "blank-class-$safeClassPrefix",
                                    ),
                                )
                                else -> saveBlankSheetsDocxSeparate(
                                    context,
                                    zipEntries,
                                    filePrefix = "blank-class-$safeClassPrefix",
                                )
                            }
                        }
                    } finally {
                        rendered.forEach { it.recycle() }
                    }
                }
            }
            isExporting = false
            result.onSuccess { files ->
                Toast.makeText(
                    context,
                    if (files.size == 1) {
                        "Пакет из ${roster.size} листов: ${files.first().name}. Откроется окно «Отправить»…"
                    } else {
                        "Подготовлено ${files.size} файлов. Откроется окно «Отправить»…"
                    },
                    Toast.LENGTH_LONG,
                ).show()
                runCatching { shareExportedFiles(context, files) }
            }.onFailure {
                Toast.makeText(context, "Не удалось подготовить пакет файлов", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(layoutConfig) {
        hiddenBubbleIds = emptySet()
        blankPanLogical = Offset.Zero
        blankScale = 1f
        selection = BlankEditorSelection.Blank
        panAtGestureStart = Offset.Zero
        alignmentGuides = emptyList()
    }

    ScreenScaffold(
        onBack = { onAction(BlankEditorAction.Back) },
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp, vertical = 4.dp)
                .verticalScroll(scrollState),
        ) {
            BlankEditorParametersPanel(
                questionsText = questionsText,
                optionsText = optionsText,
                columnCount = columnCount,
                onQuestionsTextChange = { questionsText = it },
                onOptionsTextChange = { optionsText = it },
                onColumnCountChange = { columnCount = it },
                modifier = Modifier.padding(start = 4.dp),
            )
            SettingsShellCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Масштаб выбранного:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val scaleMinusIx = remember { MutableInteractionSource() }
                    val scalePlusIx = remember { MutableInteractionSource() }
                    val scaleMinusPal = mainMenuBubbleGradient(4)
                    val scalePlusPal = mainMenuBubbleGradient(2)
                    RealtimeBubbleIconButton(
                        onClick = { scaleMinus() },
                        contentDescription = "Уменьшить масштаб",
                        topColor = scaleMinusPal.first,
                        bottomColor = scaleMinusPal.second,
                        size = 44.dp,
                        interactionSource = scaleMinusIx,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    RealtimeBubbleIconButton(
                        onClick = { scalePlus() },
                        contentDescription = "Увеличить масштаб",
                        topColor = scalePlusPal.first,
                        bottomColor = scalePlusPal.second,
                        size = 44.dp,
                        interactionSource = scalePlusIx,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (val s = selection) {
                            BlankEditorSelection.Blank -> "${(blankScale * 100).toInt()}%"
                            is BlankEditorSelection.Text -> {
                                val block = textBlocks.firstOrNull { it.id == s.id }
                                "${(((block?.scale ?: 1f) * 100).toInt())}%"
                            }
                            BlankEditorSelection.None -> "--%"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SettingsShellCard(
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                A4SheetPage(
                layoutConfig = layoutConfig,
                renderParams = bubbleRenderParams,
                hiddenBubbleIds = hiddenBubbleIds,
                blankPanLogical = blankPanLogical,
                blankScale = blankScale,
                textBlocks = textBlocks,
                showVariantMarker = variantMarkerEnabled,
                showStudentNameMarker = studentNameMarkerEnabled,
                studentNamePrintedMode = studentNamePrintedMode,
                studentSurnameText = studentSurnameText,
                studentNameText = studentNameText,
                variantPrintedMode = variantPrintedMode,
                variantPrintedNumber = variantNumberText.ifBlank { "1" },
                showTextElements = showTextElements,
                alignmentGuides = alignmentGuides,
                selection = selection,
                onBlankPanDragStart = { panAtGestureStart = blankPanLogical },
                onBlankPanDragAccumulatedLogical = { acc ->
                    blankPanLogical = clampBlankPanOnSheet(panAtGestureStart + acc, bubbleLayoutForPan, blankScale)
                },
                onBlankPanDragRevertForTap = { blankPanLogical = panAtGestureStart },
                onTextBlockTap = { id ->
                    selection = BlankEditorSelection.Text(id)
                },
                onTextBlockDragStart = { id ->
                    selection = BlankEditorSelection.Text(id)
                    alignmentGuides = emptyList()
                },
                onTextBlockDragDeltaLogical = { id, delta ->
                    updateTextBlock(id) { block ->
                        val newPos = clampTextPositionOnSheet(block.position + delta)
                        alignmentGuides = computeAlignmentGuides(
                            movingId = id,
                            movingPos = newPos,
                            blocks = textBlocks,
                        )
                        block.copy(position = newPos)
                    }
                },
                onTextBlockDragEnd = {
                    alignmentGuides = emptyList()
                },
                onBlankTap = {
                    selection = BlankEditorSelection.Blank
                    alignmentGuides = emptyList()
                },
                onTapOutsideElements = {
                    selection = BlankEditorSelection.None
                    alignmentGuides = emptyList()
                },
                onBubbleToggle = { id ->
                    val next = applyBubbleVisibilityToggle(id, optionCount, hiddenBubbleIds)
                    if (next != null) hiddenBubbleIds = next
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BlankSheetSpec.aspectRatio),
                horizontalPadding = 6.dp,
                verticalPadding = 4.dp,
            )
            }

            BlankEditorMarkerFieldsPanel(
                variantMarkerEnabled = variantMarkerEnabled,
                studentNameMarkerEnabled = studentNameMarkerEnabled,
                studentNamePrintedMode = studentNamePrintedMode,
                studentSurnameText = studentSurnameText,
                studentNameText = studentNameText,
                variantPrintedMode = variantPrintedMode,
                variantNumberText = variantNumberText,
                onVariantMarkerEnabledChange = { variantMarkerEnabled = it },
                onStudentNameMarkerEnabledChange = { studentNameMarkerEnabled = it },
                onStudentNamePrintedModeChange = { studentNamePrintedMode = it },
                onStudentSurnameTextChange = { studentSurnameText = it },
                onStudentNameTextChange = { studentNameText = it },
                onVariantPrintedModeChange = { variantPrintedMode = it },
                onVariantNumberTextChange = { variantNumberText = it },
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )

            SettingsShellCard(
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Показывать все текстовые элементы",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ShellBubbleSwitch(
                            checked = showTextElements,
                            onCheckedChange = { checked ->
                                textBlocks = textBlocks.map { it.copy(isVisible = checked) }
                                if (!checked && selection is BlankEditorSelection.Text) {
                                    selection = BlankEditorSelection.Blank
                                }
                                alignmentGuides = emptyList()
                            },
                        )
                    }
                    textBlocks.forEach { block ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Показывать: ${block.label}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ShellBubbleSwitch(
                                checked = block.isVisible,
                                onCheckedChange = { checked ->
                                    updateTextBlock(block.id) { it.copy(isVisible = checked) }
                                    if (!checked && selection == BlankEditorSelection.Text(block.id)) {
                                        selection = BlankEditorSelection.Blank
                                    }
                                    alignmentGuides = emptyList()
                                },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = block.text,
                                onValueChange = { raw ->
                                    updateTextBlock(block.id) { it.copy(text = raw) }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(block.label) },
                            )
                            OutlinedTextField(
                                value = block.fontSizePx.toInt().toString(),
                                onValueChange = { raw ->
                                    val parsed = raw.filter { it.isDigit() }.take(2).toIntOrNull()
                                    if (parsed != null) {
                                        updateTextBlock(block.id) {
                                            it.copy(fontSizePx = clampTextFontSizePx(parsed.toFloat()))
                                        }
                                    }
                                },
                                modifier = Modifier.width(96.dp),
                                singleLine = true,
                                label = { Text("Шрифт") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                }
            }

            BlankEditorAdvancedParametersPanel(
                expanded = advancedParamsExpanded,
                bubbleSizeText = bubbleSizeText,
                bubbleFontSizeText = bubbleFontSizeText,
                gapHorizontalText = gapHorizontalText,
                gapVerticalText = gapVerticalText,
                gridPaddingText = gridPaddingText,
                borderWidthText = borderWidthText,
                onExpandedChange = { advancedParamsExpanded = it },
                onBubbleSizeTextChange = { bubbleSizeText = it },
                onBubbleFontSizeTextChange = { bubbleFontSizeText = it },
                onGapHorizontalTextChange = { gapHorizontalText = it },
                onGapVerticalTextChange = { gapVerticalText = it },
                onGridPaddingTextChange = { gridPaddingText = it },
                onBorderWidthTextChange = { borderWidthText = it },
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )

            SettingsShellCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Подготовка файла",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Файл создаётся во временной папке приложения; затем открывается системное окно «Отправить» (не автосохранение в галерею).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShellBubblePrimaryButton(
                        text = "Экспорт",
                        onClick = { requestExport() },
                        enabled = !isExporting,
                        paletteIndex = 3,
                    )
                }
            }

            val pendingFmt = pendingExportFormat
            if (showJournalExportDialog && pendingFmt != null) {
                fun formatLabelRu(f: String): String = when (f) {
                    "png-bg" -> "PNG с фоном"
                    "png-transparent" -> "PNG без фона"
                    "pdf" -> "PDF"
                    else -> "DOCX"
                }
                val journalDlgScrimIx = remember(pendingFmt) { MutableInteractionSource() }
                val journalDlgCardIx = remember(pendingFmt) { MutableInteractionSource() }
                Dialog(
                    onDismissRequest = {
                        showJournalExportDialog = false
                        pendingExportFormat = null
                    },
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
                                interactionSource = journalDlgScrimIx,
                            ) {
                                showJournalExportDialog = false
                                pendingExportFormat = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .fillMaxHeight(0.88f)
                                .clickable(
                                    indication = null,
                                    interactionSource = journalDlgCardIx,
                                ) { },
                        ) {
                            SettingsShellCard(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                            ) {
                            val journalCloseIx = remember(pendingFmt, "close") { MutableInteractionSource() }
                            val journalClosePal = mainMenuBubbleGradient(5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Пакет по журналу",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                RealtimeBubbleIconButton(
                                    onClick = {
                                        showJournalExportDialog = false
                                        pendingExportFormat = null
                                    },
                                    contentDescription = "Закрыть",
                                    topColor = journalClosePal.first,
                                    bottomColor = journalClosePal.second,
                                    size = 48.dp,
                                    interactionSource = journalCloseIx,
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
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color.White.copy(alpha = 0.14f),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(journalDialogScroll),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                            Text(
                                text = "Формат: ${formatLabelRu(pendingFmt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Формат файла", style = MaterialTheme.typography.labelLarge)
                            listOf(
                                "pdf" to "PDF",
                                "docx" to "DOCX",
                                "png-bg" to "PNG с фоном",
                                "png-transparent" to "PNG без фона",
                            ).forEach { (key, label) ->
                                val selected = pendingFmt == key
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected,
                                            onClick = {
                                                pendingExportFormat = key
                                                exportFormatKey = key
                                            },
                                            role = Role.RadioButton,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selected, onClick = null)
                                    Text(label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            Text("Класс", style = MaterialTheme.typography.labelLarge)
                            if (journalClasses.isEmpty()) {
                                Text(
                                    text = "Классы не найдены в журнале. Доступен режим «Один файл».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                journalClasses.forEach { cls ->
                                    val selected = cls == journalExportSelectedClass
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = selected,
                                                onClick = { journalExportSelectedClass = cls },
                                                role = Role.RadioButton,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                        )
                                        Text(
                                            text = cls,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }
                            if (variantMarkerEnabled && variantPrintedMode) {
                                Text("Номер варианта на бланке", style = MaterialTheme.typography.labelLarge)
                                listOf(
                                    "none" to "Не печатать номер",
                                    "single" to "Один номер для всех",
                                    "cycle" to "По очереди: 1, 2, 3, 4, затем снова 1",
                                ).forEach { (key, label) ->
                                    val sel = journalVariantModeKey == key
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = sel,
                                                onClick = { journalVariantModeKey = key },
                                                role = Role.RadioButton,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = sel, onClick = null)
                                        Text(label, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                if (journalVariantModeKey == "single") {
                                    OutlinedTextField(
                                        value = journalSingleVariantText,
                                        onValueChange = { journalSingleVariantText = it.filter { ch -> ch.isDigit() }.take(1) },
                                        label = { Text("Номер (1–4)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (journalVariantModeKey == "cycle") {
                                    OutlinedTextField(
                                        value = journalCycleVariantMaxText,
                                        onValueChange = { raw ->
                                            journalCycleVariantMaxText = raw.filter { it.isDigit() }.take(1)
                                        },
                                        label = { Text("Сколько вариантов чередовать (2–4)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (pendingFmt == "png-bg" || pendingFmt == "png-transparent") {
                                Text("Как сохранить PNG", style = MaterialTheme.typography.labelLarge)
                                listOf(
                                    "zip" to "Одним ZIP-архивом",
                                    "files" to "Отдельными PNG-файлами",
                                ).forEach { (key, label) ->
                                    val sel = journalPngOutputModeKey == key
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = sel,
                                                onClick = { journalPngOutputModeKey = key },
                                                role = Role.RadioButton,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = sel, onClick = null)
                                        Text(label, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color.White.copy(alpha = 0.14f),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShellBubbleOutlinedButton(
                                    text = "Один файл",
                                    onClick = {
                                        exportSingleSheet(pendingFmt)
                                        showJournalExportDialog = false
                                        pendingExportFormat = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    fillMaxWidth = true,
                                )
                                ShellBubblePrimaryButton(
                                    text = "По классу",
                                    onClick = {
                                        if (journalClasses.isEmpty() || journalExportSelectedClass.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                "Для режима «По классу» сначала заполните журнал.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } else {
                                            runJournalBatchExport(pendingFmt, journalExportSelectedClass)
                                            showJournalExportDialog = false
                                            pendingExportFormat = null
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    fillMaxWidth = true,
                                    paletteIndex = 1,
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
