package com.tscan.scanertestov.feature.mainmenu.components

/**
 * Описание: карточки и заголовок главного меню в стиле «пузырьков».
 */
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.feature.mainmenu.MainMenuItemState
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val MenuTitleColor = Color(0xFFE8F2FA)
private val MenuMutedColor = Color(0xFFB0C4D6)

internal fun mainMenuBubbleGradient(index: Int): Pair<Color, Color> {
    val k = ((index % 7) + 7) % 7
    return when (k) {
        0 -> Color(0xFF8ADDFE) to Color(0xFF2EA3F3)
        1 -> Color(0xFF7BE2BB) to Color(0xFF2BB98A)
        2 -> Color(0xFFAE9EFF) to Color(0xFF6D58EA)
        3 -> Color(0xFFFFD47F) to Color(0xFFF5A93E)
        4 -> Color(0xFFA8B3C7) to Color(0xFF73829A)
        5 -> Color(0xFFFFB4C0) to Color(0xFFD94D6A)
        6 -> Color(0xFFB8CEFF) to Color(0xFF3D56C6)
        else -> Color(0xFFA8B3C7) to Color(0xFF73829A)
    }
}

@Composable
private fun MainMenuTitleVariant16Orb(modifier: Modifier = Modifier) {
    val paperCenter = Color(0xFFE8DFD0).copy(alpha = 0.42f)
    val paperEdge = Color(0xFFE8DFD0).copy(alpha = 0.09f)
    val stripeInk = Color(0xFFF2F6FA).copy(alpha = 0.5f)
    Canvas(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(width = 2.dp, color = Color.White.copy(alpha = 0.22f), shape = CircleShape),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(paperCenter, paperEdge),
                center = Offset(cx - r * 0.12f, cy - r * 0.18f),
                radius = r * 1.2f,
            ),
            radius = r * 0.98f,
            center = Offset(cx, cy),
        )
        val inset = 3.dp.toPx()
        drawCircle(
            color = Color.Black.copy(alpha = 0.15f),
            radius = (r - inset).coerceAtLeast(1f),
            center = Offset(cx, cy),
            style = Stroke(width = inset),
        )
        val innerR = r * 0.61f
        val oval = Path().apply { addOval(Rect(cx - innerR, cy - innerR, cx + innerR, cy + innerR)) }
        clipPath(oval) {
            val stripeAlong = (45f * PI / 180f).toFloat()
            val ux = cos(stripeAlong)
            val uy = sin(stripeAlong)
            val nx = -uy
            val ny = ux
            val spacing = 3.5.dp.toPx()
            val strokeW = maxOf(1.1.dp.toPx(), 1.4f)
            val maxK = ceil(innerR * 2.6f / spacing).toInt().coerceAtLeast(4)
            for (k in -maxK..maxK) {
                val d = k * spacing
                if (abs(d) > innerR * 1.2f) continue
                val disc = innerR * innerR - d * d
                if (disc < 0f) continue
                val halfChord = sqrt(disc)
                val px = cx + nx * d
                val py = cy + ny * d
                val ax = px - ux * halfChord * 1.15f
                val ay = py - uy * halfChord * 1.15f
                val bx = px + ux * halfChord * 1.15f
                val by = py + uy * halfChord * 1.15f
                drawLine(
                    color = stripeInk,
                    start = Offset(ax, ay),
                    end = Offset(bx, by),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun MainMenuHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Сканер",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MenuTitleColor,
                letterSpacing = 0.45.sp,
            )
            MainMenuTitleVariant16Orb(Modifier.padding(horizontal = 10.dp))
            Text(
                text = "тестов",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MenuTitleColor,
                letterSpacing = 0.45.sp,
            )
        }
    }
}

@Composable
internal fun StaggeredAppear(
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(staggerIndex) {
        delay(staggerIndex * 70L)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "menuStagger",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 28f
            scaleX = 0.94f + progress * 0.06f
            scaleY = 0.94f + progress * 0.06f
        },
    ) {
        content()
    }
}

@Composable
internal fun MainMenuHeroBubble(
    item: MainMenuItemState,
    paletteIndex: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroPress",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            RealtimeBubbleIconButton(
                onClick = onClick,
                contentDescription = "${item.title}. ${item.description}",
                topColor = top,
                bottomColor = bottom,
                size = 88.dp,
                interactionSource = interactionSource,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Text(
            text = item.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MenuTitleColor,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, end = 8.dp),
        )
        Text(
            text = item.description,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MenuMutedColor.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp),
        )
    }
}

@Composable
internal fun MainMenuBubbleCell(
    item: MainMenuItemState,
    paletteIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cellPress",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        ) {
            RealtimeBubbleIconButton(
                onClick = onClick,
                contentDescription = "${item.title}. ${item.description}",
                topColor = top,
                bottomColor = bottom,
                size = 64.dp,
                interactionSource = interactionSource,
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MenuTitleColor,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Clip,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = item.description,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MenuMutedColor.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}
