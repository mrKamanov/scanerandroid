package com.tscan.scanertestov.ui.components

/**
 * Описание: карточка-секция с холодным градиентом для пакетной обработки и похожих экранов.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ShellSectionHi = Color(0xFF10364E).copy(alpha = 0.48f)
private val ShellSectionLo = Color(0xFF0B2738).copy(alpha = 0.42f)
private val ShellSectionShape = RoundedCornerShape(18.dp)

@Composable
fun ShellBubbleSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShellSectionShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), ShellSectionShape)
            .background(
                brush = Brush.verticalGradient(listOf(ShellSectionHi, ShellSectionLo)),
                shape = ShellSectionShape,
            )
            .padding(16.dp),
        content = content,
    )
}
