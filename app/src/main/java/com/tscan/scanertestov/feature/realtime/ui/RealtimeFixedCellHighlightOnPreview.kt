package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: подсветка текущей ячейки «исправление» поверх превью в быстрой проверке.
 */
import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.batch.engine.BatchSheetGridLayout
import kotlin.math.roundToInt

@Composable
internal fun RealtimeFixedCellHighlightOnPreview(
    sheetBitmap: Bitmap,
    questionsCount: Int,
    choicesCount: Int,
    columnCount: Int,
    questionIndex: Int,
    choiceIndex: Int,
    modifier: Modifier = Modifier,
) {
    val cellRect = remember(
        sheetBitmap,
        questionsCount,
        choicesCount,
        columnCount,
        questionIndex,
        choiceIndex,
    ) {
        BatchSheetGridLayout.findCell(
            bitmap = sheetBitmap,
            questionsCount = questionsCount,
            choicesCount = choicesCount,
            columnCount = columnCount,
            questionIndex = questionIndex,
            choiceIndex = choiceIndex,
        )
    } ?: return

    val pulse = rememberInfiniteTransition(label = "fixedCellPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fixedCellPulseAlpha",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cwPx = with(density) { maxWidth.toPx() }
        val chPx = with(density) { maxHeight.toPx() }
        val fit = RealtimeSheetImageFit.compute(
            viewportWidthPx = cwPx,
            viewportHeightPx = chPx,
            bitmapWidthPx = sheetBitmap.width.toFloat(),
            bitmapHeightPx = sheetBitmap.height.toFloat(),
        )
        val leftPx = fit.offsetX + cellRect.left * fit.scale
        val topPx = fit.offsetY + cellRect.top * fit.scale
        val wPx = (cellRect.right - cellRect.left) * fit.scale
        val hPx = (cellRect.bottom - cellRect.top) * fit.scale

        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .width(with(density) { wPx.toDp() })
                .height(with(density) { hPx.toDp() })
                .background(
                    Color(0xFFFFD54F).copy(alpha = pulseAlpha),
                    RoundedCornerShape(4.dp),
                )
                .border(
                    width = 3.dp,
                    color = Color(0xFFFFB300),
                    shape = RoundedCornerShape(4.dp),
                ),
        )
    }
}
