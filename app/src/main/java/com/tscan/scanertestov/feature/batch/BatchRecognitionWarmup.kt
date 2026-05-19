package com.tscan.scanertestov.feature.batch

/**
 * Описание: единый прогрев OCR-моделей и словаря ФИО для realtime и пакетной обработки.
 */
import android.content.Context
import com.tscan.scanertestov.ml.ocr.CyrillicPpOcrV3RecEngine
import com.tscan.scanertestov.ml.ocr.EnNumberMobileV2PpOcrRecEngine
import com.tscan.scanertestov.ml.ocr.EslavPpOcrV5RecEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object BatchRecognitionWarmup {
    private val mutex = Mutex()

    @Volatile
    private var completed = false

    @Volatile
    private var cachedErrors: List<String> = emptyList()

    suspend fun ensure(appContext: Context): List<String> {
        if (completed) return cachedErrors
        return mutex.withLock {
            if (completed) return@withLock cachedErrors
            val errors = withContext(Dispatchers.Default) {
                listOfNotNull(
                    EnNumberMobileV2PpOcrRecEngine.ensureLoaded(appContext),
                    CyrillicPpOcrV3RecEngine.ensureLoaded(appContext),
                    EslavPpOcrV5RecEngine.ensureLoaded(appContext),
                ).filter { it.isNotBlank() }
            }.toMutableList()

            runCatching {
                BatchNameDictionaryCorrector.correct(
                    context = appContext,
                    surname = null,
                    name = null,
                    confidence = 1f,
                )
            }.onFailure { throwable ->
                errors += (throwable.message ?: "Ошибка прогрева словаря ФИО")
            }

            cachedErrors = errors
            completed = true
            cachedErrors
        }
    }
}

