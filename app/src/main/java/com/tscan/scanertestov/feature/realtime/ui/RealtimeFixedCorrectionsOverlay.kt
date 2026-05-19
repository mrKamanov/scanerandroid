package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: панель уточнения ячеек «исправление» на экране быстрой проверки.
 */
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.RealtimeFixedCorrections
import com.tscan.scanertestov.ui.components.SettingsShellCard

@Composable
internal fun RealtimeFixedCorrectionsOverlay(
    fixedCells: List<BatchCellPrediction>,
    decisions: Map<String, Boolean>,
    sheetBitmap: Bitmap,
    questionsCount: Int,
    choicesCount: Int,
    columnCount: Int,
    onMarkAnswered: (String) -> Unit,
    onMarkEmpty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unresolved = fixedCells.filter { decisions[RealtimeFixedCorrections.keyFor(it)] == null }
    if (unresolved.isEmpty()) return

    val current = unresolved.first()
    val total = fixedCells.size
    val stepIndex = total - unresolved.size + 1
    val key = RealtimeFixedCorrections.keyFor(current)

    val cellCrop = remember(
        sheetBitmap,
        questionsCount,
        choicesCount,
        columnCount,
        current.questionIndex,
        current.choiceIndex,
    ) {
        RealtimeFixedCellCrop.cropAroundCell(
            bitmap = sheetBitmap,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
            columnCount = columnCount,
            questionIndex = current.questionIndex,
            choiceIndex = current.choiceIndex,
        )
    }
    DisposableEffect(cellCrop) {
        onDispose {
            val crop = cellCrop
            if (crop != null && crop !== sheetBitmap && !crop.isRecycled) {
                crop.recycle()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.18f)),
            )
            Box(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxWidth()
                    .background(Color(0xFF061018).copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                SettingsShellCard(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (total > 1) {
                                "Исправление на бланке ($stepIndex из $total)"
                            } else {
                                "Исправление на бланке"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFE08A),
                        )
                        Text(
                            text = "Вопрос ${current.questionIndex + 1}, вариант ${current.choiceIndex + 1}. " +
                                "Система увидела зачёркнутую или перечёркнутую отметку — уточните, как её засчитать.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB8D4E8),
                        )
                        if (cellCrop != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.06f),
                            ) {
                                Image(
                                    bitmap = cellCrop.asImageBitmap(),
                                    contentDescription = "Увеличенный фрагмент ячейки",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(132.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Text(
                                text = "Фрагмент ячейки (увеличено)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8FAFC4),
                            )
                        }
                        RealtimeFixedCellDecisionButtons(
                            onMarkAnswered = { onMarkAnswered(key) },
                            onMarkEmpty = { onMarkEmpty(key) },
                        )
                        if (unresolved.size > 1) {
                            Text(
                                text = "После выбора откроется следующая ячейка (осталось ${unresolved.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8FAFC4),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeFixedCellDecisionButtons(
    onMarkAnswered: () -> Unit,
    onMarkEmpty: () -> Unit,
) {
    Text(
        text = "Как засчитать эту отметку?",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = Color(0xFFE8F4FC),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val answeredPal = mainMenuBubbleGradient(1)
        val emptyPal = mainMenuBubbleGradient(5)
        val answeredIx = remember { MutableInteractionSource() }
        val emptyIx = remember { MutableInteractionSource() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            RealtimeBubbleIconButton(
                onClick = onMarkAnswered,
                contentDescription = "Считать отмеченным",
                topColor = answeredPal.first,
                bottomColor = answeredPal.second,
                size = 52.dp,
                interactionSource = answeredIx,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = "Отмечено",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB8D4E8),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            RealtimeBubbleIconButton(
                onClick = onMarkEmpty,
                contentDescription = "Считать пустым",
                topColor = emptyPal.first,
                bottomColor = emptyPal.second,
                size = 52.dp,
                interactionSource = emptyIx,
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = "Пусто",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB8D4E8),
            )
        }
    }
}
