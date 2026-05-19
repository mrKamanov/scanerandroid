package com.tscan.scanertestov.feature.batch

/**
 * Описание: эффективный вариант эталона для работы и правило, можно ли снова запустить авто-распознавание.
 */
fun BatchWorkItem.effectiveVariant(): Int? = variantOverride ?: detectedVariant

fun BatchWorkItem.canEnqueueAutoRecognition(): Boolean {
    if (isVariantDetecting) return false
    if (!autoRecognitionRequested) return true
    val variantMissing = effectiveVariant() == null
    val failed =
        status == BatchWorkStatus.Error ||
            !variantDetectionError.isNullOrBlank() ||
            !studentNameDetectionError.isNullOrBlank()
    return variantMissing || failed
}
