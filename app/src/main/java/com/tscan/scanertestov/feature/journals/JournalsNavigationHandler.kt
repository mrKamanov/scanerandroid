package com.tscan.scanertestov.feature.journals

/**
 * Описание: обработчик навигационных действий экрана журналов.
 */
import androidx.navigation.NavHostController

fun handleJournalsAction(navController: NavHostController, action: JournalsAction) {
    when (action) {
        JournalsAction.Back -> navController.popBackStack()
    }
}
