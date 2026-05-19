package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: разметка текста одного раздела инструкции (шаги, советы, образцы кнопок).
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.instructions.InstructionSection
import com.tscan.scanertestov.feature.instructions.InstructionStep
import com.tscan.scanertestov.feature.instructions.InstructionTopic
import com.tscan.scanertestov.ui.components.SettingsShellCard

@Composable
internal fun InstructionsTopicDetailContent(
    topic: InstructionTopic,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsShellCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8F4FC),
                )
                Text(
                    text = topic.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8D4E8),
                )
            }
        }
        topic.sections.forEach { section ->
            InstructionSectionBlock(section = section)
        }
    }
}

@Composable
private fun InstructionSectionBlock(section: InstructionSection) {
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFE08A),
            )
            section.lead?.let { lead ->
                Text(
                    text = lead,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE8F4FC),
                )
            }
            section.steps.forEachIndexed { index, step ->
                InstructionStepRow(stepIndex = index + 1, step = step)
            }
            section.tips.forEach { tip ->
                InstructionCallout(
                    label = "Совет",
                    text = tip,
                    background = Color(0xFF1A4A3A).copy(alpha = 0.55f),
                    border = Color(0xFF4CAF7A).copy(alpha = 0.45f),
                    labelColor = Color(0xFF8FE8B0),
                )
            }
            section.examples.forEach { example ->
                InstructionCallout(
                    label = "Пример",
                    text = example,
                    background = Color(0xFF2A3548).copy(alpha = 0.55f),
                    border = Color(0xFF5EC4FF).copy(alpha = 0.35f),
                    labelColor = Color(0xFF9AD4FF),
                )
            }
        }
    }
}

@Composable
private fun InstructionStepRow(
    stepIndex: Int,
    step: InstructionStep,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF2EA3F3)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stepIndex.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE8F4FC),
            )
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8D4E8),
            )
            if (step.buttonHints.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    step.buttonHints.forEach { ref ->
                        InstructionsUiSample(ref = ref)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionCallout(
    label: String,
    text: String,
    background: Color,
    border: Color,
    labelColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE8F4FC),
        )
    }
}
