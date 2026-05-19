package com.tscan.scanertestov.ui.launch

/**
 * Описание: заставка приложения с заголовком «Сканер тестов» и угловой рамкой.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.ui.scanShellBackdrop

private val ScanCornerColor = Color(0xFF8ADDFE).copy(alpha = 0.7f)

@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .scanShellBackdrop(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 280.dp, minHeight = 88.dp)
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .drawBehind {
                    val arm = 14.dp.toPx()
                    val sw = 2.dp.toPx()
                    val c = ScanCornerColor
                    val cap = StrokeCap.Round
                    val w = size.width
                    val h = size.height
                    drawLine(c, Offset(0f, arm), Offset(0f, 0f), sw, cap)
                    drawLine(c, Offset(0f, 0f), Offset(arm, 0f), sw, cap)
                    drawLine(c, Offset(w, h - arm), Offset(w, h), sw, cap)
                    drawLine(c, Offset(w, h), Offset(w - arm, h), sw, cap)
                }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Сканер тестов",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE8F2FA),
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
