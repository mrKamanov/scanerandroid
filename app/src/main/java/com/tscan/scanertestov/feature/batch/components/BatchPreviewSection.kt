package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: предпросмотр работ — сворачиваемый блок, фильтр по варианту, миниатюры, карточка листа, смена варианта эталона.
 */
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.batch.BatchProcessingAction
import com.tscan.scanertestov.feature.batch.BatchProcessingState
import com.tscan.scanertestov.feature.batch.BatchWorkItem
import com.tscan.scanertestov.feature.batch.effectiveVariant
import com.tscan.scanertestov.ui.components.ShellBubbleSectionCard

private sealed class PreviewVariantFilter {
    data object All : PreviewVariantFilter()
    data class One(val variant: Int) : PreviewVariantFilter()
    data object Unassigned : PreviewVariantFilter()
}

@Composable
fun BatchPreviewSection(
    state: BatchProcessingState,
    onAction: (BatchProcessingAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ShellBubbleSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.previewSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!expanded) {
                        Text(
                            text = state.previewSectionCollapsedHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) {
                        state.previewSectionCollapseA11y
                    } else {
                        state.previewSectionExpandA11y
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                BatchPreviewSectionBody(
                    state = state,
                    onAction = onAction,
                    showSectionTitle = false,
                )
            }
            state.lastRunSummary?.let { summary ->
                Text(
                    text = "Последний запуск: $summary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BatchPreviewSectionBody(
    state: BatchProcessingState,
    onAction: (BatchProcessingAction) -> Unit,
    showSectionTitle: Boolean,
) {
    Column(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSectionTitle) {
            Text(state.previewSectionTitle, style = MaterialTheme.typography.titleMedium)
        }
        if (state.workItems.isEmpty()) {
            Text(
                text = state.previewPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val previewId = state.previewWorkId?.takeIf { id ->
                state.workItems.any { it.id == id }
            } ?: state.workItems.first().id

            val grouped = remember(state.workItems) {
                state.workItems.groupBy { it.effectiveVariant() }
            }
            var filter by remember(state.workItems) {
                mutableStateOf<PreviewVariantFilter>(PreviewVariantFilter.All)
            }

            val visible = remember(filter, state.workItems) {
                visibleWorks(filter, state.workItems)
            }

            LaunchedEffect(grouped, filter) {
                if (visibleWorks(filter, state.workItems).isEmpty() && filter !is PreviewVariantFilter.All) {
                    filter = PreviewVariantFilter.All
                }
            }

            val visibleIds = remember(visible) { visible.map { it.id } }
            LaunchedEffect(filter, visibleIds, state.previewWorkId) {
                if (visibleIds.isEmpty()) return@LaunchedEffect
                if (state.previewWorkId !in visibleIds) {
                    onAction(BatchProcessingAction.SelectPreviewWork(visibleIds.first()))
                }
            }

            Text(
                text = "Группа",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filter is PreviewVariantFilter.All,
                    onClick = { filter = PreviewVariantFilter.All },
                    label = { Text("Все · ${state.workItems.size}") },
                )
                grouped.keys.filterNotNull().sorted().forEach { v ->
                    val n = grouped.getValue(v).size
                    FilterChip(
                        selected = filter is PreviewVariantFilter.One && (filter as PreviewVariantFilter.One).variant == v,
                        onClick = { filter = PreviewVariantFilter.One(v) },
                        label = { Text("Вариант $v · $n") },
                    )
                }
                val unassigned = grouped[null].orEmpty()
                if (unassigned.isNotEmpty()) {
                    FilterChip(
                        selected = filter is PreviewVariantFilter.Unassigned,
                        onClick = { filter = PreviewVariantFilter.Unassigned },
                        label = { Text("Без номера · ${unassigned.size}") },
                    )
                }
            }

            Text(
                text = "Листы",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visible.forEach { work ->
                    val selected = work.id == previewId
                    BatchWorkThumb(
                        contentUri = work.contentUri,
                        selected = selected,
                        onClick = { onAction(BatchProcessingAction.SelectPreviewWork(work.id)) },
                    )
                }
            }

            HorizontalDivider()

            val previewItem = state.workItems.first { it.id == previewId }
            val maxV = batchVariantPickerUpperBound(state)
            PreviewDetailCard(
                item = previewItem,
                maxVariantInclusive = maxV,
                onSetOverride = { v ->
                    onAction(BatchProcessingAction.SetWorkVariantOverride(previewItem.id, v))
                },
            )
        }
    }
}

private fun visibleWorks(filter: PreviewVariantFilter, items: List<BatchWorkItem>): List<BatchWorkItem> {
    return when (filter) {
        is PreviewVariantFilter.All -> items
        is PreviewVariantFilter.One -> items.filter { it.effectiveVariant() == filter.variant }
        is PreviewVariantFilter.Unassigned -> items.filter { it.effectiveVariant() == null }
    }
}

private fun batchVariantPickerUpperBound(state: BatchProcessingState): Int {
    val fromKey = state.answerKeyVariantsCount
    val fromWorks = state.workItems.mapNotNull { it.effectiveVariant() }.maxOrNull() ?: 0
    return maxOf(fromKey, fromWorks, 1).coerceAtMost(9)
}
