package com.tscan.scanertestov.ui

/**
 * Описание: общий фон экранов приложения — заливка и светлая сетка.
 */
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val ScanShellBackdropFill = Color(0xFF071F31)
private const val ScanShellBackdropGridAlpha = 0.10f

fun Modifier.scanShellBackdrop(): Modifier = composed {
    val gridStepPx = with(LocalDensity.current) { 22.dp.toPx() }
    drawBehind {
        val w = size.width
        val h = size.height
        drawRect(color = ScanShellBackdropFill)
        val line = Color.White.copy(alpha = ScanShellBackdropGridAlpha)
        var x = 0f
        while (x <= w + 0.5f) {
            drawLine(line, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += gridStepPx
        }
        var y = 0f
        while (y <= h + 0.5f) {
            drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += gridStepPx
        }
    }
}
