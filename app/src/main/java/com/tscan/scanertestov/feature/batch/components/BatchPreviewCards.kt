package com.tscan.scanertestov.feature.batch.components

/**
 * Описание: карточки предпросмотра работы — детальная карточка, смена варианта и рендер превью изображения.
 */
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tscan.scanertestov.feature.batch.BatchWorkItem
import com.tscan.scanertestov.feature.batch.BatchWorkSource
import com.tscan.scanertestov.feature.batch.decodeBitmapFromContentUri
import com.tscan.scanertestov.feature.batch.effectiveVariant
import com.tscan.scanertestov.feature.batch.scaleBitmapForPreview
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.scanShellBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PreviewDetailCard(
    item: BatchWorkItem,
    maxVariantInclusive: Int,
    onSetOverride: (Int?) -> Unit,
) {
    val sourceLabel = when (item.source) {
        BatchWorkSource.Camera -> "Камера"
        BatchWorkSource.Gallery -> "Галерея"
    }
    val fullName = listOfNotNull(item.detectedStudentSurname, item.detectedStudentName)
        .joinToString(" ")
        .trim()
    val journalClass = item.detectedJournalClassName?.trim().orEmpty()
    SettingsShellCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$sourceLabel · ${item.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val ev = item.effectiveVariant()
            val recognizing = item.autoRecognitionRequested && item.isVariantDetecting
            when {
                recognizing ->
                    Text(
                        text = "Определяем вариант и имя…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                ev != null ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Вариант $ev") },
                        enabled = false,
                        modifier = Modifier.align(Alignment.Start),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                else ->
                    Text(
                        text = "Вариант не определён",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            if (!recognizing) {
                when {
                    fullName.isNotBlank() ->
                        Text(
                            text = fullName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    item.autoRecognitionRequested ->
                        Text(
                            text = "ФИО не распознано",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        Text(
                            text = "Авто-распознавание выключено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            if (journalClass.isNotEmpty()) {
                Text(
                    text = "Класс: $journalClass",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BatchWorkPreviewImageLarge(
                contentUri = item.contentUri,
                workTitle = item.title,
            )
            AssignVariantPanel(
                item = item,
                maxVariantInclusive = maxVariantInclusive,
                onSetOverride = onSetOverride,
            )
        }
    }
}

@Composable
private fun AssignVariantPanel(
    item: BatchWorkItem,
    maxVariantInclusive: Int,
    onSetOverride: (Int?) -> Unit,
) {
    var choicesOpen by remember(item.id) { mutableStateOf(false) }
    val summary = when {
        item.variantOverride == null && item.detectedVariant != null ->
            "Сейчас: как на фото (вариант ${item.detectedVariant})"
        item.variantOverride == null -> "Сейчас: как на фото"
        else -> "Сейчас: вручную вариант ${item.variantOverride}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Вариант эталона для сравнения",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (choicesOpen) "Скрыть список" else "Варианты",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val tuneIx = remember(item.id) { MutableInteractionSource() }
            val tunePal = mainMenuBubbleGradient(4)
            RealtimeBubbleIconButton(
                onClick = { choicesOpen = !choicesOpen },
                contentDescription = if (choicesOpen) "Скрыть варианты" else "Изменить вариант",
                topColor = tunePal.first,
                bottomColor = tunePal.second,
                size = 48.dp,
                interactionSource = tuneIx,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = choicesOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShellBubbleOutlinedButton(
                    text = if (item.detectedVariant != null) "Фото · в${item.detectedVariant}" else "По фото",
                    onClick = {
                        onSetOverride(null)
                        choicesOpen = false
                    },
                    fillMaxWidth = true,
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                for (v in 1..maxVariantInclusive) {
                    ShellBubbleOutlinedButton(
                        text = "В$v",
                        onClick = {
                            onSetOverride(v)
                            choicesOpen = false
                        },
                        fillMaxWidth = true,
                    )
                }
            }
        }
    }
    val detected = item.detectedVariant
    val override = item.variantOverride
    if (override != null && override != detected) {
        Text(
            text = "Распознано на фото: ${detected ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BatchWorkThumb(
    contentUri: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(contentUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(contentUri) {
        bitmap = null
        val decoded = withContext(Dispatchers.IO) {
            decodeBitmapFromContentUri(context, Uri.parse(contentUri))?.let { scaleBitmapForPreview(it, 480) }
        }
        bitmap = decoded?.asImageBitmap()
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val shape = RoundedCornerShape(8.dp)
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
                .clickable(onClick = onClick),
            contentScale = ContentScale.Crop,
        )
    } else {
        Spacer(
            modifier = Modifier
                .size(72.dp)
                .border(1.dp, borderColor, shape)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun BatchWorkPreviewImageLarge(
    contentUri: String,
    workTitle: String,
) {
    val context = LocalContext.current
    var bitmap by remember(contentUri) { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember(contentUri) { mutableStateOf(false) }
    var zoomOpen by remember(contentUri) { mutableStateOf(false) }
    LaunchedEffect(contentUri) {
        bitmap = null
        loadError = false
        val decoded = withContext(Dispatchers.IO) {
            decodeBitmapFromContentUri(context, Uri.parse(contentUri))?.let { scaleBitmapForPreview(it, 1600) }
        }
        if (decoded == null) {
            loadError = true
        } else {
            bitmap = decoded.asImageBitmap()
        }
    }
    val ready = bitmap
    val interaction = remember { MutableInteractionSource() }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (ready != null) {
            Image(
                bitmap = ready,
                contentDescription = "Фото работы: $workTitle",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = { zoomOpen = true },
                    ),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "Нажмите на фото — полный экран и масштаб двумя пальцами",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (loadError) {
            Text(
                text = "Не удалось показать превью",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (zoomOpen) {
        BatchWorkFullScreenZoomDialog(
            contentUri = contentUri,
            onDismiss = { zoomOpen = false },
        )
    }
}

@Composable
private fun BatchWorkFullScreenZoomDialog(
    contentUri: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(contentUri) { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember(contentUri) { mutableStateOf(false) }
    var scale by remember(contentUri) { mutableFloatStateOf(1f) }
    var offset by remember(contentUri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(contentUri) {
        bitmap = null
        loadError = false
        scale = 1f
        offset = Offset.Zero
        val decoded = withContext(Dispatchers.IO) {
            decodeBitmapFromContentUri(context, Uri.parse(contentUri))?.let {
                scaleBitmapForPreview(it, 4096)
            }
        }
        if (decoded == null) {
            loadError = true
        } else {
            bitmap = decoded.asImageBitmap()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scanShellBackdrop(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val resetIx = remember(contentUri) { MutableInteractionSource() }
                    val resetPal = mainMenuBubbleGradient(2)
                    RealtimeBubbleIconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        contentDescription = "Сброс масштаба",
                        topColor = resetPal.first,
                        bottomColor = resetPal.second,
                        enabled = scale > 1.02f || offset != Offset.Zero,
                        size = 52.dp,
                        interactionSource = resetIx,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    val closeIx = remember(contentUri) { MutableInteractionSource() }
                    val closePal = mainMenuBubbleGradient(7)
                    RealtimeBubbleIconButton(
                        onClick = onDismiss,
                        contentDescription = "Закрыть",
                        topColor = closePal.first,
                        bottomColor = closePal.second,
                        size = 52.dp,
                        interactionSource = closeIx,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RectangleShape)
                        .pointerInput(contentUri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 12f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (loadError) {
                        Text(
                            text = "Не удалось загрузить изображение",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                SettingsShellCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Два пальца — масштаб · один палец — сдвиг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    )
                }
            }
        }
    }
}
