package com.tscan.scanertestov.feature.batch

/**
 * Описание: ключи результата съёмки бланка для [androidx.navigation.NavController] SavedStateHandle.
 */
object BatchCaptureKeys {
    /** Список URI снимков (несколько подряд, пока открыта камера). */
    const val RESULT_URIS: String = "batch_document_capture_result_uris"
}
