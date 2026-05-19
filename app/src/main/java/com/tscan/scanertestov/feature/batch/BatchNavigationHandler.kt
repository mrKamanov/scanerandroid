package com.tscan.scanertestov.feature.batch

/**
 * Описание: обработчики навигационных действий для пакетной обработки.
 */
import androidx.navigation.NavHostController
import com.tscan.scanertestov.navigation.AppDestinations

fun handleBatchResultsAction(navController: NavHostController, action: BatchResultsAction) {
    when (action) {
        BatchResultsAction.Back -> navController.popBackStack()
        BatchResultsAction.OpenReports -> navController.navigate(AppDestinations.REPORTS)
        BatchResultsAction.SendAllToReports -> Unit
        is BatchResultsAction.SendWorkToReports -> Unit
        is BatchResultsAction.MarkFixedAsAnswered -> Unit
        is BatchResultsAction.MarkFixedAsEmpty -> Unit
    }
}
