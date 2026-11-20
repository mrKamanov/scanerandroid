package com.tscan.scanertestov.feature.tests

/**
 * Описание: последний этап мастера теста — «Предпросмотр и печать».
 *
 * Лист показывается РОВНО таким, как будет напечатан: картинку рисует [renderBlankSheetBitmap] — тот же код,
 * что PDF/PNG/пакет на класс (метки бланка v2, без убранных кружков, реальные шрифты и поля). Двумя пальцами —
 * крупнее, двойное касание — вернуть. Ниже — как печатать (сколько бланков на листе, файл или именные на класс)
 * и одна кнопка «Сохранить и печатать». Оформление бланка здесь больше не меняется — только на этапе «Бланк».
 */
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.data.PrintedBlankParamsStore
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.blankeditor.BlankDesign
import com.tscan.scanertestov.feature.blankeditor.BlankJournalVariantExportMode
import com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec
import com.tscan.scanertestov.feature.blankeditor.blankTileOptions
import com.tscan.scanertestov.feature.blankeditor.buildJournalExportSnapshot
import com.tscan.scanertestov.feature.blankeditor.journalDistinctClasses
import com.tscan.scanertestov.feature.blankeditor.renderBlankSheetBitmap
import com.tscan.scanertestov.feature.blankeditor.ruPlural
import com.tscan.scanertestov.feature.blankeditor.saveBlankSheetPdf
import com.tscan.scanertestov.feature.blankeditor.saveBlankSheetPng
import com.tscan.scanertestov.feature.blankeditor.saveBlankSheetsPdfTiled
import com.tscan.scanertestov.feature.blankeditor.saveBlankSheetsPdfVector
import com.tscan.scanertestov.feature.blankeditor.shareExportedFile
import com.tscan.scanertestov.feature.blankeditor.studentsInClass
import com.tscan.scanertestov.feature.journals.JournalRepository
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что печатать. */
private enum class PrintKind(val title: String, val hint: String) {
    Pdf("Для печати (PDF)", "Файл A4 — отправить на принтер или себе."),
    Picture("Картинка в документ", "Только бланк, без пустого поля листа — вставить в свой Word."),
    Class("Именные на класс", "По листу на ученика: фамилия и имя напечатаны, код ученика закрашен."),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreviewStep(
    name: String,
    design: BlankDesign,
    variants: Int,
    gradingSummary: String,
    saveAsTemplate: Boolean,
    onSaveAsTemplate: (Boolean) -> Unit,
    templateName: String,
    onTemplateName: (String) -> Unit,
    /** Сохранить тест; null — не сохранён (ошибка показана мастером). */
    onSave: () -> BatchCriteriaPreset?,
    saved: BatchCriteriaPreset?,
    /** id теста, если он уже есть (правка) — чтобы показать уже выданные номера учеников. */
    testId: String?,
    /** Закрашивать номер варианта при печати (по странице на вариант) — когда кружки у вариантов одинаковые. */
    onVariantPrinted: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onList: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    // Бланк на каждый вариант с закрашенным номером: у вариантов разные кружки — всегда, иначе — если учитель выбрал.
    val differ = variants > 1 && (design.variantsDiffer || design.variantPrinted)
    var shownVariant by remember { mutableIntStateOf(1) }
    if (shownVariant > variants) shownVariant = 1
    fun snapFor(v: Int?) = design.exportSnapshot(variants, v)
    val snapshot = remember(design, variants, differ, shownVariant) { snapFor(if (differ) shownVariant else null) }

    // ——— Лист: тот же код, что печать.
    val sheet by produceState<Bitmap?>(null, snapshot) {
        value = withContext(Dispatchers.Default) { runCatching { renderBlankSheetBitmap(snapshot, withBackground = true, scale = 1.6f) }.getOrNull() }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (differ) {
                Hint(
                    if (design.variantsDiffer) "У вариантов разные кружки — у каждого свой бланк, номер варианта на нём уже закрашен."
                    else "Номер варианта закрашивается при печати — у каждого варианта свой лист.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..variants).forEach { v -> Pill("Вариант $v", shownVariant == v) { shownVariant = v } }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally)
                    .aspectRatio(BlankSheetSpec.aspectRatio)
                    .clipToBounds()
                    .background(Color.White)
                    .border(1.dp, Scrapbook.Ink.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { zoom = 1f; shift = Offset.Zero })
                    }
                    .pointerInput(Unit) {
                        // Двумя пальцами — увеличить и сдвинуть; одним — прокрутка экрана как обычно.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.count { it.pressed } >= 2) {
                                    val z = ev.calculateZoom()
                                    zoom = (zoom * z).coerceIn(1f, 5f)
                                    shift = if (zoom <= 1.01f) Offset.Zero else shift + ev.calculatePan()
                                    ev.changes.forEach { it.consume() }
                                }
                                if (ev.changes.none { it.pressed }) break
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = sheet
                if (bmp == null) {
                    CircularProgressIndicator(color = Scrapbook.Ink)
                } else {
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = "Бланк так, как он будет напечатан",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom; scaleY = zoom
                            translationX = shift.x; translationY = shift.y
                        },
                    )
                }
            }
            Hint("Так бланк выйдет на бумаге. Двумя пальцами — крупнее, двойное касание — вернуть.")
            Text(
                "${name.ifBlank { "Без названия" }} · ${design.questions} ${ruPlural(design.questions, "вопрос", "вопроса", "вопросов")} · " +
                    "$variants ${ruPlural(variants, "вариант", "варианта", "вариантов")} · оценки: $gradingSummary",
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
            )
        }
    }

    // ——— Как печатать.
    val tiles = remember(snapshot) { runCatching { blankTileOptions(snapshot) }.getOrDefault(emptyList()) }
    var perSheet by remember { mutableStateOf(1) }
    var kind by remember { mutableStateOf(PrintKind.Pdf) }
    val students = produceState(emptyList<com.tscan.scanertestov.feature.journals.JournalStudent>()) {
        value = withContext(Dispatchers.IO) { JournalRepository(app).loadAll() }
    }.value
    val classes = remember(students) { journalDistinctClasses(students) }
    var className by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Варианты учеников для именных бланков: по очереди по списку (1, 2, 1, 2…), нажатием — поменять.
    val variantOf = remember { mutableStateMapOf<String, Int>() }
    fun studentKey(st: com.tscan.scanertestov.feature.journals.JournalStudent) = "${st.surname}|${st.name}"
    // Уже выданные по тесту номера учеников (код на именном бланке) — новые ученики получат свободные.
    val idForCodes = saved?.id ?: testId
    var issued by remember(idForCodes) {
        mutableStateOf(idForCodes?.let { StudentCodeStore.load(app, it) }.orEmpty())
    }

    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Как печатать", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (variants > 1 && !design.variantsDiffer) {
                Label("Номер варианта")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Ученик отмечает сам", !design.variantPrinted) { onVariantPrinted(false) }
                    Pill("Закрасить при печати", design.variantPrinted) { onVariantPrinted(true) }
                }
                Hint(
                    if (design.variantPrinted) "PDF — по странице на каждый вариант с закрашенным номером; именные — варианты по очереди по списку."
                    else "Один бланк на все варианты: ученик сам закрасит кружок своего варианта.",
                )
            }
            PrintKind.entries.forEach { k ->
                Choice(selected = kind == k, title = k.title, subtitle = k.hint) { kind = k }
            }
            if (kind == PrintKind.Class) {
                if (classes.isEmpty()) {
                    Hint("Классов пока нет — добавьте учеников в «Журналы».")
                } else {
                    Label("Класс")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        classes.forEach { c -> Pill(c, (className ?: classes.first()) == c) { className = c } }
                    }
                    val cls0 = className ?: classes.first()
                    val roster = studentsInClass(students, cls0)
                    val codes = StudentCodeStore.plan(issued, roster)
                    Label(if (differ) "Ученики $cls0 · номера и варианты" else "Ученики $cls0 · номера")
                    Hint(
                        "Номер закрашен на бланке квадратами. Тест запомнит, кому какой номер выдан, — при проверке " +
                            "фамилия подставится сама. Для другого класса номера продолжатся." +
                            if (differ) " Варианты — по очереди по списку; нажмите на вариант, чтобы поменять." else "",
                    )
                    roster.forEachIndexed { i, st ->
                        val v = variantOf[studentKey(st)] ?: (i % variants + 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        ) {
                            Text(
                                codes[i]?.let { "№$it" } ?: "—",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Scrapbook.InkMuted,
                                modifier = Modifier.widthIn(min = 36.dp),
                            )
                            Text(
                                "${st.surname} ${st.name}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Scrapbook.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (differ) Pill("Вариант $v", true) { variantOf[studentKey(st)] = v % variants + 1 }
                        }
                    }
                    if (codes.any { it == null }) {
                        Hint("На бланке помещается ${StudentCodeStore.MAX_CODE} номера на тест — для учеников с «—» номеров не хватило.")
                    }
                }
            }
            if (kind != PrintKind.Picture) {
                val fit = tiles.filter { it.fits }
                if (fit.size > 1) {
                    Label("Бланков на листе A4")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fit.forEach { o -> Pill(if (o.perSheet == 1) "1 — во весь лист" else "${o.perSheet} — разрезать", perSheet == o.perSheet) { perSheet = o.perSheet } }
                    }
                }
            }
        }
    }

    if (saved == null) {
        SettingsShellCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { onSaveAsTemplate(!saveAsTemplate) }) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = onSaveAsTemplate)
                    Text("Сохранить оформление как шаблон", fontWeight = FontWeight.Bold)
                }
                if (saveAsTemplate) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = onTemplateName,
                        label = { Text("Название шаблона") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    fun print() {
        if (busy) return
        val test = saved ?: onSave() ?: return
        val cls = className ?: classes.firstOrNull()
        if (kind == PrintKind.Class && cls == null) {
            Toast.makeText(context, "Сначала добавьте класс в «Журналы».", Toast.LENGTH_LONG).show()
            return
        }
        PrintedBlankParamsStore.remember(app, design.render)
        busy = true
        scope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val opt = tiles.firstOrNull { it.perSheet == perSheet && it.fits }
                    when (kind) {
                        PrintKind.Pdf -> when {
                            // Разные бланки вариантов: по странице на вариант (при «N на листе» — страница из бланков одного варианта).
                            differ && (opt == null || opt.perSheet <= 1) ->
                                saveBlankSheetsPdfVector(app, (1..variants).map { snapFor(it) }, filePrefix = "blank-variants")
                            differ ->
                                saveBlankSheetsPdfTiled(app, (1..variants).flatMap { v -> List(opt!!.perSheet) { snapFor(v) } }, opt!!, filePrefix = "blank-variants-x${opt.perSheet}")
                            opt == null || opt.perSheet <= 1 -> saveBlankSheetPdf(app, snapshot)
                            else -> saveBlankSheetsPdfTiled(app, List(opt.perSheet) { snapshot }, opt, filePrefix = "blank-x${opt.perSheet}")
                        }
                        PrintKind.Picture -> {
                            val bmp = renderBlankSheetBitmap(snapshot, withBackground = false)
                            try { saveBlankSheetPng(app, bmp, withBackground = false) } finally { bmp.recycle() }
                        }
                        PrintKind.Class -> {
                            val roster = studentsInClass(students, cls!!)
                            // Номера — на весь тест: уже выданные остаются, новым ученикам — свободные.
                            val codes = StudentCodeStore.plan(StudentCodeStore.load(app, test.id), roster)
                            require(codes.none { it == null }) {
                                "по тесту уже выдано ${StudentCodeStore.MAX_CODE} номеров — для остальных учеников номеров не хватает"
                            }
                            val snaps = roster.mapIndexed { i0, st ->
                                val v = if (differ) variantOf[studentKey(st)] ?: (i0 % variants + 1) else null
                                // Код на бланке = studentIndex + 1.
                                val i = codes[i0]!! - 1
                                if (v != null) {
                                    return@mapIndexed buildJournalExportSnapshot(
                                        layoutConfig = design.layoutConfig,
                                        renderParams = design.render,
                                        hiddenBubbleIds = design.hiddenFor(v),
                                        blankPanLogical = design.pan,
                                        blankScale = design.scale,
                                        textBlocks = design.textBlocks,
                                        variantMarkerEnabled = true,
                                        variantPrintedMode = true,
                                        variantNumberText = v.toString(),
                                        studentNameMarkerEnabled = design.nameMarker,
                                        studentNamePrintedMode = true,
                                        studentSurnameFallback = "",
                                        studentNameFallback = "",
                                        student = st,
                                        studentIndex = i,
                                        variantMode = BlankJournalVariantExportMode.Single,
                                        singleVariantText = v.toString(),
                                        cycleVariantMax = variants.coerceIn(2, 4),
                                        v2Base = design.v2Options(variants),
                                    )
                                }
                                buildJournalExportSnapshot(
                                    layoutConfig = design.layoutConfig,
                                    renderParams = design.render,
                                    hiddenBubbleIds = design.hiddenBubbleIds,
                                    blankPanLogical = design.pan,
                                    blankScale = design.scale,
                                    textBlocks = design.textBlocks,
                                    variantMarkerEnabled = design.variantMarker,
                                    // Номер варианта не закрашен — ученик отметит сам (закрашенный — ветка выше).
                                    variantPrintedMode = false,
                                    variantNumberText = design.variantNumber,
                                    studentNameMarkerEnabled = design.nameMarker,
                                    studentNamePrintedMode = true,
                                    studentSurnameFallback = "",
                                    studentNameFallback = "",
                                    student = st,
                                    studentIndex = i,
                                    variantMode = BlankJournalVariantExportMode.Single,
                                    singleVariantText = design.variantNumber,
                                    cycleVariantMax = variants.coerceIn(2, 4),
                                    v2Base = design.v2Options(variants)?.copy(printedVariant = null),
                                )
                            }
                            require(snaps.isNotEmpty()) { "В классе нет учеников" }
                            val issuedNow = roster.mapIndexed { i0, st ->
                                IssuedStudentCode(
                                    code = codes[i0]!!,
                                    className = st.className,
                                    surname = st.surname,
                                    name = st.name,
                                    variant = if (differ) variantOf[studentKey(st)] ?: (i0 % variants + 1) else null,
                                )
                            }
                            StudentCodeStore.issue(app, test.id, issuedNow)
                            withContext(Dispatchers.Main) { issued = StudentCodeStore.load(app, test.id) }
                            val prefix = "blank-${test.name.take(20)}-${cls.take(10)}".replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
                            if (opt == null || opt.perSheet <= 1) saveBlankSheetsPdfVector(app, snaps, filePrefix = prefix)
                            else saveBlankSheetsPdfTiled(app, snaps, opt, filePrefix = prefix)
                        }
                    }
                }
            }
            busy = false
            file.onSuccess { f ->
                Toast.makeText(context, "Файл готов (${f.name}). Откроется окно «Отправить»…", Toast.LENGTH_SHORT).show()
                runCatching { shareExportedFile(context, f) }
            }.onFailure {
                Toast.makeText(context, "Не удалось подготовить файл: ${it.message ?: "ошибка"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ShellBubblePrimaryButton(
        text = when {
            busy -> "Готовлю файл…"
            saved == null -> "Сохранить тест и печатать"
            else -> "Печатать"
        },
        onClick = { print() },
        paletteIndex = 3,
    )
    if (saved == null) {
        ShellBubbleOutlinedButton(text = "Только сохранить тест", onClick = { onSave() })
    } else {
        Text("Тест сохранён ✓", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2F6B4F))
        ShellBubbleOutlinedButton(text = "Проверить класс", onClick = onCheck)
        TextButton(onClick = onList, modifier = Modifier.padding(top = 2.dp)) { Text("К списку тестов", color = Scrapbook.InkMuted) }
    }
}
