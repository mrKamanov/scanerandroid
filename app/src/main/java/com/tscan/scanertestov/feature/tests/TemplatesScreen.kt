package com.tscan.scanertestov.feature.tests

/**
 * Описание: «Шаблоны бланков» — свои шаблоны: изменить (мастер без шага «Ответы»), удалить, создать новый.
 * Готовые шаблоны зашиты в приложение: их нельзя удалить, но можно взять за основу нового.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.tscan.scanertestov.feature.blankeditor.BlankBuiltInTemplates
import com.tscan.scanertestov.feature.blankeditor.BlankTemplate
import com.tscan.scanertestov.feature.blankeditor.BlankTemplateStore
import com.tscan.scanertestov.navigation.AppDestinations
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook

/** Перечитывать данные каждый раз, когда экран снова виден (вернулись из мастера). */
@Composable
internal fun OnResume(action: () -> Unit) {
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) action() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
fun TemplatesRoute(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext
    var templates by remember { mutableStateOf(BlankTemplateStore.load(app)) }
    var toDelete by remember { mutableStateOf<BlankTemplate?>(null) }
    OnResume { templates = BlankTemplateStore.load(app) }

    ScreenScaffold(onBack = { navController.popBackStack() }, title = "Шаблоны") { padding ->
        ScreenContentColumn(padding = padding, verticalScroll = true) {
            Text(
                "Шаблон — заготовка бланка без правильных ответов: число вопросов, расположение на листе, надписи, поля " +
                    "и критерии оценки. Его выбирают первым шагом в «Новом тесте». «Изменить» открывает шаги «Вопросы», " +
                    "«Бланк на листе» и «Критерии». Тесты, уже сделанные по шаблону, не меняются.",
                style = MaterialTheme.typography.bodyMedium,
                color = Scrapbook.InkMuted,
            )
            ShellBubblePrimaryButton(
                text = "+ Новый шаблон",
                onClick = {
                    PendingTestLaunch.openTemplateEditor(null)
                    navController.navigate(AppDestinations.TEST_EDIT)
                },
                paletteIndex = 6,
            )
            if (templates.isEmpty()) {
                SettingsShellCard {
                    Text(
                        "Своих шаблонов пока нет. Их удобно сохранять на последнем шаге «Нового теста» галочкой " +
                            "«Сохранить оформление как шаблон» — или создать здесь.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            templates.forEach { t ->
                SettingsShellCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SheetMini(t.design, Modifier.width(56.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("★ ${t.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(t.summary, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                        }
                        Column {
                            TextButton(onClick = {
                                PendingTestLaunch.openTemplateEditor(t.id)
                                navController.navigate(AppDestinations.TEST_EDIT)
                            }) { Text("Изменить", color = Scrapbook.Ink, fontWeight = FontWeight.SemiBold) }
                            TextButton(onClick = { toDelete = t }) { Text("Удалить", color = Scrapbook.AccentInk) }
                        }
                    }
                }
            }
            Text(
                "Готовые шаблоны («" + BlankBuiltInTemplates.all.joinToString("», «") { it.name } + "») встроены в приложение: " +
                    "их нельзя удалить, но можно выбрать за основу в «+ Новый шаблон».",
                style = MaterialTheme.typography.bodySmall,
                color = Scrapbook.InkMuted,
            )
        }
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить шаблон?") },
            text = { Text("«${t.name}» будет удалён. Тесты, сделанные по нему, останутся как есть.") },
            confirmButton = {
                TextButton(onClick = {
                    BlankTemplateStore.delete(app, t.id)
                    templates = BlankTemplateStore.load(app)
                    toDelete = null
                }) { Text("Удалить", color = Scrapbook.AccentInk) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}
