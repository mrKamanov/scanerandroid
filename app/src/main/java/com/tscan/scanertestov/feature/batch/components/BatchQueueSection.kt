package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: секция очереди работ для пакетной обработки.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.batch.BatchProcessingAction
import com.tscan.scanertestov.feature.batch.BatchProcessingState
import com.tscan.scanertestov.feature.batch.canEnqueueAutoRecognition
import com.tscan.scanertestov.feature.batch.effectiveVariant
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubbleSectionCard
import com.tscan.scanertestov.ui.components.ShellBubbleTextChip
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch

@Composable
fun BatchQueueSection(
    state: BatchProcessingState,
    onAction: (BatchProcessingAction) -> Unit
) {
    ShellBubbleSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.queueSectionTitle, style = MaterialTheme.typography.titleMedium)
                if (state.ocrWarmupInProgress || state.ocrWarmupReady) {
                    val indicatorColor = if (state.ocrWarmupReady) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(indicatorColor, CircleShape),
                    )
                }
            }
            Text(
                text = state.capturePhotoGuideText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val camInteraction = remember { MutableInteractionSource() }
                val galleryInteraction = remember { MutableInteractionSource() }
                val camPalette = mainMenuBubbleGradient(0)
                val galleryPalette = mainMenuBubbleGradient(1)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    RealtimeBubbleIconButton(
                        onClick = { onAction(BatchProcessingAction.CaptureByCamera) },
                        contentDescription = state.captureButtonText,
                        topColor = camPalette.first,
                        bottomColor = camPalette.second,
                        size = 64.dp,
                        enabled = !state.isProcessing,
                        interactionSource = camInteraction,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    RealtimeBubbleIconButton(
                        onClick = { onAction(BatchProcessingAction.LoadFromGallery) },
                        contentDescription = state.loadButtonText,
                        topColor = galleryPalette.first,
                        bottomColor = galleryPalette.second,
                        size = 64.dp,
                        enabled = !state.isProcessing,
                        interactionSource = galleryInteraction,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.autoRecognitionToggleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShellBubbleSwitch(
                    checked = state.autoRecognitionEnabled,
                    onCheckedChange = { onAction(BatchProcessingAction.SetAutoRecognitionEnabled(it)) },
                    enabled = !state.isProcessing,
                )
            }
            val recognitionEligibleCount = state.workItems.count { it.canEnqueueAutoRecognition() }
            val allSelected =
                state.selectedWorkIds.size == state.workItems.size && state.workItems.isNotEmpty()
            if (state.autoRecognitionEnabled && recognitionEligibleCount > 0) {
                ShellBubbleOutlinedButton(
                    text = "${state.runQueuedRecognitionButtonText} ($recognitionEligibleCount)",
                    onClick = { onAction(BatchProcessingAction.RunAutoRecognitionForQueued) },
                    enabled = !state.isProcessing,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Добавлено работ: ${state.workItems.size}",
                    style = MaterialTheme.typography.labelLarge
                )
                ShellBubbleTextChip(
                    text = if (allSelected) "Снять выделение" else "Выделить все",
                    onClick = { onAction(BatchProcessingAction.ToggleSelectAll) },
                    enabled = state.workItems.isNotEmpty() && !state.isProcessing,
                )
            }
            if (state.workItems.isEmpty()) {
                Text(
                    text = state.queueEmptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.workItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = state.selectedWorkIds.contains(item.id),
                                onCheckedChange = {
                                    onAction(BatchProcessingAction.ToggleWorkSelection(item.id))
                                },
                                enabled = !state.isProcessing
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                when {
                                    item.isVariantDetecting -> {
                                        Text(
                                            text = "Определяем вариант и имя…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    item.autoRecognitionRequested &&
                                        item.effectiveVariant() == null &&
                                        !item.isVariantDetecting -> {
                                        val err = item.variantDetectionError?.trim()
                                        val line = when {
                                            !err.isNullOrEmpty() -> "Вариант не распознан: $err"
                                            else -> "Вариант не распознан"
                                        }
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val studentFullName = listOfNotNull(
                                    item.detectedStudentSurname,
                                    item.detectedStudentName,
                                ).joinToString(" ").trim()
                                when {
                                    item.isVariantDetecting -> Unit
                                    studentFullName.isNotBlank() -> {
                                        val cls = item.detectedJournalClassName?.trim().orEmpty()
                                        val studentLine = if (cls.isNotEmpty()) {
                                            "Ученик: $cls · $studentFullName"
                                        } else {
                                            "Ученик: $studentFullName"
                                        }
                                        Text(
                                            text = studentLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    item.autoRecognitionRequested &&
                                        !item.studentNameDetectionError.isNullOrBlank() -> {
                                        Text(
                                            text = "Имя не распознано: ${item.studentNameDetectionError}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            ShellBubbleTextChip(
                                text = "Убрать",
                                onClick = { onAction(BatchProcessingAction.RemoveWork(item.id)) },
                                enabled = !state.isProcessing,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            ShellBubbleOutlinedButton(
                text = state.clearButtonText,
                onClick = { onAction(BatchProcessingAction.ClearQueue) },
                enabled = state.workItems.isNotEmpty() && !state.isProcessing,
            )
        }
    }
}
