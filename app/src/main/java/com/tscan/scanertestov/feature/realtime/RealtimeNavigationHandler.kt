package com.tscan.scanertestov.feature.realtime

/**
 * Описание: обработчик навигационных действий экрана быстрой проверки.
 */
import androidx.navigation.NavHostController

fun handleRealtimeScanAction(navController: NavHostController, action: RealtimeScanAction) {
    when (action) {
        RealtimeScanAction.Back -> navController.popBackStack()
    }
}
