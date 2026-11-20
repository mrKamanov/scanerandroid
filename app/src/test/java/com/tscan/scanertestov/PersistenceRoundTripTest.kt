package com.tscan.scanertestov

import com.tscan.scanertestov.feature.batch.BatchLastRunStore
import com.tscan.scanertestov.feature.batch.BatchRun
import com.tscan.scanertestov.feature.batch.BatchRunItemResult
import com.tscan.scanertestov.feature.batch.engine.BatchCellClass
import com.tscan.scanertestov.feature.batch.engine.BatchCellPrediction
import com.tscan.scanertestov.feature.batch.engine.BatchColumnFrameGrid
import com.tscan.scanertestov.feature.batch.engine.BatchOmrConfig
import com.tscan.scanertestov.feature.batch.engine.BatchOmrResult
import com.tscan.scanertestov.feature.batch.engine.BatchQuestionScore
import com.tscan.scanertestov.feature.reports.ReportsSelectedWorksStore
import com.tscan.scanertestov.feature.reports.ReportsWorkSnapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PersistenceRoundTripTest {
    private val config = BatchOmrConfig(2, 3, 2, listOf(setOf(0), setOf(1, 2)), strictScoring = false)
    private val preds = listOf(
        BatchCellPrediction(0, 0, BatchCellClass.Yes, 0.97f),
        BatchCellPrediction(0, 1, BatchCellClass.No, 0.91f),
        BatchCellPrediction(1, 2, BatchCellClass.Fixed, 0.55f),
    )
    private val scores = listOf(
        BatchQuestionScore(0, setOf(0), 1f, true, false),
        BatchQuestionScore(1, setOf(2), 0f, false, true),
    )

    @Test
    fun batchRunSurvivesDisk() {
        val item = BatchRunItemResult(
            title = "Работа №1",
            layoutLabel = "2 колонки",
            config = config,
            detectedVariant = 2,
            detectedStudentSurname = "Иванов",
            detectedStudentName = "Пётр",
            detectedJournalClassName = "7А",
            omr = BatchOmrResult(
                predictions = preds,
                questionScores = scores,
                fixedCells = preds.filter { it.klass == BatchCellClass.Fixed },
                contourFound = true,
                columnFrames = listOf(BatchColumnFrameGrid(0, 1, 1, 10, 20, 300, 400)),
            ),
        )
        val failed = BatchRunItemResult(title = "Работа №2", layoutLabel = "1 колонка", config = config, error = "не удалось открыть изображение")
        val run = BatchRun("run-1", 123L, listOf(item, failed))
        val json = BatchLastRunStore.runToJson(run).toString()
        val restored = BatchLastRunStore.runFromJson(JSONObject(json), File("/nonexistent"))
        assertEquals(run.runId, restored.runId)
        assertEquals(run.createdAtMs, restored.createdAtMs)
        assertEquals(item, restored.items[0])
        assertEquals(failed, restored.items[1])
    }

    @Test
    fun reportSnapshotSurvivesDisk() {
        val w = ReportsWorkSnapshot(
            sourceKey = "run-1#0", title = "Работа 1", grade = 4, percent = 75f, scoreCorrect = 3, scoreTotal = 4,
            questionScores = scores, variant = 1, studentSurname = "Смирнова", studentName = "Анна",
            journalClassName = "7Б", strictScoring = false, partialCompletedCount = 1, hasMultiChoiceQuestions = true,
        )
        val restored = ReportsSelectedWorksStore.fromJson(JSONObject(ReportsSelectedWorksStore.toJson(w).toString()))
        assertEquals(w, restored)
    }
}
