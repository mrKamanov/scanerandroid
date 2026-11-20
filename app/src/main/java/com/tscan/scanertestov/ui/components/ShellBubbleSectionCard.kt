package com.tscan.scanertestov.ui.components

/**
 * Описание: секция пакетной обработки — тот же лист бумаги, что и [SettingsShellCard].
 */
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ShellBubbleSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsShellCard(modifier = modifier, content = content)
}
