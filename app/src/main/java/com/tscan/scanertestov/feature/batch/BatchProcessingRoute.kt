package com.tscan.scanertestov.feature.batch

/**
 * Описание: маршрут экрана пакетной обработки — локальное состояние очереди, эталона, импорт изображений и навигация.
 */
import com.tscan.scanertestov.feature.tests.IssuedStudentCode
import com.tscan.scanertestov.feature.tests.StudentCodeStore
import com.tscan.scanertestov.feature.tests.blankDesign
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Observer
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.journals.JournalRepository
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaVariantAnswers
import com.tscan.scanertestov.feature.batch.canEnqueueAutoRecognition
import com.tscan.scanertestov.feature.batch.effectiveVariant
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.batch.BatchRecognitionWarmup
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.navigation.AppDestinations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Одновременно распознаём не больше двух листов: каждый держит в памяти полный кадр
 * и две OCR-сессии, а 30 параллельных задач на слабом телефоне приводили к OOM и лагам.
 */
private val autoRecognitionPermits = Semaphore(permits = 2)

private fun newWorkId(): String = "w-${UUID.randomUUID()}"

@Composable
fun BatchProcessingRoute(navController: NavHostController) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { BatchCriteriaRepository(appContext) }
    val batchNavEntry = remember(navController) {
        navController.getBackStackEntry(AppDestinations.BATCH_PROCESSING)
    }
    val restoredState = remember(repository, batchNavEntry) {
        BatchProcessingPersistence.restore(batchNavEntry.savedStateHandle)
            ?.copy(savedCriteria = repository.getAll())
            ?: BatchProcessingState(savedCriteria = repository.getAll())
    }
    var state by remember {
        mutableStateOf(restoredState)
    }
    LaunchedEffect(batchNavEntry, state) {
        BatchProcessingPersistence.save(batchNavEntry.savedStateHandle, state)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(appContext) {
        state = state.copy(ocrWarmupInProgress = true, ocrWarmupReady = false)
        val warmupErrors = BatchRecognitionWarmup.ensure(appContext)
        state = if (warmupErrors.isEmpty()) {
            state.copy(ocrWarmupInProgress = false, ocrWarmupReady = true)
        } else {
            state.copy(
                ocrWarmupInProgress = false,
                ocrWarmupReady = false,
                statusMessage = "OCR прогрев не завершен: ${warmupErrors.first()}",
            )
        }
    }

    fun clampQuestions(value: Int): Int = value.coerceIn(1, 35)
    fun clampChoices(value: Int): Int = value.coerceIn(2, 6)
    fun normalizeAnswers(questions: Int, choices: Int, source: List<Set<Int>>): List<Set<Int>> {
        return List(questions) { idx ->
            val valid = source.getOrNull(idx)
                ?.filter { it in 0 until choices }
                ?.toSet()
                .orEmpty()
                .toMutableSet()
            while (valid.size >= choices) {
                valid.remove(valid.maxOrNull())
            }
            valid.toSet()
        }
    }

    fun editableAnswers(state: BatchProcessingState): List<Set<Int>> {
        val selected = state.selectedAnswerKeyVariant
        if (selected != null && state.answerKeyVariantsCount > 0) {
            return state.correctAnswersByVariant[selected] ?: state.correctAnswers
        }
        return state.correctAnswers
    }

    fun resolveAnswerKeyForWork(item: BatchWorkItem, state: BatchProcessingState): List<Set<Int>> {
        val fallbackBase = state.correctAnswersByVariant[1] ?: state.correctAnswers
        if (state.answerKeyVariantsCount <= 0) return fallbackBase
        val variant = item.effectiveVariant() ?: return fallbackBase
        return state.correctAnswersByVariant[variant] ?: fallbackBase
    }

    /** Подставить сохранённый тест (эталон): число вопросов, вариантов, колонки и ключи. */
    fun applyPreset(preset: com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset, status: String) {
        val q = clampQuestions(preset.questionsCount)
        val c = clampChoices(preset.choicesCount)
        val layout = if (preset.columnCount == 2) BatchPageLayout.TwoColumns else BatchPageLayout.OneColumn
        state = state.copy(
            questionsCount = q,
            choicesCount = c,
            correctAnswers = normalizeAnswers(q, c, preset.correctAnswers.map { it.toSet() }),
            correctAnswersByVariant = preset.variantAnswerKeys.associate { item ->
                item.variant to normalizeAnswers(q, c, item.correctAnswers.map { it.toSet() })
            },
            answerKeyVariantsCount = preset.variantAnswerKeys.size.coerceIn(0, 4),
            selectedAnswerKeyVariant = preset.variantAnswerKeys.firstOrNull()?.variant,
            pageLayout = layout,
            criteriaNameDraft = "",
            statusMessage = status,
            savedCriteria = repository.getAll(),
            gradingCriteriaJson = preset.gradingCriteriaJson,
            testId = preset.id,
            testTitle = preset.name,
        )
    }

    /** Ученик по коду с именного бланка (см. [StudentCodeStore]) и что не сходится. */
    class CodeStudent(val issued: IssuedStudentCode?, val note: String?, val warning: String?, val unknownCode: Int?)

    fun codeStudent(testId: String?, code: Int?, variantOnSheet: Int?): CodeStudent {
        if (code == null) return CodeStudent(null, null, null, null)
        val issued = StudentCodeStore.lookup(appContext, testId, code)
            ?: return CodeStudent(
                null, null,
                "Код №$code не выдавался по этому тесту — возможно, бланк от другого теста. Выберите ученика.",
                code,
            )
        val warning = if (issued.variant != null && variantOnSheet != null && issued.variant != variantOnSheet) {
            "Выдан вариант ${issued.variant}, а на бланке закрашен вариант $variantOnSheet. Проверено по варианту $variantOnSheet — как на бланке."
        } else null
        return CodeStudent(issued, "${issued.className} · по коду №$code", warning, null)
    }

    /**
     * Убранные с бланка кружки теста, по которому идёт проверка (одно место правды — оформление теста).
     * [variant] — вариант работы: у вариантов могут быть свои убранные кружки; null — общие для всех.
     */
    fun hiddenBubblesOfTest(testId: String? = state.testId, variant: Int? = null): Set<String> =
        testId?.let { repository.getById(it) }?.blankDesign()?.hiddenFor(variant).orEmpty()

    // Открыли из «Моих тестов» → ключ теста подставляется сам.
    LaunchedEffect(Unit) {
        com.tscan.scanertestov.feature.tests.PendingTestLaunch.takeBatchPresetId()
            ?.let { repository.getById(it) }
            ?.let {
                val how = if (it.gradingCriteriaJson != null) "правильные ответы и критерии оценки теста подставлены" else "правильные ответы подставлены"
                applyPreset(it, "Тест «${it.name}»: $how. Добавьте фото работ.")
            }
    }

    fun refreshPresets(newStatus: String? = null) {
        state = state.copy(savedCriteria = repository.getAll(), statusMessage = newStatus)
    }

    fun updateWorkItem(workId: String, transform: (BatchWorkItem) -> BatchWorkItem) {
        state = state.copy(
            workItems = state.workItems.map { item ->
                if (item.id == workId) transform(item) else item
            }
        )
    }

    fun renumberWorkTitles(items: List<BatchWorkItem>): List<BatchWorkItem> =
        items.mapIndexed { index, item -> item.copy(title = "Работа №${index + 1}") }

    fun enqueueAutoRecognition(workId: String, uri: Uri) {
        val maxVariantHint = state.answerKeyVariantsCount.takeIf { it > 0 } ?: 4
        val questionCount = state.questionsCount
        val optionCount = state.choicesCount
        val columnCount = if (state.pageLayout == BatchPageLayout.TwoColumns) 2 else 1
        scope.launch {
            var savedNameCropPath: String? = null
            val (variantResult, nameResult) = autoRecognitionPermits.withPermit { coroutineScope {
                val rosterDeferred = async(Dispatchers.IO) {
                    JournalRepository(appContext).loadAll()
                }
                // Декодирование JPEG — тяжёлая операция; раньше шла в главном потоке и «вешала» UI.
                val bmp = withContext(Dispatchers.IO) { decodeBitmapFromContentUri(appContext, uri) }
                if (bmp == null) {
                    return@coroutineScope BatchVariantRecognitionResult(
                        variant = null,
                        confidence = 0f,
                        rawText = "",
                        error = "Не удалось открыть изображение",
                    ) to BatchStudentNameRecognitionResult(
                        error = "Не удалось открыть изображение",
                    )
                }
                try {
                    // Бланк v2 (угловые метки): вариант — по кружку, ученик — по коду; иначе прежний путь.
                    val v2 = withContext(Dispatchers.Default) {
                        readBlankV2(appContext, bmp, questionCount, optionCount, columnCount, hiddenBubblesOfTest())
                    }
                    if (v2 != null) {
                        savedNameCropPath = saveNameCropImage(appContext, workId, v2.nameCrop)
                        try {
                            val roster = rosterDeferred.await()
                            val vr = BatchVariantRecognitionResult(
                                variant = v2.variant?.takeIf { it <= maxVariantHint },
                                confidence = if (v2.variant != null) 1f else 0f,
                                rawText = v2.variantNote,
                            )
                            val byCode = codeStudent(state.testId, v2.code, v2.variant).issued
                            val nr = if (byCode != null) {
                                BatchStudentNameRecognitionResult(
                                    surname = byCode.surname,
                                    name = byCode.name,
                                    journalClassName = byCode.className,
                                    confidence = 1f,
                                    rawText = "код ${v2.code}",
                                )
                            } else {
                                withContext(Dispatchers.Default) {
                                    recognizeStudentNameFromNameCropMat(appContext, v2.nameCrop, roster)
                                }
                            }
                            return@coroutineScope vr to nr
                        } finally {
                            v2.nameCrop.release()
                        }
                    }
                    val (variantCrop, nameCrop) = withContext(Dispatchers.Default) {
                        extractVariantAndNameCropsSingleSnapshot(bmp, questionCount, optionCount, columnCount, appContext)
                    }
                    // Сохраняем место для имени картинкой: учитель увидит подпись и выберет ученика сам.
                    savedNameCropPath = saveNameCropImage(appContext, workId, nameCrop)
                    try {
                        val roster = rosterDeferred.await()
                        val variantDeferred = async(Dispatchers.Default) {
                            recognizeVariantFromVariantCropMat(
                                context = appContext,
                                variantCrop = variantCrop,
                                maxVariantHint = maxVariantHint,
                            )
                        }
                        val nameDeferred = async(Dispatchers.Default) {
                            recognizeStudentNameFromNameCropMat(
                                context = appContext,
                                nameCrop = nameCrop,
                                journalRoster = roster,
                            )
                        }
                        variantDeferred.await() to nameDeferred.await()
                    } finally {
                        variantCrop.release()
                        nameCrop.release()
                    }
                } finally {
                    bmp.recycle()
                }
            } }
            if (state.workItems.none { it.id == workId }) return@launch
            updateWorkItem(workId) { item ->
                val merged = item.copy(
                    status = if (variantResult.error == null && nameResult.error == null) {
                        BatchWorkStatus.Ready
                    } else {
                        BatchWorkStatus.Error
                    },
                    detectedVariant = variantResult.variant,
                    detectedVariantConfidence = variantResult.confidence,
                    detectedVariantRawText = variantResult.rawText.ifBlank { null },
                    isVariantDetecting = false,
                    variantDetectionError = variantResult.error,
                    detectedStudentSurname = nameResult.surname,
                    detectedStudentName = nameResult.name,
                    detectedStudentNameConfidence = nameResult.confidence,
                    detectedStudentNameRawText = nameResult.rawText.ifBlank { null },
                    studentNameDetectionError = nameResult.error,
                    detectedJournalClassName = nameResult.journalClassName?.takeIf { it.isNotBlank() },
                    studentCode = nameResult.rawText.removePrefix("код ").trim().toIntOrNull()
                        ?.takeIf { nameResult.rawText.startsWith("код ") },
                    nameCropPath = savedNameCropPath ?: item.nameCropPath,
                )
                merged.copy(
                    subtitle = buildWorkSubtitle(
                        source = merged.source,
                        displayName = merged.displayName,
                        autoRecognitionRequested = merged.autoRecognitionRequested,
                        isDetecting = false,
                        detectedVariant = merged.effectiveVariant(),
                        surname = merged.detectedStudentSurname,
                        name = merged.detectedStudentName,
                        journalClass = merged.detectedJournalClassName,
                    ),
                )
            }
            val stillDetecting = state.workItems.count { it.isVariantDetecting }
            if (!state.isProcessing) {
                state = state.copy(
                    statusMessage = autoRecognitionProgressMessage(stillDetecting)
                )
            }
        }
    }

    // Если экран уходил в камеру/результаты во время распознавания, корутины были отменены,
    // а у работ остался флаг «распознаётся». Перезапускаем их, иначе они зависали навсегда
    // и проверялись по ключу варианта 1.
    LaunchedEffect(Unit) {
        state.workItems
            .filter { it.isVariantDetecting }
            .forEach { enqueueAutoRecognition(it.id, Uri.parse(it.contentUri)) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestBatchState by rememberUpdatedState(state)

    fun applyCameraCaptureUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        var cur = latestBatchState
        val previewBefore = cur.previewWorkId
        val addedItems = mutableListOf<BatchWorkItem>()
        var skipped = 0
        var workItems = cur.workItems
        for (uri in uris) {
            val uriStr = uri.toString()
            if (workItems.any { it.contentUri == uriStr }) {
                skipped++
                continue
            }
            val id = newWorkId()
            val displayName = resolveBatchImageDisplayName(context, uri)
            val item = BatchWorkItem(
                id = id,
                title = "Работа №${workItems.size + 1}",
                displayName = displayName,
                autoRecognitionRequested = cur.autoRecognitionEnabled,
                subtitle = buildWorkSubtitle(
                    source = BatchWorkSource.Camera,
                    displayName = displayName,
                    autoRecognitionRequested = cur.autoRecognitionEnabled,
                    isDetecting = cur.autoRecognitionEnabled,
                    detectedVariant = null,
                    surname = null,
                    name = null,
                ),
                contentUri = uriStr,
                source = BatchWorkSource.Camera,
                status = BatchWorkStatus.Queued,
                isVariantDetecting = cur.autoRecognitionEnabled,
            )
            workItems = workItems + item
            addedItems += item
        }
        if (addedItems.isEmpty()) {
            state = cur.copy(statusMessage = "Эти снимки уже в очереди")
            return
        }
        val withNew = renumberWorkTitles(workItems)
        val msg = when {
            addedItems.size == 1 && skipped == 0 ->
                addedSingleWorkMessage(
                    itemTitle = withNew.last().title,
                    autoRecognitionEnabled = cur.autoRecognitionEnabled,
                )
            else ->
                "Добавлено с камеры: ${addedItems.size}" +
                    (if (skipped > 0) " · пропущено дублей: $skipped" else "") +
                    (if (cur.autoRecognitionEnabled) " · распознаём…" else "")
        }
        state = cur.copy(
            workItems = withNew,
            previewWorkId = previewBefore ?: addedItems.first().id,
            statusMessage = msg,
        )
        if (cur.autoRecognitionEnabled) {
            addedItems.forEach { item ->
                enqueueAutoRecognition(item.id, Uri.parse(item.contentUri))
            }
        }
    }

    DisposableEffect(batchNavEntry, lifecycleOwner) {
        val liveData =
            batchNavEntry.savedStateHandle.getLiveData<ArrayList<String>>(BatchCaptureKeys.RESULT_URIS)
        val observer = Observer<ArrayList<String>?> { list ->
            if (list.isNullOrEmpty()) return@Observer
            val snapshot = ArrayList(list)
            batchNavEntry.savedStateHandle.remove<ArrayList<String>>(BatchCaptureKeys.RESULT_URIS)
            applyCameraCaptureUris(snapshot.map { Uri.parse(it) })
        }
        liveData.observe(lifecycleOwner, observer)
        onDispose {
            liveData.removeObserver(observer)
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            navController.navigate(AppDestinations.BATCH_DOCUMENT_CAPTURE)
        } else {
            state = state.copy(statusMessage = "Нужен доступ к камере")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var cur = state
        val previewBefore = cur.previewWorkId
        var added = 0
        var skipped = 0
        val addedItems = mutableListOf<BatchWorkItem>()
        for (uri in uris) {
            val uriStr = uri.toString()
            if (cur.workItems.any { it.contentUri == uriStr }) {
                skipped++
                continue
            }
            val id = newWorkId()
            val displayName = resolveBatchImageDisplayName(context, uri)
            val item = BatchWorkItem(
                id = id,
                title = "Работа №${cur.workItems.size + 1}",
                displayName = displayName,
                autoRecognitionRequested = state.autoRecognitionEnabled,
                subtitle = buildWorkSubtitle(
                    source = BatchWorkSource.Gallery,
                    displayName = displayName,
                    autoRecognitionRequested = state.autoRecognitionEnabled,
                    isDetecting = state.autoRecognitionEnabled,
                    detectedVariant = null,
                    surname = null,
                    name = null,
                ),
                contentUri = uriStr,
                source = BatchWorkSource.Gallery,
                status = BatchWorkStatus.Queued,
                isVariantDetecting = state.autoRecognitionEnabled,
            )
            cur = cur.copy(workItems = cur.workItems + item)
            addedItems += item
            added++
        }
        cur = cur.copy(workItems = renumberWorkTitles(cur.workItems))
        val msg = addedGalleryWorksMessage(
            added = added,
            skipped = skipped,
            autoRecognitionEnabled = state.autoRecognitionEnabled,
        )
        state = cur.copy(
            statusMessage = msg,
            previewWorkId = previewBefore ?: addedItems.firstOrNull()?.id,
        )
        addedItems.forEach { item ->
            if (state.autoRecognitionEnabled) {
                enqueueAutoRecognition(item.id, Uri.parse(item.contentUri))
            }
        }
    }

    fun launchCameraCapture() {
        if (state.isProcessing) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            navController.navigate(AppDestinations.BATCH_DOCUMENT_CAPTURE)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Выход с экрана очищает список работ — если он не пуст, сначала спрашиваем.
    var confirmExit by remember { mutableStateOf(false) }
    fun requestExit() {
        if (state.workItems.isNotEmpty()) confirmExit = true else navController.popBackStack()
    }
    androidx.activity.compose.BackHandler(enabled = state.workItems.isNotEmpty()) { confirmExit = true }
    if (confirmExit) {
        val n = state.workItems.size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { androidx.compose.material3.Text("Выйти из проверки?") },
            text = {
                androidx.compose.material3.Text(
                    "В списке работ: $n. Если выйти, список на этом экране очистится и снимки придётся добавлять заново. " +
                        "Работы, которые вы уже отправили «В отчёт», останутся в отчёте.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmExit = false; navController.popBackStack() }) {
                    androidx.compose.material3.Text("Выйти")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmExit = false }) {
                    androidx.compose.material3.Text("Остаться")
                }
            },
        )
    }

    BatchProcessingScreen(
        state = state,
        onAction = { action ->
            when (action) {
                BatchProcessingAction.Back -> requestExit()
                BatchProcessingAction.CaptureByCamera -> launchCameraCapture()
                BatchProcessingAction.LoadFromGallery -> galleryLauncher.launch("image/*")
                is BatchProcessingAction.SetAutoRecognitionEnabled -> {
                    val nowDetecting = state.workItems.count { it.isVariantDetecting }
                    val pendingQueued = state.workItems.count { it.canEnqueueAutoRecognition() }
                    val msg = toggleAutoRecognitionMessage(
                        enabled = action.enabled,
                        pendingQueued = pendingQueued,
                        nowDetecting = nowDetecting,
                        runQueuedRecognitionButtonText = state.runQueuedRecognitionButtonText,
                    )
                    state = state.copy(autoRecognitionEnabled = action.enabled, statusMessage = msg)
                }
                BatchProcessingAction.RunAutoRecognitionForQueued -> {
                    val targets = state.workItems.filter { it.canEnqueueAutoRecognition() }
                    if (targets.isEmpty()) {
                        state = state.copy(statusMessage = "Нет работ для запуска авто-распознавания")
                        return@BatchProcessingScreen
                    }
                    val targetIds = targets.map { it.id }.toSet()
                    state = state.copy(
                        workItems = state.workItems.map { item ->
                            if (item.id !in targetIds) item else {
                                item.copy(
                                    autoRecognitionRequested = true,
                                    isVariantDetecting = true,
                                    status = BatchWorkStatus.Queued,
                                    subtitle = buildWorkSubtitle(
                                        source = item.source,
                                        displayName = item.displayName,
                                        autoRecognitionRequested = true,
                                        isDetecting = true,
                                        detectedVariant = item.effectiveVariant(),
                                        surname = item.detectedStudentSurname,
                                        name = item.detectedStudentName,
                                        journalClass = item.detectedJournalClassName,
                                    ),
                                )
                            }
                        },
                        statusMessage = "Запущено авто-распознавание для ${targets.size} работ",
                    )
                    targets.forEach { item ->
                        enqueueAutoRecognition(item.id, Uri.parse(item.contentUri))
                    }
                }
                BatchProcessingAction.ClearQueue -> {
                    val toDelete = state.workItems.filter { it.source == BatchWorkSource.Camera }.map { it.contentUri }
                    scope.launch(Dispatchers.IO) { toDelete.forEach { deleteOwnBatchCaptureFile(appContext, it) } }
                    state = state.copy(
                        workItems = emptyList(),
                        selectedWorkIds = emptySet(),
                        previewWorkId = null,
                        statusMessage = "Очередь очищена"
                    )
                }
                BatchProcessingAction.ToggleSelectAll -> {
                    val allSelected =
                        state.selectedWorkIds.size == state.workItems.size && state.workItems.isNotEmpty()
                    state = state.copy(
                        selectedWorkIds = if (allSelected) {
                            emptySet()
                        } else {
                            state.workItems.map { it.id }.toSet()
                        }
                    )
                }
                is BatchProcessingAction.ToggleWorkSelection -> {
                    val next = state.selectedWorkIds.toMutableSet()
                    if (next.contains(action.workId)) next.remove(action.workId) else next.add(action.workId)
                    state = state.copy(selectedWorkIds = next)
                }
                is BatchProcessingAction.RemoveWork -> {
                    state.workItems.firstOrNull { it.id == action.workId }
                        ?.takeIf { it.source == BatchWorkSource.Camera }
                        ?.let { removed -> scope.launch(Dispatchers.IO) { deleteOwnBatchCaptureFile(appContext, removed.contentUri) } }
                    val nextItems = renumberWorkTitles(state.workItems.filterNot { it.id == action.workId })
                    val nextPreview = when {
                        state.previewWorkId == action.workId -> nextItems.firstOrNull()?.id
                        state.previewWorkId != null && nextItems.none { it.id == state.previewWorkId } ->
                            nextItems.firstOrNull()?.id
                        else -> state.previewWorkId
                    }
                    state = state.copy(
                        workItems = nextItems,
                        selectedWorkIds = state.selectedWorkIds - action.workId,
                        previewWorkId = nextPreview,
                    )
                }
                is BatchProcessingAction.SelectPreviewWork -> {
                    if (state.workItems.any { it.id == action.workId }) {
                        state = state.copy(previewWorkId = action.workId)
                    }
                }
                is BatchProcessingAction.SetWorkVariantOverride -> {
                    if (action.variant != null && (action.variant < 1 || action.variant > 9)) {
                        return@BatchProcessingScreen
                    }
                    updateWorkItem(action.workId) { item ->
                        if (item.id != action.workId) {
                            item
                        } else {
                            val next = item.copy(variantOverride = action.variant)
                            next.copy(
                                subtitle = buildWorkSubtitle(
                                    source = next.source,
                                    displayName = next.displayName,
                                    autoRecognitionRequested = next.autoRecognitionRequested,
                                    isDetecting = next.isVariantDetecting,
                                    detectedVariant = next.effectiveVariant(),
                                    surname = next.detectedStudentSurname,
                                    name = next.detectedStudentName,
                                    journalClass = next.detectedJournalClassName,
                                ),
                            )
                        }
                    }
                }
                BatchProcessingAction.AddAnswerKeyVariant -> {
                    if (state.answerKeyVariantsCount >= 4) {
                        state = state.copy(statusMessage = "Максимум 4 варианта эталона")
                        return@BatchProcessingScreen
                    }
                    val nextCount = state.answerKeyVariantsCount + 1
                    val normalizedDefault = normalizeAnswers(
                        state.questionsCount,
                        state.choicesCount,
                        editableAnswers(state),
                    )
                    state = state.copy(
                        answerKeyVariantsCount = nextCount,
                        selectedAnswerKeyVariant = state.selectedAnswerKeyVariant ?: nextCount,
                        correctAnswersByVariant = state.correctAnswersByVariant + (nextCount to normalizedDefault),
                        statusMessage = null
                    )
                }
                BatchProcessingAction.RemoveAnswerKeyVariant -> {
                    if (state.answerKeyVariantsCount <= 0) return@BatchProcessingScreen
                    val nextCount = state.answerKeyVariantsCount - 1
                    val nextSelected = when {
                        nextCount == 0 -> null
                        (state.selectedAnswerKeyVariant ?: 0) > nextCount -> nextCount
                        else -> state.selectedAnswerKeyVariant
                    }
                    state = state.copy(
                        answerKeyVariantsCount = nextCount,
                        selectedAnswerKeyVariant = nextSelected,
                        correctAnswersByVariant = state.correctAnswersByVariant.filterKeys { it <= nextCount },
                    )
                }
                is BatchProcessingAction.SelectAnswerKeyVariant -> {
                    if (action.variant !in 1..state.answerKeyVariantsCount) return@BatchProcessingScreen
                    state = state.copy(selectedAnswerKeyVariant = action.variant)
                }
                is BatchProcessingAction.SetQuestionsCount -> {
                    val q = clampQuestions(action.count)
                    val normalizedBase = normalizeAnswers(q, state.choicesCount, state.correctAnswers)
                    val normalizedVariants = state.correctAnswersByVariant
                        .mapValues { (_, answers) -> normalizeAnswers(q, state.choicesCount, answers) }
                    // Устройство теста изменили вручную — критерии загруженного теста больше не применяем.
                    val hadTestCriteria = state.gradingCriteriaJson != null && q != state.questionsCount
                    state = state.copy(
                        questionsCount = q,
                        correctAnswers = normalizedBase,
                        correctAnswersByVariant = normalizedVariants,
                        gradingCriteriaJson = if (hadTestCriteria) null else state.gradingCriteriaJson,
                        statusMessage = if (hadTestCriteria) "Число вопросов изменено — оценки будут по общим критериям из Настроек" else state.statusMessage,
                    )
                }
                is BatchProcessingAction.SetChoicesCount -> {
                    val c = clampChoices(action.count)
                    val normalizedBase = normalizeAnswers(state.questionsCount, c, state.correctAnswers)
                    val normalizedVariants = state.correctAnswersByVariant
                        .mapValues { (_, answers) -> normalizeAnswers(state.questionsCount, c, answers) }
                    val hadTestCriteria = state.gradingCriteriaJson != null && c != state.choicesCount
                    state = state.copy(
                        choicesCount = c,
                        correctAnswers = normalizedBase,
                        correctAnswersByVariant = normalizedVariants,
                        gradingCriteriaJson = if (hadTestCriteria) null else state.gradingCriteriaJson,
                        statusMessage = if (hadTestCriteria) "Число вариантов ответа изменено — оценки будут по общим критериям из Настроек" else state.statusMessage,
                    )
                }
                is BatchProcessingAction.ToggleCorrectAnswerSelection -> {
                    if (action.questionIndex !in 0 until state.questionsCount) return@BatchProcessingScreen
                    if (action.choiceIndex !in 0 until state.choicesCount) return@BatchProcessingScreen
                    val currentAnswers = editableAnswers(state)
                    val next = currentAnswers.toMutableList()
                    val current = next[action.questionIndex].toMutableSet()
                    if (current.contains(action.choiceIndex)) {
                        if (current.size == 1) {
                            state = state.copy(statusMessage = "В вопросе должен остаться минимум 1 выбранный вариант")
                            return@BatchProcessingScreen
                        }
                        current.remove(action.choiceIndex)
                    } else {
                        if (current.size >= state.choicesCount - 1) {
                            state = state.copy(statusMessage = "Нельзя выбрать все варианты в вопросе")
                            return@BatchProcessingScreen
                        }
                        current.add(action.choiceIndex)
                    }
                    next[action.questionIndex] = current.toSet()
                    val selectedVariant = state.selectedAnswerKeyVariant
                    state = if (selectedVariant != null && state.answerKeyVariantsCount > 0) {
                        state.copy(
                            correctAnswersByVariant = state.correctAnswersByVariant + (selectedVariant to next),
                            statusMessage = null,
                        )
                    } else {
                        state.copy(correctAnswers = next, statusMessage = null)
                    }
                }
                is BatchProcessingAction.SetCriteriaNameDraft -> {
                    state = state.copy(criteriaNameDraft = action.value)
                }
                BatchProcessingAction.SaveCriteriaPreset -> {
                    val baseForFallback = state.correctAnswersByVariant[1] ?: state.correctAnswers
                    if (hasInvalidAnswerKey(state)) {
                        state = state.copy(statusMessage = "В каждом вопросе выберите от 1 до ${state.choicesCount - 1} вариантов")
                        return@BatchProcessingScreen
                    }
                    val name = state.criteriaNameDraft.trim().ifBlank {
                        val time = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
                        "Эталон $time"
                    }
                    val duplicateNameExists = repository.getAll().any {
                        it.name.trim().equals(name, ignoreCase = true)
                    }
                    if (duplicateNameExists) {
                        state = state.copy(statusMessage = "Имя эталона уже существует. Укажите уникальное имя.")
                        return@BatchProcessingScreen
                    }
                    val preset = BatchCriteriaPreset(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        questionsCount = state.questionsCount,
                        choicesCount = state.choicesCount,
                        columnCount = if (state.pageLayout == BatchPageLayout.OneColumn) 1 else 2,
                        correctAnswers = baseForFallback.map { it.sorted() },
                        variantAnswerKeys = (1..state.answerKeyVariantsCount).map { variant ->
                            BatchCriteriaVariantAnswers(
                                variant = variant,
                                correctAnswers = (state.correctAnswersByVariant[variant] ?: baseForFallback)
                                    .map { it.sorted() },
                            )
                        },
                        gradingCriteriaJson = state.gradingCriteriaJson,
                    )
                    repository.add(preset)
                    refreshPresets("Эталон сохранён: $name")
                    state = state.copy(criteriaNameDraft = "")
                }
                BatchProcessingAction.ToggleSavedCriteriaExpanded -> {
                    state = state.copy(savedCriteriaExpanded = !state.savedCriteriaExpanded)
                }
                is BatchProcessingAction.LoadCriteriaPreset -> {
                    val preset = repository.getById(action.presetId)
                    if (preset == null) {
                        refreshPresets("Эталон не найден")
                    } else {
                        applyPreset(preset, "Эталон загружен: ${preset.name}")
                    }
                }
                is BatchProcessingAction.DeleteCriteriaPreset -> {
                    repository.delete(action.presetId)
                    refreshPresets("Эталон удалён")
                }
                is BatchProcessingAction.SetPageLayout -> {
                    state = state.copy(pageLayout = action.layout)
                }
                BatchProcessingAction.ToggleStrictScoring -> {
                    state = state.copy(strictScoring = !state.strictScoring)
                }
                BatchProcessingAction.StartProcessing -> {
                    if (state.workItems.isEmpty()) {
                        state = state.copy(statusMessage = "Сначала добавьте работы в очередь")
                        return@BatchProcessingScreen
                    }
                    if (hasInvalidAnswerKey(state)) {
                        state = state.copy(statusMessage = "Сначала задайте эталонные ответы")
                        return@BatchProcessingScreen
                    }
                    if (state.isProcessing) return@BatchProcessingScreen
                    val stillDetecting = state.workItems.count { it.isVariantDetecting }
                    if (stillDetecting > 0) {
                        // Иначе работа без определённого варианта молча проверится по ключу варианта 1.
                        state = state.copy(
                            statusMessage = "Дождитесь окончания распознавания варианта и ФИО (осталось: $stillDetecting)",
                        )
                        return@BatchProcessingScreen
                    }
                    // Снимок состояния: фоновый цикл не должен читать изменяемое состояние экрана.
                    val snapshot = state
                    val items = snapshot.workItems.toList()
                    val layout = snapshot.pageLayout
                    val columnCount = if (layout == BatchPageLayout.OneColumn) 1 else 2
                    state = state.copy(isProcessing = true, statusMessage = "Проверяем работы… 0 из ${items.size}")
                    scope.launch {
                        try {
                            val runId = BatchLastRunStore.newRunId()
                            val createdAt = System.currentTimeMillis()
                            val runItems = withContext(Dispatchers.Default) {
                                val out = ArrayList<BatchRunItemResult>(items.size)
                                for ((index, item) in items.withIndex()) {
                                    val bmp = decodeBitmapFromContentUri(appContext, Uri.parse(item.contentUri))
                                    // Бланк v2: вариант и код ученика читаются здесь, даже если авто-распознавание выключено —
                                    // иначе работа без варианта проверилась бы по ключу варианта 1.
                                    val header = if (bmp != null && (item.effectiveVariant() == null || item.studentCode == null)) {
                                        runCatching {
                                            readBlankV2(
                                                appContext, bmp, snapshot.questionsCount, snapshot.choicesCount, columnCount,
                                                hiddenBubblesOfTest(snapshot.testId),
                                            )?.also { it.nameCrop.release() }
                                        }.getOrNull()
                                    } else null
                                    val variant = item.effectiveVariant() ?: header?.variant
                                    val code = item.studentCode ?: header?.code
                                    val who = codeStudent(snapshot.testId, code, variant)
                                    val keyBase = snapshot.correctAnswersByVariant[1] ?: snapshot.correctAnswers
                                    val cfg = BatchOmrConfig(
                                        questionsCount = snapshot.questionsCount,
                                        choicesCount = snapshot.choicesCount,
                                        columnCount = columnCount,
                                        answerKey = if (snapshot.answerKeyVariantsCount <= 0 || variant == null) keyBase
                                        else snapshot.correctAnswersByVariant[variant] ?: keyBase,
                                        strictScoring = snapshot.strictScoring,
                                        hiddenBubbleIds = hiddenBubblesOfTest(snapshot.testId, variant),
                                    )
                                    fun result(omr: com.tscan.scanertestov.feature.batch.engine.BatchOmrResult? = null, error: String? = null) =
                                        BatchRunItemResult(
                                            title = item.title,
                                            layoutLabel = layout.label,
                                            config = cfg,
                                            detectedVariant = variant,
                                            detectedStudentSurname = who.issued?.surname ?: item.detectedStudentSurname.takeIf { who.unknownCode == null },
                                            detectedStudentName = who.issued?.name ?: item.detectedStudentName.takeIf { who.unknownCode == null },
                                            detectedJournalClassName = who.issued?.className ?: item.detectedJournalClassName,
                                            nameCropPath = item.nameCropPath,
                                            studentNote = who.note,
                                            warning = who.warning,
                                            variantUnread = snapshot.answerKeyVariantsCount > 1 && variant == null,
                                            omr = omr,
                                            error = error,
                                        )
                                    val itemResult = if (bmp == null) {
                                        result(error = "не удалось открыть изображение")
                                    } else {
                                        try {
                                            result(omr = BatchOmrEngine.process(context = appContext, bitmap = bmp, config = cfg))
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            result(error = e.message ?: e.toString())
                                        } catch (e: OutOfMemoryError) {
                                            result(error = "недостаточно памяти для обработки фото")
                                        } finally {
                                            bmp.recycle()
                                        }
                                    }
                                    // Полный кадр бланка — на диск, в памяти остаётся только миниатюра.
                                    out += BatchLastRunStore.detachSheetImage(appContext, runId, index, itemResult)
                                    withContext(Dispatchers.Main) {
                                        state = state.copy(statusMessage = "Проверяем работы… ${index + 1} из ${items.size}")
                                    }
                                }
                                BatchLastRunStore.publish(
                                    appContext, runId, createdAt, out, snapshot.gradingCriteriaJson, snapshot.testId, snapshot.testTitle,
                                    variantKeys = if (snapshot.answerKeyVariantsCount > 1) {
                                        (1..snapshot.answerKeyVariantsCount).associateWith { v ->
                                            snapshot.correctAnswersByVariant[v] ?: snapshot.correctAnswersByVariant[1] ?: snapshot.correctAnswers
                                        }
                                    } else emptyMap(),
                                )
                                out
                            }
                            state = state.copy(
                                isProcessing = false,
                                lastRunSummary = "Распознано работ: ${runItems.size} · эталонных вопросов: ${snapshot.questionsCount}",
                                statusMessage = null
                            )
                            navController.navigate(AppDestinations.BATCH_RESULTS)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            state = state.copy(
                                isProcessing = false,
                                statusMessage = e.message ?: "Ошибка распознавания"
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Ключ некорректен, если в каком-то вопросе не выбран ни один вариант или выбраны все. */
private fun hasInvalidAnswerKey(state: BatchProcessingState): Boolean {
    fun bad(answers: List<Set<Int>>) = answers.any { it.isEmpty() || it.size >= state.choicesCount }
    val base = state.correctAnswersByVariant[1] ?: state.correctAnswers
    if (bad(base)) return true
    if (state.answerKeyVariantsCount <= 0) return false
    return (1..state.answerKeyVariantsCount).any { v -> bad(state.correctAnswersByVariant[v].orEmpty()) }
}

/** Сохраняет место для имени с бланка картинкой (для «Чья работа?» на экране результатов). */
private fun saveNameCropImage(context: android.content.Context, workId: String, crop: Mat): String? = runCatching {
    if (crop.empty()) return@runCatching null
    val dir = java.io.File(context.filesDir, "batch_names").apply { mkdirs() }
    val safe = workId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
    val file = java.io.File(dir, "$safe.jpg")
    val rgba = Mat()
    try {
        when (crop.channels()) {
            3 -> org.opencv.imgproc.Imgproc.cvtColor(crop, rgba, org.opencv.imgproc.Imgproc.COLOR_BGR2RGBA)
            1 -> org.opencv.imgproc.Imgproc.cvtColor(crop, rgba, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
            else -> crop.copyTo(rgba)
        }
        val bmp = android.graphics.Bitmap.createBitmap(rgba.cols(), rgba.rows(), android.graphics.Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(rgba, bmp)
            java.io.FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        } finally {
            bmp.recycle()
        }
        file.absolutePath
    } finally {
        rgba.release()
    }
}.getOrNull()
