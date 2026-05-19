package com.tscan.scanertestov.ml

/**
 * Описание: активная ONNX-модель OMR и синхронизация выбора из настроек приложения.
 */
import android.content.Context
import android.util.Log
import com.tscan.scanertestov.data.InferenceModelVariant
import com.tscan.scanertestov.data.AppSettingsStore

object InferenceModelRuntime {
    private const val TAG = "InferenceModelRuntime"

    @Volatile
    var activeVariant: InferenceModelVariant = InferenceModelVariant.BALANCED_128
        private set

    fun syncFromPreferences(context: Context) {
        val v = AppSettingsStore.getInferenceModel(context.applicationContext)
        activeVariant = v
        Log.i(TAG, "Активная модель: $v → assets/${v.assetRelativePath}")
    }

    fun applySelectionFromSettings(context: Context) {
        syncFromPreferences(context)
        Log.i(TAG, "Модель обновлена по настройкам; при наличии OrtSession — пересоздать здесь.")
    }
}
