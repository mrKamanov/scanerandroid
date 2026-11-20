package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: редактор эталонной сетки и вспомогательные контролы параметров.
 */
import androidx.compose.foundation.background
import com.tscan.scanertestov.ui.theme.Scrapbook
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
    // Ключ ответов в виде строк бланка: «№ ○ ○ ● ○». Раньше каждый вопрос был отдельной
    // карточкой, и на 35 вопросов уходило несколько экранов прокрутки.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Scrapbook.Paper)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabsContent?.invoke()
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (choice in 0 until choicesCount) {
                Text(
                    text = (choice + 1).toString(),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Scrapbook.InkMuted,
                )
            }
        }
        for (question in 0 until questionsCount) {
            AnswerKeyRow(
                question = question,
                choicesCount = choicesCount,
                selectedChoices = correctAnswers.getOrNull(question).orEmpty(),
                enabled = enabled,
                striped = question % 2 == 1,
                onSelect = { onSelect(question, it) },
            )
        }
    }
}

@Composable
private fun AnswerKeyRow(
    question: Int,
    choicesCount: Int,
    selectedChoices: Set<Int>,
    enabled: Boolean,
    striped: Boolean,
    onSelect: (choiceIndex: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (striped) Scrapbook.Board.copy(alpha = 0.45f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${question + 1}.",
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedChoices.isEmpty()) Scrapbook.AccentInk else Scrapbook.Ink,
        )
        for (choice in 0 until choicesCount) {
            val selected = selectedChoices.contains(choice)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) Scrapbook.Ink else Scrapbook.Paper)
                    .border(2.dp, Scrapbook.Ink.copy(alpha = if (selected) 1f else 0.55f), CircleShape)
                    .clickable(enabled = enabled) { onSelect(choice) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (choice + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else Scrapbook.InkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
