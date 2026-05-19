package com.tscan.scanertestov.feature.realtime

/**
 * Описание: действия пользователя на экране сканирования в реальном времени.
 */
sealed interface RealtimeScanAction {
    data object Back : RealtimeScanAction
}
