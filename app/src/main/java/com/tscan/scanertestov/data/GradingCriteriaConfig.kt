package com.tscan.scanertestov.data

/**
 * Описание: критерии перевода результата пакетной проверки в оценку 2–5 (проценты или сумма баллов).
 */
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

enum class GradingCriteriaMode {
    PERCENT,
    POINTS,
}

data class GradingCriteriaRow(
    val grade: Int,
    val percentMin: Int?,
    val percentMax: Int?,
    val pointsMin: Double?,
    val pointsMax: Double?,
)

data class GradingCriteriaConfig(
    val mode: GradingCriteriaMode,
    val rows: List<GradingCriteriaRow>,
) {
    fun resolveGrade(percent: Float, pointsSum: Double): Int {
        return when (mode) {
            GradingCriteriaMode.PERCENT -> gradeFromPercent(percent)
            GradingCriteriaMode.POINTS -> {
                gradeFromPoints(pointsSum) ?: defaultPercent().gradeFromPercent(percent)
            }
        }
    }

    private fun gradeFromPercent(percent: Float): Int {
        val x = floor(percent.toDouble()).toInt().coerceIn(0, 100)
        for (row in rows.sortedByDescending { it.grade }) {
            val lo = row.percentMin ?: continue
            val hi = row.percentMax ?: continue
            if (x in lo..hi) return row.grade
        }
        return 2
    }

    private fun gradeFromPoints(pointsSum: Double): Int? {
        for (row in rows.sortedByDescending { it.grade }) {
            val lo = row.pointsMin ?: continue
            val hi = row.pointsMax ?: continue
            if (pointsSum >= lo && pointsSum <= hi) return row.grade
        }
        return null
    }

    fun toPrefsString(): String {
        val o = JSONObject()
        o.put("mode", if (mode == GradingCriteriaMode.PERCENT) "PERCENT" else "POINTS")
        val arr = JSONArray()
        for (row in rows.sortedBy { it.grade }) {
            val jo = JSONObject()
            jo.put("g", row.grade)
            if (row.percentMin != null) jo.put("pmin", row.percentMin) else jo.put("pmin", JSONObject.NULL)
            if (row.percentMax != null) jo.put("pmax", row.percentMax) else jo.put("pmax", JSONObject.NULL)
            if (row.pointsMin != null) jo.put("tmin", row.pointsMin) else jo.put("tmin", JSONObject.NULL)
            if (row.pointsMax != null) jo.put("tmax", row.pointsMax) else jo.put("tmax", JSONObject.NULL)
            arr.put(jo)
        }
        o.put("rows", arr)
        return o.toString()
    }

    companion object {
        fun defaultPercent(): GradingCriteriaConfig = GradingCriteriaConfig(
            mode = GradingCriteriaMode.PERCENT,
            rows = listOf(
                GradingCriteriaRow(5, 90, 100, null, null),
                GradingCriteriaRow(4, 70, 89, null, null),
                GradingCriteriaRow(3, 50, 69, null, null),
                GradingCriteriaRow(2, 0, 49, null, null),
            ),
        )

        fun fromPrefsString(raw: String?): GradingCriteriaConfig {
            if (raw.isNullOrBlank()) return defaultPercent()
            return try {
                val o = JSONObject(raw)
                val mode = when (o.optString("mode", "PERCENT").uppercase()) {
                    "POINTS" -> GradingCriteriaMode.POINTS
                    else -> GradingCriteriaMode.PERCENT
                }
                val arr = o.getJSONArray("rows")
                val parsed = ArrayList<GradingCriteriaRow>(arr.length())
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    parsed += GradingCriteriaRow(
                        grade = row.getInt("g"),
                        percentMin = if (row.has("pmin") && !row.isNull("pmin")) row.getInt("pmin") else null,
                        percentMax = if (row.has("pmax") && !row.isNull("pmax")) row.getInt("pmax") else null,
                        pointsMin = if (row.has("tmin") && !row.isNull("tmin")) row.getDouble("tmin") else null,
                        pointsMax = if (row.has("tmax") && !row.isNull("tmax")) row.getDouble("tmax") else null,
                    )
                }
                if (parsed.size != 4 || parsed.map { it.grade }.toSet() != setOf(2, 3, 4, 5)) {
                    return defaultPercent()
                }
                GradingCriteriaConfig(mode, parsed.sortedBy { it.grade })
            } catch (_: Exception) {
                defaultPercent()
            }
        }
    }
}
