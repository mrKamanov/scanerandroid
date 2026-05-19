package com.tscan.scanertestov.feature.mainmenu

/**
 * Описание: обработчик навигационных действий главного меню.
 */
import androidx.navigation.NavHostController
import com.tscan.scanertestov.navigation.AppDestinations

fun handleMainMenuAction(navController: NavHostController, action: MainMenuAction) {
    when (action) {
        MainMenuAction.OpenRealtimeScan -> navController.navigate(AppDestinations.REALTIME_SCAN)
        MainMenuAction.OpenBatchProcessing -> navController.navigate(AppDestinations.BATCH_PROCESSING)
        MainMenuAction.OpenJournals -> navController.navigate(AppDestinations.JOURNALS)
        MainMenuAction.OpenReports -> navController.navigate(AppDestinations.REPORTS)
        MainMenuAction.OpenBlankEditor -> navController.navigate(AppDestinations.BLANK_EDITOR)
        MainMenuAction.OpenInstructions -> navController.navigate(AppDestinations.INSTRUCTIONS)
        MainMenuAction.OpenSettings -> navController.navigate(AppDestinations.SETTINGS)
    }
}
