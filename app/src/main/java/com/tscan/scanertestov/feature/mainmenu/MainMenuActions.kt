package com.tscan.scanertestov.feature.mainmenu

/**
 * Описание: набор действий пользователя на экране главного меню.
 */
sealed interface MainMenuAction {
    data object OpenRealtimeScan : MainMenuAction
    data object OpenBatchProcessing : MainMenuAction
    data object OpenJournals : MainMenuAction
    data object OpenReports : MainMenuAction
    data object OpenBlankEditor : MainMenuAction
    data object OpenInstructions : MainMenuAction
    data object OpenSettings : MainMenuAction
}
