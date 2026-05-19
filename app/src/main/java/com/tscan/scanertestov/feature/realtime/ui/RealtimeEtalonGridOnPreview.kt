package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: интерактивная сетка эталона поверх превью бланка.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun RealtimeEtalonGridOnPreview(
    bitmapWidthPx: Int,
    bitmapHeightPx: Int,
    questionsCount: Int,
    choicesCount: Int,
    answerKey: List<Set<Int>>,
    onToggleCell: (questionIndex: Int, choiceIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questionsCount < 1 || choicesCount < 1) return
    val padded = padAnswerKey(answerKey, questionsCount)
    val bw = bitmapWidthPx.toFloat().coerceAtLeast(1f)
    val bh = bitmapHeightPx.toFloat().coerceAtLeast(1f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cwPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val chPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val scale = minOf(cwPx / bw, chPx / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        val offX = (cwPx - drawW) / 2f
        val offY = (chPx - drawH) / 2f

        Box(
            modifier = Modifier
                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .width(with(density) { drawW.toDp() })
                .height(with(density) { drawH.toDp() }),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (q in 0 until questionsCount) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        for (c in 0 until choicesCount) {
                            val selected = padded[q].contains(c)
                            val label = "${q + 1}.${c + 1}"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFFF6D00).copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(2.dp),
                                    )
                                    .background(
                                        if (selected) {
                                            Color(0xFF2E7D32).copy(alpha = 0.42f)
                                        } else {
                                            Color.White.copy(alpha = 0.06f)
                                        },
                                    )
                                    .clickable { onToggleCell(q, c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
