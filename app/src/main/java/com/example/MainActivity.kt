package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.navigation.Screen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TexForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TexForgeTheme {
                val navController = rememberNavController()
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val imageState by viewModel.imageState.collectAsStateWithLifecycle()
                val processingState by viewModel.processingState.collectAsStateWithLifecycle()
                val generatedState by viewModel.generatedState.collectAsStateWithLifecycle()
                val aiDepthState by viewModel.aiDepthState.collectAsStateWithLifecycle()
                val fastState by viewModel.fastState.collectAsStateWithLifecycle()
                val activeVariant by viewModel.activeVariant.collectAsStateWithLifecycle()
                val selfTestResult by viewModel.selfTestResult.collectAsStateWithLifecycle()
                val isSelfTesting by viewModel.isSelfTesting.collectAsStateWithLifecycle()
                val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()

                // Keep screen on during heavy processing
                DisposableEffect(processingState.isProcessing) {
                    if (processingState.isProcessing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // If results are generated, show the ResultsScreen
                if (generatedState != null) {
                    BackHandler {
                        viewModel.clearResults()
                    }
                    ResultsScreen(
                        generatedState = generatedState!!,
                        aiDepthState = aiDepthState,
                        fastState = fastState,
                        activeVariant = activeVariant,
                        sourceBitmap = imageState.croppedBitmap,
                        normalFormat = appSettings.normalFormat,
                        onDetailBoostChange = { viewModel.updateDetailBoost(it) },
                        onNormalStrengthChange = { viewModel.updateNormalStrength(it) },
                        onInvertToggle = { viewModel.toggleInvertHeight() },
                        onNormalFormatToggle = { viewModel.toggleNormalFormat() },
                        onRoughnessChange = { base, contrast -> viewModel.updateRoughness(base, contrast) },
                        onAoChange = { radius, intensity -> viewModel.updateAo(radius, intensity) },
                        onCurvatureChange = { viewModel.updateCurvature(it) },
                        onMetallicChange = { threshold, boost -> viewModel.updateMetallic(threshold, boost) },
                        onAlbedoChange = { viewModel.updateAlbedo(it) },
                        onNoiseChange = { scale, octaves, intensity, blendWeight ->
                            viewModel.updateNoise(scale, octaves, intensity, blendWeight)
                        },
                        onSwitchVariant = { viewModel.switchVariant(it) },
                        onToggle2x2Tiled = { viewModel.toggle2x2TiledView() },
                        onApplySeamless = { enabled, margin -> viewModel.applySeamlessTiling(enabled, margin) },
                        onBackToCreate = { viewModel.clearResults() },
                        modifier = Modifier.fillMaxSize()
                    )
                    return@TexForgeTheme
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Only show bottom bar when not cropping
                        if (!imageState.isCropping) {
                            TexForgeBottomNav(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Create.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (imageState.isCropping) androidx.compose.foundation.layout.PaddingValues() else innerPadding)
                    ) {
                        composable(Screen.Create.route) {
                            // If cropping, Back button exits crop mode
                            if (imageState.isCropping) {
                                BackHandler {
                                    viewModel.onCancelCrop()
                                }
                            }

                            CreateScreen(
                                appSettings = appSettings,
                                imageState = imageState,
                                processingState = processingState,
                                modelStatus = modelStatus,
                                onPrepareCamera = { viewModel.prepareCameraCapture(this@MainActivity) },
                                onCameraResult = { viewModel.onCameraCaptureResult(this@MainActivity, it) },
                                onImagePicked = { viewModel.loadSelectedImage(this@MainActivity, it) },
                                onCropConfirmed = { viewModel.onCropConfirmed(it) },
                                onCancelCrop = { viewModel.onCancelCrop() },
                                onReCrop = { viewModel.reCrop() },
                                onClearImage = { viewModel.clearImage() },
                                onImportModel = { viewModel.importDepthModel(it) },
                                onGenerationModeChange = { viewModel.setGenerationMode(it) },
                                onMaskChanged = { viewModel.setExclusionMask(it) },
                                onGenerateMaps = { isSeamless -> viewModel.generatePbrMaps(isSeamless = isSeamless) },
                                onCancelGeneration = { viewModel.cancelGeneration() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(Screen.History.route) {
                            // Back button navigates back to Create screen
                            BackHandler {
                                navController.navigate(Screen.Create.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            HistoryScreen(
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(Screen.Settings.route) {
                            // Back button navigates back to Create screen
                            BackHandler {
                                navController.navigate(Screen.Create.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            SettingsScreen(
                                appSettings = appSettings,
                                onQualityChange = { viewModel.setQualityPreset(it) },
                                onNormalFormatChange = { viewModel.setNormalFormat(it) },
                                onEnginePreferenceChange = { viewModel.setEnginePreference(it) },
                                onHeightBitDepthChange = { viewModel.setHeightBitDepth(it) },
                                onTileSizeChange = { viewModel.setTileSize(it) },
                                onAoSamplesChange = { viewModel.setAoSamples(it) },
                                onNormalScalesChange = { s1, s2, s3, s4 ->
                                    viewModel.setNormalScales(s1, s2, s3, s4)
                                },
                                onExportFormatChange = { viewModel.setExportFormat(it) },
                                onJpgQualityChange = { viewModel.setJpgQuality(it) },
                                onUseNnapiChange = { viewModel.setUseNnapi(it) },
                                selfTestResult = selfTestResult,
                                isSelfTesting = isSelfTesting,
                                onRunSelfTest = { viewModel.runDepthModelSelfTest() },
                                onClearSelfTest = { viewModel.clearSelfTestResult() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TexForgeBottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.testTag("bottom_nav_bar")
    ) {
        NavigationBar(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.height(64.dp)
        ) {
            Screen.items.forEach { screen ->
                val isSelected = currentRoute == screen.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title
                        )
                    },
                    label = { 
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall
                        ) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_${screen.route}")
                )
            }
        }
    }
}

