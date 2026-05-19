package com.tscan.scanertestov.feature.batch.engine

/**
 * Описание: расчет баллов по вопросам для strict/partial режимов и single/multi ключей.
 *
 * Частичный режим: если отмечены все варианты в строке вопроса — считаем угадыванием, балл 0
 * (в строгом полное несовпадение с ключом и так даёт 0).
 */
internal object BatchScoringEngine {
    fun score(
        predictions: List<BatchCellPrediction>,
        config: BatchOmrConfig,
    ): List<BatchQuestionScore> {
        val byQuestion = predictions.groupBy { it.questionIndex }
        return (0 until config.questionsCount).map { q ->
            val key = config.answerKey.getOrNull(q).orEmpty()
            val items = byQuestion[q].orEmpty()
            val selected = items
                .filter { it.klass == BatchCellClass.Yes || it.klass == BatchCellClass.Fixed }
                .map { it.choiceIndex }
                .toSet()
            val hasFixed = items.any { it.klass == BatchCellClass.Fixed }
            val score = if (config.strictScoring) {
                scoreStrict(selected, key, hasFixed)
            } else {
                scorePartial(selected, key, hasFixed, choicesPerQuestion = config.choicesCount)
            }
            BatchQuestionScore(
                questionIndex = q,
                selectedChoices = selected,
                score = score,
                correct = score >= 0.999f,
                hasFixed = hasFixed,
            )
        }
    }

    private fun scoreStrict(selected: Set<Int>, key: Set<Int>, hasFixed: Boolean): Float {
        if (hasFixed) return 0f
        return if (selected == key) 1f else 0f
    }

    private fun scorePartial(
        selected: Set<Int>,
        key: Set<Int>,
        hasFixed: Boolean,
        choicesPerQuestion: Int,
    ): Float {
        if (key.isEmpty()) return 0f
        if (hasFixed) return 0f
        if (choicesPerQuestion > 0 && selected.size == choicesPerQuestion) {
            return 0f
        }
        val tp = selected.intersect(key).size.toFloat()
        val fp = selected.subtract(key).size.toFloat()
        val raw = tp - fp
        val norm = raw / key.size.toFloat()
        return norm.coerceIn(0f, 1f)
    }
}
