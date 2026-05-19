package com.tscan.scanertestov.feature.batch

/**
 * Описание: централизованные пользовательские тексты пакетной обработки (подписи работ и статусные сообщения).
 */
internal fun buildWorkSubtitle(
    source: BatchWorkSource,
    displayName: String,
    autoRecognitionRequested: Boolean,
    isDetecting: Boolean,
    detectedVariant: Int?,
    surname: String?,
    name: String?,
    journalClass: String? = null,
): String {
    val sourceLabel = if (source == BatchWorkSource.Camera) "Камера" else "Галерея"
    val base = "$sourceLabel · $displayName"
    val fullName = listOfNotNull(surname, name).joinToString(" ").trim()
    val cls = journalClass?.trim().orEmpty()
    val classSeg = if (cls.isNotEmpty()) " · класс $cls" else ""
    return when {
        isDetecting -> "$base · Определяем вариант и имя..."
        !autoRecognitionRequested && detectedVariant == null && fullName.isBlank() && cls.isEmpty() ->
            "$base · Без авто-распознавания"
        detectedVariant != null && fullName.isNotBlank() ->
            "$base · Вариант $detectedVariant · $fullName$classSeg"
        detectedVariant != null ->
            "$base · Вариант $detectedVariant$classSeg"
        fullName.isNotBlank() -> "$base · Вариант не распознан · $fullName$classSeg"
        cls.isNotEmpty() -> "$base · Вариант не распознан$classSeg"
        else -> "$base · Вариант не распознан"
    }
}

internal fun autoRecognitionProgressMessage(stillDetecting: Int): String {
    return if (stillDetecting > 0) {
        "Авто-распознавание: осталось $stillDetecting"
    } else {
        "Авто-распознавание завершено"
    }
}

internal fun addedSingleWorkMessage(itemTitle: String, autoRecognitionEnabled: Boolean): String {
    return if (autoRecognitionEnabled) {
        "Добавлено: $itemTitle. Авто-распознавание запущено."
    } else {
        "Добавлено: $itemTitle. Авто-распознавание выключено"
    }
}

internal fun addedGalleryWorksMessage(added: Int, skipped: Int, autoRecognitionEnabled: Boolean): String? {
    return when {
        added == 0 && skipped > 0 -> "Все выбранные файлы уже в очереди"
        added > 0 && skipped > 0 && autoRecognitionEnabled ->
            "Добавлено работ: $added, пропущено дубликатов: $skipped. Авто-распознавание запущено."
        added > 0 && skipped > 0 ->
            "Добавлено работ: $added, пропущено дубликатов: $skipped. Авто-распознавание выключено"
        added > 0 && autoRecognitionEnabled -> "Добавлено работ: $added. Авто-распознавание запущено."
        added > 0 -> "Добавлено работ: $added. Авто-распознавание выключено"
        else -> null
    }
}

internal fun toggleAutoRecognitionMessage(
    enabled: Boolean,
    pendingQueued: Int,
    nowDetecting: Int,
    runQueuedRecognitionButtonText: String,
): String? {
    return when {
        enabled && pendingQueued > 0 ->
            "Авто-распознавание включено. Уже загруженные запустите кнопкой «$runQueuedRecognitionButtonText»."
        !enabled && nowDetecting > 0 ->
            "Авто-распознавание выключено для новых работ. Текущие распознавания ($nowDetecting) будут завершены."
        else -> null
    }
}
