package com.filesizer.app.feature.archives

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.archive.ArchiveService
import com.filesizer.app.core.archive.model.ArchiveEntryItem
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.formatFileSize
import com.filesizer.app.core.storage.ContentUriFiles
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun ArchiveViewerScreen(
    archivePath: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isContentArchive = remember(archivePath) { Uri.parse(archivePath).scheme.equals("content", ignoreCase = true) }
    var archiveFile by remember(archivePath) { mutableStateOf<File?>(null) }
    var archiveDisplayName by remember(archivePath) { mutableStateOf("Archive") }

    var entries by remember { mutableStateOf<List<ArchiveEntryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isExtracting by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var extractionProgress by remember { mutableStateOf(0f) }
    var extractJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(archivePath) {
        isLoading = true
        try {
            val uri = Uri.parse(archivePath)
            val file = if (isContentArchive) {
                archiveDisplayName = ContentUriFiles.metadata(context, uri).displayName
                ContentUriFiles.copyToCache(context, uri, ContentUriFiles.MAX_ARCHIVE_IMPORT_BYTES).getOrThrow()
            } else File(if (uri.scheme.equals("file", ignoreCase = true)) uri.path ?: archivePath else archivePath).also { archiveDisplayName = it.name }
            archiveFile = file
            val result = ArchiveService.listZipEntries(file)
            if (result.isSuccess) entries = result.getOrNull() ?: emptyList()
            else Toast.makeText(context, "Failed to read ZIP archive: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(context, "Archive unavailable: ${error.message}", Toast.LENGTH_LONG).show()
        } finally {
            isLoading = false
        }
    }

    androidx.compose.runtime.DisposableEffect(archiveFile, isContentArchive) {
        onDispose { if (isContentArchive) archiveFile?.delete() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = archiveDisplayName,
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
                IconButton(
                    onClick = {
                        isTesting = true
                        scope.launch {
                            val testResult = archiveFile?.let { ArchiveService.testZipIntegrity(it) }
                            isTesting = false
                            if (testResult?.isSuccess == true && testResult.getOrNull() == true) {
                                Toast.makeText(context, "Archive integrity verified! CRC32 passed.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Integrity check failed: ${testResult?.exceptionOrNull()?.message ?: "Archive is unavailable"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isTesting && archiveFile != null
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.TaskAlt, contentDescription = "Test Archive")
                    }
                }
            }
        )

        // Archive Summary Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Entries: ${entries.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = archiveFile?.let { "Archive Size: ${formatFileSize(it.length())}" } ?: "Loading archive…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (isExtracting) {
                            extractJob?.cancel()
                        } else {
                            val source = archiveFile ?: return@Button
                            val outDir = File(source.parentFile, source.nameWithoutExtension)
                            isExtracting = true
                            extractionProgress = 0f
                            extractJob = scope.launch {
                                try {
                                    val extractResult = ArchiveService.extractZipArchive(source, outDir, onProgress = { current: Int, total: Int ->
                                        scope.launch(Dispatchers.Main.immediate) {
                                            extractionProgress = if (total == 0) 1f else current.toFloat() / total.toFloat()
                                        }
                                    })
                                    if (extractResult.isSuccess) {
                                        Toast.makeText(context, "Extracted ${extractResult.getOrNull()} entries to ${outDir.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Extraction error: ${extractResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isExtracting = false
                                    extractJob = null
                                }
                            }
                        }
                    },
                    enabled = entries.isNotEmpty() && !isContentArchive && archiveFile != null,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extracting...")
                    } else {
                        Icon(if (isExtracting) Icons.Rounded.TaskAlt else Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isExtracting) "Cancel" else stringResource(R.string.action_extract))
                    }
                }
                if (isExtracting) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { extractionProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isContentArchive) {
                    Text(
                        "This archive was opened through a document provider. Extraction to a provider folder is not supported yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Entries List
        if (entries.isEmpty() && !isLoading) {
            EmptyStateCard(
                title = "Empty Archive",
                description = "No entries found in this archive file.",
                icon = Icons.Rounded.FolderZip
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entries) { entry ->
                    ArchiveEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
fun ArchiveEntryRow(entry: ArchiveEntryItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!entry.isDirectory && entry.size > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatFileSize(entry.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entry.compressedSize > 0) {
                        val ratio = ((entry.compressedSize.toDouble() / entry.size.toDouble()) * 100).toInt()
                        Text(
                            text = "$ratio%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
