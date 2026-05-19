package com.tscan.scanertestov.feature.settings

/**
 * Описание: обработчик навигационных действий экрана настроек.
 */
import android.content.Context
import androidx.navigation.NavHostController
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.InferenceModelVariant
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.legal.AppLegalUrls
import com.tscan.scanertestov.legal.openHttpUrl
import com.tscan.scanertestov.ml.InferenceModelRuntime

fun handleSettingsAction(
    context: Context,
    navController: NavHostController,
    action: SettingsAction,
    onQuickAccessBarChanged: (Boolean) -> Unit,
    onInferenceModelChanged: (InferenceModelVariant) -> Unit,
    onGradingCriteriaChanged: (GradingCriteriaConfig) -> Unit = {},
    onOmrMlConfidenceThresholdChanged: (Float) -> Unit = {},
) {
    when (action) {
        SettingsAction.Back -> navController.popBackStack()
        is SettingsAction.ToggleQuickAccessBar -> onQuickAccessBarChanged(action.enabled)
        is SettingsAction.SelectInferenceModel -> {
            AppSettingsStore.setInferenceModel(context, action.variant)
            InferenceModelRuntime.applySelectionFromSettings(context)
            onInferenceModelChanged(action.variant)
        }
        is SettingsAction.SetOmrMlConfidenceThreshold -> {
            val v = AppSettingsStore.setOmrMlConfidenceThreshold(context, action.threshold)
            onOmrMlConfidenceThresholdChanged(v)
        }
        SettingsAction.ResetOmrMlConfidenceThreshold -> {
            val v = AppSettingsStore.setOmrMlConfidenceThreshold(
                context,
                AppSettingsStore.OMR_ML_CONFIDENCE_DEFAULT,
            )
            onOmrMlConfidenceThresholdChanged(v)
        }
        is SettingsAction.SetGradingCriteria -> {
            AppSettingsStore.setGradingCriteria(context, action.config)
            onGradingCriteriaChanged(action.config)
        }
        SettingsAction.ResetGradingCriteria -> {
            val def = GradingCriteriaConfig.defaultPercent()
            AppSettingsStore.setGradingCriteria(context, def)
            onGradingCriteriaChanged(def)
        }
        SettingsAction.ImportStudents -> Unit
        SettingsAction.ExportJournals -> Unit
        SettingsAction.ImportJournals -> Unit
        SettingsAction.OpenPrivacyPolicy -> context.openHttpUrl(AppLegalUrls.privacyPolicy)
        SettingsAction.OpenSourceCode -> context.openHttpUrl(AppLegalUrls.sourceCode)
    }
}
