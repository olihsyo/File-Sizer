package com.filesizer.app.feature.filemanager

import android.os.Environment
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.PermissionCard
import com.filesizer.app.core.designsystem.components.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String
)

@Composable
fun FileManagerScreen(
    initialPath: String? = null,
    hasStoragePermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rootPath = remember {
        initialPath ?: Environment.getExternalStorageDirectory()?.absolutePath ?: "/"
    }
    var currentPath by remember { mutableStateOf(rootPath) }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentPath, hasStoragePermission) {
        if (!hasStoragePermission) {
            fileList = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val dir = File(currentPath)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()?.map { file ->
                    FileItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        extension = file.extension.lowercase(Locale.ROOT)
                    )
                }?.sortedWith(
                    compareByDescending<FileItem> { it.isDirectory }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                ) ?: emptyList()
                fileList = files
            } else {
                fileList = emptyList()
            }
        } catch (_: Exception) {
            fileList = emptyList()
        } finally {
            isLoading = false
        }
    }

    val currentDir = File(currentPath)
    val canGoUp = currentDir.parentFile != null && currentPath != Environment.getExternalStorageDirectory()?.absolutePath

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        FileSizerTopBar(
            title = currentDir.name.ifEmpty { stringResource(R.string.nav_browse) },
            canNavigateBack = canGoUp,
            onNavigateBack = {
                val parent = currentDir.parentFile
                if (parent != null && parent.canRead()) {
                    currentPath = parent.absolutePath
                } else {
                    onNavigateBack()
                }
            }
        )

        // Breadcrumb path banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = currentPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (!hasStoragePermission) {
            Column(modifier = Modifier.padding(16.dp)) {
                PermissionCard(onGrantClick = onRequestPermission)
            }
        } else if (fileList.isEmpty() && !isLoading) {
            EmptyStateCard(
                title = stringResource(R.string.empty_folder_title),
                description = stringResource(R.string.empty_folder_desc),
                icon = Icons.Rounded.Folder
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(fileList, key = { it.path }) { item ->
                    FileRowItem(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                currentPath = item.path
                            } else {
                                onOpenFile(item)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatted = remember(item.lastModified) {
        val sdf = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
        sdf.format(Date(item.lastModified))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val (icon, tint) = getFileIconAndTint(item)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!item.isDirectory) {
                        Text(
                            text = formatFileSize(item.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun getFileIconAndTint(item: FileItem): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    if (item.isDirectory) {
        return Pair(Icons.Rounded.Folder, MaterialTheme.colorScheme.primary)
    }
    return when (item.extension) {
        "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Rounded.FolderZip, androidx.compose.ui.graphics.Color(0xFFD97706))
        "jpg", "jpeg", "png", "webp", "gif", "svg" -> Pair(Icons.Rounded.Image, androidx.compose.ui.graphics.Color(0xFF3B82F6))
        "mp4", "mkv", "webm", "avi" -> Pair(Icons.Rounded.VideoLibrary, androidx.compose.ui.graphics.Color(0xFF8B5CF6))
        "mp3", "wav", "flac", "ogg", "m4a" -> Pair(Icons.Rounded.MusicNote, androidx.compose.ui.graphics.Color(0xFFEC4899))
        else -> Pair(Icons.AutoMirrored.Rounded.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
