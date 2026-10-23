package com.filesizer.app.feature.home

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.database.DatabaseRepository
import com.filesizer.app.core.database.model.RecentFileItem
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.PermissionCard
import com.filesizer.app.core.designsystem.components.QuickActionCard
import com.filesizer.app.core.designsystem.components.StorageUsageCard
import com.filesizer.app.core.designsystem.components.formatFileSize
import com.filesizer.app.core.navigation.Screen

@Composable
fun HomeScreen(
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRepository(context) }
    val recentFiles by repository.recentFilesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        repository.refresh()
    }

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

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(title = stringResource(R.string.app_name))

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
                    PermissionCard(onGrantClick = onRequestPermission)
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

            // Recent Files Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (recentFiles.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.empty_recents_title),
                        description = stringResource(R.string.empty_recents_desc),
                        icon = Icons.Rounded.History
                    )
                }
            } else {
                items(recentFiles.take(8)) { recentItem ->
                    RecentFileRow(
                        item = recentItem,
                        onClick = {
                            if (recentItem.isDirectory) {
                                onNavigate(Screen.Browse.createRoute(recentItem.path))
                            } else {
                                val ext = recentItem.name.substringAfterLast(".", "").lowercase()
                                if (ext == "apk") {
                                    onNavigate(Screen.ApkStudio.createRoute(recentItem.path))
                                } else {
                                    onNavigate(Screen.CodeEditor.createRoute(recentItem.path))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentFileRow(item: RecentFileItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.isDirectory) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
