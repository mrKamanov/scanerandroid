package com.tscan.scanertestov.data

/**
 * Описание: чтение и запись локальных настроек приложения (SharedPreferences).
 */
import android.content.Context

object AppSettingsStore {
    private const val PREFS_NAME = "tscan_scanertestov_prefs"
    private const val KEY_INFERENCE_MODEL = "inference_model_variant"
    private const val KEY_GRADING_CRITERIA_JSON = "grading_criteria_json"
    private const val KEY_OMR_ML_CONFIDENCE_THRESHOLD = "omr_ml_confidence_threshold"

    const val OMR_ML_CONFIDENCE_MIN = 0.30f
    const val OMR_ML_CONFIDENCE_MAX = 0.95f
    const val OMR_ML_CONFIDENCE_DEFAULT = 0.70f

    fun getInferenceModel(context: Context): InferenceModelVariant {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INFERENCE_MODEL, null)
        return InferenceModelVariant.fromStorageValue(raw)
    }

    fun setInferenceModel(context: Context, variant: InferenceModelVariant) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INFERENCE_MODEL, variant.storageValue)
            .apply()
    }

    fun getGradingCriteria(context: Context): GradingCriteriaConfig {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GRADING_CRITERIA_JSON, null)
        return GradingCriteriaConfig.fromPrefsString(raw)
    }

    fun setGradingCriteria(context: Context, config: GradingCriteriaConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GRADING_CRITERIA_JSON, config.toPrefsString())
            .apply()
    }

    fun getOmrMlConfidenceThreshold(context: Context): Float {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_OMR_ML_CONFIDENCE_THRESHOLD, OMR_ML_CONFIDENCE_DEFAULT)
        return raw.coerceIn(OMR_ML_CONFIDENCE_MIN, OMR_ML_CONFIDENCE_MAX)
    }

    fun setOmrMlConfidenceThreshold(context: Context, threshold: Float): Float {
        val v = threshold.coerceIn(OMR_ML_CONFIDENCE_MIN, OMR_ML_CONFIDENCE_MAX)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_OMR_ML_CONFIDENCE_THRESHOLD, v)
            .apply()
        return v
    }
}
