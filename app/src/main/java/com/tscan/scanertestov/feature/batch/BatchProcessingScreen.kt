package com.tscan.scanertestov.feature.batch

/**
 * Описание: экран пакетной обработки — очередь, параметры, предпросмотр (по желанию), запуск проверки.
 */
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

            BatchQueueSection(state = state, onAction = onAction)
            BatchParametersSection(state = state, onAction = onAction)
            BatchPreviewSection(state = state, onAction = onAction)
            BatchRunSection(state = state, onAction = onAction)
        }
    }
}

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
                RealtimeBubbleIconButton(
                    onClick = { onAction(BatchProcessingAction.StartProcessing) },
                    contentDescription = state.startButtonText,
                    topColor = startPal.first,
                    bottomColor = startPal.second,
                    size = 72.dp,
                    enabled = !state.isProcessing,
                    interactionSource = startInteraction,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FactCheck,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
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
