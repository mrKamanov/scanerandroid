package com.tscan.scanertestov.feature.realtime

/**
 * Описание: состояние экрана быстрой проверки (камера, эталон, результат OMR).
 */
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult

data class RealtimeScanUiState(
    val cameraRunning: Boolean = true,
    val flashOn: Boolean = false,
    val contourDetected: Boolean = false,
    val isPaused: Boolean = false,
    val showCriteriaPanel: Boolean = false,
    val showEndDrawer: Boolean = false,
    val gridVisibleOnWarp: Boolean = false,
    val strictScoring: Boolean = true,
    val questionsCount: Int = 5,
    val choicesCount: Int = 4,
    val answerKey: List<Set<Int>> = emptyList(),
    val brightness: Int = 0,
    val contrast: Int = 100,
    val saturation: Int = 100,
    val sharpness: Int = 50,
    val columnCount: Int = 1,
    val isProcessing: Boolean = false,
    val lastResult: BatchOmrResult? = null,
    val errorMessage: String? = null,
)
