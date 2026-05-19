package com.tscan.scanertestov.feature.instructions

/**
 * Описание: экран инструкций — оглавление, разделы и навигация между ними.
 */
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tscan.scanertestov.feature.instructions.components.InstructionsTopicDetailContent
import com.tscan.scanertestov.feature.instructions.components.InstructionsTopicNavigationBar
import com.tscan.scanertestov.feature.instructions.components.InstructionsTopicRow
import com.tscan.scanertestov.feature.instructions.components.InstructionsWelcomeCard
import com.tscan.scanertestov.ui.components.ScreenContentColumn
import com.tscan.scanertestov.ui.components.ScreenScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    state: InstructionsState = InstructionsState(),
    onAction: (InstructionsAction) -> Unit,
) {
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTopic = selectedTopicId?.let { id -> InstructionsCatalog.findTopic(id) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTopicId) {
        scrollState.scrollTo(0)
    }

    fun openTopic(id: String) {
        selectedTopicId = id
        onAction(InstructionsAction.OpenTopic(id))
    }

    ScreenScaffold(
        onBack = {
            if (selectedTopicId != null) {
                selectedTopicId = null
                onAction(InstructionsAction.BackToTopicList)
            } else {
                onAction(InstructionsAction.Back)
            }
        },
        showBubbleSheetDecor = true,
    ) { padding ->
        ScreenContentColumn(
            padding = padding,
            verticalScroll = true,
            scrollState = scrollState,
        ) {
            when (val topic = selectedTopic) {
                null -> {
                    InstructionsWelcomeCard()
                    state.topics.forEachIndexed { index, item ->
                        InstructionsTopicRow(
                            index = index + 1,
                            topic = item,
                            onClick = { openTopic(item.id) },
                        )
                    }
                }
                else -> {
                    val index = InstructionsCatalog.topicIndex(topic.id)
                    InstructionsTopicDetailContent(topic = topic)
                    InstructionsTopicNavigationBar(
                        topicIndex = index,
                        topicsCount = state.topics.size,
                        previousTopic = InstructionsCatalog.previousTopic(topic.id),
                        nextTopic = InstructionsCatalog.nextTopic(topic.id),
                        onOpenTopic = ::openTopic,
                        onBackToList = {
                            selectedTopicId = null
                            onAction(InstructionsAction.BackToTopicList)
                        },
                    )
                }
            }
        }
    }
}
