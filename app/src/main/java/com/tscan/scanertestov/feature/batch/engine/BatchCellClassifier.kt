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
        val probs = ArrayList<FloatArray>()
        val ml = BatchCellMlClassifier.classifyBatch(mats, context, probs)
        return cells.mapIndexed { i, (q, c, mat) ->
            val mlPair = ml?.getOrNull(i)
            val (klass, conf) = mlPair ?: BatchCellHeuristicClassifier.classify(mat)
            BatchCellPrediction(q, c, klass, conf, probs = if (mlPair != null && probs.size == cells.size) probs[i] else null)
        }
    }
}
