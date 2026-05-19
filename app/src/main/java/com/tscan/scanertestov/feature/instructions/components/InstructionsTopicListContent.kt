package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: приветственная карточка и строки оглавления на экране инструкций.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.instructions.InstructionTopic
import com.tscan.scanertestov.ui.components.SettingsShellCard

@Composable
internal fun InstructionsWelcomeCard(modifier: Modifier = Modifier) {
    SettingsShellCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Руководство пользователя",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE8F4FC),
            )
            Text(
                text = "Выберите тему ниже. Материал разбит на шаги: от первого запуска до пакетной проверки и отчётов. " +
                    "Рекомендуем начать с «С чего начать», затем прочитать «Данные на вашем устройстве».",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8D4E8),
            )
        }
    }
}

@Composable
internal fun InstructionsTopicRow(
    index: Int,
    topic: InstructionTopic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsShellCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5EC4FF)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF061018),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8F4FC),
                )
                Text(
                    text = topic.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB8D4E8),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF8FAFC4),
            )
        }
    }
}
