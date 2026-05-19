package com.tscan.scanertestov.ui.components

/**
 * Описание: переключатель в стиле объёмного пузырька для экранов настроек.
 */
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient

private val BubbleTrackBorder = Color.White.copy(alpha = 0.42f)
private val BubbleThumbBorder = Color.White.copy(alpha = 0.52f)

private val TrackOffTop = Color(0xFF5C6F86)
private val TrackOffBottom = Color(0xFF3D5268)

@Composable
fun ShellBubbleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val (onTop, onBottom) = mainMenuBubbleGradient(0)
    val thumbTravel = 22.dp
    val thumbShift by animateDpAsState(
        targetValue = if (checked) thumbTravel else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "shellBubbleSwitch",
    )
    val trackBrush = if (checked) {
        Brush.verticalGradient(listOf(onTop, onBottom))
    } else {
        Brush.verticalGradient(listOf(TrackOffTop, TrackOffBottom))
    }
    val spot = Color(0xFF0B1A33).copy(alpha = if (enabled) 0.35f else 0.2f)
    val ambient = Color(0xFF1E3A5C).copy(alpha = if (enabled) 0.22f else 0.14f)
    val pill = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .width(56.dp)
            .height(34.dp)
            .shadow(10.dp, pill, clip = false, ambientColor = ambient, spotColor = spot)
            .clip(pill)
            .background(trackBrush)
            .border(1.5.dp, BubbleTrackBorder, pill)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.52f },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 4.dp)
                .size(18.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.38f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .offset(x = thumbShift)
                .size(26.dp)
                .shadow(6.dp, CircleShape, clip = false, ambientColor = ambient, spotColor = spot)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF8FAFD), Color(0xFFD8E4F0)),
                    ),
                )
                .border(1.dp, BubbleThumbBorder, CircleShape),
        )
    }
}
