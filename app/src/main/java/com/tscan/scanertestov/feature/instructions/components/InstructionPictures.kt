package com.tscan.scanertestov.feature.instructions.components

/**
 * Описание: карточки-примеры кружков в инструкции — вырезка из настоящей проверки (или правильный кружок,
 * нарисованный кодом) и подписи «что не так» / «как надо». Каждая карточка — на всю ширину (картинка слева,
 * текст справа), поэтому не разъезжается на узком экране и с крупным шрифтом.
 */
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.instructions.GoodBubble
import com.tscan.scanertestov.feature.instructions.InstructionPicture
import com.tscan.scanertestov.ui.theme.Scrapbook

@Composable
internal fun InstructionPictureCard(p: InstructionPicture) {
    val good = p.good != null
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Scrapbook.Paper)
            .border(1.dp, Scrapbook.Ink.copy(alpha = 0.15f), shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, Scrapbook.Ink.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (p.asset != null) AssetImage(p.asset) else p.good?.let { GoodBubbleImage(it) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (good) "Так правильно" else "Так не надо",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (good) Color(0xFF1E8E4E) else Scrapbook.AccentInk,
            )
            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Scrapbook.Ink)
            Text(p.problem, style = MaterialTheme.typography.bodySmall, color = Scrapbook.Ink)
            p.fix?.let {
                Text("Как надо: $it", style = MaterialTheme.typography.bodySmall, color = Scrapbook.InkMuted)
            }
        }
    }
}

@Composable
private fun AssetImage(path: String) {
    val context = LocalContext.current
    val bmp = remember(path) {
        runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Правильные кружки рисуем кодом: плотная закраска ручкой, исправление жирным крестиком, пустой. */
@Composable
private fun GoodBubbleImage(kind: GoodBubble) {
    val ink = Color(0xFF26338F)
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.86f
        drawCircle(Color(0xFF2E3440), r, c, style = Stroke(r * 0.12f))
        when (kind) {
            GoodBubble.Filled -> drawCircle(ink, r * 0.9f, c)
            GoodBubble.CrossedOut -> {
                drawCircle(ink, r * 0.9f, c)
                val d = r * 1.05f
                drawLine(Color(0xFF111111), Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), r * 0.22f, StrokeCap.Round)
                drawLine(Color(0xFF111111), Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), r * 0.22f, StrokeCap.Round)
            }
            GoodBubble.Empty -> Unit
        }
    }
}
