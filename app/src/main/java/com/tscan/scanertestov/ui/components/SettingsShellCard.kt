package com.tscan.scanertestov.ui.components

/**
 * Описание: карточка-секция в стиле «Скрапбук» — лист бумаги на картоне, приклеенный скотчем.
 * Цвет скотча выбирается по месту вызова, поэтому соседние карточки отличаются.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.theme.Scrapbook

private val PaperShape = RoundedCornerShape(3.dp)

@Composable
fun SettingsShellCard(
    modifier: Modifier = Modifier,
    paper: Color = Scrapbook.Paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tape = scrapTapeColor(currentCompositeKeyHash)
    TapedBox(tapeColor = tape, modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .stickerShadow(offset = 3.dp, cornerRadius = 3.dp)
                .background(paper, PaperShape)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content,
        )
    }
}
