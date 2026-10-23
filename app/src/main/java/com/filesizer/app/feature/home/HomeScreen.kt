package com.filesizer.app.feature.home

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.PermissionCard
import com.filesizer.app.core.designsystem.components.QuickActionCard
import com.filesizer.app.core.designsystem.components.StorageUsageCard
import com.filesizer.app.core.navigation.Screen

@Composable
fun HomeScreen(
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Read real storage metrics via StatFs
    val (usedBytes, totalBytes) = remember {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            Pair(total - free, total)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        FileSizerTopBar(
            title = stringResource(R.string.app_name)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Storage Summary Card
            item {
                StorageUsageCard(
                    usedBytes = usedBytes,
                    totalBytes = totalBytes,
                    onCardClick = { onNavigate(Screen.StorageAnalyzer.route) }
                )
            }

            // Permission Request Card if not granted
            if (!hasStoragePermission) {
                item {
                    PermissionCard(
                        onGrantClick = onRequestPermission
                    )
                }
            }

            // Quick Actions Section
            item {
                Text(
                    text = stringResource(R.string.dashboard_quick_actions),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Quick Action Grid Row 1
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionCard(
                        title = stringResource(R.string.action_browse),
                        icon = Icons.Rounded.Folder,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { onNavigate(Screen.Browse.createRoute()) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = stringResource(R.string.action_create_folder),
                        icon = Icons.Rounded.CreateNewFolder,
                        iconTint = Color(0xFF0284C7),
                        iconBgColor = Color(0xFFE0F2FE),
                        onClick = { onNavigate(Screen.Browse.createRoute()) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = stringResource(R.string.action_compress),
                        icon = Icons.Rounded.FolderZip,
                        iconTint = Color(0xFFD97706),
                        iconBgColor = Color(0xFFFEF3C7),
                        onClick = { onNavigate(Screen.Browse.createRoute()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Action Grid Row 2
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionCard(
                        title = stringResource(R.string.action_apk_studio),
                        icon = Icons.Rounded.Widgets,
                        iconTint = Color(0xFF10B981),
                        iconBgColor = Color(0xFFD1FAE5),
                        onClick = { onNavigate(Screen.ApkStudio.route) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = stringResource(R.string.action_code_editor),
                        icon = Icons.Rounded.Code,
                        iconTint = Color(0xFF8B5CF6),
                        iconBgColor = Color(0xFFEDE9FE),
                        onClick = { onNavigate(Screen.CodeEditor.route) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = stringResource(R.string.action_hex_viewer),
                        icon = Icons.Rounded.DataArray,
                        iconTint = Color(0xFFEC4899),
                        iconBgColor = Color(0xFFFCE7F3),
                        onClick = { onNavigate(Screen.CodeEditor.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent Files Header & Empty State
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                EmptyStateCard(
                    title = stringResource(R.string.empty_recents_title),
                    description = stringResource(R.string.empty_recents_desc),
                    icon = Icons.Rounded.History
                )
            }
        }
    }
}
