package com.tscan.scanertestov.feature.blankeditor

import androidx.navigation.NavHostController

/**
 * Описание: навигация с экрана конструктора бланков.
 */
fun handleBlankEditorAction(navController: NavHostController, action: BlankEditorAction) {
    when (action) {
        BlankEditorAction.Back -> navController.popBackStack()
    }
}
