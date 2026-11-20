package com.tscan.scanertestov.feature.batch

/**
 * Описание: «Чья работа?» — если имя на бланке не прочиталось (подписано от руки, бланк вставлен
 * в свой документ и т.п.), учитель смотрит на подпись с бланка и выбирает ученика из журнала
 * одним нажатием или вписывает вручную. Ничего не угадываем — значит, и не ошибаемся.
 */
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tscan.scanertestov.feature.journals.JournalStudent
import com.tscan.scanertestov.ui.components.SettingsShellCard
import com.tscan.scanertestov.ui.components.ShellBubbleOutlinedButton
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Картинка места для имени с бланка; грузится в фоне. */
@Composable
internal fun NameCropImage(path: String?, modifier: Modifier = Modifier) {
    if (path.isNullOrBlank()) return
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    val img = image ?: return
    Image(
        bitmap = img,
        contentDescription = "Подпись с бланка",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .background(Scrapbook.Paper, RoundedCornerShape(6.dp))
            .border(1.dp, Scrapbook.Ink.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
    )
}

@Composable
internal fun StudentPickerDialog(
    workTitle: String,
    nameCropPath: String?,
    roster: List<JournalStudent>,
    preferredClass: String?,
    alreadyAssigned: Set<String>,
    onPick: (surname: String, name: String, className: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f)) {
            SettingsShellCard(modifier = Modifier.fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Чья работа?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(workTitle, style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
                    NameCropImage(nameCropPath, Modifier.fillMaxWidth().height(70.dp))
                    if (roster.isEmpty()) {
                        Text(
                            "В «Журналах» пока нет учеников. Впишите фамилию и имя ниже или добавьте класс в «Журналы».",
                            style = MaterialTheme.typography.bodySmall,
                            color = Scrapbook.AccentInk,
                        )
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Поиск по фамилии") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = query.trim().lowercase(Locale.getDefault())
                    val list = remember(roster, q, preferredClass) {
                        roster
                            .filter { s -> q.isEmpty() || "${s.surname} ${s.name}".lowercase(Locale.getDefault()).contains(q) }
                            .sortedWith(
                                compareBy<JournalStudent>({ if (preferredClass != null && it.className == preferredClass) 0 else 1 })
                                    .thenBy { it.className }
                                    .thenBy { it.surname },
                            )
                    }
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list) { s ->
                            val key = "${s.surname} ${s.name}".trim()
                            val taken = key in alreadyAssigned
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (taken) Scrapbook.Ink.copy(alpha = 0.05f) else Scrapbook.Mint.copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onPick(s.surname, s.name, s.className) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(key, fontWeight = FontWeight.SemiBold, color = Scrapbook.Ink)
                                    Text(
                                        if (taken) "${s.className} · уже есть работа с этим именем" else s.className,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Scrapbook.InkMuted,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        label = { Text("Или впишите: Фамилия Имя") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ShellBubbleOutlinedButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.weight(1f))
                        ShellBubblePrimaryButton(
                            text = "Сохранить",
                            enabled = manual.isNotBlank(),
                            onClick = {
                                val parts = manual.trim().split(Regex("\\s+"), limit = 2)
                                onPick(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, preferredClass)
                            },
                            modifier = Modifier.weight(1f),
                            paletteIndex = 1,
                        )
                    }
                }
            }
        }
    }
}
