package com.tscan.scanertestov.feature.instructions

/**
 * Описание: ссылки на элементы UI инструкций с параметрами, совпадающими с экранами приложения.
 */
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed class InstructionUiRef {
    abstract val caption: String

    data class IconBubble(
        override val caption: String,
        val topColor: Color,
        val bottomColor: Color,
        val size: Dp,
        val icon: ImageVector,
        val iconSize: Dp,
    ) : InstructionUiRef()

    data class PrimaryCapsule(
        override val caption: String,
        val buttonText: String,
        val paletteIndex: Int,
    ) : InstructionUiRef()

    data class OutlinedCapsule(
        override val caption: String,
        val buttonText: String,
    ) : InstructionUiRef()
}

object InstructionUiRefs {
    val RealtimeSyncEtalon = InstructionUiRef.IconBubble(
        caption = "Обновить эталон",
        topColor = Color(0xFF8ADDFE),
        bottomColor = Color(0xFF2EA3F3),
        size = 56.dp,
        icon = Icons.Filled.Sync,
        iconSize = 28.dp,
    )
    val RealtimeGridOn = InstructionUiRef.IconBubble(
        caption = "Сетка эталона",
        topColor = Color(0xFFAE9EFF),
        bottomColor = Color(0xFF6D58EA),
        size = 56.dp,
        icon = Icons.Filled.GridOn,
        iconSize = 28.dp,
    )
    val RealtimeCameraSettings = InstructionUiRef.IconBubble(
        caption = "Настройки камеры",
        topColor = Color(0xFFA8B3C7),
        bottomColor = Color(0xFF73829A),
        size = 56.dp,
        icon = Icons.Filled.Tune,
        iconSize = 28.dp,
    )
    val RealtimeFreezeFrame = InstructionUiRef.IconBubble(
        caption = "Зафиксировать кадр",
        topColor = Color(0xFFFF9AA6),
        bottomColor = Color(0xFFF16275),
        size = 88.dp,
        icon = Icons.Filled.Stop,
        iconSize = 50.dp,
    )
    val RealtimeRunCheck = InstructionUiRef.IconBubble(
        caption = "Запустить проверку",
        topColor = Color(0xFF8ADDFE),
        bottomColor = Color(0xFF2EA3F3),
        size = 88.dp,
        icon = Icons.Filled.Check,
        iconSize = 50.dp,
    )
    val RealtimeFlashOff = InstructionUiRef.IconBubble(
        caption = "Вспышка",
        topColor = Color(0xFFA8B3C7),
        bottomColor = Color(0xFF73829A),
        size = 48.dp,
        icon = Icons.Filled.FlashlightOff,
        iconSize = 26.dp,
    )
    val BlankScaleMinus = InstructionUiRef.IconBubble(
        caption = "Уменьшить",
        topColor = Color(0xFFA8B3C7),
        bottomColor = Color(0xFF73829A),
        size = 44.dp,
        icon = Icons.Filled.Remove,
        iconSize = 22.dp,
    )
    val BlankScalePlus = InstructionUiRef.IconBubble(
        caption = "Увеличить",
        topColor = Color(0xFFAE9EFF),
        bottomColor = Color(0xFF6D58EA),
        size = 44.dp,
        icon = Icons.Filled.Add,
        iconSize = 22.dp,
    )
    val BlankExport = InstructionUiRef.PrimaryCapsule(
        caption = "Экспорт",
        buttonText = "Экспорт",
        paletteIndex = 3,
    )
    val BatchCamera = InstructionUiRef.IconBubble(
        caption = "Сделать фото",
        topColor = Color(0xFF8ADDFE),
        bottomColor = Color(0xFF2EA3F3),
        size = 64.dp,
        icon = Icons.Filled.PhotoCamera,
        iconSize = 30.dp,
    )
    val BatchGallery = InstructionUiRef.IconBubble(
        caption = "Галерея",
        topColor = Color(0xFF7BE2BB),
        bottomColor = Color(0xFF2BB98A),
        size = 64.dp,
        icon = Icons.Filled.PhotoLibrary,
        iconSize = 30.dp,
    )
    val BatchRunCheck = InstructionUiRef.IconBubble(
        caption = "Проверить работы",
        topColor = Color(0xFFAE9EFF),
        bottomColor = Color(0xFF6D58EA),
        size = 72.dp,
        icon = Icons.Filled.FactCheck,
        iconSize = 34.dp,
    )
    val ReportsExport = InstructionUiRef.OutlinedCapsule(
        caption = "Экспорт",
        buttonText = "Экспорт",
    )
    val FixedAnswered = InstructionUiRef.IconBubble(
        caption = "Отмечено",
        topColor = Color(0xFF7BE2BB),
        bottomColor = Color(0xFF2BB98A),
        size = 52.dp,
        icon = Icons.Filled.Check,
        iconSize = 26.dp,
    )
    val FixedEmpty = InstructionUiRef.IconBubble(
        caption = "Пусто",
        topColor = Color(0xFFFFB4C0),
        bottomColor = Color(0xFFD94D6A),
        size = 52.dp,
        icon = Icons.Filled.Block,
        iconSize = 26.dp,
    )
}
