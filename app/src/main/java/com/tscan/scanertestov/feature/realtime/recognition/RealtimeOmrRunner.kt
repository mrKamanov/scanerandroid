package com.tscan.scanertestov.feature.realtime.recognition

/**
 * Описание: запуск OMR на зафиксированном кадре и контуре бланка.
 */
import android.content.Context
import android.graphics.Bitmap
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrEngine
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import org.opencv.core.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RealtimeOmrRunner {

    suspend fun runOnCameraFrameWithContour(
        context: Context,
        cameraFrame: Bitmap,
        sheetContour: Array<Point>,
        questionsCount: Int,
        choicesCount: Int,
        columnCount: Int,
        answerKey: List<Set<Int>>,
        strictScoring: Boolean,
    ): BatchOmrResult = withContext(Dispatchers.Default) {
        val config = BatchOmrConfig(
            questionsCount = questionsCount,
            choicesCount = choicesCount,
            columnCount = columnCount,
            answerKey = answerKey,
            strictScoring = strictScoring,
        )
        BatchOmrEngine.processWithSheetContour(
            context = context.applicationContext,
            bitmap = cameraFrame,
            config = config,
            sheetContour = sheetContour,
        )
    }
}
