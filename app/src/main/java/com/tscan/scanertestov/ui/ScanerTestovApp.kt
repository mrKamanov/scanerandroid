package com.tscan.scanertestov.ui

/**
 * Описание: главный Compose-граф приложения с навигацией между feature-экранами.
 */
import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tscan.scanertestov.feature.batch.BatchDocumentCameraRoute
import com.tscan.scanertestov.feature.batch.BatchProcessingRoute
import com.tscan.scanertestov.feature.batch.BatchRecognitionWarmup
import com.tscan.scanertestov.feature.batch.BatchResultsRoute
import com.tscan.scanertestov.feature.blankeditor.BlankEditorScreen
import com.tscan.scanertestov.feature.blankeditor.handleBlankEditorAction
import com.tscan.scanertestov.feature.instructions.InstructionsScreen
import com.tscan.scanertestov.feature.instructions.handleInstructionsAction
import com.tscan.scanertestov.feature.journals.JournalsScreen
import com.tscan.scanertestov.feature.journals.handleJournalsAction
import com.tscan.scanertestov.feature.mainmenu.MainMenuScreen
import com.tscan.scanertestov.feature.mainmenu.handleMainMenuAction
import com.tscan.scanertestov.feature.realtime.RealtimeScanScreen
import com.tscan.scanertestov.feature.realtime.handleRealtimeScanAction
import com.tscan.scanertestov.feature.reports.ReportsScreen
import com.tscan.scanertestov.feature.reports.ReportsRoute
import com.tscan.scanertestov.data.AppSettingsStore
import com.tscan.scanertestov.ml.InferenceModelRuntime
import com.tscan.scanertestov.feature.settings.SettingsScreen
import com.tscan.scanertestov.feature.settings.SettingsState
import com.tscan.scanertestov.feature.settings.handleSettingsAction
import com.tscan.scanertestov.navigation.AppDestinations
import com.tscan.scanertestov.ui.components.AppBottomNavigationBar
import com.tscan.scanertestov.ui.launch.LaunchScreen
import com.tscan.scanertestov.ui.theme.ScanerTestovTheme

@Composable
fun ScanerTestovApp() {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    var isQuickAccessBarEnabled by rememberSaveable { mutableStateOf(false) }
    var inferenceModel by remember {
        mutableStateOf(AppSettingsStore.getInferenceModel(appContext))
    }
    var gradingCriteria by remember {
        mutableStateOf(AppSettingsStore.getGradingCriteria(appContext))
    }
    var omrMlConfidenceThreshold by remember {
        mutableStateOf(AppSettingsStore.getOmrMlConfidenceThreshold(appContext))
    }
    LaunchedEffect(Unit) {
        InferenceModelRuntime.syncFromPreferences(appContext)
        launch(Dispatchers.Default) {
            BatchRecognitionWarmup.ensure(appContext)
        }
    }
    val splashClockStart = remember { SystemClock.elapsedRealtime() }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val minVisibleMs = 480L
        delay((minVisibleMs - (SystemClock.elapsedRealtime() - splashClockStart)).coerceAtLeast(0L))
        showSplash = false
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ScanerTestovTheme {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            bottomBar = {
                if (
                    isQuickAccessBarEnabled &&
                    currentRoute != AppDestinations.BLANK_EDITOR &&
                    currentRoute != AppDestinations.MAIN_MENU
                ) {
                    AppBottomNavigationBar(
                        currentRoute = currentRoute,
                        onItemClick = { route ->
                            if (route == AppDestinations.MAIN_MENU) {
                                val popped = navController.popBackStack(
                                    route = AppDestinations.MAIN_MENU,
                                    inclusive = false,
                                    saveState = false
                                )
                                if (!popped && currentRoute != AppDestinations.MAIN_MENU) {
                                    navController.navigate(AppDestinations.MAIN_MENU) {
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestinations.MAIN_MENU,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(180))
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220)
                    ) + fadeIn(animationSpec = tween(180))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220)
                    ) + fadeOut(animationSpec = tween(180))
                }
            ) {
                composable(AppDestinations.MAIN_MENU) {
                    MainMenuScreen(
                        onAction = { action -> handleMainMenuAction(navController, action) }
                    )
                }
                composable(AppDestinations.REALTIME_SCAN) {
                    RealtimeScanScreen(
                        onAction = { action -> handleRealtimeScanAction(navController, action) }
                    )
                }
                composable(AppDestinations.BATCH_PROCESSING) {
                    BatchProcessingRoute(navController = navController)
                }
                composable(AppDestinations.BATCH_DOCUMENT_CAPTURE) {
                    BatchDocumentCameraRoute(navController = navController)
                }
                composable(AppDestinations.BATCH_RESULTS) {
                    BatchResultsRoute(navController = navController)
                }
                composable(AppDestinations.JOURNALS) {
                    JournalsScreen(
                        onAction = { action -> handleJournalsAction(navController, action) }
                    )
                }
                composable(AppDestinations.REPORTS) {
                    ReportsRoute(navController = navController)
                }
                composable(AppDestinations.BLANK_EDITOR) {
                    BlankEditorScreen(
                        onAction = { action -> handleBlankEditorAction(navController, action) }
                    )
                }
                composable(AppDestinations.INSTRUCTIONS) {
                    InstructionsScreen(
                        onAction = { action -> handleInstructionsAction(navController, action) }
                    )
                }
                composable(AppDestinations.SETTINGS) {
                    LaunchedEffect(Unit) {
                        omrMlConfidenceThreshold =
                            AppSettingsStore.getOmrMlConfidenceThreshold(appContext)
                    }
                    SettingsScreen(
                        state = SettingsState(
                            isQuickAccessBarEnabled = isQuickAccessBarEnabled,
                            selectedInferenceModel = inferenceModel,
                            gradingCriteria = gradingCriteria,
                            omrMlConfidenceThreshold = omrMlConfidenceThreshold,
                        ),
                        onAction = { action ->
                            handleSettingsAction(
                                context = activityContext,
                                navController = navController,
                                action = action,
                                onQuickAccessBarChanged = { isQuickAccessBarEnabled = it },
                                onInferenceModelChanged = { inferenceModel = it },
                                onGradingCriteriaChanged = { gradingCriteria = it },
                                onOmrMlConfidenceThresholdChanged = { omrMlConfidenceThreshold = it },
                            )
                        }
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showSplash,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(280)),
            exit = fadeOut(animationSpec = tween(480))
        ) {
            LaunchScreen()
        }
        }
    }
}
