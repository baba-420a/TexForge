package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TexForgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Core Splashscreen API
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TexForgeTheme {
                val navController = rememberNavController()
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val imageState by viewModel.imageState.collectAsStateWithLifecycle()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Only show bottom bar when not in crop mode
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
                            // If cropping, Back button exits crop mode back to Create screen
                            if (imageState.isCropping) {
                                BackHandler {
                                    viewModel.onCancelCrop()
                                }
                            }
                            // Otherwise, system back exits the app from the Create screen

                            CreateScreen(
                                appSettings = appSettings,
                                imageState = imageState,
                                onPrepareCamera = { viewModel.prepareCameraCapture(this@MainActivity) },
                                onCameraResult = { viewModel.onCameraCaptureResult(this@MainActivity, it) },
                                onImagePicked = { viewModel.loadSelectedImage(this@MainActivity, it) },
                                onCropConfirmed = { viewModel.onCropConfirmed(it) },
                                onCancelCrop = { viewModel.onCancelCrop() },
                                onReCrop = { viewModel.reCrop() },
                                onClearImage = { viewModel.clearImage() },
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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.testTag("bottom_nav_bar")
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
                label = { Text(screen.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_item_${screen.route}")
            )
        }
    }
}
