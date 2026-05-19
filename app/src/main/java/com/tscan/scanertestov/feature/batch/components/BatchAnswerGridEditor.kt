package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: редактор эталонной сетки и вспомогательные контролы параметров.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.tscan.scanertestov.ui.components.ShellBubbleMiniButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StepperField(
    title: String,
    value: Int,
    limitsLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShellBubbleMiniButton(
                text = "−",
                onClick = onMinus,
                enabled = enabled,
                paletteIndex = 4,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )
            ShellBubbleMiniButton(
                text = "+",
                onClick = onPlus,
                enabled = enabled,
                paletteIndex = 4,
            )
            Text(
                text = limitsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnswerGridEditor(
    questionsCount: Int,
    choicesCount: Int,
    correctAnswers: List<Set<Int>>,
    columnsCount: Int,
    enabled: Boolean,
    tabsContent: (@Composable () -> Unit)? = null,
    onSelect: (questionIndex: Int, choiceIndex: Int) -> Unit
) {
    val corner = RoundedCornerShape(10.dp)
    val rows: List<List<Int>> = if (columnsCount == 1) {
        (0 until questionsCount).map { listOf(it) }
    } else {
        val q1 = (questionsCount + 1) / 2
        val q2 = questionsCount - q1
        (0 until q1).map { row ->
            buildList {
                add(row)
                if (row < q2) add(q1 + row)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .border(1.dp, Color.White.copy(alpha = 0.22f), corner)
            .clip(corner)
            .background(Color(0xFF0C2839).copy(alpha = 0.38f))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tabsContent?.invoke()
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { question ->
                        QuestionAnswerCard(
                            modifier = Modifier.weight(1f),
                            question = question,
                            choicesCount = choicesCount,
                            selectedChoices = correctAnswers.getOrNull(question).orEmpty(),
                            enabled = enabled,
                            onSelect = { onSelect(question, it) }
                        )
                    }
                    if (columnsCount == 2 && rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionAnswerCard(
    modifier: Modifier,
    question: Int,
    choicesCount: Int,
    selectedChoices: Set<Int>,
    enabled: Boolean,
    onSelect: (choiceIndex: Int) -> Unit
) {
    val selectedFill = Color(0xFF2E7D32)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Вопрос ${question + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = if (choicesCount <= 6) 6 else 5
            ) {
                for (choice in 0 until choicesCount) {
                    val selected = selectedChoices.contains(choice)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) selectedFill
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) selectedFill
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable(enabled = enabled) { onSelect(choice) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (choice + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
