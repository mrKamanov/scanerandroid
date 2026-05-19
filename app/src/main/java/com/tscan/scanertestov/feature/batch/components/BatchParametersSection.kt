package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: секция параметров проверки и управления пресетами эталона.
 */
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubbleSectionCard
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.feature.batch.BatchPageLayout
import com.tscan.scanertestov.feature.batch.BatchProcessingAction
import com.tscan.scanertestov.feature.batch.BatchProcessingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchParametersSection(
    state: BatchProcessingState,
    onAction: (BatchProcessingAction) -> Unit
) {
    val selectedVariant = state.selectedAnswerKeyVariant
    val displayedAnswers = if (selectedVariant != null && state.answerKeyVariantsCount > 0) {
        state.correctAnswersByVariant[selectedVariant] ?: state.correctAnswers
    } else {
        state.correctAnswers
    }
    val normalizedDraft = state.criteriaNameDraft.trim()
    val hasDuplicateDraftName = normalizedDraft.isNotEmpty() && state.savedCriteria.any {
        it.name.trim().equals(normalizedDraft, ignoreCase = true)
    }
    ShellBubbleSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.parametersSectionTitle, style = MaterialTheme.typography.titleMedium)
            Text(state.answerKeyTitle, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShellBubbleOutlinedButton(
                    text = state.addVariantButtonText,
                    onClick = { onAction(BatchProcessingAction.AddAnswerKeyVariant) },
                    enabled = !state.isProcessing && state.answerKeyVariantsCount < 4,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillMaxWidth = false,
                )
                ShellBubbleOutlinedButton(
                    text = state.removeVariantButtonText,
                    onClick = { onAction(BatchProcessingAction.RemoveAnswerKeyVariant) },
                    enabled = !state.isProcessing && state.answerKeyVariantsCount > 0,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillMaxWidth = false,
                )
            }

            Text(state.answerGridTitle, style = MaterialTheme.typography.labelLarge)
            Text(
                text = state.answerGridHelpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StepperField(
                title = "Сколько вопросов в работе",
                value = state.questionsCount,
                limitsLabel = "от 1 до 35",
                onMinus = { onAction(BatchProcessingAction.SetQuestionsCount(state.questionsCount - 1)) },
                onPlus = { onAction(BatchProcessingAction.SetQuestionsCount(state.questionsCount + 1)) },
                enabled = !state.isProcessing
            )
            StepperField(
                title = "Сколько вариантов ответа в вопросе",
                value = state.choicesCount,
                limitsLabel = "от 2 до 9",
                onMinus = { onAction(BatchProcessingAction.SetChoicesCount(state.choicesCount - 1)) },
                onPlus = { onAction(BatchProcessingAction.SetChoicesCount(state.choicesCount + 1)) },
                enabled = !state.isProcessing
            )

            AnswerGridEditor(
                questionsCount = state.questionsCount,
                choicesCount = state.choicesCount,
                correctAnswers = displayedAnswers,
                columnsCount = if (state.pageLayout == BatchPageLayout.OneColumn) 1 else 2,
                enabled = !state.isProcessing,
                tabsContent = if (state.answerKeyVariantsCount > 0) {
                    {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            for (variant in 1..state.answerKeyVariantsCount) {
                                FilterChip(
                                    selected = state.selectedAnswerKeyVariant == variant,
                                    onClick = { onAction(BatchProcessingAction.SelectAnswerKeyVariant(variant)) },
                                    enabled = !state.isProcessing,
                                    label = { Text("В$variant") },
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onSelect = { q, c ->
                    onAction(
                        BatchProcessingAction.ToggleCorrectAnswerSelection(
                            questionIndex = q,
                            choiceIndex = c
                        )
                    )
                }
            )

            OutlinedTextField(
                value = state.criteriaNameDraft,
                onValueChange = { onAction(BatchProcessingAction.SetCriteriaNameDraft(it)) },
                enabled = !state.isProcessing,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(state.criteriaNameTitle) },
                isError = hasDuplicateDraftName,
                supportingText = {
                    if (hasDuplicateDraftName) {
                        Text("Имя уже занято. Введите уникальное название.")
                    }
                },
                singleLine = true
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val saveInteraction = remember { MutableInteractionSource() }
                val savePal = mainMenuBubbleGradient(1)
                RealtimeBubbleIconButton(
                    onClick = { onAction(BatchProcessingAction.SaveCriteriaPreset) },
                    contentDescription = state.saveCriteriaButtonText,
                    topColor = savePal.first,
                    bottomColor = savePal.second,
                    size = 64.dp,
                    enabled = !state.isProcessing && !hasDuplicateDraftName,
                    interactionSource = saveInteraction,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isProcessing) {
                        onAction(BatchProcessingAction.ToggleSavedCriteriaExpanded)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.savedCriteriaTitle, style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = if (state.savedCriteriaExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (state.savedCriteriaExpanded) state.savedCriteriaCollapseText else state.savedCriteriaExpandText,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.savedCriteriaExpanded) {
                if (state.savedCriteria.isEmpty()) {
                    Text(
                        text = "Пока нет сохранённых шаблонов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (preset in state.savedCriteria) {
                            key(preset.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "${preset.name} (${preset.questionsCount}x${preset.choicesCount}, ${preset.columnCount} кол.)",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    val loadPal = mainMenuBubbleGradient(3)
                                    val delPal = mainMenuBubbleGradient(5)
                                    val loadIx = remember(preset.id) { MutableInteractionSource() }
                                    val delIx = remember(preset.id) { MutableInteractionSource() }
                                    RealtimeBubbleIconButton(
                                        onClick = {
                                            onAction(BatchProcessingAction.LoadCriteriaPreset(preset.id))
                                        },
                                        contentDescription = "Загрузить шаблон ${preset.name}",
                                        topColor = loadPal.first,
                                        bottomColor = loadPal.second,
                                        size = 44.dp,
                                        enabled = !state.isProcessing,
                                        interactionSource = loadIx,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    RealtimeBubbleIconButton(
                                        onClick = {
                                            onAction(BatchProcessingAction.DeleteCriteriaPreset(preset.id))
                                        },
                                        contentDescription = "Удалить шаблон ${preset.name}",
                                        topColor = delPal.first,
                                        bottomColor = delPal.second,
                                        size = 44.dp,
                                        enabled = !state.isProcessing,
                                        interactionSource = delIx,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            Text(state.layoutTitle, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BatchPageLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = state.pageLayout == layout,
                        onClick = { onAction(BatchProcessingAction.SetPageLayout(layout)) },
                        enabled = !state.isProcessing,
                        label = { Text(layout.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShellBubbleSwitch(
                    checked = state.strictScoring,
                    onCheckedChange = { onAction(BatchProcessingAction.ToggleStrictScoring) },
                    enabled = !state.isProcessing,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.strictScoringTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = state.strictScoringSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
