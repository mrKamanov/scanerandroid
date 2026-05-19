package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: боковая выдвижная панель с затемнением фона.
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable
internal fun RealtimeEdgeDrawer(
    visible: Boolean,
    alignEnd: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally { full -> if (alignEnd) full else -full },
        exit = slideOutHorizontally { full -> if (alignEnd) full else -full },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(320.dp),
                shape = if (alignEnd) {
                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                } else {
                    RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                },
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                content()
            }
        }
    }
}
