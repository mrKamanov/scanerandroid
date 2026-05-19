package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: панель настроек изображения камеры в правом drawer.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RealtimeCameraSettingsPanel(
    brightness: Int,
    contrast: Int,
    saturation: Int,
    sharpness: Int,
    onBrightnessChange: (Int) -> Unit,
    onContrastChange: (Int) -> Unit,
    onSaturationChange: (Int) -> Unit,
    onSharpnessChange: (Int) -> Unit,
    onReset: () -> Unit,
    headerClose: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Настройки камеры",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            headerClose()
        }
        ParamSlider(
            label = "Яркость",
            icon = Icons.Default.LightMode,
            value = brightness,
            range = -100f..100f,
            display = { it.toInt().toString() },
            onChange = { onBrightnessChange(it.toInt()) },
        )
        ParamSlider(
            label = "Контраст",
            icon = Icons.Default.Contrast,
            value = contrast,
            range = 0f..200f,
            display = { it.toInt().toString() },
            onChange = { onContrastChange(it.toInt()) },
        )
        ParamSlider(
            label = "Насыщенность",
            icon = Icons.Default.Brush,
            value = saturation,
            range = 0f..200f,
            display = { it.toInt().toString() },
            onChange = { onSaturationChange(it.toInt()) },
        )
        ParamSlider(
            label = "Резкость",
            icon = Icons.Default.AutoFixHigh,
            value = sharpness,
            range = 0f..100f,
            display = { it.toInt().toString() },
            onChange = { onSharpnessChange(it.toInt()) },
        )
        Spacer(modifier = Modifier.padding(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            RealtimeBubbleIconButton(
                onClick = onReset,
                contentDescription = "Сбросить настройки камеры",
                topColor = Color(0xFFFFD47F),
                bottomColor = Color(0xFFF5A93E),
                size = 48.dp,
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    icon: ImageVector,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val topC = Color(0xFF3A4A62)
    val botC = Color(0xFF232F42)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = Color(0xFF0B1A33).copy(alpha = 0.22f),
                spotColor = Color(0xFF0B1A33).copy(alpha = 0.28f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topC, botC)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.42f)
                .padding(start = 8.dp, top = 6.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset.Zero,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ),
        )
        Column(modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF9FD8FF),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE4EDF8))
                }
                Text(
                    display(value.toFloat()),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFB8ECFF),
                )
            }
            Slider(
                value = value.toFloat().coerceIn(range.start, range.endInclusive),
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6AD7FF),
                    activeTrackColor = Color(0xFF2EA3F3),
                    inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                ),
            )
        }
    }
}
