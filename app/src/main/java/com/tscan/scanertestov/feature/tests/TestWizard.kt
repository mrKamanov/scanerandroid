package com.tscan.scanertestov.feature.tests

/**
 * Описание: мастер «Новый тест» по шагам — Шаблон → Вопросы → Ответы → Бланк → Оценки → Готово.
 *
 * Тот же мастер открывается для правки теста («Изменить» на карточке, сразу на шаге «Вопросы» или на шаге,
 * с которого пришли) и для шаблонов (без шага «Ответы»): «Тесты» → «Шаблоны бланков».
 *
 * Оформление бланка (BlankDesign) хранится в тесте, поэтому число вопросов бланка всегда совпадает с ключом.
 * Шаблон — то же оформление + число вариантов + критерии оценки, применяется на первом шаге целиком.
 */
import androidx.compose.ui.draw.drawBehind
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaVariantAnswers
import com.tscan.scanertestov.feature.blankeditor.A4SheetPage
import com.tscan.scanertestov.feature.blankeditor.BlankBuiltInTemplates
import com.tscan.scanertestov.feature.blankeditor.BlankDesign
import com.tscan.scanertestov.feature.blankeditor.BlankEditorSelection
import com.tscan.scanertestov.feature.blankeditor.BlankSheetSpec
import com.tscan.scanertestov.feature.blankeditor.BlankTemplate
import com.tscan.scanertestov.feature.blankeditor.BlankTemplateStore
import com.tscan.scanertestov.feature.blankeditor.blankLayoutBoundingRectTransformed
import com.tscan.scanertestov.feature.blankeditor.blankLayoutCenter
import com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout
import com.tscan.scanertestov.feature.blankeditor.clampBlankPanOnSheet
import com.tscan.scanertestov.feature.blankeditor.clampBlankScale
import com.tscan.scanertestov.feature.blankeditor.clampBubbleSizePx
import com.tscan.scanertestov.feature.blankeditor.clampTextFontSizePx
import com.tscan.scanertestov.feature.blankeditor.clampTextPositionOnSheet
import com.tscan.scanertestov.feature.blankeditor.ruPlural
import com.tscan.scanertestov.navigation.AppDestinations
import com.tscan.scanertestov.ui.components.GradingCriteriaEditor
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch
import com.tscan.scanertestov.ui.components.isFilled
import com.tscan.scanertestov.ui.components.shortSummary
import com.tscan.scanertestov.ui.theme.Scrapbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Что выбрано на шаге «Бланк на листе». */
private sealed interface SheetElement {
    data class Text(val id: String) : SheetElement
    data object Name : SheetElement
    data object Variant : SheetElement
    data object Grid : SheetElement
}

/** Шаг стрелки: 2 мм на листе А4 (595 логических единиц = 210 мм). */
private const val ARROW_STEP = 595f / 210f * 2f

private fun stepTitle(s: TestWizardStep, templateMode: Boolean): String = when (s) {
    TestWizardStep.Name -> "Название"
    TestWizardStep.Template -> if (templateMode) "Шаблон" else "Бланк"
    TestWizardStep.Questions -> "Вопросы и кружки"
    TestWizardStep.Answers -> "Правильные ответы"
    TestWizardStep.Sheet -> "Бланк на листе"
    TestWizardStep.Grades -> "Критерии оценки"
    TestWizardStep.Done -> "Готово"
    TestWizardStep.Preview -> "Предпросмотр и печать"
}

@Composable
fun TestWizardRoute(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext
    val req = remember { PendingTestLaunch.takeWizardRequest() }
    val repo = remember { BatchCriteriaRepository(app) }
    val editing = remember { req.testId?.let { repo.getById(it) } }
    val templateMode = req.templateMode
    val editingTemplate = remember { req.templateId?.let { id -> BlankTemplateStore.load(app).firstOrNull { it.id == id } } }
    var userTemplates by remember { mutableStateOf(BlankTemplateStore.load(app)) }

    val steps = remember {
        if (templateMode) {
            listOf(TestWizardStep.Template, TestWizardStep.Questions, TestWizardStep.Sheet, TestWizardStep.Grades, TestWizardStep.Done)
        } else {
            listOf(TestWizardStep.Name, TestWizardStep.Template, TestWizardStep.Questions, TestWizardStep.Answers, TestWizardStep.Grades, TestWizardStep.Preview)
        }
    }
    // У теста «Бланк на листе» — не отдельный этап, а редактор внутри этапа «Бланк».
    var editSheet by remember { mutableStateOf(!templateMode && req.startStep == TestWizardStep.Sheet) }
    var stepIndex by remember {
        val start = when {
            !templateMode && req.startStep == TestWizardStep.Sheet -> TestWizardStep.Template
            req.startStep != null -> req.startStep
            editingTemplate != null -> TestWizardStep.Questions
            editing != null -> TestWizardStep.Questions
            templateMode -> TestWizardStep.Template
            else -> TestWizardStep.Name
        }
        mutableIntStateOf(steps.indexOf(start).coerceAtLeast(0))
    }
    val step = steps[stepIndex]

    // ——— Данные теста / шаблона ———
    val startTemplate = editingTemplate ?: BlankBuiltInTemplates.empty
    var name by remember { mutableStateOf(editing?.name ?: editingTemplate?.name.orEmpty()) }
    var design by remember { mutableStateOf(editing?.blankDesign() ?: startTemplate.design) }
    // Как было в шаблоне — для кнопки «Вернуть как было в шаблоне».
    var baseDesign by remember { mutableStateOf(design) }
    var appliedTemplateId by remember { mutableStateOf(if (editing == null) startTemplate.id else null) }
    var variants by remember { mutableIntStateOf(editing?.testVariantsCount ?: startTemplate.testVariants) }
    var currentVariant by remember { mutableIntStateOf(1) }
    val globalCriteria = remember { AppSettingsStore.getGradingCriteria(app) }
    val startGrading = editing?.gradingCriteriaJson ?: if (editing == null) startTemplate.gradingCriteriaJson else null
    var ownCriteria by remember { mutableStateOf(startGrading != null) }
    var criteria by remember { mutableStateOf(startGrading?.let { GradingCriteriaConfig.fromPrefsString(it) } ?: globalCriteria) }
    var saveAsTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var savedTest by remember { mutableStateOf<BatchCriteriaPreset?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Ключи по вариантам: список вопросов → множество номеров правильных кружков (с 0).
    val keys = remember {
        mutableStateListOf<List<Set<Int>>>().apply {
            for (v in 1..4) {
                val src = editing?.variantAnswerKeys?.firstOrNull { it.variant == v }?.correctAnswers
                    ?: if (v == 1) editing?.correctAnswers else null
                add(src?.map { it.filter { i -> i >= 0 }.toSet() } ?: emptyList())
            }
        }
    }
    val questions = design.questions
    val options = design.options
    fun keyFor(v: Int): List<Set<Int>> =
        List(questions) { q -> keys[v - 1].getOrNull(q)?.filter { it < options }?.toSet() ?: emptySet() }

    /** Сколько кружков вопроса [q] (с 0) осталось на бланке варианта [v]. */
    fun visibleOptions(q: Int, v: Int): Int {
        val h = design.hiddenFor(v)
        return options - (1..options).count { "${q + 1}.$it" in h }
    }

    /**
     * Снимает из ключа ответы на кружки, которых нет на бланке. Возвращает их названия («7.5») — чтобы сказать
     * учителю, что именно снято.
     */
    fun dropHiddenFromKeys(): List<String> {
        val removed = ArrayList<String>()
        for (v in 1..4) {
            val k = keys[v - 1]
            if (k.isEmpty()) continue
            val h = design.hiddenFor(v)
            val cleaned = k.mapIndexed { q, set ->
                set.filter { o -> "${q + 1}.${o + 1}" !in h }.toSet().also { kept ->
                    if (v <= variants) (set - kept).sorted().forEach { o -> removed += (if (variants > 1) "вар. $v: " else "") + "${q + 1}.${o + 1}" }
                }
            }
            if (cleaned != k) keys[v - 1] = cleaned
        }
        return removed
    }

    fun applyTemplate(t: BlankTemplate) {
        design = t.design
        baseDesign = t.design
        variants = t.testVariants
        if (currentVariant > variants) currentVariant = variants
        ownCriteria = t.gradingCriteriaJson != null
        criteria = t.gradingCriteriaJson?.let { GradingCriteriaConfig.fromPrefsString(it) } ?: globalCriteria
        appliedTemplateId = t.id
        error = null
    }

    fun setGrid(q: Int, o: Int, cols: Int = autoColumnsFor(q, o)) {
        design = design.withGrid(q.coerceIn(1, 35), o.coerceIn(2, 6), cols)
        error = null
    }

    fun setVariants(n: Int) {
        variants = n
        if (currentVariant > n) currentVariant = n
        design = design.cleanedVariantHidden(n)
        // Номер варианта на бланке обязателен, когда вариантов больше одного: по нему выбирается ключ.
        if (n > 1) design = design.copy(variantMarker = true)
    }

    /** Ошибка ключа: (вариант, вопрос, текст) — как на экране проверки: от 1 до (кружков − 1) ответов. */
    fun keyProblem(): Triple<Int, Int, String>? {
        for (v in 1..variants) {
            val k = keyFor(v)
            val q = k.indices.indexOfFirst { i -> k[i].isEmpty() || k[i].size >= visibleOptions(i, v) }
            if (q >= 0) {
                val where = if (variants > 1) "Вариант $v, вопрос ${q + 1}" else "Вопрос ${q + 1}"
                val msg = if (k[q].isEmpty()) "$where: не выбран правильный ответ." else "$where: выбраны все варианты. Оставьте только правильные."
                return Triple(v, q, msg)
            }
        }
        return null
    }

    fun goTo(i: Int) {
        error = null
        editSheet = false
        stepIndex = i.coerceIn(0, steps.lastIndex)
    }

    fun next() {
        when (step) {
            TestWizardStep.Answers -> keyProblem()?.let { (v, _, msg) ->
                error = msg
                currentVariant = v
                return
            }
            TestWizardStep.Grades -> if (ownCriteria && !criteria.isFilled()) {
                error = "Заполните границы «от» и «до» для каждой оценки или выберите общие критерии."
                return
            }
            else -> Unit
        }
        goTo(stepIndex + 1)
    }

    fun saveTemplate(templateTitle: String, id: String = UUID.randomUUID().toString()) {
        BlankTemplateStore.save(
            app,
            BlankTemplate(
                id = id,
                name = templateTitle,
                builtIn = false,
                design = design,
                testVariants = variants,
                gradingCriteriaJson = if (ownCriteria) criteria.toPrefsString() else null,
            ),
        )
        userTemplates = BlankTemplateStore.load(app)
    }

    fun saveTest(): BatchCriteriaPreset? {
        design = design.cleanedVariantHidden(variants)
        // Кружки могли убрать на шаге «Бланк» уже после ответов: ответ на убранный кружок снимаем и просим выбрать заново.
        val removed = dropHiddenFromKeys()
        if (removed.isNotEmpty()) {
            error = "Эти кружки убраны с бланка, ответ на них снят: ${removed.joinToString(", ")}. Выберите правильный ответ среди оставшихся."
            keyProblem()?.let { (v, _, _) -> currentVariant = v }
            stepIndex = steps.indexOf(TestWizardStep.Answers)
            return null
        }
        keyProblem()?.let { (v, _, msg) ->
            error = msg
            currentVariant = v
            stepIndex = steps.indexOf(TestWizardStep.Answers)
            return null
        }
        if (ownCriteria && !criteria.isFilled()) {
            error = "Критерии оценки: заполните границы «от» и «до» для каждой оценки."
            stepIndex = steps.indexOf(TestWizardStep.Grades)
            return null
        }
        val finalName = name.trim().ifBlank { "Тест " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date()) }
        if (repo.getAll().any { it.id != editing?.id && it.name.trim().equals(finalName, ignoreCase = true) }) {
            error = "Тест с названием «$finalName» уже есть. Назовите иначе (этап «Название»)."
            return null
        }
        if (saveAsTemplate && templateName.isBlank()) {
            error = "Впишите название шаблона или снимите галочку."
            return null
        }
        val preset = BatchCriteriaPreset(
            id = editing?.id ?: savedTest?.id ?: UUID.randomUUID().toString(),
            name = finalName,
            questionsCount = questions,
            choicesCount = options,
            columnCount = design.columns,
            correctAnswers = keyFor(1).map { it.sorted() },
            variantAnswerKeys = if (variants > 1) (1..variants).map { v -> BatchCriteriaVariantAnswers(v, keyFor(v).map { it.sorted() }) } else emptyList(),
            gradingCriteriaJson = if (ownCriteria) criteria.toPrefsString() else null,
            blankDesignJson = design.keptOnSheet().toJson().toString(),
        )
        val all = repo.getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == preset.id }
        if (idx >= 0) all[idx] = preset else all.add(preset)
        repo.saveAll(all)
        if (saveAsTemplate) {
            saveTemplate(templateName.trim())
            saveAsTemplate = false
        }
        name = finalName
        savedTest = preset
        error = null
        return preset
    }

    BackHandler(enabled = stepIndex > 0 && savedTest == null) { goTo(stepIndex - 1) }

    val title = when {
        templateMode && editingTemplate != null -> "Шаблон"
        templateMode -> "Новый шаблон"
        editing != null -> "Изменить тест"
        else -> "Новый тест"
    }
    val scroll = rememberScrollState()
    LaunchedEffect(stepIndex) { scroll.scrollTo(0) }

    ScreenScaffold(onBack = { navController.popBackStack() }, title = title) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Прогресс: полоски шагов (нажатие — переход), «Шаг N из M · Название».
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    steps.forEachIndexed { i, st ->
                        val shape = RoundedCornerShape(8.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    when {
                                        i == stepIndex -> Scrapbook.Mint
                                        i < stepIndex -> Scrapbook.Sky
                                        else -> Scrapbook.Paper
                                    },
                                    shape,
                                )
                                .border(2.dp, if (i == stepIndex) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.45f), shape)
                                .clickable(enabled = savedTest == null, role = Role.Tab, onClickLabel = stepTitle(st, templateMode)) { goTo(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", fontWeight = FontWeight.Bold, color = Scrapbook.Ink)
                        }
                    }
                }
                Text(
                    "${stepIndex + 1} из ${steps.size} · ${stepTitle(step, templateMode)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Scrapbook.Ink,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // На шаге «Бланк на листе» лист закреплён, прокручиваются только настройки под ним.
                    .then(if (step == TestWizardStep.Sheet || (step == TestWizardStep.Template && editSheet)) Modifier else Modifier.verticalScroll(scroll))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    TestWizardStep.Name -> NameStep(name = name, onName = { name = it.take(60); error = null })
                    TestWizardStep.Template -> if (editSheet) {
                        TextButton(onClick = { editSheet = false }) { Text("‹ К выбору шаблона", color = Scrapbook.Ink, fontWeight = FontWeight.Bold) }
                        SheetStep(
                            design = design,
                            baseDesign = baseDesign,
                            variants = variants,
                            onDesign = { design = it },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        TemplateStep(
                            userTemplates = userTemplates,
                            selectedId = appliedTemplateId,
                            editingNote = if (editing != null) "Шаблон заменит число вопросов, оформление бланка и критерии этого теста. Правильные ответы останутся." else null,
                            onPick = { applyTemplate(it) },
                            onManage = { navController.navigate(AppDestinations.TEMPLATES) },
                        )
                        if (!templateMode) {
                            ShellBubbleOutlinedButton(text = "Надписи и место бланка на листе", onClick = { editSheet = true })
                            Hint("Название, поле имени, размер и положение рамки. Можно не трогать — шаблон уже всё расставил.")
                        }
                    }
                    TestWizardStep.Questions -> QuestionsStep(
                        design = design,
                        variants = variants,
                        onGrid = { q, o, cols -> setGrid(q, o, cols) },
                        onVariants = { setVariants(it) },
                        onDesign = { design = it },
                    )
                    TestWizardStep.Answers -> {
                        LaunchedEffect(design.hiddenBubbleIds) {
                            val removed = dropHiddenFromKeys()
                            if (removed.isNotEmpty()) {
                                error = "Эти кружки убраны с бланка, ответ на них снят: ${removed.joinToString(", ")}. Выберите правильный ответ среди оставшихся."
                            }
                        }
                        AnswersStep(
                        hidden = design.hiddenFor(currentVariant),
                        questions = questions,
                        options = options,
                        variants = variants,
                        currentVariant = currentVariant,
                        onVariant = { currentVariant = it },
                        keyFor = { keyFor(it) },
                        onKeyChange = { v, k ->
                            keys[v - 1] = k
                            error = null
                        },
                    )
                    }
                    TestWizardStep.Sheet -> SheetStep(
                        design = design,
                        baseDesign = baseDesign,
                        variants = variants,
                        onDesign = { design = it },
                        modifier = Modifier.weight(1f),
                    )
                    TestWizardStep.Grades -> GradesStep(
                        ownCriteria = ownCriteria,
                        onOwn = { ownCriteria = it; error = null },
                        criteria = criteria,
                        onCriteria = { criteria = it; error = null },
                        globalSummary = globalCriteria.shortSummary(),
                        questions = questions,
                    )
                    TestWizardStep.Preview -> PreviewStep(
                        name = name,
                        design = design,
                        variants = variants,
                        gradingSummary = if (ownCriteria) "свои: ${criteria.shortSummary()}" else "общие из Настроек",
                        saveAsTemplate = saveAsTemplate,
                        onSaveAsTemplate = {
                            saveAsTemplate = it
                            if (it && templateName.isBlank()) templateName = name.trim().ifBlank { "Мой шаблон" }
                        },
                        templateName = templateName,
                        onTemplateName = { templateName = it.take(40) },
                        onSave = { saveTest() },
                        saved = savedTest,
                        testId = editing?.id,
                        onVariantPrinted = { design = design.copy(variantPrinted = it) },
                        onCheck = {
                            savedTest?.let {
                                PendingTestLaunch.openBatch(it.id)
                                navController.popBackStack()
                                navController.navigate(AppDestinations.BATCH_PROCESSING)
                            }
                        },
                        onList = { navController.popBackStack() },
                    )
                    TestWizardStep.Done -> if (templateMode) {
                        TemplateDoneStep(
                            design = design,
                            variants = variants,
                            name = name,
                            onName = { name = it.take(40) },
                            onSave = {
                                val n = name.trim()
                                if (n.isBlank()) {
                                    error = "Впишите название шаблона."
                                } else {
                                    saveTemplate(n, editingTemplate?.id ?: UUID.randomUUID().toString())
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = Scrapbook.Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Scrapbook.Rose, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (step != TestWizardStep.Done && step != TestWizardStep.Preview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Обе кнопки одного вида и ширины, отличаются только цветом.
                    if (stepIndex > 0) {
                        ShellBubblePrimaryButton(
                            text = "Назад",
                            onClick = { goTo(stepIndex - 1) },
                            modifier = Modifier.weight(1f),
                            paletteIndex = 4,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    ShellBubblePrimaryButton(
                        text = "Дальше",
                        onClick = { next() },
                        modifier = Modifier.weight(1f),
                        paletteIndex = 1,
                    )
                }
            }
        }
    }
}

// region Шаг 1. Шаблон

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateStep(
    userTemplates: List<BlankTemplate>,
    selectedId: String?,
    editingNote: String?,
    onPick: (BlankTemplate) -> Unit,
    onManage: () -> Unit,
) {
    Hint(
        "Шаблон сразу задаёт всё: сколько вопросов и ответов, как бланк лежит на листе, какие надписи на нём есть " +
            "и критерии оценки. На следующих шагах любое из этого можно поменять.",
    )
    editingNote?.let { Hint(it) }
    val all = userTemplates + BlankBuiltInTemplates.all
    all.chunked(3).forEach { row ->
        // Все карточки одного размера: одинаковая ширина и высота, текст — ровно 2 строки.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(186.dp),
        ) {
            row.forEach { t ->
                TemplateCard(t, selected = t.id == selectedId, modifier = Modifier.weight(1f).fillMaxHeight()) { onPick(t) }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Hint("★ — ваши шаблоны. Новый шаблон сохраняется на последнем шаге галочкой «Сохранить оформление как шаблон».")
    if (userTemplates.isNotEmpty()) {
        TextButton(onClick = onManage) { Text("Мои шаблоны: изменить или удалить", color = Scrapbook.Ink, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun TemplateCard(t: BlankTemplate, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .background(if (selected) Scrapbook.Mint.copy(alpha = 0.45f) else Scrapbook.Paper, shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.25f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SheetMini(t.design, Modifier.width(64.dp))
        Text(
            (if (t.builtIn) "" else "★ ") + t.name,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${t.design.questions} вопр. · ${t.design.options} отв.\n" +
                if (t.testVariants > 1) "${t.testVariants} вар." else if (t.design.columns == 2) "2 колонки" else "1 колонка",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = Scrapbook.InkMuted,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

/** Миниатюра листа: надписи серыми полосками, рамки с кружками, поле имени и шестиугольник. */
@Composable
internal fun SheetMini(design: BlankDesign, modifier: Modifier = Modifier) {
    val layout = remember(design) { buildBlankBubbleLayout(design.layoutConfig, design.render) }
    Canvas(
        modifier = modifier
            .aspectRatio(BlankSheetSpec.aspectRatio)
            .background(Color.White)
            .border(1.dp, Scrapbook.Ink.copy(alpha = 0.3f)),
    ) {
        val k = size.width / BlankSheetSpec.LOGICAL_WIDTH_PX
        design.textBlocks.filter { it.isVisible && it.text.isNotBlank() }.forEach { b ->
            val fs = b.fontSizePx * b.scale
            val w = (b.text.length * fs * 0.5f).coerceAtMost(BlankSheetSpec.LOGICAL_WIDTH_PX - b.position.x - 10f)
            drawRect(
                color = if (b.id == "title") Scrapbook.Ink else Scrapbook.InkMuted.copy(alpha = 0.7f),
                topLeft = Offset(b.position.x * k, (b.position.y + fs * 0.3f) * k),
                size = Size(w * k, (fs * 0.45f) * k),
            )
        }
        val c = blankLayoutCenter(layout)
        fun tx(x: Float) = (design.pan.x + c.x + (x - c.x) * design.scale) * k
        fun ty(y: Float) = (design.pan.y + c.y + (y - c.y) * design.scale) * k
        val r = blankLayoutBoundingRectTransformed(layout, design.pan, design.scale)
        if (design.nameMarker) {
            drawRect(Scrapbook.Ink, Offset((r.left + 44f) * k, (r.top - 90f) * k), Size(210f * k, 34f * k), style = Stroke(width = 1.5f))
        }
        if (design.variantMarker) {
            drawRect(Scrapbook.Ink, Offset(r.left * k, (r.top - 46f) * k), Size(34f * k, 34f * k), style = Stroke(width = 1.5f))
        }
        layout.frames.forEach { f ->
            drawRect(
                Scrapbook.Ink,
                Offset(tx(f.left), ty(f.top)),
                Size(f.width * design.scale * k, f.height * design.scale * k),
                style = Stroke(width = (design.render.borderWidthPx * design.scale * k).coerceAtLeast(1.5f)),
            )
        }
        val br = design.render.bubbleSizePx / 2f * design.scale * k
        layout.bubbles.forEach { b ->
            if (!b.isEmptyPlaceholder && b.id !in design.hiddenBubbleIds) {
                drawCircle(Scrapbook.Ink, radius = br, center = Offset(tx(b.centerX), ty(b.centerY)), style = Stroke(width = (br * 0.25f).coerceAtLeast(0.8f)))
            }
        }
    }
}

// endregion

// region Шаг 2. Вопросы

@Composable
private fun QuestionsStep(
    design: BlankDesign,
    variants: Int,
    onGrid: (Int, Int, Int) -> Unit,
    onVariants: (Int) -> Unit,
    onDesign: (BlankDesign) -> Unit,
) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stepper("Вопросов", design.questions, 1, 35) { onGrid(it, design.options, autoColumnsFor(it, design.options)) }
            Stepper("Ответов в вопросе", design.options, 2, 6) { onGrid(design.questions, it, autoColumnsFor(design.questions, it)) }
            Label("Вариантов теста")
            Segmented(listOf(1, 2, 3, 4), variants) { onVariants(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Колонки на бланке", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Pill("Одна", design.columns == 1) { onGrid(design.questions, design.options, 1) }
                Pill("Две", design.columns == 2) { onGrid(design.questions, design.options, 2) }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Scrapbook.Board.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetMini(design, Modifier.width(72.dp))
        Text(
            buildString {
                append(
                    if (autoColumnsFor(design.questions, design.options) == 2) {
                        "Вопросов больше 14 — на листе они встают в две колонки. "
                    } else {
                        "В одну колонку бланк помещается на лист. "
                    },
                )
                if (variants > 1) {
                    append(
                        if (design.formatV2) "Вариантов $variants — на бланке строка кружков «Вариант», ученик закрасит свой. "
                        else "Вариантов $variants — на бланке есть шестиугольник, куда ученик впишет номер. ",
                    )
                }
                append("Надписи и место рамки на листе — на этапе «Бланк».")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Scrapbook.InkMuted,
        )
    }
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Лишние кружки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Hint(
                "Если в каком-то вопросе ответов меньше — нажмите на лишний кружок: он и все правее уберутся " +
                    "(меньше двух оставить нельзя). Перечёркнутый кружок вернётся по нажатию. Убранных кружков не будет " +
                    "на бланке, их нельзя выбрать в ответах, проверка их не смотрит.",
            )
            BubblesPerQuestion(design, variants) { onDesign(it) }
        }
    }
}

// region Этап «Название»

@Composable
private fun NameStep(name: String, onName: (String) -> Unit) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Название теста") },
                placeholder = { Text("Контрольная № 3, 7 класс") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint(
                "По названию тест ищется в списке и в отчётах. Класс можно вписать прямо в название — " +
                    "а именные бланки на класс печатаются на последнем этапе.",
            )
        }
    }
}

// endregion

// endregion

// region Шаг 3. Ответы

@Composable
private fun AnswersStep(
    hidden: Set<String>,
    questions: Int,
    options: Int,
    variants: Int,
    currentVariant: Int,
    onVariant: (Int) -> Unit,
    keyFor: (Int) -> List<Set<Int>>,
    onKeyChange: (Int, List<Set<Int>>) -> Unit,
) {
    val context = LocalContext.current
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Hint(
                "Нажмите на правильный кружок в каждом вопросе. Если верных ответов несколько, нажмите на каждый. " +
                    "В каждом вопросе должен быть выбран хотя бы один ответ, но не все сразу.",
            )
            if (hidden.isNotEmpty()) {
                Hint(
                    if (variants > 1) "Серые перечёркнутые кружки убраны с бланка варианта $currentVariant — их выбрать нельзя."
                    else "Серые перечёркнутые кружки убраны с бланка — их выбрать нельзя.",
                )
            }
            if (variants > 1) {
                Label("Какой вариант заполняете")
                Segmented((1..variants).toList(), currentVariant) { onVariant(it) }
            }
            val key = keyFor(currentVariant)
            for (q in 0 until questions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${q + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    val visible = options - (1..options).count { "${q + 1}.$it" in hidden }
                    for (o in 0 until options) {
                        val on = o in key[q]
                        val removed = "${q + 1}.${o + 1}" in hidden
                        if (removed) {
                            // Убранный кружок: бледный, перечёркнутый, не нажимается.
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(max = 44.dp)
                                    .aspectRatio(1f)
                                    .border(1.dp, Scrapbook.InkMuted.copy(alpha = 0.35f), CircleShape)
                                    .drawBehind {
                                        drawLine(
                                            Scrapbook.InkMuted.copy(alpha = 0.45f),
                                            androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.82f),
                                            androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.18f),
                                            strokeWidth = 2.dp.toPx(),
                                        )
                                    }
                                    .semantics { contentDescription = "Кружок ${q + 1}.${o + 1} убран с бланка" },
                            )
                            continue
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 44.dp)
                                .aspectRatio(1f)
                                .background(if (on) Scrapbook.Ink else Scrapbook.Paper, CircleShape)
                                .border(2.dp, Scrapbook.Ink, CircleShape)
                                .clickable(role = Role.Checkbox) {
                                    // Те же правила, что на экране проверки.
                                    val cur = keyFor(currentVariant).toMutableList()
                                    if (on) {
                                        if (cur[q].size == 1) {
                                            android.widget.Toast.makeText(context, "В вопросе должен остаться минимум 1 выбранный вариант", android.widget.Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        cur[q] = cur[q] - o
                                    } else {
                                        if (cur[q].size >= visible - 1) {
                                            android.widget.Toast.makeText(context, "Нельзя выбрать все варианты в вопросе", android.widget.Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        cur[q] = cur[q] + o
                                    }
                                    onKeyChange(currentVariant, cur)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${q + 1}.${o + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (on) Color.White else Scrapbook.InkMuted,
                            )
                        }
                    }
                }
            }
            val filled = key.count { it.isNotEmpty() }
            Hint(
                "Задано: $filled из $questions" + if (variants > 1) {
                    " (" + (1..variants).joinToString(", ") { v -> "вариант $v — ${keyFor(v).count { it.isNotEmpty() }}" } + ")"
                } else {
                    ""
                },
            )
        }
    }
}

// endregion

// region Шаг 4. Бланк на листе

/**
 * Лист закреплён сверху и всегда виден. Под ним (тоже закреплены) — кнопки элементов и панель «размер и сдвиг»
 * выбранного элемента. Прокручиваются только подробные настройки ниже.
 * Размер выбранного элемента меняется и двумя пальцами на листе; стрелки можно удерживать.
 */
@Composable
private fun SheetStep(
    design: BlankDesign,
    baseDesign: BlankDesign,
    variants: Int,
    onDesign: (BlankDesign) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<SheetElement>(SheetElement.Grid) }
    var panAtStart by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    // Чей бланк на листе: 0 — общий (нажатие убирает кружок у всех), N — бланк варианта N.
    val perVariant = design.formatV2 && variants > 1
    var mode by remember { mutableIntStateOf(0) }
    if (!perVariant || mode > variants) mode = 0
    val shownVariant = mode.takeIf { it > 0 }
    val currentVariantShown by rememberUpdatedState(shownVariant)
    val layout = remember(design.layoutConfig, design.render) { buildBlankBubbleLayout(design.layoutConfig, design.render) }
    val currentDesign by rememberUpdatedState(design)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnDesign by rememberUpdatedState(onDesign)

    fun clampPan(d: BlankDesign, p: Offset, scale: Float = d.scale): Offset =
        clampBlankPanOnSheet(p, buildBlankBubbleLayout(d.layoutConfig, d.render), scale)

    /** Сдвиг выбранного: надпись — сама, поля имени и варианта — вместе с сеткой. */
    fun move(delta: Offset) {
        val d = currentDesign
        when (val s = currentSelected) {
            is SheetElement.Text -> currentOnDesign(
                d.copy(textBlocks = d.textBlocks.map { if (it.id == s.id) it.copy(position = clampTextPositionOnSheet(it.position + delta)) else it }),
            )
            else -> currentOnDesign(d.copy(pan = clampPan(d, d.pan + delta)))
        }
    }

    /** Размер выбранного умножить на [factor]: надпись — шрифт 10–40, сетка — масштаб 65–175 %. */
    fun resize(factor: Float) {
        val d = currentDesign
        when (val s = currentSelected) {
            is SheetElement.Text -> currentOnDesign(
                d.copy(textBlocks = d.textBlocks.map { if (it.id == s.id) it.copy(fontSizePx = (it.fontSizePx * factor).coerceIn(10f, 40f)) else it }),
            )
            else -> {
                val sc = clampBlankScale(d.scale * factor)
                // Больше листа не растим: уголки-ориентиры должны остаться на бумаге.
                if (sc > d.scale && !d.fitsAt(sc)) return
                currentOnDesign(d.copy(scale = sc, pan = clampPan(d, d.pan, sc)))
            }
        }
    }

    val sizeLabel = when (val s = selected) {
        is SheetElement.Text -> "${design.textBlocks.firstOrNull { it.id == s.id }?.fontSizePx?.toInt() ?: 0} пт"
        else -> "${(design.scale * 100).toInt()} %"
    }
    val selectedTitle = when (val s = selected) {
        is SheetElement.Text -> design.textBlocks.firstOrNull { it.id == s.id }?.label ?: ""
        SheetElement.Name -> "Поле имени"
        SheetElement.Variant -> "Номер варианта"
        SheetElement.Grid -> "Сетка кружков"
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (perVariant) {
            Segmented((0..variants).toList(), mode, label = { if (it == 0) "Все" else "Вар. $it" }) { mode = it }
        }
        // ——— Лист (закреплён) ———
        Box(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxWidth()
                // Два пальца — размер выбранного элемента. Перехватываем раньше листа, чтобы сетка не уезжала.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.changes.count { it.pressed } >= 2) {
                                val z = ev.calculateZoom()
                                if (z != 1f && z.isFinite()) resize(z)
                                ev.changes.forEach { it.consume() }
                            }
                            if (ev.changes.none { it.pressed }) break
                        }
                    }
                },
        ) {
            A4SheetPage(
                layoutConfig = design.layoutConfig,
                renderParams = design.render,
                hiddenBubbleIds = design.hiddenFor(shownVariant),
                blankPanLogical = design.pan,
                blankScale = design.scale,
                textBlocks = design.textBlocks,
                showVariantMarker = design.variantMarker,
                showStudentNameMarker = design.nameMarker,
                studentNamePrintedMode = false,
                studentSurnameText = "",
                studentNameText = "",
                variantPrintedMode = shownVariant != null,
                variantPrintedNumber = (shownVariant ?: 1).toString(),
                v2 = design.v2Options(variants, shownVariant),
                showTextElements = design.textBlocks.any { it.isVisible },
                alignmentGuides = emptyList(),
                selection = when (val s = selected) {
                    is SheetElement.Text -> BlankEditorSelection.Text(s.id)
                    else -> BlankEditorSelection.Blank
                },
                onBlankPanDragStart = {
                    if (selected is SheetElement.Text) selected = SheetElement.Grid
                    panAtStart = currentDesign.pan
                },
                onBlankPanDragAccumulatedLogical = { acc ->
                    val d = currentDesign
                    currentOnDesign(d.copy(pan = clampPan(d, panAtStart + acc)))
                },
                onBlankPanDragRevertForTap = { currentOnDesign(currentDesign.copy(pan = panAtStart)) },
                onTextBlockTap = { id -> selected = SheetElement.Text(id) },
                onTextBlockDragStart = { id -> selected = SheetElement.Text(id) },
                onTextBlockDragDeltaLogical = { id, delta ->
                    val d = currentDesign
                    currentOnDesign(
                        d.copy(textBlocks = d.textBlocks.map { if (it.id == id) it.copy(position = clampTextPositionOnSheet(it.position + delta)) else it }),
                    )
                },
                onTextBlockDragEnd = {},
                onBlankTap = { if (selected is SheetElement.Text) selected = SheetElement.Grid },
                onTapOutsideElements = {},
                onBubbleToggle = { id ->
                    // То же правило, что в карточке «Лишние кружки»: для всех или только для показанного варианта.
                    val (next, why) = currentDesign.tapBubble(id, currentVariantShown, variants)
                    if (next != null) currentOnDesign(next)
                    why?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxSize(),
                horizontalPadding = 4.dp,
                verticalPadding = 4.dp,
            )
        }

        // ——— Элементы (закреплены, листаются вбок) ———
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ElementChip("Сетка кружков", true, selected == SheetElement.Grid) { selected = SheetElement.Grid }
            design.textBlocks.forEach { b ->
                ElementChip(b.label, b.isVisible, selected == SheetElement.Text(b.id)) { selected = SheetElement.Text(b.id) }
            }
            ElementChip("Поле имени", design.nameMarker, selected == SheetElement.Name) { selected = SheetElement.Name }
            ElementChip("Номер варианта", design.variantMarker, selected == SheetElement.Variant) { selected = SheetElement.Variant }
        }

        // ——— Размер и сдвиг выбранного (закреплены) ———
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Scrapbook.Paper, RoundedCornerShape(12.dp))
                .border(1.dp, Scrapbook.Ink.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            RepeatButton("−", "Меньше") { resize(if (selected is SheetElement.Text) 1f - 1f / 18f else 0.97f) }
            Text(
                sizeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(46.dp),
            )
            RepeatButton("+", "Больше") { resize(if (selected is SheetElement.Text) 1f + 1f / 18f else 1.03f) }
            Spacer(Modifier.weight(1f))
            RepeatButton("←", "Левее") { move(Offset(-ARROW_STEP, 0f)) }
            RepeatButton("↑", "Выше") { move(Offset(0f, -ARROW_STEP)) }
            RepeatButton("↓", "Ниже") { move(Offset(0f, ARROW_STEP)) }
            RepeatButton("→", "Правее") { move(Offset(ARROW_STEP, 0f)) }
        }

        // ——— Подробные настройки выбранного (прокручиваются) ———
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val s = selected) {
                is SheetElement.Text -> {
                    val b = design.textBlocks.firstOrNull { it.id == s.id }
                    if (b != null) {
                        fun upd(f: (com.tscan.scanertestov.feature.blankeditor.BlankTextBlock) -> com.tscan.scanertestov.feature.blankeditor.BlankTextBlock) =
                            onDesign(design.copy(textBlocks = design.textBlocks.map { if (it.id == b.id) f(it) else it }))
                        PanelHeader(selectedTitle, b.isVisible) { v ->
                            upd {
                                val shown = it.copy(isVisible = v)
                                // Включили — ставим в свободное место на листе, а не под сетку.
                                if (v) com.tscan.scanertestov.feature.blankeditor.placeTextBlockInFreeSpace(
                                    shown, design.textBlocks,
                                    com.tscan.scanertestov.feature.blankeditor.buildBlankBubbleLayout(design.layoutConfig, design.render),
                                    design.pan, design.scale,
                                ) else shown
                            }
                        }
                        if (b.isVisible) {
                            OutlinedTextField(
                                value = b.text,
                                onValueChange = { t -> upd { it.copy(text = t.take(80)) } },
                                label = { Text("Текст") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Hint("Двигайте надпись пальцем по листу или стрелками (удерживайте — поедет плавно). Размер — «−/+» или двумя пальцами на листе.")
                        } else {
                            Hint("Надпись скрыта и не будет напечатана. Включите «Показывать», чтобы вернуть её.")
                        }
                        val base = baseDesign.textBlocks.firstOrNull { it.id == b.id }
                        if (base != null && base != b) {
                            TextButton(onClick = { upd { base } }) { Text("Вернуть как было в шаблоне", color = Scrapbook.InkMuted) }
                        }
                    }
                }
                SheetElement.Name -> {
                    PanelHeader(selectedTitle, design.nameMarker) { onDesign(design.copy(nameMarker = it)) }
                    Hint(
                        "Прямоугольник над рамкой с кружками: ученик пишет в нём фамилию и имя, а приложение читает их при " +
                            "проверке. Стоит над сеткой и двигается вместе с ней — стрелки сейчас двигают всю сетку.",
                    )
                    if (design.nameMarker) {
                        Hint(
                            "На обычных бланках поле пустое — ученик пишет сам. Фамилия и имя печатаются из журнала, " +
                                "если на этапе «Предпросмотр и печать» выбрать «Именные на класс».",
                        )
                    }
                }
                SheetElement.Variant -> {
                    val locked = variants > 1
                    PanelHeader(selectedTitle, design.variantMarker, enabled = !locked) { onDesign(design.copy(variantMarker = it)) }
                    val many = "У теста $variants ${ruPlural(variants, "вариант", "варианта", "вариантов")}"
                    Hint(
                        when {
                            design.formatV2 && locked ->
                                "$many, поэтому строка кружков «Вариант» обязательна: по ней приложение выбирает, по какому " +
                                    "ключу проверять работу. Стоит над сеткой и двигается вместе с ней."
                            design.formatV2 ->
                                "Строка кружков «Вариант» над сеткой — ученик закрашивает номер своего варианта. При одном " +
                                    "варианте теста не нужна."
                            locked ->
                                "$many, поэтому шестиугольник обязателен: по номеру в нём приложение выбирает, по какому ключу " +
                                    "проверять работу. Двигается вместе с сеткой."
                            else ->
                                "Шестиугольник над рамкой, куда ученик вписывает номер варианта. При одном варианте теста не нужен."
                        },
                    )
                    if (locked) {
                        Hint(
                            if (design.variantsDiffer) {
                                "У вариантов разные кружки, поэтому у каждого свой бланк и номер варианта на нём закрашен сам."
                            } else {
                                "Закрасить ли номер варианта заранее — выбирается на этапе «Предпросмотр и печать»."
                            },
                        )
                    }
                }
                SheetElement.Grid -> {
                    Hint(
                        "Двигайте сетку пальцем или стрелками, размер — «−/+» или двумя пальцами на листе. " +
                            "Поле имени и номер варианта двигаются вместе с ней. Нажатие на кружок убирает его" +
                            (if (perVariant) " — с бланков всех вариантов или, если вверху выбран вариант, только с его бланка." else ".")
                    )
                    if (gridOverlapsText(design)) {
                        Text(
                            "Сетка или поля над ней заезжают на надпись. Сдвиньте сетку ниже или надпись выше — " +
                                "иначе бланк может распознаться неверно.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Scrapbook.Ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Scrapbook.Rose, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        )
                    }
                    Stepper("Размер кружка", design.render.bubbleSizePx.toInt(), 20, 60, step = 2) { v ->
                        onDesign(design.copy(render = design.render.copy(bubbleSizePx = clampBubbleSizePx(v.toFloat()))).keptOnSheet())
                    }
                    if (design.pan != baseDesign.pan || design.scale != baseDesign.scale || design.render != baseDesign.render) {
                        TextButton(onClick = {
                            val fresh = baseDesign.withGrid(design.questions, design.options, design.columns)
                            onDesign(design.copy(pan = fresh.pan, scale = fresh.scale, render = baseDesign.render))
                        }) { Text("Вернуть как было в шаблоне", color = Scrapbook.InkMuted) }
                    }
                }
            }
        }
    }
}

/**
 * Кружки по вопросам, как на бланке. «Все»: нажатие на кружок убирает его и все правее с бланков всех вариантов,
 * на перечёркнутый — возвращает. «Вар. N» (бланк v2, вариантов > 1): то же, но только на бланке этого варианта
 * (синие перечёркнутые). Убираются всегда с конца строки и не меньше двух — как на листе.
 */
@Composable
private fun BubblesPerQuestion(design: BlankDesign, variants: Int, onDesign: (BlankDesign) -> Unit) {
    val context = LocalContext.current
    val perVariant = design.formatV2 && variants > 1
    var mode by remember { mutableIntStateOf(0) }
    if (!perVariant || mode > variants) mode = 0
    if (perVariant) VariantModeSwitch(variants, mode) { mode = it }
    for (q in 1..design.questions) {
        val g = design.visibleCount(q, null)
        val k = design.visibleCount(q, mode.takeIf { it > 0 })
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$q", fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            for (o in 1..design.options) {
                val removedAll = o > g
                val removed = o > k
                val lineColor = if (removedAll) Scrapbook.InkMuted.copy(alpha = 0.45f) else VariantBlue
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = 44.dp)
                        .aspectRatio(1f)
                        .background(if (removed && !removedAll) VariantBlue.copy(alpha = 0.08f) else Scrapbook.Paper, CircleShape)
                        .border(
                            if (removed) 1.dp else 2.dp,
                            when {
                                removedAll -> Scrapbook.InkMuted.copy(alpha = 0.35f)
                                removed -> VariantBlue.copy(alpha = 0.6f)
                                else -> Scrapbook.Ink
                            },
                            CircleShape,
                        )
                        .drawBehind {
                            if (removed) {
                                drawLine(
                                    lineColor,
                                    androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.82f),
                                    androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.18f),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (removed) "Вернуть кружок $q.$o" else "Убрать кружок $q.$o",
                        ) {
                            val (next, why) = design.tapBubble("$q.$o", mode.takeIf { it > 0 }, variants)
                            if (next != null) onDesign(next)
                            why?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!removed) {
                        Text("$q.$o", style = MaterialTheme.typography.labelSmall, color = Scrapbook.InkMuted)
                    }
                }
            }
        }
    }
}

/**
 * «Все / Вар. 1 / Вар. 2…» — для каких бланков убирать кружки. Один и тот же переключатель в карточке
 * «Лишние кружки» и над листом в редакторе «Надписи и место бланка на листе».
 */
@Composable
private fun VariantModeSwitch(variants: Int, mode: Int, onMode: (Int) -> Unit) {
    Segmented((0..variants).toList(), mode, label = { if (it == 0) "Все" else "Вар. $it" }) { onMode(it) }
    Hint(
        if (mode == 0) {
            "Кружок, которого нет ни в одном варианте, уберётся с бланков всех вариантов."
        } else {
            "Кружок уберётся только с бланка варианта $mode (синий). Тогда у каждого варианта свой бланк — " +
                "номер варианта на нём закрашен при печати."
        },
    )
}

/** Цвет «только для этого варианта» — тот же синий, что номер варианта в живой проверке. */
private val VariantBlue = Color(0xFF1555B8)

/** Кнопка, которая повторяет действие, пока её держат: сразу, через 0,4 с и дальше каждые 70 мс. */
@Composable
private fun RepeatButton(symbol: String, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Scrapbook.Board.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .border(1.5.dp, Scrapbook.Ink, RoundedCornerShape(10.dp))
            .semantics { contentDescription = description; role = Role.Button }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        step()
                        val job = launch {
                            delay(400)
                            while (isActive) {
                                step()
                                delay(70)
                            }
                        }
                        waitForUpOrCancellation()
                        job.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.Bold)
    }
}

/** Сетка вместе с полями над ней (поле имени на 90 выше рамки) пересекается с видимыми надписями. */
private fun gridOverlapsText(d: BlankDesign): Boolean {
    val layout = buildBlankBubbleLayout(d.layoutConfig, d.render)
    val g = blankLayoutBoundingRectTransformed(layout, d.pan, d.scale)
    val top = if (d.nameMarker || d.variantMarker) g.top - 92f else g.top
    return d.textBlocks.any { b ->
        if (!b.isVisible || b.text.isBlank()) return@any false
        val h = b.fontSizePx * b.scale * 1.25f
        val w = b.text.length * b.fontSizePx * b.scale * 0.55f
        val l = b.position.x
        val t = b.position.y
        l < g.right && l + w > g.left && t < g.bottom && t + h > top
    }
}

@Composable
private fun ElementChip(label: String, visible: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) Scrapbook.Lavender.copy(alpha = 0.7f) else Scrapbook.Paper, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.25f), shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(if (visible) "◉" else "○", color = if (visible) Scrapbook.Ink else Scrapbook.InkMuted)
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = if (visible) Scrapbook.Ink else Scrapbook.InkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PanelHeader(title: String, shown: Boolean, enabled: Boolean = true, onShown: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Показывать", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        ShellBubbleSwitch(checked = shown, onCheckedChange = onShown, enabled = enabled)
    }
}

// endregion

// region Шаг 5. Критерии

@Composable
private fun GradesStep(
    ownCriteria: Boolean,
    onOwn: (Boolean) -> Unit,
    criteria: GradingCriteriaConfig,
    onCriteria: (GradingCriteriaConfig) -> Unit,
    globalSummary: String,
    questions: Int,
) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice(!ownCriteria, "Общие из Настроек", globalSummary) { onOwn(false) }
            Choice(ownCriteria, "Свои для этого теста", "Например, если контрольная сложная и «5» можно ставить от 85%.") { onOwn(true) }
            if (ownCriteria) {
                GradingCriteriaEditor(
                    config = criteria,
                    onChange = onCriteria,
                    pointsHint = "Сумма баллов за работу: за каждый верный вопрос 1 балл, всего $questions.",
                )
            }
        }
    }
}

// endregion

// region Шаг 6. Готово

@Composable
private fun TemplateDoneStep(
    design: BlankDesign,
    variants: Int,
    name: String,
    onName: (String) -> Unit,
    onSave: () -> Unit,
) {
    SettingsShellCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SheetMini(design, Modifier.width(84.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${design.questions} ${ruPlural(design.questions, "вопрос", "вопроса", "вопросов")} · ${design.options} ${ruPlural(design.options, "ответ", "ответа", "ответов")}", style = MaterialTheme.typography.bodyMedium)
                Text("$variants ${ruPlural(variants, "вариант", "варианта", "вариантов")}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text("Название шаблона") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Hint("Тесты, уже сделанные по этому шаблону, не изменятся.")
    ShellBubblePrimaryButton(text = "Сохранить шаблон", onClick = onSave, paletteIndex = 1)
}

// endregion

// region Общие элементы

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Scrapbook.Ink)
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        RoundButton("−", value > min) { onChange((value - step).coerceAtLeast(min)) }
        Text(
            "$value",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        RoundButton("+", value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Scrapbook.Paper, CircleShape)
            .border(2.dp, if (enabled) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.25f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold, color = if (enabled) Scrapbook.Ink else Scrapbook.InkMuted)
    }
}

@Composable
private fun Segmented(values: List<Int>, selected: Int, label: (Int) -> String = { "$it" }, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Scrapbook.Ink, RoundedCornerShape(12.dp)),
    ) {
        values.forEach { v ->
            val on = v == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .background(if (on) Scrapbook.Mint else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(role = Role.RadioButton) { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(v), fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = Scrapbook.Ink, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) Scrapbook.Lemon else Scrapbook.Paper, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.3f), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = Scrapbook.Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Choice(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .background(if (selected) Scrapbook.Mint.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Scrapbook.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
    }
}

// endregion
