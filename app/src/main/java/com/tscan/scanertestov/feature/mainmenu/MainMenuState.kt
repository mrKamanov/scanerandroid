package com.tscan.scanertestov.feature.mainmenu

/**
 * Описание: состояние экрана главного меню и список пунктов навигации.
 */
import androidx.annotation.DrawableRes
import com.tscan.scanertestov.R

data class MainMenuState(
    val menuItems: List<MainMenuItemState> = defaultMenuItems()
)

data class MainMenuItemState(
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val action: MainMenuAction
)

private fun defaultMenuItems(): List<MainMenuItemState> = listOf(
    MainMenuItemState(
        title = "Инструкция",
        description = "Пошаговые руководства по всем разделам",
        iconRes = R.drawable.ic_help_outline,
        action = MainMenuAction.OpenInstructions
    ),
    MainMenuItemState(
        title = "Быстрая проверка",
        description = "Проверка простых тестов",
        iconRes = R.drawable.ic_main_menu_scan,
        action = MainMenuAction.OpenRealtimeScan
    ),
    MainMenuItemState(
        title = "Пакетная обработка",
        description = "Проверка нескольких тестов",
        iconRes = R.drawable.ic_main_menu_batch,
        action = MainMenuAction.OpenBatchProcessing
    ),
    MainMenuItemState(
        title = "Конструктор бланков",
        description = "Создание и экспорт шаблона",
        iconRes = R.drawable.ic_main_menu_editor,
        action = MainMenuAction.OpenBlankEditor
    ),
    MainMenuItemState(
        title = "Настройки",
        description = "Настройка критериев и модели",
        iconRes = R.drawable.ic_main_menu_settings,
        action = MainMenuAction.OpenSettings
    ),
    MainMenuItemState(
        title = "Отчеты",
        description = "Результат работы",
        iconRes = R.drawable.ic_main_menu_reports,
        action = MainMenuAction.OpenReports
    ),
    MainMenuItemState(
        title = "Журналы",
        description = "Журнал и списки учеников",
        iconRes = R.drawable.ic_main_menu_journals,
        action = MainMenuAction.OpenJournals
    ),
)
