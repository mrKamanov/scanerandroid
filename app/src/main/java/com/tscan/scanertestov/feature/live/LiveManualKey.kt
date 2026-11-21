package com.tscan.scanertestov.feature.live

/**
 * «Один бланк в камере» без теста: учитель задаёт число вопросов, ответов, колонок, вариантов и отмечает
 * правильные ответы прямо здесь. Ответы не становятся тестом и в отчёты не попадают; последние введённые
 * запоминаются, чтобы не вводить заново при следующем открытии камеры.
 */
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tscan.scanertestov.feature.batch.components.AnswerGridEditor
import com.tscan.scanertestov.feature.batch.components.StepperField
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaPreset
import com.tscan.scanertestov.feature.batch.criteria.BatchCriteriaVariantAnswers
import com.tscan.scanertestov.feature.tests.autoColumnsFor
import com.tscan.scanertestov.ui.components.ShellBubblePrimaryButton
import com.tscan.scanertestov.ui.theme.Scrapbook
import org.json.JSONArray
import org.json.JSONObject

/** Ключ, введённый вручную в «Одном бланке в камере». [keys] — по вариантам (1 вариант — один ключ). */
internal data class LiveManualKey(
    val questions: Int,
    val options: Int,
    val columns: Int,
    val keys: List<List<Set<Int>>>,
) {
    val variants: Int get() = keys.size

    /** Вопросы без правильного ответа или со всеми отмеченными (так ключ задать нельзя): номера с 1 по вариантам. */
    fun problems(): List<Pair<Int, Int>> = keys.flatMapIndexed { v, key ->
        (0 until questions).filter { q -> key.getOrNull(q).orEmpty().let { it.isEmpty() || it.size >= options } }
            .map { (v + 1) to (it + 1) }
    }

    fun toPreset(): BatchCriteriaPreset = BatchCriteriaPreset(
        // Своё id на каждую сетку: поправка меток и кэш геометрии камеры хранятся по id.
        id = "$PRESET_ID:$questions:$options:$columns",
        name = "ответы вручную",
        questionsCount = questions,
        choicesCount = options,
        columnCount = columns,
        correctAnswers = keys.first().map { it.sorted() },
        variantAnswerKeys = if (variants > 1) {
            keys.mapIndexed { i, k -> BatchCriteriaVariantAnswers(i + 1, k.map { it.sorted() }) }
        } else emptyList(),
    )

    /** Подогнать ключи под новые числа вопросов/ответов/вариантов, сохранив уже отмеченное. */
    fun resized(q: Int = questions, o: Int = options, cols: Int = columns, v: Int = variants): LiveManualKey = LiveManualKey(
        questions = q,
        options = o,
        columns = cols,
        keys = List(v) { vi ->
            val src = keys.getOrNull(vi) ?: keys.first()
            List(q) { qi -> src.getOrNull(qi).orEmpty().filter { it < o }.toSet() }
        },
    )

    companion object {
        const val PRESET_ID = "live_manual"
        private const val PREF = "manual_key"

        fun blank(q: Int = 10, o: Int = 4): LiveManualKey =
            LiveManualKey(q, o, autoColumnsFor(q, o), listOf(List(q) { emptySet() }))

        fun load(context: Context): LiveManualKey? = runCatching {
            val s = prefs(context).getString(PREF, null) ?: return null
            val o = JSONObject(s)
            val ks = o.getJSONArray("keys")
            LiveManualKey(
                questions = o.getInt("q").coerceIn(1, 35),
                options = o.getInt("o").coerceIn(2, 6),
                columns = o.getInt("cols").coerceIn(1, 2),
                keys = List(ks.length()) { v ->
                    val a = ks.getJSONArray(v)
                    List(a.length()) { q -> a.getJSONArray(q).let { s2 -> (0 until s2.length()).map { s2.getInt(it) }.toSet() } }
                },
            ).takeIf { it.keys.isNotEmpty() }?.let { it.resized() }
        }.getOrNull()

        fun save(context: Context, k: LiveManualKey) {
            val o = JSONObject()
                .put("q", k.questions).put("o", k.options).put("cols", k.columns)
                .put("keys", JSONArray().apply {
                    k.keys.forEach { key -> put(JSONArray().apply { key.forEach { s -> put(JSONArray(s.sorted())) } }) }
                })
            prefs(context).edit().putString(PREF, o.toString()).apply()
        }

        private fun prefs(context: Context) = context.getSharedPreferences("live_scan", Context.MODE_PRIVATE)
    }
}

/** Окно ввода ответов вручную: числа вопросов, ответов, колонок, вариантов и ключ. */
@Composable
internal fun LiveManualKeyDialog(
    initial: LiveManualKey,
    onDone: (LiveManualKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var k by remember { mutableStateOf(initial) }
    var tab by remember { mutableIntStateOf(0) }
    var tried by remember { mutableStateOf(false) }
    if (tab >= k.variants) tab = 0
    val problems = k.problems()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Scrapbook.Paper)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ответы вручную",
                    fontFamily = Scrapbook.HandFont, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium, color = Scrapbook.Ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Отмена", color = Scrapbook.InkMuted) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Проверка без теста: задайте, сколько на бланке вопросов и ответов, и отметьте правильные. " +
                        "Бланк должен быть нового вида — с четырьмя чёрными квадратами по углам — и с тем же числом " +
                        "вопросов, ответов и колонок. Ответы запомнятся до следующего раза, но тестом не станут и " +
                        "в отчёты не попадут.",
                    style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted,
                )
                StepperField(
                    "Сколько вопросов", k.questions, "от 1 до 35",
                    onMinus = { if (k.questions > 1) k.questions.minus(1).let { q -> k = k.resized(q = q, cols = autoColumnsFor(q, k.options)) } },
                    onPlus = { if (k.questions < 35) k.questions.plus(1).let { q -> k = k.resized(q = q, cols = autoColumnsFor(q, k.options)) } },
                    enabled = true,
                )
                StepperField(
                    "Сколько ответов в вопросе", k.options, "от 2 до 6",
                    onMinus = { if (k.options > 2) k = k.resized(o = k.options - 1, cols = autoColumnsFor(k.questions, k.options - 1)) },
                    onPlus = { if (k.options < 6) k = k.resized(o = k.options + 1, cols = autoColumnsFor(k.questions, k.options + 1)) },
                    enabled = true,
                )
                Text("Колонок на бланке", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("1 колонка", k.columns == 1) { k = k.resized(cols = 1) }
                    Pill("2 колонки", k.columns == 2) { k = k.resized(cols = 2) }
                }
                StepperField(
                    "Сколько вариантов", k.variants, "от 1 до 4",
                    onMinus = { if (k.variants > 1) k = k.resized(v = k.variants - 1) },
                    onPlus = { if (k.variants < 4) k = k.resized(v = k.variants + 1) },
                    enabled = true,
                )
                if (k.variants > 1) {
                    Text(
                        "Вариант камера прочитает по строке кружков «Вариант» на бланке и проверит по его ответам.",
                        style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted,
                    )
                }
                Text("Правильные ответы", style = MaterialTheme.typography.labelLarge)
                AnswerGridEditor(
                    questionsCount = k.questions,
                    choicesCount = k.options,
                    correctAnswers = k.keys[tab],
                    columnsCount = k.columns,
                    enabled = true,
                    tabsContent = if (k.variants > 1) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                (0 until k.variants).forEach { v -> Pill("Вариант ${v + 1}", tab == v) { tab = v } }
                            }
                        }
                    } else null,
                ) { q, c ->
                    val cur = k.keys[tab][q]
                    val next = if (c in cur) cur - c else cur + c
                    k = k.copy(keys = k.keys.mapIndexed { v, key -> if (v == tab) key.mapIndexed { i, s -> if (i == q) next else s } else key })
                }
            }
            if (tried && problems.isNotEmpty()) {
                // «вариант 1: № 1, 2, 3 … и ещё 7; вариант 2: № 4»
                val list = problems.groupBy({ it.first }, { it.second }).entries.joinToString("; ") { (v, qs) ->
                    val nums = qs.take(8).joinToString(", ") + if (qs.size > 8) " и ещё ${qs.size - 8}" else ""
                    if (k.variants > 1) "вариант $v: № $nums" else "№ $nums"
                }
                Text(
                    "В каждом вопросе отметьте правильный ответ (но не все кружки сразу). Не готово — $list.",
                    style = MaterialTheme.typography.bodySmall, color = Scrapbook.Ink,
                    modifier = Modifier.fillMaxWidth()
                        .background(Scrapbook.Rose.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                )
            }
            ShellBubblePrimaryButton(
                text = "Проверять с этими ответами",
                onClick = {
                    tried = true
                    if (problems.isEmpty()) onDone(k)
                },
            )
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Scrapbook.Ink,
        modifier = Modifier
            .background(if (selected) Scrapbook.Lemon else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, Scrapbook.Ink.copy(alpha = if (selected) 0.9f else 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
