package com.tscan.scanertestov.feature.blankeditor

/**
 * Описание: панели параметров бланка на экране конструктора.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch

private fun clampInputToRange(raw: String, min: Int, max: Int, maxDigits: Int = 2): String {
    val digits = raw.filter { it.isDigit() }.take(maxDigits)
    if (digits.isEmpty()) return min.toString()
    return digits.toInt().coerceIn(min, max).toString()
}

@Composable
fun BlankEditorParametersPanel(
    questionsText: String,
    optionsText: String,
    columnCount: Int,
    onQuestionsTextChange: (String) -> Unit,
    onOptionsTextChange: (String) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsShellCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Text(
            text = "Параметры бланка",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = questionsText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(2)
                    onQuestionsTextChange(digits)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Вопросов") },
                supportingText = { Text("1–35") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = optionsText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(1)
                    onOptionsTextChange(digits)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Вариантов") },
                supportingText = { Text("2–9") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Столбцы:",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilterChip(
                selected = columnCount == 1,
                onClick = { onColumnCountChange(1) },
                label = { Text("Один") },
            )
            FilterChip(
                selected = columnCount == 2,
                onClick = { onColumnCountChange(2) },
                label = { Text("Два") },
            )
        }
        }
    }
}

@Composable
fun BlankEditorMarkerFieldsPanel(
    variantMarkerEnabled: Boolean,
    studentNameMarkerEnabled: Boolean,
    studentNamePrintedMode: Boolean,
    studentSurnameText: String,
    studentNameText: String,
    variantPrintedMode: Boolean,
    variantNumberText: String,
    onVariantMarkerEnabledChange: (Boolean) -> Unit,
    onStudentNameMarkerEnabledChange: (Boolean) -> Unit,
    onStudentNamePrintedModeChange: (Boolean) -> Unit,
    onStudentSurnameTextChange: (String) -> Unit,
    onStudentNameTextChange: (String) -> Unit,
    onVariantPrintedModeChange: (Boolean) -> Unit,
    onVariantNumberTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsShellCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Text(
            text = "Поля бланка",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Поле «Вариант» на бланке",
                style = MaterialTheme.typography.bodyMedium,
            )
            ShellBubbleSwitch(
                checked = variantMarkerEnabled,
                onCheckedChange = onVariantMarkerEnabledChange,
            )
        }
        if (variantMarkerEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !variantPrintedMode,
                    onClick = { onVariantPrintedModeChange(false) },
                    label = { Text("Заполнять вручную") },
                )
                FilterChip(
                    selected = variantPrintedMode,
                    onClick = { onVariantPrintedModeChange(true) },
                    label = { Text("Печатать номер") },
                )
            }
            if (variantPrintedMode) {
                OutlinedTextField(
                    value = variantNumberText,
                    onValueChange = { raw ->
                        onVariantNumberTextChange(raw.filter { it.isDigit() }.take(2))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Номер варианта") },
                    supportingText = { Text("Например: 1, 2, 3, 4") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Поле «Имя ученика» на бланке",
                style = MaterialTheme.typography.bodyMedium,
            )
            ShellBubbleSwitch(
                checked = studentNameMarkerEnabled,
                onCheckedChange = onStudentNameMarkerEnabledChange,
            )
        }
        if (studentNameMarkerEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !studentNamePrintedMode,
                    onClick = { onStudentNamePrintedModeChange(false) },
                    label = { Text("Оставить пустым") },
                )
                FilterChip(
                    selected = studentNamePrintedMode,
                    onClick = { onStudentNamePrintedModeChange(true) },
                    label = { Text("Печатать имя") },
                )
            }
            if (studentNamePrintedMode) {
                OutlinedTextField(
                    value = studentSurnameText,
                    onValueChange = onStudentSurnameTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Фамилия") },
                )
                OutlinedTextField(
                    value = studentNameText,
                    onValueChange = onStudentNameTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Имя") },
                )
            }
        }
        }
    }
}

@Composable
fun BlankEditorAdvancedParametersPanel(
    expanded: Boolean,
    bubbleSizeText: String,
    bubbleFontSizeText: String,
    gapHorizontalText: String,
    gapVerticalText: String,
    gridPaddingText: String,
    borderWidthText: String,
    onExpandedChange: (Boolean) -> Unit,
    onBubbleSizeTextChange: (String) -> Unit,
    onBubbleFontSizeTextChange: (String) -> Unit,
    onGapHorizontalTextChange: (String) -> Unit,
    onGapVerticalTextChange: (String) -> Unit,
    onGridPaddingTextChange: (String) -> Unit,
    onBorderWidthTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsShellCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        ShellBubbleOutlinedButton(
            text = if (expanded) "Дополнительные параметры бланка ▲" else "Дополнительные параметры бланка ▼",
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!expanded) return@Column

        Text(
            text = "Размеры элементов",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = bubbleSizeText,
                onValueChange = { raw ->
                    onBubbleSizeTextChange(clampInputToRange(raw, min = 20, max = 80))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Кружки (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = bubbleFontSizeText,
                onValueChange = { raw ->
                    onBubbleFontSizeTextChange(clampInputToRange(raw, min = 10, max = 30))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Шрифт (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Text(
            text = "Отступы и интервалы",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = gapHorizontalText,
                onValueChange = { raw ->
                    onGapHorizontalTextChange(clampInputToRange(raw, min = 5, max = 50))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Гориз. (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = gapVerticalText,
                onValueChange = { raw ->
                    onGapVerticalTextChange(clampInputToRange(raw, min = 5, max = 50))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Вертик. (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = gridPaddingText,
                onValueChange = { raw ->
                    onGridPaddingTextChange(clampInputToRange(raw, min = 5, max = 50))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Край сетки (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = borderWidthText,
                onValueChange = { raw ->
                    onBorderWidthTextChange(clampInputToRange(raw, min = 1, max = 20))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Контур (px)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        }
    }
}
