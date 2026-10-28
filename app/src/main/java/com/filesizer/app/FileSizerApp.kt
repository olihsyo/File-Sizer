package com.filesizer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filesizer.app.core.designsystem.components.FileSizerNavigationBar
import com.filesizer.app.core.designsystem.theme.FileSizerTheme
import com.filesizer.app.core.navigation.Screen
import com.filesizer.app.core.preferences.AppPreferences
import com.filesizer.app.core.preferences.ThemePreference
import com.filesizer.app.feature.apkstudio.ApkStudioScreen
import com.filesizer.app.feature.editor.CodeEditorScreen
import com.filesizer.app.feature.filemanager.FileManagerScreen
import com.filesizer.app.feature.home.HomeScreen
import com.filesizer.app.feature.settings.SettingsScreen
import com.filesizer.app.feature.storageanalyzer.StorageAnalyzerScreen
import com.filesizer.app.core.storage.ContentUriFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileSizerApp(openedUri: String? = null) {
    val context = LocalContext.current
    val appPreferences = remember(context) { AppPreferences(context) }
    var themePreference by remember { mutableStateOf(appPreferences.themePreference) }
    var showHiddenFiles by remember { mutableStateOf(appPreferences.showHiddenFiles) }
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    FileSizerTheme(darkTheme = useDarkTheme) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        LaunchedEffect(openedUri) {
            if (openedUri != null) {
                val uri = Uri.parse(openedUri)
                val displayName = withContext(Dispatchers.IO) {
                    runCatching {
                        if (uri.scheme.equals("content", ignoreCase = true)) ContentUriFiles.metadata(context, uri).displayName
                        else uri.lastPathSegment.orEmpty()
                    }.getOrDefault(uri.lastPathSegment.orEmpty())
                }
                val extension = displayName.substringAfterLast('.', "").lowercase()
                val route = when {
                    extension == "apk" -> Screen.ApkStudio.createRoute(openedUri)
                    extension in listOf("zip", "jar") -> Screen.ArchiveViewer.createRoute(openedUri)
                    extension in listOf("txt", "json", "xml", "md", "kt", "java", "smali", "html", "css", "js", "csv", "log") -> Screen.CodeEditor.createRoute(openedUri)
                    else -> Screen.HexViewer.createRoute(openedUri)
                }
                navController.navigate(route)
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current

        var hasStoragePermission by remember {
            mutableStateOf(checkStoragePermission(context))
        }

        // Recheck permission on resume when user returns from settings
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasStoragePermission = checkStoragePermission(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            hasStoragePermission = checkStoragePermission(context)
        }

        val requestStoragePermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(fallbackIntent)
                }
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        val showBottomBar = currentRoute in listOf(
            Screen.Home.route,
            Screen.Browse.route,
            Screen.StorageAnalyzer.route,
            Screen.Settings.route
        )

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    FileSizerNavigationBar(
                        currentRoute = currentRoute,
                        onNavigateToRoute = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        hasStoragePermission = hasStoragePermission,
                        onRequestPermission = requestStoragePermission,
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }

                composable(
                    route = Screen.Browse.route,
                    arguments = listOf(
                        navArgument("path") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path")
                    val dbRepo = remember { com.filesizer.app.core.database.DatabaseRepository(context) }
                    val scope = androidx.compose.runtime.rememberCoroutineScope()

                    FileManagerScreen(
                        initialPath = path,
                        hasStoragePermission = hasStoragePermission,
                        showHiddenFiles = showHiddenFiles,
                        onRequestPermission = requestStoragePermission,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenFile = { file ->
                            scope.launch {
                                dbRepo.recordRecentAccess(file.name, file.path, file.size, file.isDirectory)
                            }
                            if (file.extension == "apk") {
                                navController.navigate(Screen.ApkStudio.createRoute(file.path))
                            } else if (file.extension in listOf("txt", "json", "xml", "md", "kt", "java", "smali", "html", "css", "js")) {
                                navController.navigate(Screen.CodeEditor.createRoute(file.path))
                            }
                        },
                        onOpenArchive = { archivePath ->
                            navController.navigate(Screen.ArchiveViewer.createRoute(archivePath))
                        }
                    )
                }

                composable(
                    route = Screen.ArchiveViewer.route,
                    arguments = listOf(
                        navArgument("path") {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val archivePath = backStackEntry.arguments?.getString("path") ?: ""
                    com.filesizer.app.feature.archives.ArchiveViewerScreen(
                        archivePath = archivePath,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.StorageAnalyzer.route) {
                    StorageAnalyzerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.CodeEditor.route,
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val uri = backStackEntry.arguments?.getString("uri")
                    CodeEditorScreen(
                        fileUri = uri,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.ApkStudio.route,
                    arguments = listOf(
                        navArgument("uri") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val uri = backStackEntry.arguments?.getString("uri")
                    ApkStudioScreen(
                        apkUri = uri,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.HexViewer.route,
                    arguments = listOf(
                        navArgument("path") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path")
                    com.filesizer.app.feature.hexeditor.HexViewerScreen(
                        filePath = path,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.DevTools.route) {
                    com.filesizer.app.feature.devtools.DevToolsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        themePreference = themePreference,
                        onThemePreferenceChange = { preference ->
                            appPreferences.themePreference = preference
                            themePreference = preference
                        },
                        showHiddenFiles = showHiddenFiles,
                        onShowHiddenFilesChange = { enabled ->
                            appPreferences.showHiddenFiles = enabled
                            showHiddenFiles = enabled
                        }
                    )
                }
            }
        }
    }
}

fun checkStoragePermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
