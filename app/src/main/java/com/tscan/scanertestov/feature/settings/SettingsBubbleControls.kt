package com.tscan.scanertestov.feature.settings

/**
 * Описание: кнопки в стиле «пузырьков» на экране настроек.
 */
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButtonRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.tscan.scanertestov.ui.theme.Scrapbook
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton

@Composable
fun SettingsBubbleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    paletteIndex: Int = 4,
    enabled: Boolean = true,
) {
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interactionSource = remember { MutableInteractionSource() }
    // Подписанная кнопка вместо одной иконки: «↺» без текста было непонятно.
    ShellBubblePrimaryButtonRow(
        text = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        paletteIndex = paletteIndex,
        fillMaxWidth = false,
        leading = { Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(22.dp)) },
    )
}
