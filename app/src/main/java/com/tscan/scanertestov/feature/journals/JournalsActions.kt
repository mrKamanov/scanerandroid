package com.tscan.scanertestov.feature.journals

/**
 * Описание: действия пользователя на экране журналов.
 */
sealed interface JournalsAction {
    data object Back : JournalsAction
}
