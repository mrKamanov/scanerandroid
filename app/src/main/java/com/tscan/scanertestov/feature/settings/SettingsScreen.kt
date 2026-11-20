package com.tscan.scanertestov.feature.settings

/**
 * Описание: экран настроек прототипа (навигация, модель ИИ, данные).
 */
import androidx.compose.foundation.clickable
import com.tscan.scanertestov.ui.theme.Scrapbook
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.data.GradingCriteriaConfig
import com.tscan.scanertestov.data.GradingCriteriaMode
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleSwitch
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState = SettingsState(),
    onAction: (SettingsAction) -> Unit
) {
    ScreenScaffold(
        onBack = { onAction(SettingsAction.Back) },
        title = "Настройки",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HintsSettingsCard()
            SettingsShellCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.navigationTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.quickAccessBarSwitchText, modifier = Modifier.weight(1f).padding(end = 12.dp))
                        ShellBubbleSwitch(
                            checked = state.isQuickAccessBarEnabled,
                            onCheckedChange = { onAction(SettingsAction.ToggleQuickAccessBar(it)) },
                        )
                    }
                }
            }
            SettingsShellCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.aiModelTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.aiModelSummary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.aiModelFacts.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(k, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.42f))
                            Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.58f))
                        }
                    }
                }
            }
            SettingsShellCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.gradingTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.gradingHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.gradingCriteria.mode == GradingCriteriaMode.PERCENT,
                            onClick = {
                                onAction(
                                    SettingsAction.SetGradingCriteria(
                                        state.gradingCriteria.copy(mode = GradingCriteriaMode.PERCENT),
                                    ),
                                )
                            },
                            label = { Text(state.gradingModePercent) },
                            colors = settingsFilterChipColors(),
                        )
                        FilterChip(
                            selected = state.gradingCriteria.mode == GradingCriteriaMode.POINTS,
                            onClick = {
                                onAction(
                                    SettingsAction.SetGradingCriteria(
                                        state.gradingCriteria.copy(mode = GradingCriteriaMode.POINTS),
                                    ),
                                )
                            },
                            label = { Text(state.gradingModePoints) },
                            colors = settingsFilterChipColors(),
                        )
                    }
                    Text(
                        text = if (state.gradingCriteria.mode == GradingCriteriaMode.PERCENT) {
                            state.gradingPercentIntro
                        } else {
                            state.gradingPointsIntro
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (row in state.gradingCriteria.rows.sortedByDescending { it.grade }) {
                        val grade = row.grade
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Оценка $grade",
                                modifier = Modifier.width(88.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (state.gradingCriteria.mode == GradingCriteriaMode.PERCENT) {
                                GradingRangeFieldsInt(
                                    modifier = Modifier.weight(1f),
                                    min = row.percentMin,
                                    max = row.percentMax,
                                    fromLabel = state.gradingRowFrom,
                                    toLabel = state.gradingRowTo,
                                    onMin = { v ->
                                        val cur = state.gradingCriteria
                                        val r = cur.rows.first { it.grade == grade }
                                        onAction(
                                            SettingsAction.SetGradingCriteria(
                                                cur.withPercentRow(grade, v, r.percentMax),
                                            ),
                                        )
                                    },
                                    onMax = { v ->
                                        val cur = state.gradingCriteria
                                        val r = cur.rows.first { it.grade == grade }
                                        onAction(
                                            SettingsAction.SetGradingCriteria(
                                                cur.withPercentRow(grade, r.percentMin, v),
                                            ),
                                        )
                                    },
                                )
                            } else {
                                GradingRangeFieldsDouble(
                                    modifier = Modifier.weight(1f),
                                    min = row.pointsMin,
                                    max = row.pointsMax,
                                    fromLabel = state.gradingRowFrom,
                                    toLabel = state.gradingRowTo,
                                    onMin = { v ->
                                        val cur = state.gradingCriteria
                                        val r = cur.rows.first { it.grade == grade }
                                        onAction(
                                            SettingsAction.SetGradingCriteria(
                                                cur.withPointsRow(grade, v, r.pointsMax),
                                            ),
                                        )
                                    },
                                    onMax = { v ->
                                        val cur = state.gradingCriteria
                                        val r = cur.rows.first { it.grade == grade }
                                        onAction(
                                            SettingsAction.SetGradingCriteria(
                                                cur.withPointsRow(grade, r.pointsMin, v),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SettingsBubbleIconButton(
                            onClick = { onAction(SettingsAction.ResetGradingCriteria) },
                            contentDescription = state.gradingResetText,
                        )
                    }
                }
            }
            SettingsShellCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.legalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.legalPrivacyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.legalPrivacyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(SettingsAction.OpenPrivacyPolicy) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun settingsOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorTextColor = MaterialTheme.colorScheme.error,
    focusedBorderColor = Scrapbook.Ink.copy(alpha = 0.45f),
    unfocusedBorderColor = Scrapbook.Ink.copy(alpha = 0.22f),
    disabledBorderColor = Scrapbook.Ink.copy(alpha = 0.12f),
    errorBorderColor = MaterialTheme.colorScheme.error,
    cursorColor = MaterialTheme.colorScheme.secondary,
    errorCursorColor = MaterialTheme.colorScheme.error,
    focusedLabelColor = Scrapbook.Ink.copy(alpha = 0.78f),
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorLabelColor = MaterialTheme.colorScheme.error,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorSupportingTextColor = MaterialTheme.colorScheme.error,
    focusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPrefixColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorPrefixColor = MaterialTheme.colorScheme.error,
    focusedSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSuffixColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorSuffixColor = MaterialTheme.colorScheme.error,
)

@Composable
private fun settingsFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Scrapbook.Ink.copy(alpha = 0.07f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = Scrapbook.Mint,
    disabledContainerColor = Scrapbook.Ink.copy(alpha = 0.04f),
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    selectedLabelColor = Scrapbook.Ink,
    selectedLeadingIconColor = Scrapbook.Ink,
    selectedTrailingIconColor = Scrapbook.Ink,
)

private fun GradingCriteriaConfig.withPercentRow(grade: Int, min: Int?, max: Int?): GradingCriteriaConfig =
    copy(rows = rows.map { if (it.grade == grade) it.copy(percentMin = min, percentMax = max) else it })

private fun GradingCriteriaConfig.withPointsRow(grade: Int, min: Double?, max: Double?): GradingCriteriaConfig =
    copy(rows = rows.map { if (it.grade == grade) it.copy(pointsMin = min, pointsMax = max) else it })

@Composable
private fun GradingRangeFieldsInt(
    modifier: Modifier = Modifier,
    min: Int?,
    max: Int?,
    fromLabel: String,
    toLabel: String,
    onMin: (Int?) -> Unit,
    onMax: (Int?) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = min?.toString() ?: "",
            onValueChange = { s ->
                val digits = s.filter { it.isDigit() }.take(3)
                onMin(if (digits.isEmpty()) null else digits.toIntOrNull())
            },
            modifier = Modifier.weight(1f),
            label = { Text(fromLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsOutlinedTextFieldColors(),
        )
        OutlinedTextField(
            value = max?.toString() ?: "",
            onValueChange = { s ->
                val digits = s.filter { it.isDigit() }.take(3)
                onMax(if (digits.isEmpty()) null else digits.toIntOrNull())
            },
            modifier = Modifier.weight(1f),
            label = { Text(toLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsOutlinedTextFieldColors(),
        )
    }
}

@Composable
private fun GradingRangeFieldsDouble(
    modifier: Modifier = Modifier,
    min: Double?,
    max: Double?,
    fromLabel: String,
    toLabel: String,
    onMin: (Double?) -> Unit,
    onMax: (Double?) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = formatPointsField(min),
            onValueChange = { s -> onMin(parseGradingPointsInput(s)) },
            modifier = Modifier.weight(1f),
            label = { Text(fromLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = settingsOutlinedTextFieldColors(),
        )
        OutlinedTextField(
            value = formatPointsField(max),
            onValueChange = { s -> onMax(parseGradingPointsInput(s)) },
            modifier = Modifier.weight(1f),
            label = { Text(toLabel, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = settingsOutlinedTextFieldColors(),
        )
    }
}

private fun formatPointsField(v: Double?): String = when {
    v == null -> ""
    v == floor(v) && !v.isNaN() && !v.isInfinite() -> v.toLong().toString()
    else -> v.toString()
}

private fun parseGradingPointsInput(s: String): Double? {
    val cleaned = buildString {
        var dot = false
        for (ch in s.replace(',', '.')) {
            when {
                ch.isDigit() -> append(ch)
                ch == '.' && !dot -> {
                    append('.')
                    dot = true
                }
            }
        }
    }
    if (cleaned.isEmpty() || cleaned == ".") return null
    return cleaned.toDoubleOrNull()
}

/** Подсказки-экскурсии: включить/выключить и пройти заново. Хранится в TourStore. */
@Composable
private fun HintsSettingsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.tscan.scanertestov.data.TourStore.isEnabled(context))
    }
    SettingsShellCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Подсказки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Показывать экскурсии по экранам при первом входе",
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                ShellBubbleSwitch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        com.tscan.scanertestov.data.TourStore.setEnabled(context, it)
                    },
                )
            }
            com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton(
                text = "Пройти экскурсию заново",
                onClick = {
                    com.tscan.scanertestov.data.TourStore.resetAll(context)
                    enabled = true
                    android.widget.Toast.makeText(
                        context,
                        "Готово: экскурсия начнётся в главном меню и на каждом экране.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }
}
