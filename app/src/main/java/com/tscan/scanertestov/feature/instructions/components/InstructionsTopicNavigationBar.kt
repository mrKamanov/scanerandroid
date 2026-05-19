package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: панель «Назад / Далее» для перехода между разделами инструкции без возврата к оглавлению.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.instructions.InstructionTopic
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton

@Composable
internal fun InstructionsTopicNavigationBar(
    topicIndex: Int,
    topicsCount: Int,
    previousTopic: InstructionTopic?,
    nextTopic: InstructionTopic?,
    onOpenTopic: (String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsShellCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Раздел ${topicIndex + 1} из $topicsCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8FAFC4),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (previousTopic != null) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ShellBubbleOutlinedButton(
                            text = "Назад",
                            onClick = { onOpenTopic(previousTopic.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = previousTopic.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB8D4E8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ShellBubbleOutlinedButton(
                            text = "К оглавлению",
                            onClick = onBackToList,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (nextTopic != null) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ShellBubblePrimaryButton(
                            text = "Далее",
                            onClick = { onOpenTopic(nextTopic.id) },
                            modifier = Modifier.fillMaxWidth(),
                            paletteIndex = 1,
                        )
                        Text(
                            text = nextTopic.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFE08A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ShellBubblePrimaryButton(
                            text = "К оглавлению",
                            onClick = onBackToList,
                            modifier = Modifier.fillMaxWidth(),
                            paletteIndex = 3,
                        )
                        Text(
                            text = "Вы прошли все разделы",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8FAFC4),
                        )
                    }
                }
            }
        }
    }
}
