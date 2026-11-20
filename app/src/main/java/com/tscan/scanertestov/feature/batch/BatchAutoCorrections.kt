package com.tscan.scanertestov.feature.batch

/**
 * Описание: автоматическое решение ОЧЕВИДНЫХ исправлений на бланке.
 *
 * Очевидный случай — ученик зачеркнул (или стёр) кружок и в том же вопросе уверенно закрасил другой:
 * зачёркнутый кружок считаем «Пусто». Это тот же выбор, который учитель делает вручную в таком случае
 * (см. инструкцию «Исправления на бланке»).
 *
 * Всё остальное — спорное и остаётся учителю: зачёркнутый кружок без другого ответа в вопросе,
 * неуверенная нейросеть, эвристика без вероятностей.
 *
 * Авто-решения не записываются на диск: решение учителя всегда главнее, а если учитель ничего не трогал,
 * при каждом открытии результата правило применяется заново. На карточке такие ячейки помечены ✎.
 */
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction

/** Нейросеть уверена, что кружок исправлен (зачёркнут/стёрт). */
private const val MIN_FIXED_PROB = 0.80f

/** Нейросеть уверена, что другой кружок в вопросе закрашен. */
private const val MIN_YES_PROB = 0.90f

private fun BatchCellPrediction.fixedProb(): Float? = probs?.getOrNull(2)
private fun BatchCellPrediction.yesProb(): Float? = probs?.getOrNull(1)

/** Ключ ячейки → false («Пусто») для очевидных случаев во всех работах прогона. */
internal fun autoFixedDecisions(runItems: List<BatchRunItemResult>): Map<String, Boolean> {
    val out = HashMap<String, Boolean>()
    runItems.forEachIndexed { itemIndex, item ->
        val omr = item.omr ?: return@forEachIndexed
        if (!omr.contourFound || omr.fixedCells.isEmpty()) return@forEachIndexed
        val byQuestion = omr.predictions.groupBy { it.questionIndex }
        for (fixed in omr.fixedCells) {
            val pFix = fixed.fixedProb() ?: continue
            if (pFix < MIN_FIXED_PROB) continue
            val others = byQuestion[fixed.questionIndex].orEmpty().filter { it.choiceIndex != fixed.choiceIndex }
            val hasConfidentAnswer = others.any { o ->
                o.klass == BatchCellClass.Yes && (o.yesProb() ?: 0f) >= MIN_YES_PROB
            }
            if (hasConfidentAnswer) {
                out[fixedCellKey(itemIndex, fixed)] = false
            }
        }
    }
    return out
}
