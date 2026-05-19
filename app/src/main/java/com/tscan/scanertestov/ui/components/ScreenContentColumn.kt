package com.tscan.scanertestov.ui.components

/**
 * Описание: стандартная вертикальная компоновка контента экрана с едиными отступами.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenContentColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalScroll: Boolean = false,
    scrollState: ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = scrollState ?: rememberScrollState()
    val padded = modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
    Column(
        modifier = if (verticalScroll) padded.verticalScroll(scroll) else padded,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
