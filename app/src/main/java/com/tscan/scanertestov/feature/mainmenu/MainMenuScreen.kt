package com.tscan.scanertestov.feature.mainmenu

/**
 * Описание: главное меню — декор, герой «Инструкция» и сетка пунктов.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tscan.scanertestov.feature.mainmenu.components.MainMenuBubbleCell
import com.tscan.scanertestov.feature.mainmenu.components.MainMenuBubbleSheetDecor
import com.tscan.scanertestov.feature.mainmenu.components.MainMenuHeader
import com.tscan.scanertestov.feature.mainmenu.components.MainMenuHeroBubble
import com.tscan.scanertestov.feature.mainmenu.components.StaggeredAppear
import com.tscan.scanertestov.ui.scanShellBackdrop

@Composable
fun MainMenuScreen(
    state: MainMenuState = MainMenuState(),
    onAction: (MainMenuAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .scanShellBackdrop()
    ) {
        MainMenuBubbleSheetDecor(modifier = Modifier.fillMaxSize())

        val items = state.menuItems
        if (items.size < 7) return@Box

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                StaggeredAppear(staggerIndex = 0) {
                    MainMenuHeader()
                }
            }
            item {
                StaggeredAppear(staggerIndex = 1) {
                    MainMenuHeroBubble(
                        item = items[0],
                        paletteIndex = 3,
                        onClick = { onAction(items[0].action) },
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 2) {
                            MainMenuBubbleCell(
                                item = items[1],
                                paletteIndex = 0,
                                onClick = { onAction(items[1].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 3) {
                            MainMenuBubbleCell(
                                item = items[2],
                                paletteIndex = 1,
                                onClick = { onAction(items[2].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 4) {
                            MainMenuBubbleCell(
                                item = items[3],
                                paletteIndex = 2,
                                onClick = { onAction(items[3].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 5) {
                            MainMenuBubbleCell(
                                item = items[4],
                                paletteIndex = 4,
                                onClick = { onAction(items[4].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 6) {
                            MainMenuBubbleCell(
                                item = items[5],
                                paletteIndex = 5,
                                onClick = { onAction(items[5].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredAppear(staggerIndex = 7) {
                            MainMenuBubbleCell(
                                item = items[6],
                                paletteIndex = 6,
                                onClick = { onAction(items[6].action) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
