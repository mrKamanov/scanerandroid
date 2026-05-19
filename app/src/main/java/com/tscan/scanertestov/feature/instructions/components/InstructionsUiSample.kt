package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: отрисовка образца кнопки/элемента UI в тексте инструкции (те же компоненты, что на экранах приложения).
 */
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.instructions.InstructionUiRef
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton

@Composable
internal fun InstructionsUiSample(
    ref: InstructionUiRef,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (ref) {
            is InstructionUiRef.IconBubble -> {
                val ix = remember(ref) { MutableInteractionSource() }
                RealtimeBubbleIconButton(
                    onClick = {},
                    contentDescription = ref.caption,
                    topColor = ref.topColor,
                    bottomColor = ref.bottomColor,
                    size = ref.size,
                    interactionSource = ix,
                    enabled = false,
                ) {
                    Icon(
                        imageVector = ref.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(ref.iconSize),
                    )
                }
            }
            is InstructionUiRef.PrimaryCapsule -> {
                ShellBubblePrimaryButton(
                    text = ref.buttonText,
                    onClick = {},
                    enabled = false,
                    paletteIndex = ref.paletteIndex,
                    fillMaxWidth = false,
                )
            }
            is InstructionUiRef.OutlinedCapsule -> {
                ShellBubbleOutlinedButton(
                    text = ref.buttonText,
                    onClick = {},
                    enabled = false,
                    fillMaxWidth = false,
                )
            }
        }
        if (ref is InstructionUiRef.IconBubble) {
            Text(
                text = ref.caption,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFB8D4E8),
            )
        }
    }
}
