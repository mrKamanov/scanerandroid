package com.tscan.scanertestov

import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchScoringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchScoringEngineTest {
    /** Одна строка вопроса: отмеченные варианты [marked], спорные [fixed]. */
    private fun row(q: Int, choices: Int, marked: Set<Int>, fixed: Set<Int> = emptySet()) =
        (0 until choices).map { c ->
            val klass = when (c) {
                in fixed -> BatchCellClass.Fixed
                in marked -> BatchCellClass.Yes
                else -> BatchCellClass.No
            }
            BatchCellPrediction(q, c, klass, 0.99f)
        }

    private fun cfg(key: List<Set<Int>>, strict: Boolean, choices: Int = 4) =
        BatchOmrConfig(key.size, choices, 1, key, strict)

    @Test
    fun strictExactMatchOnly() {
        val config = cfg(listOf(setOf(0), setOf(1, 2)), strict = true)
        val preds = row(0, 4, setOf(0)) + row(1, 4, setOf(1))
        val s = BatchScoringEngine.score(preds, config)
        assertTrue(s[0].correct)
        assertEquals(0f, s[1].score, 1e-6f)
    }

    @Test
    fun partialGivesFractionAndPenalisesWrong() {
        val config = cfg(listOf(setOf(1, 2)), strict = false)
        assertEquals(0.5f, BatchScoringEngine.score(row(0, 4, setOf(1)), config)[0].score, 1e-6f)
        assertEquals(0f, BatchScoringEngine.score(row(0, 4, setOf(1, 3)), config)[0].score, 1e-6f)
        assertEquals(1f, BatchScoringEngine.score(row(0, 4, setOf(1, 2)), config)[0].score, 1e-6f)
    }

    @Test
    fun partialAllMarkedIsGuessing() {
        val config = cfg(listOf(setOf(1, 2)), strict = false)
        assertEquals(0f, BatchScoringEngine.score(row(0, 4, setOf(0, 1, 2, 3)), config)[0].score, 1e-6f)
    }

    @Test
    fun unresolvedFixedGivesZeroAndIsFlagged() {
        val config = cfg(listOf(setOf(0)), strict = true)
        val s = BatchScoringEngine.score(row(0, 4, setOf(0), fixed = setOf(2)), config)[0]
        assertTrue(s.hasFixed)
        assertFalse(s.correct)
    }

    @Test
    fun missingRowCountsAsUnanswered() {
        val config = cfg(listOf(setOf(0), setOf(1)), strict = true)
        val s = BatchScoringEngine.score(row(0, 4, setOf(0)), config)
        assertEquals(2, s.size)
        assertFalse(s[1].correct)
    }
}
