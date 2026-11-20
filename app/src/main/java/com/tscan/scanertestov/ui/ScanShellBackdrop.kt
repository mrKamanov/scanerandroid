package com.tscan.scanertestov.ui

/**
 * Описание: общий фон экранов — картон в мелкую точку (стиль «Скрапбук»).
 */
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.theme.Scrapbook

fun Modifier.scanShellBackdrop(): Modifier = composed {
    val density = LocalDensity.current
    val step = with(density) { 6.dp.toPx() }
    val r = with(density) { 0.6.dp.toPx() }
    drawBehind {
        drawRect(color = Scrapbook.Board)
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(Scrapbook.BoardDot, radius = r, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}
