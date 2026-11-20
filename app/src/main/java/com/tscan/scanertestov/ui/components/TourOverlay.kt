package com.tscan.scanertestov.ui.components

/**
 * Описание: экскурсия по экрану «как в играх»: экран затемняется, нужный элемент подсвечивается,
 * рядом бумажка-подсказка с текстом и кнопками «Назад / Дальше / Пропустить».
 *
 * Как подключить к экрану:
 *  1) val tour = rememberTour("main_menu", steps)            — шаги с ключами элементов;
 *  2) Modifier.tourTarget(tour, "batch") на нужных элементах  — запоминает их место на экране;
 *  3) TourOverlay(tour) последним в корневом Box экрана.
 * Экскурсия показывается один раз (TourStore), если подсказки не выключены в Настройках.
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.data.TourStore
import com.tscan.scanertestov.ui.theme.Scrapbook
import kotlin.math.roundToInt

/** Шаг экскурсии: [targetKey] — какой элемент подсветить (null — подсказка по центру, без подсветки). */
data class TourStep(
    val targetKey: String?,
    val title: String,
    val text: String,
)

@Stable
class TourState internal constructor(
    val tourId: String,
    val steps: List<TourStep>,
    initiallyActive: Boolean,
) {
    internal val targets = mutableStateMapOf<String, Rect>()
    var active by mutableStateOf(initiallyActive)
        internal set
    var index by mutableIntStateOf(0)
        internal set

    /** Запустить экскурсию заново (например, по кнопке «?»). */
    fun start() {
        index = 0
        active = steps.isNotEmpty()
    }
}

@Composable
fun rememberTour(tourId: String, steps: List<TourStep>): TourState {
    val context = LocalContext.current
    return remember(tourId) {
        TourState(tourId, steps, initiallyActive = steps.isNotEmpty() && TourStore.shouldShow(context, tourId))
    }
}

/** Запоминает, где на экране элемент, чтобы экскурсия могла его подсветить. */
fun Modifier.tourTarget(tour: TourState, key: String): Modifier =
    this.onGloballyPositioned { tour.targets[key] = it.boundsInWindow() }

@Composable
fun TourOverlay(tour: TourState) {
    val context = LocalContext.current
    fun finish() {
        tour.active = false
        TourStore.markSeen(context, tour.tourId)
    }
    AnimatedVisibility(visible = tour.active, enter = fadeIn(), exit = fadeOut()) {
        val step = tour.steps.getOrNull(tour.index) ?: return@AnimatedVisibility
        var origin by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        val blocker = remember { MutableInteractionSource() }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                // Пока идёт экскурсия, нажатия мимо подсказки не уходят на экран.
                .clickable(interactionSource = blocker, indication = null) { },
        ) {
            val screenHpx = with(density) { maxHeight.toPx() }
            val target = step.targetKey?.let { tour.targets[it] }?.translate(-origin.x, -origin.y)
                // Элемент за пределами экрана (ниже прокрутки) — подсказка по центру, без подсветки.
                ?.takeIf { it.top >= 0f && it.top < screenHpx * 0.85f }
            val pad = with(density) { 8.dp.toPx() }
            val hole = target?.inflate(pad)?.let { r -> Rect(r.left, r.top, r.right, minOf(r.bottom, screenHpx * 0.6f)) }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    if (hole != null) addRoundRect(RoundRect(hole, CornerRadius(pad * 1.5f)))
                }
                drawPath(path, Color(0xFF1E1A14).copy(alpha = 0.66f))
                if (hole != null) {
                    drawRoundRect(
                        Color.White,
                        topLeft = hole.topLeft,
                        size = hole.size,
                        cornerRadius = CornerRadius(pad * 1.5f),
                        style = Stroke(width = with(density) { 3.dp.toPx() }),
                    )
                }
            }
            // Бумажка с подсказкой: под элементом, если целиком помещается, иначе над ним, иначе прижата к низу экрана.
            val screenH = with(density) { maxHeight.toPx() }
            val gap = with(density) { 14.dp.toPx() }
            var noteHeightPx by remember(tour.index) { mutableIntStateOf(0) }
            val h = noteHeightPx.toFloat()
            val y: Int = when {
                hole == null -> ((screenH - h) / 2f).roundToInt()
                hole.bottom + gap + h <= screenH -> (hole.bottom + gap).roundToInt()
                hole.top - gap - h >= 0f -> (hole.top - gap - h).roundToInt()
                else -> (screenH - h - gap).roundToInt().coerceAtLeast(0)
            }
            Column(
                modifier = Modifier
                    .offset { IntOffset(0, y) }
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { noteHeightPx = it.size.height }
                    .background(Scrapbook.Paper, RoundedCornerShape(4.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "ШАГ ${tour.index + 1} ИЗ ${tour.steps.size}",
                    color = Scrapbook.AccentInk,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    step.title,
                    fontFamily = Scrapbook.HandFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    color = Scrapbook.Ink,
                )
                Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Scrapbook.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    tour.steps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(5.dp)
                                .background(
                                    if (i <= tour.index) Scrapbook.Ink else Scrapbook.Ink.copy(alpha = 0.18f),
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (tour.index > 0) {
                        ShellBubbleOutlinedButton(
                            text = "Назад",
                            onClick = { tour.index -= 1 },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val last = tour.index == tour.steps.lastIndex
                    ShellBubblePrimaryButton(
                        text = if (last) "Понятно" else "Дальше",
                        onClick = { if (last) finish() else tour.index += 1 },
                        modifier = Modifier.weight(1.5f),
                        paletteIndex = 1,
                    )
                }
                if (tour.index < tour.steps.lastIndex) {
                    TextButton(onClick = { finish() }) {
                        Text("Пропустить экскурсию", color = Scrapbook.InkMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Экран с экскурсией из подсказок по центру (без подсветки элементов). [enabled] — показывать ли сейчас. */
@Composable
fun ScreenTour(tourId: String, steps: List<TourStep>, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (enabled) {
            TourOverlay(rememberTour(tourId, steps))
        }
    }
}

object ScreenTours {
    val BatchResults = listOf(
        TourStep(
            null,
            "Результаты",
            "Вверху — сколько пятёрок, четвёрок, троек и двоек. Ниже карточка каждой работы: фамилия, если она " +
                "прочиталась, вариант, сколько верно и оценка. Нажмите на карточку, чтобы увидеть бланк и ошибки по вопросам.",
        ),
        TourStep(
            null,
            "«Проверьте» на карточке",
            "Ученик исправлял ответы, и приложение не уверено, как их засчитать. Откройте работу и для каждого такого " +
                "кружка выберите «Отмечено» или «Пусто» — оценка пересчитается сразу. Пока вы не решили, " +
                "этот вопрос считается неверным.",
        ),
        TourStep(
            null,
            "Фамилия и отчёт",
            "Если фамилия не прочиталась, нажмите «Чья работа?» и выберите ученика из журнала. " +
                "Затем «В отчёт» — для одной работы или для всех сразу: только после этого работы появятся в «Отчётах».",
        ),
    )
    val Reports = listOf(
        TourStep(
            null,
            "Отчёт по тесту",
            "Отчёт заводится сам на каждый тест и собирает все классы. Вкладки: «Обзор» — главные цифры, " +
                "«Классы» — сравнение классов, «Варианты» — равноценны ли варианты, «Вопросы» — где ошибаются и что выбирают, " +
                "«Ученики» — список работ. Кнопки с названиями классов показывают только один класс.",
        ),
        TourStep(
            null,
            "Повторы и выгрузка",
            "Если один бланк сфотографировали дважды, приложение спросит, какую работу оставить; повторы в расчёт не идут. " +
                "Работу можно убрать из отчёта в «Учениках» (меню ⋮). «Выгрузить» — Excel или PDF, весь отчёт или один класс.",
        ),
    )
}
