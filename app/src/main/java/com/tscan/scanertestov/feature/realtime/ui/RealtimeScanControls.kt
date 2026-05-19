package com.tscan.scanertestov.feature.realtime.ui

/**
 * Описание: нижняя панель и кнопка стоп-кадра на экране быстрой проверки.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import java.util.Locale

@Composable
internal fun RealtimeScanResultOverlay(
    result: BatchOmrResult,
    gradingCriteria: GradingCriteriaConfig,
    showGradeStamp: Boolean = true,
    pendingCorrectionsCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val total = result.questionScores.size
    if (total == 0) return

    val correct = result.correctCount
    val incorrect = result.incorrectCount
    val percent = result.scorePercent
    val grade = gradingCriteria.resolveGrade(percent, result.scoreRaw.toDouble())
    val percentLabel = "${"%.1f".format(Locale.US, percent)}%"

    Box(
        modifier = modifier.padding(10.dp),
    ) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0A1E2E).copy(alpha = 0.72f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RealtimeResultStatLine(label = "Всего вопросов", value = total.toString())
                RealtimeResultStatLine(
                    label = "Правильно",
                    value = correct.toString(),
                    valueColor = Color(0xFF7BE2A8),
                )
                RealtimeResultStatLine(
                    label = "Неправильно",
                    value = incorrect.toString(),
                    valueColor = Color(0xFFFF9A9A),
                )
                RealtimeResultStatLine(
                    label = "Процент выполнения",
                    value = percentLabel,
                    valueColor = realtimePercentColor(percent),
                )
                if (pendingCorrectionsCount > 0) {
                    RealtimeResultStatLine(
                        label = "Исправления",
                        value = "уточните $pendingCorrectionsCount",
                        valueColor = Color(0xFFFFE08A),
                    )
                }
            }
        }

        if (showGradeStamp) {
            RealtimeGradeStamp(
                grade = grade,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun RealtimeResultStatLine(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
private fun RealtimeGradeStamp(
    grade: Int,
    modifier: Modifier = Modifier,
) {
    val stampMain = realtimeGradeStampColor(grade)
    val stampAccent = realtimeGradeStampAccent(grade)

    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = -12f }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 4.dp,
                    brush = Brush.linearGradient(
                        listOf(stampMain, stampAccent, stampMain.copy(alpha = 0.75f)),
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            stampMain.copy(alpha = 0.28f),
                            Color(0xFF1A0F24).copy(alpha = 0.55f),
                        ),
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 22.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ОЦЕНКА",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = stampMain.copy(alpha = 0.95f),
                )
                RealtimeOutlinedGradeDigit(
                    text = grade.toString(),
                    fillColor = stampMain,
                    outlineColor = Color(0xFF061018).copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun RealtimeOutlinedGradeDigit(
    text: String,
    fillColor: Color,
    outlineColor: Color,
) {
    val digitStyle = TextStyle(
        fontSize = 80.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 80.sp,
    )
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = digitStyle.copy(
                color = outlineColor,
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
            ),
        )
        Text(
            text = text,
            style = digitStyle.copy(color = fillColor),
        )
    }
}

private fun realtimeGradeStampColor(grade: Int): Color = when (grade) {
    5 -> Color(0xFF5FD492)
    4 -> Color(0xFF6BA8FF)
    3 -> Color(0xFFE8C06A)
    2 -> Color(0xFFFF7B7B)
    else -> Color(0xFFFF8A8A)
}

private fun realtimeGradeStampAccent(grade: Int): Color = when (grade) {
    5 -> Color(0xFF2E9B6A)
    4 -> Color(0xFF3D6FCC)
    3 -> Color(0xFFB8923A)
    2 -> Color(0xFFC94A4A)
    else -> Color(0xFFB85A5A)
}

private fun realtimePercentColor(percent: Float): Color = when {
    percent < 50f -> Color(0xFFFF9A9A)
    percent < 70f -> Color(0xFFE8C06A)
    percent < 90f -> Color(0xFF8BB8FF)
    else -> Color(0xFF7BE2A8)
}

@Composable
internal fun RealtimeProcessingOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF061018).copy(alpha = 0.74f)),
        contentAlignment = Alignment.Center,
    ) {
        SettingsShellCard(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Проверяем бланк",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE8F4FC),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF5EC4FF),
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeWidth = 4.dp,
                )
                Text(
                    text = "Считаем отметки и сверяем их с эталоном. Подождите несколько секунд.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8D4E8).copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun RealtimeScanActionButtons(
    contourReady: Boolean,
    isPaused: Boolean,
    isProcessing: Boolean,
    canRunCheck: Boolean,
    onFreezeClick: () -> Unit,
    onRunCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RealtimeFreezeFrameButton(
            contourReady = contourReady,
            isPaused = isPaused,
            isProcessing = isProcessing,
            onClick = onFreezeClick,
        )
        RealtimeRunCheckButton(
            enabled = canRunCheck,
            isProcessing = isProcessing,
            onClick = onRunCheckClick,
        )
    }
}

@Composable
private fun RealtimeFreezeFrameButton(
    contourReady: Boolean,
    isPaused: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = when {
        isProcessing -> false
        isPaused -> true
        contourReady -> true
        else -> false
    }
    val top = when {
        !enabled -> Color(0xFF9AA3B4)
        !isPaused -> Color(0xFFFF9AA6)
        else -> Color(0xFF7BE2BB)
    }
    val bottom = when {
        !enabled -> Color(0xFF6B7382)
        !isPaused -> Color(0xFFF16275)
        else -> Color(0xFF2BB98A)
    }
    RealtimeBubbleIconButton(
        onClick = onClick,
        contentDescription = if (isPaused) "Снять паузу и переснять" else "Зафиксировать кадр",
        topColor = top,
        bottomColor = bottom,
        contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        enabled = enabled,
        size = 88.dp,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isPaused && !isProcessing) Icons.Default.PlayArrow else Icons.Default.Stop,
            contentDescription = null,
            modifier = Modifier.size(50.dp),
        )
    }
}

@Composable
private fun RealtimeRunCheckButton(
    enabled: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = enabled && !isProcessing
    RealtimeBubbleIconButton(
        onClick = onClick,
        contentDescription = "Запустить проверку",
        topColor = if (active) Color(0xFF8ADDFE) else Color(0xFF9AA3B4),
        bottomColor = if (active) Color(0xFF2EA3F3) else Color(0xFF6B7382),
        contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        enabled = active,
        size = 88.dp,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(50.dp),
        )
    }
}

@Composable
internal fun RealtimeScanBottomBar(
    gridVisible: Boolean,
    onUpdateAnswers: () -> Unit,
    onOpenGridEtalon: () -> Unit,
    onCameraSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundToolButton(
            onClick = onUpdateAnswers,
            contentDescription = "Обновить эталон",
            topColor = Color(0xFF8ADDFE),
            bottomColor = Color(0xFF2EA3F3),
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(28.dp))
        }
        RoundToolButton(
            onClick = onOpenGridEtalon,
            contentDescription = "Сетка эталона на кадре",
            topColor = if (gridVisible) Color(0xFFAE9EFF) else Color(0xFFA8B3C7),
            bottomColor = if (gridVisible) Color(0xFF6D58EA) else Color(0xFF73829A),
        ) {
            Icon(
                Icons.Default.GridOn,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        RoundToolButton(
            onClick = onCameraSettings,
            contentDescription = "Настройки камеры",
            topColor = Color(0xFFA8B3C7),
            bottomColor = Color(0xFF73829A),
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RoundToolButton(
    onClick: () -> Unit,
    contentDescription: String,
    topColor: Color,
    bottomColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    RealtimeBubbleIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        topColor = topColor,
        bottomColor = bottomColor,
        contentColor = Color.White,
        enabled = true,
        size = 56.dp,
        modifier = modifier,
    ) {
        content()
    }
}
