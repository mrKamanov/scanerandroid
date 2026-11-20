package com.tscan.scanertestov.feature.settings

import com.tscan.scanertestov.data.GradingCriteriaConfig

/**
 * Описание: действия пользователя на экране настроек.
 */
sealed interface SettingsAction {
    data object Back : SettingsAction
    data class ToggleQuickAccessBar(val enabled: Boolean) : SettingsAction
    data class SetOmrMlConfidenceThreshold(val threshold: Float) : SettingsAction
    data object ResetOmrMlConfidenceThreshold : SettingsAction
    data class SetGradingCriteria(val config: GradingCriteriaConfig) : SettingsAction
    data object ResetGradingCriteria : SettingsAction
    data object ImportStudents : SettingsAction
    data object ExportJournals : SettingsAction
    data object ImportJournals : SettingsAction
    data object OpenPrivacyPolicy : SettingsAction
}
