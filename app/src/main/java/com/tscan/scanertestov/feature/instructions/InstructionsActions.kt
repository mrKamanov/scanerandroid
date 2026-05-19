package com.tscan.scanertestov.feature.instructions

/**
 * Описание: действия пользователя на экране инструкций.
 */
sealed interface InstructionsAction {
    data object Back : InstructionsAction
    data class OpenTopic(val topicId: String) : InstructionsAction
    data object BackToTopicList : InstructionsAction
}
