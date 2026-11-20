package com.tscan.scanertestov.feature.tests

/**
 * Описание: «Мои тесты» — список тестов (бланк + правильные ответы): «Бланк» (сохранить бланк теста в файл),
 * «Проверить», «Изменить» (мастер теста), «Удалить». Создание и правка теста — в TestWizard.kt.
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaRepository
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaVariantAnswers
import com.tscan.scanertestov.navigation.AppDestinations
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.isFilled
import com.tscan.scanertestov.ui.components.shortSummary
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// region Список тестов

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestsRoute(navController: NavHostController) {
    val context = LocalContext.current
    val repo = remember { BatchCriteriaRepository(context.applicationContext) }
    var tests by remember { mutableStateOf(repo.getAll()) }
    OnResume { tests = repo.getAll() }
    var toDelete by remember { mutableStateOf<BatchCriteriaPreset?>(null) }

    ScreenScaffold(onBack = { navController.popBackStack() }, title = "Мои тесты") { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            Text(
                "Тест — это бланк и правильные ответы вместе. Создайте его один раз: отсюда же делается бланк, " +
                    "а при проверке класса ключ подставляется сам.",
                style = MaterialTheme.typography.bodyMedium,
                color = Scrapbook.InkMuted,
            )
            ShellBubblePrimaryButton(
                text = "Новый тест",
                onClick = {
                    PendingTestLaunch.openEditor(null)
                    navController.navigate(AppDestinations.TEST_EDIT)
                },
                paletteIndex = 6,
            )
            if (tests.isEmpty()) {
                SettingsShellCard {
                    Text(
                        "Тестов пока нет. Нажмите «Новый тест» — по шагам выберете шаблон, число вопросов, отметите правильные " +
                            "ответы, настроите бланк на листе и критерии оценки.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            tests.forEach { t ->
                SettingsShellCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("${t.questionsCount} ${plural(t.questionsCount, "вопрос", "вопроса", "вопросов")}")
                            Chip("${t.choicesCount} ${plural(t.choicesCount, "кружок", "кружка", "кружков")} в строке")
                            Chip("${t.testVariantsCount} ${plural(t.testVariantsCount, "вариант", "варианта", "вариантов")}")
                            if (t.columnCount == 2) Chip("2 колонки")
                        }
                        if (t.isKeyComplete) {
                            Text("✓ Правильные ответы заданы", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2F6B4F), fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(
                                "Правильные ответы заданы не во всех вопросах. Задайте их здесь («Изменить») или на экране проверки.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Scrapbook.Ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Scrapbook.Lemon, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            ShellBubbleOutlinedButton(
                                text = "Бланк",
                                onClick = {
                                    // Печать — последний этап мастера: лист ровно как на бумаге, без второго оформления.
                                    PendingTestLaunch.openEditor(t.id, TestWizardStep.Preview)
                                    navController.navigate(AppDestinations.TEST_EDIT)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ShellBubblePrimaryButton(
                                text = "Проверить",
                                onClick = {
                                    PendingTestLaunch.openBatch(t.id)
                                    navController.navigate(AppDestinations.BATCH_PROCESSING)
                                },
                                modifier = Modifier.weight(1f),
                                paletteIndex = 1,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TextButton(onClick = {
                                PendingTestLaunch.openEditor(t.id)
                                navController.navigate(AppDestinations.TEST_EDIT)
                            }) { Text("Изменить", color = Scrapbook.Ink) }
                            TextButton(onClick = { toDelete = t }) { Text("Удалить", color = Scrapbook.AccentInk) }
                        }
                    }
                }
            }
            TextButton(onClick = { navController.navigate(AppDestinations.TEMPLATES) }) {
                Text("Шаблоны бланков", color = Scrapbook.Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить тест?") },
            text = { Text("«${t.name}» и его правильные ответы будут удалены. Напечатанные бланки и проверенные работы останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(t.id)
                    tests = repo.getAll()
                    toDelete = null
                }) { Text("Удалить", color = Scrapbook.AccentInk) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Scrapbook.Ink,
        modifier = Modifier
            .background(Scrapbook.Board.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

// endregion

// region Создание и правка теста
