package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: объёмная круглая кнопка-иконка для панели быстрой проверки.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun RealtimeBubbleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    topColor: Color,
    bottomColor: Color,
    borderColor: Color = Color.White.copy(alpha = 0.52f),
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    interactionSource: MutableInteractionSource? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val src = interactionSource ?: remember { MutableInteractionSource() }
    val shadowElev = if (enabled) 14.dp else 5.dp
    val spot = Color(0xFF0B1A33).copy(alpha = if (enabled) 0.38f else 0.22f)
    val ambient = Color(0xFF1E3A5C).copy(alpha = if (enabled) 0.28f else 0.16f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(shadowElev, CircleShape, clip = false, ambientColor = ambient, spotColor = spot)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(listOf(topColor, bottomColor)),
                CircleShape,
            )
            .border(1.5.dp, borderColor, CircleShape)
            .graphicsLayer { alpha = if (enabled) 1f else 0.72f }
            .clickable(
                interactionSource = src,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.34f)
                .background(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.White.copy(alpha = 0.08f),
                        1f to Color.White.copy(alpha = 0.18f),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(size * 0.58f)
                .padding(start = 6.dp, top = 5.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.58f),
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset.Zero,
                        radius = size.value * 0.95f,
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 9.dp)
                .size(size * 0.22f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                        center = Offset.Zero,
                    ),
                    shape = CircleShape,
                ),
        )
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            content()
        }
    }
}
