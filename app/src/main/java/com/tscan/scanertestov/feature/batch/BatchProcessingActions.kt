package com.tscan.scanertestov.feature.batch

/**
 * Описание: действия пользователя на экране пакетной обработки.
 */
sealed interface BatchProcessingAction {
    data object Back : BatchProcessingAction
    data object CaptureByCamera : BatchProcessingAction
    data object LoadFromGallery : BatchProcessingAction
    data class SetAutoRecognitionEnabled(val enabled: Boolean) : BatchProcessingAction
    data object RunAutoRecognitionForQueued : BatchProcessingAction
    data object StartProcessing : BatchProcessingAction
    data object ClearQueue : BatchProcessingAction
    data object ToggleSelectAll : BatchProcessingAction
    data class ToggleWorkSelection(val workId: String) : BatchProcessingAction
    data class RemoveWork(val workId: String) : BatchProcessingAction
    data class SelectPreviewWork(val workId: String) : BatchProcessingAction
    data class SetWorkVariantOverride(val workId: String, val variant: Int?) : BatchProcessingAction
    data object AddAnswerKeyVariant : BatchProcessingAction
    data object RemoveAnswerKeyVariant : BatchProcessingAction
    data class SelectAnswerKeyVariant(val variant: Int) : BatchProcessingAction
    data class SetQuestionsCount(val count: Int) : BatchProcessingAction
    data class SetChoicesCount(val count: Int) : BatchProcessingAction
    data class ToggleCorrectAnswerSelection(val questionIndex: Int, val choiceIndex: Int) : BatchProcessingAction
    data class SetCriteriaNameDraft(val value: String) : BatchProcessingAction
    data object SaveCriteriaPreset : BatchProcessingAction
    data object ToggleSavedCriteriaExpanded : BatchProcessingAction
    data class LoadCriteriaPreset(val presetId: String) : BatchProcessingAction
    data class DeleteCriteriaPreset(val presetId: String) : BatchProcessingAction
    data class SetPageLayout(val layout: BatchPageLayout) : BatchProcessingAction
    data object ToggleStrictScoring : BatchProcessingAction
}
