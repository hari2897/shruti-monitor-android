package com.shrutimonitor.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.shrutimonitor.app.data.SettingsRepository
import com.shrutimonitor.app.util.HapticManager
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shrutimonitor.app.BuildConfig
import com.shrutimonitor.app.data.update.AppUpdateManager
import com.shrutimonitor.app.data.update.DownloadStatus
import com.shrutimonitor.app.data.update.UpdateCheckStatus
import com.shrutimonitor.app.data.update.UpdateInfo
import com.shrutimonitor.app.navigation.Screen
import com.shrutimonitor.app.ui.PermissionScreen
import com.shrutimonitor.app.ui.SplashScreen
import com.shrutimonitor.app.ui.monitor.MonitorScreen
import com.shrutimonitor.app.ui.play.PlayScreen
import com.shrutimonitor.app.ui.raga.RagaScreen
import com.shrutimonitor.app.ui.settings.SettingsScreen
import com.shrutimonitor.app.ui.theme.PrimarySaffron
import com.shrutimonitor.app.ui.theme.SecondaryViolet
import com.shrutimonitor.app.ui.theme.TextPrimary
import com.shrutimonitor.app.ui.theme.TextSecondary
import com.shrutimonitor.app.ui.update.UpdateDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root Composable orchestrating app shell, scaffold layout, top/bottom navigation, and routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShruthiMonitorApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val view = LocalView.current

    // Observe current route to determine top/bottom bar visibility
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val mainTabs = listOf(
        Screen.Monitor,
        Screen.Play,
        Screen.Ragas,
        Screen.Settings
    )

    val settingsRepository = remember { SettingsRepository(context) }
    val showControlsState by settingsRepository.monitorControlsVisible.collectAsState(initial = true)

    val showBars = currentRoute in mainTabs.map { it.route } && !(currentRoute == Screen.Monitor.route && !showControlsState)
    val showTopBar = showBars && currentRoute != Screen.Monitor.route

    // In-App Update Engine
    val scope = rememberCoroutineScope()
    val appUpdateManager = remember { AppUpdateManager(context) }
    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var autoDownloadStatus by remember { mutableStateOf<DownloadStatus>(DownloadStatus.NotStarted) }
    var showAutoDialog by remember { mutableStateOf(false) }

    // Check for updates automatically in background once every 24 hours
    LaunchedEffect(Unit) {
        val lastCheckTime = settingsRepository.lastUpdateCheckTime.first()
        val ignoredVersion = settingsRepository.ignoredUpdateVersion.first()
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        if (now - lastCheckTime > oneDayMs) {
            val currentVersion = try {
                BuildConfig.VERSION_NAME
            } catch (e: Throwable) {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1.1"
            }

            val result = appUpdateManager.checkForUpdate(currentVersion)
            settingsRepository.updateLastUpdateCheckTime(now)

            if (result is UpdateCheckStatus.UpdateAvailable) {
                if (result.updateInfo.latestVersionName != ignoredVersion) {
                    autoUpdateInfo = result.updateInfo
                    showAutoDialog = true
                }
            }
        }
    }

    // Launcher to request microphone permission
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            navController.navigate(Screen.Monitor.route) {
                popUpTo(Screen.Permission.route) { inclusive = true }
            }
        } else {
            // If denied, they can skip and still explore play instruments / ragas
            navController.navigate(Screen.Monitor.route) {
                popUpTo(Screen.Permission.route) { inclusive = true }
            }
            navController.navigate(Screen.Play.route)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "SHRUTI MONITOR",
                            fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = PrimarySaffron,
                            style = androidx.compose.ui.text.TextStyle(letterSpacing = 2.sp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = PrimarySaffron
                    )
                )
            }
        },
        bottomBar = {
            if (showBars) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .height(46.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainTabs.forEach { screen ->
                        val selected = currentRoute == screen.route
                        val label = when (screen) {
                            Screen.Monitor -> "Monitor"
                            Screen.Play -> "Instruments"
                            Screen.Ragas -> "Ragas"
                            Screen.Settings -> "Settings"
                            else -> ""
                        }

                        val icon = when (screen) {
                            Screen.Monitor -> Icons.Default.CompassCalibration
                            Screen.Play -> Icons.Default.Piano
                            Screen.Ragas -> Icons.Default.LibraryMusic
                            Screen.Settings -> Icons.Default.Settings
                            else -> Icons.Default.Settings
                        }

                        val activeColor = MaterialTheme.colorScheme.primary
                        val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        val color = if (selected) activeColor else inactiveColor

                        val translationY by animateDpAsState(
                            targetValue = if (selected) (-2).dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.7f),
                            label = "tabTranslation"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    HapticManager.tick(view)
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Monitor.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(vertical = 2.dp)
                                .offset(y = translationY),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(18.dp),
                                tint = color
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(250)
                ) + fadeIn(tween(250))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(250)
                ) + fadeOut(tween(250))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(250)
                ) + fadeIn(tween(250))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(250)
                ) + fadeOut(tween(250))
            }
        ) {
            // Splash Screen Route
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        // Check microphone permission status
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMicPermission) {
                            navController.navigate(Screen.Monitor.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Permission.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // Permission Request Route
            composable(Screen.Permission.route) {
                PermissionScreen(
                    onGrantClick = {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSkipClick = {
                        navController.navigate(Screen.Monitor.route) {
                            popUpTo(Screen.Permission.route) { inclusive = true }
                        }
                        navController.navigate(Screen.Play.route)
                    }
                )
            }

            // Main Tab Routes
            composable(Screen.Monitor.route) {
                MonitorScreen(
                    onNavigateToRagas = {
                        navController.navigate(Screen.Ragas.route) {
                            popUpTo(Screen.Monitor.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Play.route) {
                PlayScreen()
            }

            composable(Screen.Ragas.route) {
                RagaScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }

    // Auto-update notification dialog
    if (showAutoDialog && autoUpdateInfo != null) {
        val updateInfo = autoUpdateInfo!!
        UpdateDialog(
            updateInfo = updateInfo,
            downloadStatus = autoDownloadStatus,
            onDownloadAndInstall = {
                scope.launch {
                    appUpdateManager.downloadApk(updateInfo).collect { status ->
                        autoDownloadStatus = status
                        if (status is DownloadStatus.ReadyToInstall) {
                            if (!appUpdateManager.canRequestPackageInstalls()) {
                                appUpdateManager.requestInstallPermission()
                            } else {
                                appUpdateManager.installApk(status.apkFile)
                            }
                        }
                    }
                }
            },
            onInstallNow = { file ->
                if (!appUpdateManager.canRequestPackageInstalls()) {
                    appUpdateManager.requestInstallPermission()
                } else {
                    appUpdateManager.installApk(file)
                }
            },
            onOpenInBrowser = { url ->
                appUpdateManager.openWebUrl(url)
            },
            onDismiss = {
                showAutoDialog = false
                autoDownloadStatus = DownloadStatus.NotStarted
            },
            onSkipVersion = {
                scope.launch {
                    settingsRepository.updateIgnoredUpdateVersion(updateInfo.latestVersionName)
                    showAutoDialog = false
                    autoDownloadStatus = DownloadStatus.NotStarted
                }
            }
        )
    }
}
