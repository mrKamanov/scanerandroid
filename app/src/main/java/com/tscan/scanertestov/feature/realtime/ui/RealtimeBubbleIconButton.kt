package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: круглая кнопка-иконка в стиле «Скрапбук» — наклейка с белой каймой,
 * чернильной обводкой и «жёсткой» тенью. Используется на многих экранах.
 */
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.ui.theme.Scrapbook

@Suppress("UNUSED_PARAMETER")
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
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "stickerPress")
    // На светлой «бумажке» белая иконка не читается — рисуем её чернилами.
    val iconColor = when {
        !enabled -> Scrapbook.Ink.copy(alpha = 0.45f)
        contentColor == Color.White && topColor.luminance() > 0.5f -> Scrapbook.Ink
        else -> contentColor
    }
    val shadowOffset = if (pressed) 1.dp else 3.dp
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.7f
            }
            .drawBehind {
                val o = shadowOffset.toPx()
                drawCircle(Color(0x33000000), radius = this.size.minDimension / 2, center = center + Offset(o, o))
            }
            .clip(CircleShape)
            .background(Color.White, CircleShape)
            .border(2.dp, Scrapbook.Ink.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(
                interactionSource = src,
                indication = ripple(color = Scrapbook.Ink.copy(alpha = 0.2f)),
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
        // Белая кайма вокруг цветного кружка — как у вырезанной наклейки.
        Box(
            modifier = Modifier
                .size(size - 10.dp)
                .clip(CircleShape)
                .background(topColor, CircleShape),
        )
        CompositionLocalProvider(LocalContentColor provides iconColor) {
            content()
        }
    }
}
