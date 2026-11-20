package com.tscan.scanertestov.ui.components

/**
 * Описание: текстовые кнопки и чипы в стиле «Скрапбук» — бумажные ярлыки с чернильной обводкой
 * и «жёсткой» тенью. Имена и параметры сохранены, чтобы экраны не менялись.
 */
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.ui.theme.Scrapbook

private val LabelShape = RoundedCornerShape(10.dp)
private val ChipShape = RoundedCornerShape(8.dp)

/** Общая «бумажная» поверхность кнопки: заливка, обводка, тень и отклик на нажатие. */
private fun Modifier.paperButton(
    fill: Color,
    shape: Shape,
    enabled: Boolean,
    pressed: Boolean,
    interaction: MutableInteractionSource,
    label: String,
    onClick: () -> Unit,
    shadowCorner: Dp,
    shadow: Boolean = true,
): Modifier = this
    .stickerShadow(offset = if (pressed) 1.dp else 3.dp, cornerRadius = shadowCorner, enabled = shadow && enabled)
    .clip(shape)
    .background(fill, shape)
    .border(2.dp, Scrapbook.Ink.copy(alpha = if (enabled) 1f else 0.35f), shape)
    .clickable(
        interactionSource = interaction,
        indication = ripple(color = Scrapbook.Ink.copy(alpha = 0.18f)),
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )
    .semantics {
        role = Role.Button
        contentDescription = label
    }
    .graphicsLayer { alpha = if (enabled) 1f else 0.6f }

@Composable
private fun pressScale(interaction: MutableInteractionSource): Pair<Boolean, Float> {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "paperPress")
    return pressed to scale
}

@Composable
fun ShellBubblePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paletteIndex: Int = 0,
    fillMaxWidth: Boolean = true,
) {
    val (fill, _) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    val (pressed, scale) = pressScale(interaction)
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 50.dp)
            .paperButton(fill, LabelShape, enabled, pressed, interaction, text, onClick, 10.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitWordsText(
            text = text,
            color = Scrapbook.Ink,
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ShellBubblePrimaryButtonRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paletteIndex: Int = 0,
    fillMaxWidth: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    /** Меньше внутренних отступов — для двух кнопок рядом, чтобы подпись умещалась в строку. */
    compact: Boolean = false,
) {
    val (fill, _) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    val (pressed, scale) = pressScale(interaction)
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 50.dp)
            .paperButton(fill, LabelShape, enabled, pressed, interaction, text, onClick, 10.dp)
            .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            CompositionLocalProvider(LocalContentColor provides Scrapbook.Ink) { leading() }
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        }
        FitWordsText(
            text = text,
            color = Scrapbook.Ink,
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ShellBubbleOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val (pressed, scale) = pressScale(interaction)
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = 48.dp)
            .paperButton(Scrapbook.Paper, LabelShape, enabled, pressed, interaction, text, onClick, 10.dp, shadow = false)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitWordsText(
            text = text,
            color = Scrapbook.Ink,
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ShellBubbleMiniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paletteIndex: Int = 4,
    size: Dp = 44.dp,
) {
    val (fill, _) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    val (pressed, scale) = pressScale(interaction)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .paperButton(fill, CircleShape, enabled, pressed, interaction, text, onClick, size / 2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Scrapbook.Ink,
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}

@Composable
fun ShellBubbleTextChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val (pressed, _) = pressScale(interaction)
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .paperButton(Scrapbook.Lemon, ChipShape, enabled, pressed, interaction, text, onClick, 8.dp, shadow = false)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitWordsText(
            text = text,
            color = Scrapbook.Ink,
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
