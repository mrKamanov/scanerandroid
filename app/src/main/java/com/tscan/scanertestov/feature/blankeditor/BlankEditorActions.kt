package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: действия пользователя на экране конструктора бланков.
 */
sealed interface BlankEditorAction {
    data object Back : BlankEditorAction
}
