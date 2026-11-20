package com.tscan.scanertestov.ui.components

/**
 * Описание: базовые элементы стиля «Скрапбук»: лист бумаги со скотчем и «жёсткая» тень.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.theme.Scrapbook

/** Скотч разных цветов — по индексу, чтобы соседние листы отличались. */
fun scrapTapeColor(index: Int): Color = when (((index % 5) + 5) % 5) {
    0 -> Scrapbook.TapeYellow
    1 -> Scrapbook.TapeBlue
    2 -> Scrapbook.TapePink
    3 -> Scrapbook.TapeMint
    else -> Scrapbook.TapeWhite
}

/**
 * «Жёсткая» тень со смещением, как у наклейки: рисуется под элементом без размытия.
 * Дешевле обычной shadow и не зависит от формы клипа.
 */
fun Modifier.stickerShadow(
    offset: Dp = 3.dp,
    cornerRadius: Dp = 2.dp,
    color: Color = Color(0x33000000),
    enabled: Boolean = true,
): Modifier = if (!enabled) this else this.drawBehind {
    val o = offset.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(o, o),
        size = size,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
    )
}

/** Полоска скотча; размещается поверх верхнего края листа. */
@Composable
fun ScrapTape(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 18.dp,
    rotation: Float = -4f,
) {
    Box(
        modifier = modifier
            .size(width, height)
            .rotate(rotation)
            .background(color),
    )
}

/** Обёртка, которая добавляет скотч к верхнему краю содержимого. */
@Composable
fun TapedBox(
    tapeColor: Color,
    modifier: Modifier = Modifier,
    tapeAlignment: Alignment = Alignment.TopStart,
    tapeOffsetX: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        ScrapTape(
            color = tapeColor,
            modifier = Modifier
                .align(tapeAlignment)
                .offset(x = if (tapeAlignment == Alignment.TopEnd) -tapeOffsetX else tapeOffsetX, y = (-9).dp),
        )
    }
}
