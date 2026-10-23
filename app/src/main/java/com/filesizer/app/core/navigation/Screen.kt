package com.filesizer.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        titleRes = com.filesizer.app.R.string.nav_home,
        icon = Icons.Rounded.Home
    )

    data object Browse : Screen(
        route = "browse?path={path}",
        titleRes = com.filesizer.app.R.string.nav_browse,
        icon = Icons.Rounded.Folder
    ) {
        fun createRoute(path: String? = null): String {
            return if (path != null) "browse?path=${android.net.Uri.encode(path)}" else "browse"
        }
    }

    data object StorageAnalyzer : Screen(
        route = "analyzer",
        titleRes = com.filesizer.app.R.string.nav_analyzer,
        icon = Icons.Rounded.Analytics
    )

    data object CodeEditor : Screen(
        route = "editor?uri={uri}",
        titleRes = com.filesizer.app.R.string.action_code_editor,
        icon = Icons.Rounded.Code
    ) {
        fun createRoute(uri: String? = null): String {
            return if (uri != null) "editor?uri=${android.net.Uri.encode(uri)}" else "editor"
        }
    }

    data object ApkStudio : Screen(
        route = "apkstudio?uri={uri}",
        titleRes = com.filesizer.app.R.string.action_apk_studio,
        icon = Icons.Rounded.Widgets
    ) {
        fun createRoute(uri: String? = null): String {
            return if (uri != null) "apkstudio?uri=${android.net.Uri.encode(uri)}" else "apkstudio"
        }
    }

    data object ArchiveViewer : Screen(
        route = "archive?path={path}",
        titleRes = com.filesizer.app.R.string.action_compress,
        icon = Icons.Rounded.Folder
    ) {
        fun createRoute(path: String): String {
            return "archive?path=${android.net.Uri.encode(path)}"
        }
    }

    data object HexViewer : Screen(
        route = "hex?path={path}",
        titleRes = com.filesizer.app.R.string.action_hex_viewer,
        icon = Icons.Rounded.DataArray
    ) {
        fun createRoute(path: String? = null): String {
            return if (path != null) "hex?path=${android.net.Uri.encode(path)}" else "hex"
        }
    }

    data object DevTools : Screen(
        route = "devtools",
        titleRes = com.filesizer.app.R.string.nav_tools,
        icon = Icons.Rounded.DataArray
    )

    data object Settings : Screen(
        route = "settings",
        titleRes = com.filesizer.app.R.string.nav_settings,
        icon = Icons.Rounded.Settings
    )
}
