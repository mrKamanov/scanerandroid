package com.tscan.scanertestov.ui.components

/**
 * Описание: текстовые кнопки и чипы в стиле объёмных пузырьков главного меню.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val CapsuleShape = RoundedCornerShape(999.dp)

@Composable
fun ShellBubblePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paletteIndex: Int = 0,
    fillMaxWidth: Boolean = true,
) {
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .clip(CapsuleShape)
            .background(
                brush = Brush.verticalGradient(listOf(top, bottom)),
                shape = CapsuleShape,
            )
            .border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.48f else 0.22f), CapsuleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.65f),
            fontWeight = FontWeight.SemiBold,
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
) {
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .clip(CapsuleShape)
            .background(
                brush = Brush.verticalGradient(listOf(top, bottom)),
                shape = CapsuleShape,
            )
            .border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.48f else 0.22f), CapsuleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.65f),
            fontWeight = FontWeight.SemiBold,
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
    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .clip(CapsuleShape)
            .border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.42f else 0.18f), CapsuleShape)
            .background(Color(0xFF0C2839).copy(alpha = if (enabled) 0.35f else 0.18f), CapsuleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.15f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color(0xFFE8F2FA).copy(alpha = if (enabled) 0.95f else 0.5f),
            fontWeight = FontWeight.Medium,
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
    val (top, bottom) = mainMenuBubbleGradient(paletteIndex)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = if (enabled) 0.45f else 0.2f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.55f),
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
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(CapsuleShape)
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.38f else 0.16f), CapsuleShape)
            .background(Color(0xFF0C2839).copy(alpha = if (enabled) 0.32f else 0.14f), CapsuleShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.12f)),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFFE8F2FA).copy(alpha = if (enabled) 0.95f else 0.45f),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}
