package com.tscan.scanertestov.feature.realtime

/**
 * Описание: учёт и разрешение ячеек «исправление» при быстрой проверке.
 */
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BatchScoringEngine

internal object RealtimeFixedCorrections {

    fun keyFor(prediction: BatchCellPrediction): String =
        "${prediction.questionIndex}_${prediction.choiceIndex}"

    fun unresolvedCount(
        result: BatchOmrResult,
        decisions: Map<String, Boolean>,
    ): Int = result.fixedCells.count { decisions[keyFor(it)] == null }

    fun applyDecisions(
        result: BatchOmrResult,
        config: BatchOmrConfig,
        decisions: Map<String, Boolean>,
    ): BatchOmrResult {
        val adjustedPredictions = result.predictions.map { pred ->
            if (pred.klass != BatchCellClass.Fixed) {
                pred
            } else {
                when (decisions[keyFor(pred)]) {
                    true -> pred.copy(klass = BatchCellClass.Yes)
                    false -> pred.copy(klass = BatchCellClass.No)
                    null -> pred
                }
            }
        }
        val scores = BatchScoringEngine.score(adjustedPredictions, config)
        return result.copy(
            predictions = adjustedPredictions,
            questionScores = scores,
        )
    }
}
