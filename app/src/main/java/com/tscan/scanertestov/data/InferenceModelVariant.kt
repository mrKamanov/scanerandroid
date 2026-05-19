package com.tscan.scanertestov.data

/**
 * Описание: варианты ONNX-модели OMR (размер входа 64 / 128 / 256) и пути к файлам в assets.
 */
enum class InferenceModelVariant(
    val storageValue: String,
    val inputSize: Int,
    val assetRelativePath: String,
) {
    FAST_64(
        storageValue = "64",
        inputSize = 64,
        assetRelativePath = "models/64x64_model/best_medium_model.onnx",
    ),
    BALANCED_128(
        storageValue = "128",
        inputSize = 128,
        assetRelativePath = "models/128x128_model/best_128x128_model.onnx",
    ),
    PRECISE_256(
        storageValue = "256",
        inputSize = 256,
        assetRelativePath = "models/256x256_model/best_256x256_model.onnx",
    ),
    ;

    companion object {
        fun fromStorageValue(value: String?): InferenceModelVariant =
            entries.firstOrNull { it.storageValue == value } ?: BALANCED_128
    }
}
