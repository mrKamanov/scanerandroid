package com.tscan.scanertestov.feature.batch

/**
 * Описание: экран пакетной обработки — очередь, параметры, предпросмотр (по желанию), запуск проверки.
 */
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButtonRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.tscan.scanertestov.ui.theme.Scrapbook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.feature.batch.components.BatchParametersSection
import com.tscan.scanertestov.feature.batch.components.BatchPreviewSection
import com.tscan.scanertestov.feature.batch.components.BatchQueueSection
import com.tscan.scanertestov.ml.InferenceModelRuntime
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.ShellBubbleSectionCard
import com.tscan.scanertestov.ui.components.TourOverlay
import com.tscan.scanertestov.ui.components.TourStep
import com.tscan.scanertestov.ui.components.rememberTour
import com.tscan.scanertestov.ui.components.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProcessingScreen(
    state: BatchProcessingState = BatchProcessingState(),
    onAction: (BatchProcessingAction) -> Unit
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        InferenceModelRuntime.syncFromPreferences(appContext)
    }
    val tour = rememberTour("batch", BatchTourSteps)
    Box {
    ScreenScaffold(
        onBack = { onAction(BatchProcessingAction.Back) },
        showBubbleSheetDecor = true,
    ) { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            Text(text = state.screenTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                text = state.hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // По какому тесту идёт проверка — видно сразу, а не только в строке состояния внизу.
            state.testTitle?.let { title ->
                Text(
                    text = "Проверка по тесту «$title»: правильные ответы и критерии подставлены из теста, результаты попадут в его отчёт.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.tscan.scanertestov.ui.theme.Scrapbook.Lemon, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            Box(modifier = Modifier.tourTarget(tour, "queue")) {
                BatchQueueSection(state = state, onAction = onAction)
            }
            Box(modifier = Modifier.tourTarget(tour, "params")) {
                BatchParametersSection(state = state, onAction = onAction)
            }
            BatchPreviewSection(state = state, onAction = onAction)
            Box(modifier = Modifier.tourTarget(tour, "run")) {
                BatchRunSection(state = state, onAction = onAction)
            }
        }
    }
    TourOverlay(tour)
    }
}

/** Мини-экскурсия по проверке класса: три шага экрана. */
private val BatchTourSteps = listOf(
    TourStep(
        "queue",
        "Шаг 1. Фотографии бланков",
        "Кнопка «Камера» открывает съёмку с белым контуром. Положите бланк на стол, держите телефон прямо над ним " +
            "и уложите чёрную рамку бланка в контур. Бланки можно снимать один за другим, не выходя из камеры. " +
            "Если фото уже есть, нажмите «Галерея» и выберите все сразу. Включённое «Авто-распознавание» " +
            "сразу читает с каждого бланка номер варианта и фамилию.",
    ),
    TourStep(
        "params",
        "Шаг 2. Правильные ответы",
        "Укажите число вопросов и вариантов ответа так же, как на бланке, и нажмите на правильные кружки. " +
            "Если у теста несколько вариантов, нажмите «Добавить» и задайте ключ для каждого — работа проверится " +
            "по ключу своего варианта. «Проверять строго» важно только для вопросов с несколькими верными ответами. " +
            "Сохраните ключ шаблоном, чтобы в другом классе просто выбрать его.",
    ),
    TourStep(
        "run",
        "Шаг 3. Проверка",
        "Нажмите «Проверить работы». Если ученик исправлял ответ и приложение не уверено, как его засчитать, " +
            "работа будет помечена «Проверьте» — откройте её и решите сами. Зачёркнутый кружок, рядом с которым " +
            "уверенно закрашен другой, приложение считает пустым само и помечает значком ✎.",
    ),
)

@Composable
private fun BatchRunSection(
    state: BatchProcessingState,
    onAction: (BatchProcessingAction) -> Unit
) {
    ShellBubbleSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.runSectionTitle, style = MaterialTheme.typography.titleMedium)
            if (state.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Проверяем работы…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val startInteraction = remember { MutableInteractionSource() }
                val startPal = mainMenuBubbleGradient(2)
                ShellBubblePrimaryButtonRow(
                    text = state.startButtonText,
                    onClick = { onAction(BatchProcessingAction.StartProcessing) },
                    enabled = !state.isProcessing,
                    paletteIndex = 1,
                    fillMaxWidth = true,
                    leading = { Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(22.dp)) },
                )
            }
            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
