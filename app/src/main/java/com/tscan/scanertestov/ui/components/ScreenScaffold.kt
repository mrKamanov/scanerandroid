package com.tscan.scanertestov.ui.components

/**
 * Описание: каркас экрана с кнопкой «Назад» и областью контента на фоне оболочки.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.tscan.scanertestov.ui.theme.Scrapbook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.scanShellBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    onBack: (() -> Unit)? = null,
    showBubbleSheetDecor: Boolean = false,
    title: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {},
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scanShellBackdrop(),
            )
            // Декоративные «пузыри» поверх контента убраны: они перекрывали текст и кнопки.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
            ) {
                if (onBack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppBackIconButton(onClick = onBack)
                        if (title != null) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(start = 4.dp),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Scrapbook.Ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    content(PaddingValues(0.dp))
                }
            }
        }
    }
}
