package com.tscan.scanertestov

import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.GradingCriteriaMode
import com.tscan.scanertestov.data.GradingCriteriaRow
import org.junit.Assert.assertEquals
import org.junit.Test

class GradingCriteriaConfigTest {
    private val percent = GradingCriteriaConfig.defaultPercent()

    @Test
    fun percentBoundaries() {
        assertEquals(5, percent.resolveGrade(100f, 0.0))
        assertEquals(5, percent.resolveGrade(90f, 0.0))
        assertEquals(4, percent.resolveGrade(89.9f, 0.0))
        assertEquals(3, percent.resolveGrade(50f, 0.0))
        assertEquals(2, percent.resolveGrade(49.99f, 0.0))
        assertEquals(2, percent.resolveGrade(0f, 0.0))
    }

    private val points = GradingCriteriaConfig(
        mode = GradingCriteriaMode.POINTS,
        rows = listOf(
            GradingCriteriaRow(2, null, null, 0.0, 4.0),
            GradingCriteriaRow(3, null, null, 5.0, 7.0),
            GradingCriteriaRow(4, null, null, 8.0, 9.0),
            GradingCriteriaRow(5, null, null, 10.0, 10.0),
        ),
    )

    @Test
    fun pointsExactRows() {
        assertEquals(2, points.resolveGrade(0f, 3.0))
        assertEquals(3, points.resolveGrade(0f, 5.0))
        assertEquals(5, points.resolveGrade(0f, 10.0))
    }

    @Test
    fun pointsFractionalGapGoesToReachedRow() {
        // 4.5 балла при частичном оценивании: минимум «3» (5.0) не набран → «2».
        assertEquals(2, points.resolveGrade(99f, 4.5))
        assertEquals(3, points.resolveGrade(0f, 7.5))
        assertEquals(4, points.resolveGrade(0f, 9.5))
    }

    @Test
    fun prefsRoundTrip() {
        val restored = GradingCriteriaConfig.fromPrefsString(points.toPrefsString())
        assertEquals(points, restored)
        assertEquals(percent, GradingCriteriaConfig.fromPrefsString("мусор"))
    }
}
