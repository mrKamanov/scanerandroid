package com.tscan.scanertestov.feature.instructions

/**
 * Описание: навигация с экрана инструкций (назад в меню).
 */
import androidx.navigation.NavHostController

fun handleInstructionsAction(navController: NavHostController, action: InstructionsAction) {
    when (action) {
        InstructionsAction.Back -> navController.popBackStack()
        InstructionsAction.BackToTopicList,
        is InstructionsAction.OpenTopic -> Unit
    }
}
