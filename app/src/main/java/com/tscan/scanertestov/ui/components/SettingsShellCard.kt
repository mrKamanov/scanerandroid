package com.tscan.scanertestov.ui.components

/**
 * Описание: полупрозрачная карточка-секция с обводкой для экранов на фоне оболочки.
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

private val SettingsPanelShape = RoundedCornerShape(18.dp)

@Composable
fun SettingsShellCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE8DFD0).copy(alpha = 0.11f),
            Color.White.copy(alpha = 0.06f),
        ),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SettingsPanelShape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.22f), SettingsPanelShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}
