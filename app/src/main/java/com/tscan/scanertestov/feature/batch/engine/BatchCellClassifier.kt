package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: маршрутизатор классификации ячейки (ML -> fallback-эвристика).
 */
import android.content.Context
import org.opencv.core.Mat

internal object BatchCellClassifier {
    fun classify(cellBgr: Mat, context: Context): Pair<BatchCellClass, Float> =
        BatchCellMlClassifier.classify(cellBgr = cellBgr, context = context)
            ?: BatchCellHeuristicClassifier.classify(cellBgr)

    fun classifyCells(
        cells: List<Triple<Int, Int, Mat>>,
        context: Context,
    ): List<BatchCellPrediction> {
        if (cells.isEmpty()) return emptyList()
        val mats = cells.map { it.third }
        val ml = BatchCellMlClassifier.classifyBatch(mats, context)
        return cells.mapIndexed { i, (q, c, mat) ->
            val mlPair = ml?.getOrNull(i)
            val (klass, conf) = mlPair ?: BatchCellHeuristicClassifier.classify(mat)
            BatchCellPrediction(q, c, klass, conf)
        }
    }
}
