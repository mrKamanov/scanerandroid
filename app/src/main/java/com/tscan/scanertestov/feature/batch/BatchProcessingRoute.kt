package com.tscan.scanertestov.feature.batch

/**
 * Описание: маршрут экрана пакетной обработки — локальное состояние очереди, эталона, импорт изображений и навигация.
 */
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun BatchProcessingRoute(navController: NavHostController) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { BatchCriteriaRepository(appContext) }
    var state by remember {
        mutableStateOf(
            BatchProcessingState(
                savedCriteria = repository.getAll()
            )
        )
    }
    var nextWorkId by remember { mutableIntStateOf(1) }
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
    fun clampChoices(value: Int): Int = value.coerceIn(2, 9)
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
            val (variantResult, nameResult) = coroutineScope {
                val rosterDeferred = async(Dispatchers.IO) {
                    JournalRepository(appContext).loadAll()
                }
                val bmp = decodeBitmapFromContentUri(appContext, uri)
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
                    val (variantCrop, nameCrop) = withContext(Dispatchers.Default) {
                        extractVariantAndNameCropsSingleSnapshot(bmp)
                    }
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
            }
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

    val batchNavEntry = remember(navController) {
        navController.getBackStackEntry(AppDestinations.BATCH_PROCESSING)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestBatchState by rememberUpdatedState(state)

    fun applyCameraCaptureUri(uri: Uri) {
        var cur = latestBatchState
        val uriStr = uri.toString()
        if (cur.workItems.any { it.contentUri == uriStr }) {
            state = cur.copy(statusMessage = "Этот снимок уже в очереди")
            return
        }
        val id = "w-${nextWorkId++}"
        val displayName = resolveBatchImageDisplayName(context, uri)
        val item = BatchWorkItem(
            id = id,
            title = "Работа №${cur.workItems.size + 1}",
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
        val withNew = renumberWorkTitles(cur.workItems + item)
        state = cur.copy(
            workItems = withNew,
            previewWorkId = cur.previewWorkId ?: item.id,
            statusMessage = addedSingleWorkMessage(
                itemTitle = withNew.last().title,
                autoRecognitionEnabled = cur.autoRecognitionEnabled,
            ),
        )
        if (cur.autoRecognitionEnabled) enqueueAutoRecognition(item.id, uri)
    }

    DisposableEffect(batchNavEntry, lifecycleOwner) {
        val liveData = batchNavEntry.savedStateHandle.getLiveData<String?>(BatchCaptureKeys.RESULT_URI)
        val observer = Observer<String?> { uriStr ->
            if (uriStr.isNullOrEmpty()) return@Observer
            batchNavEntry.savedStateHandle.remove<String>(BatchCaptureKeys.RESULT_URI)
            applyCameraCaptureUri(Uri.parse(uriStr))
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
            val id = "w-${nextWorkId++}"
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

    BatchProcessingScreen(
        state = state,
        onAction = { action ->
            when (action) {
                BatchProcessingAction.Back -> navController.popBackStack()
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
                    state = state.copy(
                        questionsCount = q,
                        correctAnswers = normalizedBase,
                        correctAnswersByVariant = normalizedVariants,
                    )
                }
                is BatchProcessingAction.SetChoicesCount -> {
                    val c = clampChoices(action.count)
                    val normalizedBase = normalizeAnswers(state.questionsCount, c, state.correctAnswers)
                    val normalizedVariants = state.correctAnswersByVariant
                        .mapValues { (_, answers) -> normalizeAnswers(state.questionsCount, c, answers) }
                    state = state.copy(
                        choicesCount = c,
                        correctAnswers = normalizedBase,
                        correctAnswersByVariant = normalizedVariants,
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
                    val hasInvalidBase = baseForFallback.any { it.isEmpty() || it.size >= state.choicesCount }
                    val hasInvalidVariants = if (state.answerKeyVariantsCount <= 0) {
                        false
                    } else {
                        (1..state.answerKeyVariantsCount).any { variant ->
                            state.correctAnswersByVariant[variant]
                                .orEmpty()
                                .any { it.isEmpty() || it.size >= state.choicesCount }
                        }
                    }
                    val hasInvalid = hasInvalidBase || hasInvalidVariants
                    if (hasInvalid) {
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
                        val q = clampQuestions(preset.questionsCount)
                        val c = clampChoices(preset.choicesCount)
                        val layout = if (preset.columnCount == 2) {
                            BatchPageLayout.TwoColumns
                        } else {
                            BatchPageLayout.OneColumn
                        }
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
                            statusMessage = "Эталон загружен: ${preset.name}",
                            savedCriteria = repository.getAll()
                        )
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
                    val baseForFallback = state.correctAnswersByVariant[1] ?: state.correctAnswers
                    val hasInvalidBase = baseForFallback.any { it.isEmpty() || it.size >= state.choicesCount }
                    val hasInvalidVariants = if (state.answerKeyVariantsCount <= 0) {
                        false
                    } else {
                        (1..state.answerKeyVariantsCount).any { variant ->
                            state.correctAnswersByVariant[variant]
                                .orEmpty()
                                .any { it.isEmpty() || it.size >= state.choicesCount }
                        }
                    }
                    if (hasInvalidBase || hasInvalidVariants) {
                        state = state.copy(statusMessage = "Сначала задайте эталонные ответы")
                        return@BatchProcessingScreen
                    }
                    if (state.isProcessing) return@BatchProcessingScreen
                    val items = state.workItems.toList()
                    val layout = state.pageLayout
                    state = state.copy(isProcessing = true, statusMessage = "Проверяем работы…")
                    scope.launch {
                        try {
                            val lines = withContext(Dispatchers.Default) {
                                val out = mutableListOf<String>()
                                val runItems = mutableListOf<BatchRunItemResult>()
                                for (item in items) {
                                    val itemAnswerKey = resolveAnswerKeyForWork(item, state)
                                    val itemVariant = item.effectiveVariant()
                                    val variantSuffix = itemVariant?.let { " · вариант: $it" }.orEmpty()
                                    val uri = Uri.parse(item.contentUri)
                                    val bmp = decodeBitmapFromContentUri(appContext, uri)
                                    if (bmp == null) {
                                        val msg = "${item.title}: не удалось открыть изображение"
                                        out += msg
                                        runItems += BatchRunItemResult(
                                            title = item.title,
                                            layoutLabel = layout.label,
                                            config = BatchOmrConfig(
                                                questionsCount = state.questionsCount,
                                                choicesCount = state.choicesCount,
                                                columnCount = if (layout == BatchPageLayout.OneColumn) 1 else 2,
                                                answerKey = itemAnswerKey,
                                                strictScoring = state.strictScoring,
                                            ),
                                            detectedVariant = item.effectiveVariant(),
                                            detectedStudentSurname = item.detectedStudentSurname,
                                            detectedStudentName = item.detectedStudentName,
                                            detectedJournalClassName = item.detectedJournalClassName,
                                            error = "не удалось открыть изображение",
                                        )
                                        continue
                                    }
                                    try {
                                        val cfg = BatchOmrConfig(
                                            questionsCount = state.questionsCount,
                                            choicesCount = state.choicesCount,
                                            columnCount = if (layout == BatchPageLayout.OneColumn) 1 else 2,
                                            answerKey = itemAnswerKey,
                                            strictScoring = state.strictScoring,
                                        )
                                        val omr = BatchOmrEngine.process(
                                            context = appContext,
                                            bitmap = bmp,
                                            config = cfg,
                                        )
                                        runItems += BatchRunItemResult(
                                            title = item.title,
                                            layoutLabel = layout.label,
                                            config = cfg,
                                            detectedVariant = item.effectiveVariant(),
                                            detectedStudentSurname = item.detectedStudentSurname,
                                            detectedStudentName = item.detectedStudentName,
                                            detectedJournalClassName = item.detectedJournalClassName,
                                            omr = omr,
                                        )
                                        val fixedSuffix = if (omr.fixedCells.isNotEmpty()) {
                                            " · fixed: ${omr.fixedCells.size}"
                                        } else {
                                            ""
                                        }
                                        if (!omr.contourFound) {
                                            out += "${item.title}: контур бланка не найден"
                                        } else {
                                            val itemEtalonText = itemAnswerKey.joinToString(separator = " | ") { answerSet ->
                                                answerSet.sorted().joinToString("+") { (it + 1).toString() }
                                            }
                                            out += "${item.title}: ${omr.correctCount}/${state.questionsCount} · ${"%.1f".format(Locale.US, omr.scorePercent)}%$fixedSuffix · ${layout.label}$variantSuffix · ключ [$itemEtalonText]"
                                        }
                                    } catch (e: Exception) {
                                        out += "${item.title}: ошибка — ${e.message ?: e.toString()}"
                                        runItems += BatchRunItemResult(
                                            title = item.title,
                                            layoutLabel = layout.label,
                                            config = BatchOmrConfig(
                                                questionsCount = state.questionsCount,
                                                choicesCount = state.choicesCount,
                                                columnCount = if (layout == BatchPageLayout.OneColumn) 1 else 2,
                                                answerKey = itemAnswerKey,
                                                strictScoring = state.strictScoring,
                                            ),
                                            detectedVariant = item.effectiveVariant(),
                                            detectedStudentSurname = item.detectedStudentSurname,
                                            detectedStudentName = item.detectedStudentName,
                                            detectedJournalClassName = item.detectedJournalClassName,
                                            error = e.message ?: e.toString(),
                                        )
                                    } finally {
                                        bmp.recycle()
                                    }
                                }
                                BatchLastRunStore.resultItems = runItems
                                out.toList()
                            }
                            BatchLastRunStore.resultLines = lines
                            state = state.copy(
                                isProcessing = false,
                                lastRunSummary = "Распознано работ: ${items.size} · эталонных вопросов: ${state.questionsCount}",
                                statusMessage = null
                            )
                            navController.navigate(AppDestinations.BATCH_RESULTS)
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
