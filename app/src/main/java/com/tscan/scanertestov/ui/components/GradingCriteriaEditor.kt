package com.tscan.scanertestov.ui.components

/**
 * Описание: редактор критериев оценки (проценты или баллы, границы «от–до» для оценок 5–2).
 * Используется в тесте («свои критерии для этого теста»). Логика полей — как в Настройках.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.GradingCriteriaMode
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlin.math.floor

/** Короткое описание критериев одной строкой: «5 — от 90%, 4 — от 70%, 3 — от 50%». */
fun GradingCriteriaConfig.shortSummary(): String {
    val rows = rows.sortedByDescending { it.grade }.filter { it.grade > 2 }
    return if (mode == GradingCriteriaMode.PERCENT) {
        rows.joinToString(", ") { "«${it.grade}» — от ${it.percentMin ?: "?"}%" }
    } else {
        rows.joinToString(", ") { "«${it.grade}» — от ${fmt(it.pointsMin)} б." }
    }
}

/** Все границы заполнены (для текущего режима). */
fun GradingCriteriaConfig.isFilled(): Boolean = rows.all { r ->
    if (mode == GradingCriteriaMode.PERCENT) r.percentMin != null && r.percentMax != null
    else r.pointsMin != null && r.pointsMax != null
}

@Composable
fun GradingCriteriaEditor(
    config: GradingCriteriaConfig,
    onChange: (GradingCriteriaConfig) -> Unit,
    pointsHint: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = config.mode == GradingCriteriaMode.PERCENT,
                onClick = { onChange(config.copy(mode = GradingCriteriaMode.PERCENT)) },
                label = { Text("Проценты") },
            )
            FilterChip(
                selected = config.mode == GradingCriteriaMode.POINTS,
                onClick = { onChange(config.copy(mode = GradingCriteriaMode.POINTS)) },
                label = { Text("Баллы") },
            )
        }
        Text(
            if (config.mode == GradingCriteriaMode.PERCENT) {
                "Доля правильных ответов, целые проценты от 0 до 100."
            } else {
                pointsHint
            },
            style = MaterialTheme.typography.bodySmall,
            color = Scrapbook.InkMuted,
        )
        for (row in config.rows.sortedByDescending { it.grade }) {
            val g = row.grade
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Оценка $g", modifier = Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
                if (config.mode == GradingCriteriaMode.PERCENT) {
                    NumberField(row.percentMin?.toString().orEmpty(), "от", Modifier.weight(1f)) { s ->
                        onChange(config.withRow(g) { it.copy(percentMin = s.filter(Char::isDigit).take(3).toIntOrNull()) })
                    }
                    NumberField(row.percentMax?.toString().orEmpty(), "до", Modifier.weight(1f)) { s ->
                        onChange(config.withRow(g) { it.copy(percentMax = s.filter(Char::isDigit).take(3).toIntOrNull()) })
                    }
                } else {
                    NumberField(fmt(row.pointsMin), "от", Modifier.weight(1f), decimal = true) { s ->
                        onChange(config.withRow(g) { it.copy(pointsMin = parsePoints(s)) })
                    }
                    NumberField(fmt(row.pointsMax), "до", Modifier.weight(1f), decimal = true) { s ->
                        onChange(config.withRow(g) { it.copy(pointsMax = parsePoints(s)) })
                    }
                }
            }
        }
    }
}

private fun GradingCriteriaConfig.withRow(
    grade: Int,
    f: (com.tscan.scanertestov.data.GradingCriteriaRow) -> com.tscan.scanertestov.data.GradingCriteriaRow,
): GradingCriteriaConfig = copy(rows = rows.map { if (it.grade == grade) f(it) else it })

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, decimal: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

private fun fmt(v: Double?): String = when {
    v == null -> ""
    v == floor(v) -> v.toLong().toString()
    else -> v.toString()
}

private fun parsePoints(s: String): Double? {
    val cleaned = buildString {
        var dot = false
        for (ch in s.replace(',', '.')) {
            if (ch.isDigit()) append(ch) else if (ch == '.' && !dot) { append('.'); dot = true }
        }
    }
    if (cleaned.isEmpty() || cleaned == ".") return null
    return cleaned.toDoubleOrNull()
}
