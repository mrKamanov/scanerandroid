package com.tscan.scanertestov.data

/**
 * Описание: ONNX-модель OMR и путь к файлу в assets. Осталась одна — 128×128, дообученная на бланках приложения
 * (прежние 64 и 256 убраны: на фото работали хуже). Старое значение настройки («64»/«256») читается как эта модель.
 */
enum class InferenceModelVariant(
    val storageValue: String,
    val inputSize: Int,
    val assetRelativePath: String,
) {
    BALANCED_128(
        storageValue = "128",
        inputSize = 128,
        assetRelativePath = "models/128x128_model/best_128x128_model.onnx",
    ),
    ;

    companion object {
        fun fromStorageValue(value: String?): InferenceModelVariant =
            BALANCED_128
    }
}
