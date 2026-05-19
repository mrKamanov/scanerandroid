package com.tscan.scanertestov.ui.components

/**
 * Описание: нижняя панель быстрого доступа к основным разделам приложения.
 */
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.R
import com.tscan.scanertestov.feature.mainmenu.components.mainMenuBubbleGradient
import com.tscan.scanertestov.feature.realtime.ui.RealtimeBubbleIconButton
import com.tscan.scanertestov.navigation.AppDestinations
import com.tscan.scanertestov.ui.scanShellBackdrop

private data class BottomBarItem(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int?,
    val paletteIndex: Int,
)

private val BubbleBarMutedTop = Color(0xFF5C6F86)
private val BubbleBarMutedBottom = Color(0xFF3D5268)

private val bottomBarItems = listOf(
    BottomBarItem(AppDestinations.MAIN_MENU, "Главная", iconRes = null, paletteIndex = 3),
    BottomBarItem(AppDestinations.REALTIME_SCAN, "Скан", iconRes = R.drawable.ic_main_menu_scan, paletteIndex = 0),
    BottomBarItem(AppDestinations.BATCH_PROCESSING, "Пакет", iconRes = R.drawable.ic_main_menu_batch, paletteIndex = 1),
    BottomBarItem(AppDestinations.JOURNALS, "Журналы", iconRes = R.drawable.ic_main_menu_journals, paletteIndex = 6),
    BottomBarItem(AppDestinations.REPORTS, "Отчёты", iconRes = R.drawable.ic_main_menu_reports, paletteIndex = 5),
)

@Composable
fun AppBottomNavigationBar(
    currentRoute: String?,
    onItemClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scanShellBackdrop(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomBarItems.forEach { item ->
                val selected = currentRoute == item.route
                val (top, bottom) = if (selected) {
                    mainMenuBubbleGradient(item.paletteIndex)
                } else {
                    BubbleBarMutedTop to BubbleBarMutedBottom
                }
                val borderAlpha = if (selected) 0.52f else 0.36f
                val interactionSource = remember(item.route) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (pressed) 0.93f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "bottomBarBubblePress",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        },
                    ) {
                        RealtimeBubbleIconButton(
                            onClick = { onItemClick(item.route) },
                            contentDescription = "${item.label}. ${if (selected) "Текущий раздел" else "Перейти"}",
                            topColor = top,
                            bottomColor = bottom,
                            borderColor = Color.White.copy(alpha = borderAlpha),
                            size = 50.dp,
                            interactionSource = interactionSource,
                        ) {
                            if (item.iconRes == null) {
                                Icon(
                                    imageVector = Icons.Filled.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = Color.White,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            Color(0xFFE8F2FA)
                        } else {
                            Color(0xFFB0C4D6)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
