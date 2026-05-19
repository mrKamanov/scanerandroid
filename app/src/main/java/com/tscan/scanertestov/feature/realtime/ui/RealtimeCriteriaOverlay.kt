package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: панель параметров теста (режим, число вопросов и вариантов).
 */
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PanelAlpha = 0.88f

private val CounterBlockBg = Color(0xE6232F45)
private val CounterBlockBorder = Color(0xFF57CCFF).copy(alpha = 0.42f)
private val ValueChipBg = Color(0xFF1A2838)
private val ValueChipBorder = Color(0xFF6AD7FF).copy(alpha = 0.55f)

@Composable
internal fun RealtimeCriteriaOverlay(
    visible: Boolean,
    strictScoring: Boolean,
    questionsCount: Int,
    choicesCount: Int,
    onDismiss: () -> Unit,
    onStrictChange: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDecQuestions: () -> Unit,
    onIncQuestions: () -> Unit,
    onDecChoices: () -> Unit,
    onIncChoices: () -> Unit,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            val surface = MaterialTheme.colorScheme.surfaceContainerHigh
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 400.dp)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surface.copy(alpha = PanelAlpha),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Параметры теста",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = strictScoring,
                            onClick = { onStrictChange(true) },
                            label = { Text("Строгий") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = !strictScoring,
                            onClick = { onStrictChange(false) },
                            label = { Text("Частичный") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CounterBlockBg,
                        border = BorderStroke(1.dp, CounterBlockBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CompactCounterLine(
                                label = "Вопросы",
                                rangeText = "2–20",
                                value = questionsCount,
                                onMinus = onDecQuestions,
                                onPlus = onIncQuestions,
                            )
                            CompactCounterLine(
                                label = "Варианты",
                                rangeText = "2–5",
                                value = choicesCount,
                                onMinus = onDecChoices,
                                onPlus = onIncChoices,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RealtimeBubbleIconButton(
                            onClick = onDismiss,
                            contentDescription = "Закрыть без сохранения",
                            topColor = Color(0xFFA8B3C7),
                            bottomColor = Color(0xFF73829A),
                            size = 48.dp,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                        RealtimeBubbleIconButton(
                            onClick = onApply,
                            contentDescription = "Готово",
                            topColor = Color(0xFF7BE2BB),
                            bottomColor = Color(0xFF2BB98A),
                            size = 48.dp,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                        RealtimeBubbleIconButton(
                            onClick = onReset,
                            contentDescription = "Сбросить ответы эталона",
                            topColor = Color(0xFFFFD47F),
                            bottomColor = Color(0xFFF5A93E),
                            size = 48.dp,
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCounterLine(
    label: String,
    rangeText: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE8F4FF).copy(alpha = 0.95f),
            )
            Text(
                text = rangeText,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9EC4E8).copy(alpha = 0.85f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RealtimeBubbleIconButton(
                onClick = onMinus,
                contentDescription = "Минус",
                topColor = Color(0xFFB4BCC9),
                bottomColor = Color(0xFF7A8496),
                size = 38.dp,
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ValueChipBg,
                border = BorderStroke(1.dp, ValueChipBorder),
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB8ECFF),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            RealtimeBubbleIconButton(
                onClick = onPlus,
                contentDescription = "Плюс",
                topColor = Color(0xFF8ADDFE),
                bottomColor = Color(0xFF2EA3F3),
                size = 38.dp,
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}
