package com.filesizer.app.feature.filemanager

import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.PermissionCard
import com.filesizer.app.core.designsystem.components.formatFileSize
import com.filesizer.app.core.operations.ConflictPolicy
import com.filesizer.app.core.operations.FileOperationService
import com.filesizer.app.core.operations.FileOperationProgress
import com.filesizer.app.core.operations.PartialOperationException
import com.filesizer.app.core.preferences.AppPreferences
import com.filesizer.app.core.storage.DocumentTreeProvider
import com.filesizer.app.core.storage.DocumentPartialMoveException
import com.filesizer.app.core.database.DatabaseRepository
import com.filesizer.app.core.database.model.OperationHistoryItem
import com.filesizer.app.feature.details.FileDetailsDialog
import com.filesizer.app.feature.filemanager.dialogs.CreateFolderDialog
import com.filesizer.app.feature.filemanager.dialogs.DeleteConfirmDialog
import com.filesizer.app.feature.filemanager.dialogs.RenameDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
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
    onOpenArchive: (String) -> Unit = {},
    showHiddenFiles: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val databaseRepository = remember(context) { DatabaseRepository(context) }

    val savedTreePath = remember(context) {
        AppPreferences(context).documentTreeUri?.let { saved ->
            runCatching {
                val uri = Uri.parse(saved)
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)).toString()
            }.getOrNull()
        }
    }
    val rootPath = remember(initialPath, savedTreePath) {
        initialPath ?: savedTreePath ?: Environment.getExternalStorageDirectory()?.absolutePath ?: "/"
    }
    var currentPath by remember(initialPath, savedTreePath) { mutableStateOf(rootPath) }
    val safParentStack = remember(initialPath, savedTreePath) { androidx.compose.runtime.mutableStateListOf<String>() }
    val isSafLocation = remember(currentPath) { Uri.parse(currentPath).scheme.equals("content", ignoreCase = true) }
    var currentLocationName by remember(currentPath) {
        mutableStateOf(if (Uri.parse(currentPath).scheme == "content") "Selected folder" else File(currentPath).name.ifEmpty { "Storage" })
    }
    suspend fun recordHistory(operation: String, source: String, status: String, completed: Int) {
        databaseRepository.recordOperation(
            OperationHistoryItem(
                operation = operation,
                source = source.take(120),
                destination = currentLocationName.take(120),
                status = status,
                completedCount = completed
            )
        )
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    var fileList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Search & Filter State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FileFilterType.ALL) }
    var sortOption by remember { mutableStateOf(FileSortOption()) }
    var isGridView by remember { mutableStateOf(false) }

    // Multi-Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Clipboard for Copy / Move operations
    var clipboardFiles by remember { mutableStateOf<List<String>?>(null) }
    var isMoveOperation by remember { mutableStateOf(false) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var operationJob by remember { mutableStateOf<Job?>(null) }

    // Dialogs
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateArchiveDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemForDetails by remember { mutableStateOf<FileItem?>(null) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selectedTreeUri ->
        if (selectedTreeUri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(selectedTreeUri, flags) }
                .onFailure { runCatching { context.contentResolver.takePersistableUriPermission(selectedTreeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val documentUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(
                    selectedTreeUri,
                    DocumentsContract.getTreeDocumentId(selectedTreeUri)
                )
            }.getOrNull()
            if (documentUri != null) {
                AppPreferences(context).documentTreeUri = selectedTreeUri.toString()
                safParentStack.clear()
                currentPath = documentUri.toString()
            } else {
                Toast.makeText(context, "Could not open this folder from the selected provider.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Directory reads run off the main thread; SAF content URIs stay URIs throughout the flow.
    val reloadFiles: suspend () -> Unit = {
        if (hasStoragePermission || isSafLocation) {
            isLoading = true
            try {
                loadError = null
                if (isSafLocation) {
                    val currentUri = Uri.parse(currentPath)
                    val docs = DocumentTreeProvider.listChildren(context, currentUri).getOrThrow()
                    fileList = docs.map { doc ->
                        FileItem(
                            name = doc.name,
                            path = doc.uri.toString(),
                            isDirectory = doc.isDirectory,
                            size = doc.size,
                            lastModified = doc.lastModified,
                            extension = if (doc.isDirectory) "" else doc.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        )
                    }
                    currentLocationName = DocumentTreeProvider.getDocument(context, currentUri).getOrNull()?.name ?: "Selected folder"
                } else {
                    fileList = withContext(Dispatchers.IO) {
                        val dir = File(currentPath)
                        if (!dir.exists() || !dir.isDirectory) emptyList() else {
                            dir.listFiles()?.map { file ->
                                FileItem(
                                    name = file.name,
                                    path = file.absolutePath,
                                    isDirectory = file.isDirectory,
                                    size = if (file.isDirectory) 0L else file.length(),
                                    lastModified = file.lastModified(),
                                    extension = file.extension.lowercase(Locale.ROOT)
                                )
                            } ?: emptyList()
                        }
                    }
                    currentLocationName = File(currentPath).name.ifEmpty { "Storage" }
                }
            } catch (error: Exception) {
                fileList = emptyList()
                loadError = error.message ?: "Could not read this folder. Access may have been revoked."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentPath, hasStoragePermission) {
        reloadFiles()
        // Reset selection on directory change
        isSelectionMode = false
        selectedPaths = emptySet()
    }

    // Filter & Sort file list
    val displayedFiles = remember(fileList, searchQuery, selectedFilter, sortOption, showHiddenFiles) {
        fileList.filter { item ->
            val matchesVisibility = showHiddenFiles || !item.name.startsWith('.')
            // Search query filter
            val matchesSearch = searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true)

            // Category filter
            val matchesFilter = when (selectedFilter) {
                FileFilterType.ALL -> true
                FileFilterType.IMAGES -> !item.isDirectory && item.extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg")
                FileFilterType.VIDEOS -> !item.isDirectory && item.extension in listOf("mp4", "mkv", "webm", "avi", "mov")
                FileFilterType.AUDIO -> !item.isDirectory && item.extension in listOf("mp3", "wav", "flac", "ogg", "m4a")
                FileFilterType.DOCUMENTS -> !item.isDirectory && item.extension in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
                FileFilterType.ARCHIVES -> !item.isDirectory && item.extension in listOf("zip", "rar", "7z", "tar", "gz")
                FileFilterType.APKS -> !item.isDirectory && item.extension == "apk"
            }

            matchesVisibility && matchesSearch && matchesFilter
        }.sortedWith { a, b ->
            // Directories always on top
            if (a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                val comp = when (sortOption.field) {
                    SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                    SortField.SIZE -> a.size.compareTo(b.size)
                    SortField.DATE -> a.lastModified.compareTo(b.lastModified)
                    SortField.TYPE -> a.extension.compareTo(b.extension, ignoreCase = true)
                }
                if (sortOption.ascending) comp else -comp
            }
        }
    }

    val currentDir = if (isSafLocation) null else File(currentPath)
    val canGoUp = if (isSafLocation) safParentStack.isNotEmpty() else currentDir?.parentFile != null && currentPath != Environment.getExternalStorageDirectory()?.absolutePath

    Column(modifier = modifier.fillMaxSize()) {
        // Selection Toolbar or Normal Header TopBar
        if (isSelectionMode) {
            SelectionToolbar(
                selectedCount = selectedPaths.size,
                totalCount = displayedFiles.size,
                onCloseSelection = {
                    isSelectionMode = false
                    selectedPaths = emptySet()
                },
                onSelectAll = {
                    selectedPaths = displayedFiles.map { it.path }.toSet()
                },
                onCopy = {
                    clipboardFiles = selectedPaths.toList()
                    isMoveOperation = false
                    Toast.makeText(context, "${selectedPaths.size} item(s) copied to clipboard", Toast.LENGTH_SHORT).show()
                    isSelectionMode = false
                },
                onMove = {
                    clipboardFiles = selectedPaths.toList()
                    isMoveOperation = true
                    Toast.makeText(context, "${selectedPaths.size} item(s) ready to move", Toast.LENGTH_SHORT).show()
                    isSelectionMode = false
                },
                onCompress = {
                    if (isSafLocation) {
                        Toast.makeText(context, "Archive creation for selected document-provider files is not available yet.", Toast.LENGTH_LONG).show()
                    } else showCreateArchiveDialog = true
                },
                onRename = {
                    val single = displayedFiles.find { it.path == selectedPaths.first() }
                    itemToRename = single
                },
                onDelete = {
                    showDeleteConfirmDialog = true
                },
                onDetails = {
                    val single = displayedFiles.find { it.path == selectedPaths.first() }
                    itemForDetails = single
                }
            )
        } else {
            FileSizerTopBar(
                title = currentLocationName.ifEmpty { stringResource(R.string.nav_browse) },
                canNavigateBack = canGoUp,
                onNavigateBack = {
                    if (isSafLocation) {
                        if (safParentStack.isNotEmpty()) currentPath = safParentStack.removeAt(safParentStack.lastIndex)
                    } else {
                        val parent = currentDir?.parentFile
                        if (parent != null && parent.canRead()) currentPath = parent.absolutePath else onNavigateBack()
                    }
                },
                actions = {
                    IconButton(onClick = { treePicker.launch(null) }) {
                        Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = "Open a folder using Storage Access Framework")
                    }
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Toggle Grid/List"
                        )
                    }
                    IconButton(onClick = {
                        // Toggle sorting field & direction
                        sortOption = when (sortOption.field) {
                            SortField.NAME -> FileSortOption(SortField.DATE, ascending = false)
                            SortField.DATE -> FileSortOption(SortField.SIZE, ascending = false)
                            SortField.SIZE -> FileSortOption(SortField.TYPE, ascending = true)
                            SortField.TYPE -> FileSortOption(SortField.NAME, ascending = true)
                        }
                        Toast.makeText(context, "Sorted by ${sortOption.field.label}", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                    }
                }
            )
        }

        // Search Input Bar
        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files in this folder...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(FileFilterType.values()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Breadcrumb Path Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSafLocation) "Storage Access Framework · $currentLocationName" else currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${displayedFiles.size} items",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Paste Banner if files are in clipboard
        if (clipboardFiles != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${clipboardFiles!!.size} item(s) to ${if (isMoveOperation) "move" else "copy"}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row {
                        if (isOperationRunning) {
                            TextButton(onClick = { operationJob?.cancel() }) {
                                Text("Cancel operation", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { clipboardFiles = null }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        TextButton(
                            onClick = {
                                val sources = clipboardFiles!!
                                val move = isMoveOperation
                                if (!isOperationRunning) operationJob = scope.launch {
                                    isOperationRunning = true
                                    operationProgress = 0L to 0L
                                    try {
                                        val result = if (isSafLocation && sources.all { Uri.parse(it).scheme == "content" }) {
                                            DocumentTreeProvider.copyOrMove(
                                                context,
                                                sources.map(Uri::parse),
                                                Uri.parse(currentPath),
                                                move,
                                                onProgress = { progress ->
                                                    scope.launch(Dispatchers.Main.immediate) { operationProgress = progress.completedBytes to progress.totalBytes }
                                                }
                                            )
                                        } else if (!isSafLocation && sources.none { Uri.parse(it).scheme == "content" }) {
                                            val sourceFiles = sources.map(::File)
                                            val progressCallback: (FileOperationProgress) -> Unit = { progress ->
                                                scope.launch(Dispatchers.Main.immediate) { operationProgress = progress.completedBytes to progress.totalBytes }
                                            }
                                            if (move) FileOperationService.moveItems(sourceFiles, File(currentPath), ConflictPolicy.RENAME_NEW, progressCallback)
                                            else FileOperationService.copyItems(sourceFiles, File(currentPath), ConflictPolicy.RENAME_NEW, progressCallback)
                                        } else {
                                            Result.failure(IllegalStateException("Copying between filesystem and document-provider locations is not supported."))
                                        }
                                    recordHistory(
                                        operation = if (move) "Move" else "Copy",
                                        source = "${sources.size} selected item(s)",
                                        status = when {
                                            result.isSuccess -> "COMPLETED"
                                            result.exceptionOrNull() is PartialOperationException || result.exceptionOrNull() is DocumentPartialMoveException -> "PARTIAL"
                                            else -> "FAILED"
                                        },
                                        completed = result.getOrNull() ?: (result.exceptionOrNull() as? PartialOperationException)?.completedItems ?: 0
                                    )
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "${result.getOrNull()} items processed successfully", Toast.LENGTH_SHORT).show()
                                        clipboardFiles = null
                                        reloadFiles()
                                    } else {
                                        Toast.makeText(context, "Operation failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                    } finally {
                                        isOperationRunning = false
                                        operationProgress = null
                                        operationJob = null
                                    }
                                }
                            }
                        ) {
                            Text("Paste Here", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (isOperationRunning) {
                        val (done, total) = operationProgress ?: (0L to 0L)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (!hasStoragePermission && !isSafLocation) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PermissionCard(onGrantClick = onRequestPermission)
                    TextButton(onClick = { treePicker.launch(null) }) { Text("Browse a folder using Storage Access Framework") }
                }
            } else if (loadError != null && !isLoading) {
                EmptyStateCard(
                    title = "Folder unavailable",
                    description = loadError ?: "Folder could not be read.",
                    icon = Icons.Rounded.Folder
                )
            } else if (displayedFiles.isEmpty() && !isLoading) {
                EmptyStateCard(
                    title = stringResource(R.string.empty_folder_title),
                    description = if (searchQuery.isNotEmpty()) "No files match your search query." else stringResource(R.string.empty_folder_desc),
                    icon = Icons.Rounded.Folder
                )
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedFiles, key = { it.path }) { item ->
                            val isSelected = selectedPaths.contains(item.path)
                            FileGridCard(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedPaths = if (isSelected) selectedPaths - item.path else selectedPaths + item.path
                                    } else {
                                        if (item.isDirectory) {
                                            if (isSafLocation) safParentStack.add(currentPath)
                                            currentPath = item.path
                                        } else if (item.extension in listOf("zip", "jar")) {
                                            onOpenArchive(item.path)
                                        } else if (item.extension in listOf("rar", "7z", "tar", "gz", "bz2", "xz")) {
                                            Toast.makeText(context, "${item.extension.uppercase()} archives are not supported yet. ZIP is supported.", Toast.LENGTH_LONG).show()
                                        } else {
                                            onOpenFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedPaths = setOf(item.path)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedFiles, key = { it.path }) { item ->
                            val isSelected = selectedPaths.contains(item.path)
                            FileRowItem(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedPaths = if (isSelected) selectedPaths - item.path else selectedPaths + item.path
                                    } else {
                                        if (item.isDirectory) {
                                            if (isSafLocation) safParentStack.add(currentPath)
                                            currentPath = item.path
                                        } else if (item.extension in listOf("zip", "jar")) {
                                            onOpenArchive(item.path)
                                        } else if (item.extension in listOf("rar", "7z", "tar", "gz", "bz2", "xz")) {
                                            Toast.makeText(context, "${item.extension.uppercase()} archives are not supported yet. ZIP is supported.", Toast.LENGTH_LONG).show()
                                        } else {
                                            onOpenFile(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedPaths = setOf(item.path)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Floating Action Button: Create Folder
            if ((hasStoragePermission || isSafLocation) && !isSelectionMode) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.CreateNewFolder, contentDescription = "Create Folder")
                }
            }
        }
    }

    // Dialogs execution
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                scope.launch {
                    val result = if (isSafLocation) {
                        DocumentTreeProvider.createFolder(context, Uri.parse(currentPath), name)
                    } else {
                        FileOperationService.createFolder(File(currentPath), name)
                    }
                    if (result.isSuccess) {
                        recordHistory("Create folder", name, "COMPLETED", 1)
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                        reloadFiles()
                    } else {
                        recordHistory("Create folder", name, "FAILED", 0)
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showCreateArchiveDialog) {
        com.filesizer.app.feature.archives.dialogs.CreateArchiveDialog(
            itemCount = selectedPaths.size,
            onDismiss = { showCreateArchiveDialog = false },
            onConfirm = { archiveName, level ->
                showCreateArchiveDialog = false
                val sources = selectedPaths.map { File(it) }
                val targetZip = File(currentPath, archiveName)
                scope.launch {
                    val result = com.filesizer.app.core.archive.ArchiveService.createZipArchive(sources, targetZip, level)
                    if (result.isSuccess) {
                        recordHistory("Create ZIP", "${sources.size} selected item(s)", "COMPLETED", 1)
                        Toast.makeText(context, "Archive created: ${targetZip.name}", Toast.LENGTH_SHORT).show()
                        isSelectionMode = false
                        selectedPaths = emptySet()
                        reloadFiles()
                    } else {
                        recordHistory("Create ZIP", "${sources.size} selected item(s)", "FAILED", 0)
                        Toast.makeText(context, "Failed to create archive: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (itemToRename != null) {
        RenameDialog(
            initialName = itemToRename!!.name,
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                val target = itemToRename!!
                itemToRename = null
                scope.launch {
                    val result = if (isSafLocation) {
                        DocumentTreeProvider.rename(context, Uri.parse(target.path), newName)
                    } else {
                        FileOperationService.renameItem(File(target.path), newName)
                    }
                    if (result.isSuccess) {
                        recordHistory("Rename", "${target.name} → $newName", "COMPLETED", 1)
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                        isSelectionMode = false
                        selectedPaths = emptySet()
                        reloadFiles()
                    } else {
                        recordHistory("Rename", target.name, "FAILED", 0)
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            itemCount = selectedPaths.size,
            singleItemName = if (selectedPaths.size == 1) displayedFiles.find { it.path == selectedPaths.first() }?.name else null,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                scope.launch {
                    val result = if (isSafLocation) {
                        DocumentTreeProvider.delete(context, selectedPaths.map(Uri::parse))
                    } else {
                        FileOperationService.deleteItems(selectedPaths.map(::File))
                    }
                    if (result.isSuccess) {
                        recordHistory("Delete", "${selectedPaths.size} selected item(s)", "COMPLETED", result.getOrNull() ?: 0)
                        Toast.makeText(context, "${result.getOrNull()} item(s) deleted", Toast.LENGTH_SHORT).show()
                        isSelectionMode = false
                        selectedPaths = emptySet()
                        reloadFiles()
                    } else {
                        val error = result.exceptionOrNull()
                        recordHistory(
                            "Delete",
                            "${selectedPaths.size} selected item(s)",
                            if (error is PartialOperationException) "PARTIAL" else "FAILED",
                            (error as? PartialOperationException)?.completedItems ?: 0
                        )
                        Toast.makeText(context, "Delete failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (itemForDetails != null) {
        FileDetailsDialog(
            item = itemForDetails!!,
            onDismiss = { itemForDetails = null }
        )
    }
}

@Composable
fun SelectionToolbar(
    selectedCount: Int,
    totalCount: Int,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onCompress: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCloseSelection) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Selection")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                }
                if (selectedCount > 0) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = onMove) {
                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = "Move")
                    }
                    IconButton(onClick = onCompress) {
                        Icon(Icons.Rounded.FolderZip, contentDescription = "Compress")
                    }
                }
                if (selectedCount == 1) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = onDetails) {
                        Icon(Icons.Rounded.Info, contentDescription = "Details")
                    }
                }
                if (selectedCount > 0) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatted = remember(item.lastModified) {
        if (item.lastModified <= 0L) "Date unavailable" else {
            val sdf = SimpleDateFormat("MMM dd, yyyy  HH:mm", Locale.getDefault())
            sdf.format(Date(item.lastModified))
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 6.dp)
                )
            }

            val (icon, tint) = getFileIconAndTint(item)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!item.isDirectory && item.size >= 0L) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            val (icon, tint) = getFileIconAndTint(item)
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(44.dp)
                )
                if (isSelectionMode && isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.isDirectory && item.size >= 0L) {
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun getFileIconAndTint(item: FileItem): Pair<ImageVector, Color> {
    if (item.isDirectory) {
        return Pair(Icons.Rounded.Folder, MaterialTheme.colorScheme.primary)
    }
    return when (item.extension) {
        "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Rounded.FolderZip, Color(0xFFD97706))
        "jpg", "jpeg", "png", "webp", "gif", "svg" -> Pair(Icons.Rounded.Image, Color(0xFF3B82F6))
        "mp4", "mkv", "webm", "avi", "mov" -> Pair(Icons.Rounded.VideoLibrary, Color(0xFF8B5CF6))
        "mp3", "wav", "flac", "ogg", "m4a" -> Pair(Icons.Rounded.MusicNote, Color(0xFFEC4899))
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> Pair(Icons.AutoMirrored.Rounded.InsertDriveFile, Color(0xFFEA580C))
        else -> Pair(Icons.AutoMirrored.Rounded.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
