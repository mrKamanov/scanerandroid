package com.tscan.scanertestov.feature.mainmenu

/**
 * Описание: главное меню в стиле «Скрапбук» — разделы как бумажки, приклеенные скотчем.
 * Раскладка задана в координатах макета 390×844 и масштабируется под ширину экрана.
 */
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tscan.scanertestov.ui.components.TourOverlay
import com.tscan.scanertestov.ui.components.TourStep
import com.tscan.scanertestov.ui.components.rememberTour
import com.tscan.scanertestov.ui.components.tourTarget
import com.tscan.scanertestov.ui.theme.Scrapbook

/** Бумажка на доске: координаты и размеры — в точках макета шириной 390. */
private data class ScrapNote(
    val title: String,
    val subtitle: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float,
    val paper: Color,
    val tape: Color,
    val titleSize: Float,
    val action: MainMenuAction,
)

private val notes = listOf(
    ScrapNote("Проверить класс", "фото → оценки", 26f, 200f, 220f, 150f, -2f, Scrapbook.Mint, Scrapbook.TapeWhite, 44f, MainMenuAction.OpenBatchProcessing),
    ScrapNote("Один бланк", "в камере", 256f, 220f, 112f, 120f, 5f, Scrapbook.Peach, Scrapbook.TapeBlue, 32f, MainMenuAction.OpenRealtimeScan),
    ScrapNote("Тесты", "ответы и бланки", 36f, 380f, 140f, 120f, 4f, Scrapbook.Lemon, Scrapbook.TapePink, 38f, MainMenuAction.OpenBlankEditor),
    ScrapNote("Журналы", "ученики", 196f, 372f, 168f, 126f, -4f, Scrapbook.Lavender, Scrapbook.TapeYellow, 38f, MainMenuAction.OpenJournals),
    ScrapNote("Отчёты", "итоги", 30f, 532f, 170f, 120f, -3f, Scrapbook.Rose, Scrapbook.TapeWhite, 38f, MainMenuAction.OpenReports),
    ScrapNote("Инструкция", "как начать", 216f, 530f, 146f, 120f, 3f, Scrapbook.Paper, Scrapbook.TapeMint, 32f, MainMenuAction.OpenInstructions),
    ScrapNote("Настройки", "под себя", 120f, 690f, 160f, 96f, -1f, Scrapbook.Sky, Scrapbook.TapeYellow, 32f, MainMenuAction.OpenSettings),
)

/** Экскурсия по главному меню при первом запуске. Ключи — названия бумажек. */
private val MainMenuTourSteps = listOf(
    TourStep(
        null,
        "С чего начать",
        "Порядок работы такой: вы делаете бланк под свой тест и печатаете его, ученики закрашивают кружки, " +
            "потом вы фотографируете бланки и получаете оценки. Всё считается прямо в телефоне, без интернета — " +
            "фотографии и результаты никуда не отправляются.",
    ),
    TourStep(
        "Тесты",
        "Ваши тесты",
        "Тест — это бланк и правильные ответы вместе. «Новый тест» ведёт по шести этапам: название, бланк, вопросы " +
            "и кружки, правильные ответы, критерии оценки, предпросмотр и печать. Кнопка «Бланк» на карточке открывает " +
            "печать (PDF, картинка или именные бланки на класс), а «Проверить» — проверку класса с уже подставленным ключом.",
    ),
    TourStep(
        "Проверить класс",
        "Проверка после контрольной",
        "Сфотографируйте все бланки подряд камерой приложения или выберите готовые фото из галереи, " +
            "отметьте правильные ответы для каждого варианта и нажмите «Проверить работы». " +
            "Для каждой работы будет оценка, число верных ответов и список вопросов с ошибками. " +
            "Ключ можно сохранить шаблоном, чтобы в параллельном классе не вводить его заново.",
    ),
    TourStep(
        "Один бланк",
        "Одна работа сразу",
        "Проверка прямо в камере: выберите тест и наведите камеру на бланк — через секунду на листе появятся " +
            "пометки «верно / ошибка» и оценка, нажимать ничего не нужно. Удобно, когда ученик сдал работу и ждёт " +
            "результат у стола.",
    ),
    TourStep(
        "Журналы",
        "Списки классов",
        "Классы и ученики. Можно ввести вручную или загрузить из Excel — шаблон с правилами там же, внизу. " +
            "По журналу печатаются именные бланки: на них закрашен номер ученика, и при проверке фамилия " +
            "подставляется сама.",
    ),
    TourStep(
        "Отчёты",
        "Итоги по классу",
        "Сюда попадают работы, которые вы отправили кнопкой «В отчёт» на экране результатов. " +
            "Здесь средний балл, успеваемость и качество, вопросы, на которые ответило меньше всего учеников, " +
            "и совпадающие работы. Отчёт сохраняется в Excel — оттуда удобно переносить оценки в электронный журнал.",
    ),
    TourStep(
        "Инструкция",
        "Если что-то не получается",
        "Здесь подробно: как снимать, чтобы бланк узнавался с первого раза, что делать с зачёркнутыми ответами, " +
            "почему не прочиталась фамилия. Раздел «Как заполнять бланк, чтобы проверка не ошибалась» стоит один раз показать классу перед первой " +
            "контрольной — аккуратно закрашенные бланки почти не дают ошибок.",
    ),
    TourStep(
        "Настройки",
        "Границы оценок",
        "По умолчанию «5» ставится от 90% верных ответов, «4» — от 70%, «3» — от 50%. " +
            "Если в вашей школе другие нормы, поменяйте их здесь один раз — все проверки будут считаться по ним. " +
            "Здесь же можно выключить эти подсказки или пройти экскурсию заново.",
    ),
)

private const val DESIGN_WIDTH = 390f
private const val DESIGN_HEIGHT = 810f

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainMenuScreen(
    state: MainMenuState = MainMenuState(),
    onAction: (MainMenuAction) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrapbook.Board)
            .dottedPaper(),
    ) {
        // Доска рассчитана на телефон: на планшете масштаб — по меньшей стороне (вся доска на экране), доска по центру.
        val k = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT).coerceAtLeast(maxWidth.value.coerceAtMost(DESIGN_WIDTH) / DESIGN_WIDTH)
        fun d(v: Float): Dp = (v * k).dp
        val tour = rememberTour("main_menu", MainMenuTourSteps)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Меню — «доска» с бумажками фиксированного размера (масштаб — по ширине экрана). Крупный шрифт системы
            // не увеличивает надписи сверх их места, иначе подписи обрезаются; надписи и так крупные.
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    baseDensity.density,
                    baseDensity.fontScale.coerceAtMost(1f),
                ),
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .width(d(DESIGN_WIDTH))
                    .height(d(DESIGN_HEIGHT)),
            ) {
                TitleNote(modifier = Modifier.offset(d(30f), d(60f)), k = k)
                BlankScrapDecor(modifier = Modifier.offset(d(296f), d(70f)), k = k)
                notes.forEachIndexed { index, note ->
                    PaperNote(
                        note = note,
                        index = index,
                        k = k,
                        modifier = Modifier.offset(d(note.x), d(note.y)).tourTarget(tour, note.title),
                        onClick = { onAction(note.action) },
                    )
                }
                PencilDecor(modifier = Modifier.offset(d(14f), d(728f)), k = k)
                GradeFiveDecor(modifier = Modifier.offset(d(298f), d(694f)), k = k)
            }
            }
        }
        TourOverlay(tour)
    }
}

/** Мелкие точки по фону — фактура картона. */
private fun Modifier.dottedPaper(): Modifier = this.drawBehind {
    val step = 6.dp.toPx()
    val r = 0.6.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(Scrapbook.BoardDot, radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

@Composable
private fun TitleNote(modifier: Modifier, k: Float) {
    Box(modifier = modifier.rotate(-3f)) {
        Box(
            modifier = Modifier
                .shadow((4 * k).dp, RoundedCornerShape(2.dp))
                .background(Scrapbook.Paper, RoundedCornerShape(2.dp))
                .padding(horizontal = (22 * k).dp, vertical = (12 * k).dp),
        ) {
            Text(
                text = "Сканер тестов",
                fontFamily = Scrapbook.HandFont,
                fontWeight = FontWeight.Bold,
                fontSize = (54 * k).sp,
                lineHeight = (52 * k).sp,
                color = Scrapbook.Ink,
            )
        }
        Tape(
            color = Scrapbook.TapeYellow,
            width = (80 * k).dp,
            height = (22 * k).dp,
            rotation = 4f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-12 * k).dp),
        )
    }
}

/** Украшение: обрывок бланка со скотчем — несколько кружков закрашены «синими чернилами», рядом красная галочка. */
@Composable
private fun BlankScrapDecor(modifier: Modifier, k: Float) {
    val ink = Color(0xFF2A3F8A)
    val red = Color(0xFFD23B2A)
    val filled = setOf(1, 3, 8, 10)
    Box(modifier = modifier.rotate(9f)) {
        Canvas(
            modifier = Modifier
                .size((66 * k).dp, (96 * k).dp)
                .shadow((3 * k).dp, RoundedCornerShape(1.dp))
                .background(Scrapbook.Paper),
        ) {
            val u = size.width / 66f
            val fx = 8 * u
            val fy = 12 * u
            val fw = 50 * u
            val fh = 62 * u
            drawRect(
                Scrapbook.Ink,
                topLeft = Offset(fx, fy),
                size = androidx.compose.ui.geometry.Size(fw, fh),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3 * u),
            )
            val r = 5.5f * u
            for (row in 0 until 4) {
                for (col in 0 until 3) {
                    val c = Offset(fx + 12 * u + col * 13 * u, fy + 11 * u + row * 13.5f * u)
                    if (row * 3 + col in filled) {
                        drawCircle(ink, radius = r, center = c)
                    } else {
                        drawCircle(
                            Scrapbook.Ink,
                            radius = r,
                            center = c,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * u),
                        )
                    }
                }
            }
            val check = androidx.compose.ui.graphics.Path().apply {
                moveTo(40 * u, 84 * u)
                lineTo(48 * u, 92 * u)
                lineTo(64 * u, 76 * u)
            }
            drawPath(
                check,
                red,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.5f * u,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
        Tape(
            color = Scrapbook.TapeBlue,
            width = (34 * k).dp,
            height = (14 * k).dp,
            rotation = -4f,
            modifier = Modifier.offset(x = (16 * k).dp, y = (-8 * k).dp),
        )
    }
}

/** Украшение: жёлтый карандаш наискосок в углу доски. */
@Composable
private fun PencilDecor(modifier: Modifier, k: Float) {
    Canvas(modifier = modifier.size((92 * k).dp, (44 * k).dp).rotate(-24f)) {
        val u = size.width / 86f
        val cy = size.height / 2f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2 * u)
        fun rect(x: Float, w: Float, color: Color) {
            drawRect(color, Offset(x * u, cy - 7 * u), androidx.compose.ui.geometry.Size(w * u, 14 * u))
            drawRect(Scrapbook.Ink, Offset(x * u, cy - 7 * u), androidx.compose.ui.geometry.Size(w * u, 14 * u), style = stroke)
        }
        rect(14f, 56f, Color(0xFFFFD65A))
        rect(70f, 7f, Color(0xFFC9C2B4))
        rect(77f, 8f, Scrapbook.Rose)
        val wood = androidx.compose.ui.graphics.Path().apply {
            moveTo(14 * u, cy - 7 * u); lineTo(2 * u, cy); lineTo(14 * u, cy + 7 * u); close()
        }
        drawPath(wood, Color(0xFFF3D9B5))
        drawPath(wood, Scrapbook.Ink, style = stroke)
        val lead = androidx.compose.ui.graphics.Path().apply {
            moveTo(6 * u, cy - 2.4f * u); lineTo(2 * u, cy); lineTo(6 * u, cy + 2.4f * u); close()
        }
        drawPath(lead, Scrapbook.Ink)
    }
}

/** Украшение: красная учительская «5» с подчёркиванием. */
@Composable
private fun GradeFiveDecor(modifier: Modifier, k: Float) {
    val red = Color(0xFFD23B2A)
    Column(
        modifier = modifier
            .rotate(-8f)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // У рукописного шрифта «5» похожа на крючок — берём основной шрифт, жирный.
        Text(
            text = "5",
            fontFamily = Scrapbook.BodyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (52 * k).sp,
            lineHeight = (52 * k).sp,
            color = red,
            modifier = Modifier.offset(y = (6 * k).dp),
        )
        Canvas(modifier = Modifier.size((54 * k).dp, (12 * k).dp)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.05f, size.height * 0.7f)
                cubicTo(
                    size.width * 0.3f, size.height * 0.15f,
                    size.width * 0.63f, size.height * 0.15f,
                    size.width * 0.95f, size.height * 0.5f,
                )
            }
            drawPath(
                p,
                red,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3 * k * density,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

@Composable
private fun PaperNote(
    note: ScrapNote,
    index: Int,
    k: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Нажатие: бумажка чуть «прижимается» к доске и выпрямляется.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "notePress",
    )
    val pressRotation by animateFloatAsState(
        targetValue = if (pressed) note.rotation * 0.3f else note.rotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "noteTilt",
    )
    // Появление: бумажки «приклеиваются» по очереди.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        modifier = modifier
            .size((note.w * k).dp, (note.h * k).dp)
            .graphicsLayer {
                rotationZ = pressRotation
                val s = pressScale * (0.85f + 0.15f * appear.value)
                scaleX = s
                scaleY = s
                alpha = appear.value
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (pressed) (2 * k).dp else (6 * k).dp, RoundedCornerShape(2.dp))
                .background(note.paper, RoundedCornerShape(2.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = note.title,
                    onClick = onClick,
                )
                .padding((14 * k).dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = note.title,
                fontFamily = Scrapbook.HandFont,
                fontWeight = FontWeight.Bold,
                fontSize = (note.titleSize * k).sp,
                lineHeight = (note.titleSize * 0.95f * k).sp,
                color = Scrapbook.Ink,
            )
            Text(
                text = note.subtitle,
                fontFamily = Scrapbook.BodyFont,
                fontWeight = FontWeight.Medium,
                fontSize = (11 * k).sp,
                color = Scrapbook.Ink,
            )
        }
        Tape(
            color = note.tape,
            width = (46 * k).dp,
            height = (18 * k).dp,
            rotation = -6f,
            modifier = Modifier.offset(x = (14 * k).dp, y = (-10 * k).dp),
        )
    }
}

@Composable
private fun Tape(color: Color, width: Dp, height: Dp, rotation: Float, modifier: Modifier) {
    Canvas(modifier = modifier.size(width, height).rotate(rotation)) {
        drawRect(color)
    }
}
