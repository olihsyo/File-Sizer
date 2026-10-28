package com.filesizer.app.feature.editor

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.editor.SyntaxHighlighter
import com.filesizer.app.core.storage.ContentUriFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class EditorTab(
    val title: String,
    val filePath: String?,
    val content: String,
    val isDirty: Boolean = false,
    val initialSize: Long? = null,
    val initialModified: Long? = null,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
)

@Composable
fun CodeEditorScreen(
    fileUri: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tabs = remember { mutableStateListOf<EditorTab>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var fontSize by remember { mutableFloatStateOf(13f) }

    // Find and Replace state
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var tabPendingClose by remember { mutableIntStateOf(-1) }

    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val index = selectedTabIndex
        val tab = tabs.getOrNull(index)
        if (uri != null && tab != null) {
            scope.launch {
                val result = ContentUriFiles.writeText(context, uri, tab.content)
                if (result.isSuccess) {
                    val meta = ContentUriFiles.metadata(context, uri)
                    tabs[index] = tab.copy(
                        title = meta.displayName,
                        filePath = uri.toString(),
                        isDirty = false,
                        initialSize = meta.size,
                        initialModified = meta.lastModified
                    )
                    Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, result.exceptionOrNull()?.message ?: "Save failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch {
                val existingIndex = tabs.indexOfFirst { it.filePath == uri.toString() }
                if (existingIndex >= 0) selectedTabIndex = existingIndex
                else runCatching {
                    val meta = ContentUriFiles.metadata(context, uri)
                    val content = ContentUriFiles.readText(context, uri).getOrThrow()
                    tabs.add(EditorTab(meta.displayName, uri.toString(), content, initialSize = meta.size, initialModified = meta.lastModified))
                    selectedTabIndex = tabs.lastIndex
                }.onFailure { Toast.makeText(context, "Could not open document: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // Initialize or load document
    LaunchedEffect(fileUri) {
        if (fileUri != null) {
            val existingIndex = tabs.indexOfFirst { it.filePath == fileUri }
            if (existingIndex >= 0) {
                selectedTabIndex = existingIndex
            } else {
                try {
                    val uri = Uri.parse(fileUri)
                    val tab = if (uri.scheme.equals("content", ignoreCase = true)) {
                        val meta = ContentUriFiles.metadata(context, uri)
                        val text = ContentUriFiles.readText(context, uri).getOrThrow()
                        EditorTab(meta.displayName, fileUri, text, initialSize = meta.size, initialModified = meta.lastModified)
                    } else {
                        val file = if (uri.scheme.equals("file", ignoreCase = true)) File(uri.path ?: fileUri) else File(fileUri)
                        require(file.isFile && file.canRead()) { "File is unavailable or unreadable." }
                        require(file.length() <= ContentUriFiles.MAX_TEXT_FILE_BYTES) { "Text file is larger than the 2 MiB editor limit." }
                        EditorTab(file.name, file.absolutePath, withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }, initialSize = file.length(), initialModified = file.lastModified())
                    }
                    tabs.add(tab)
                    selectedTabIndex = tabs.lastIndex
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (tabs.isEmpty()) {
            tabs.add(
                EditorTab(
                    title = "Untitled.txt",
                    filePath = null,
                    content = "// File Sizer Code & Text Editor\n// Create, edit, and inspect local source code.\n\nfun main() {\n    println(\"Hello, File Sizer!\")\n}\n"
                )
            )
        }
    }

    val currentTab = if (tabs.isNotEmpty() && selectedTabIndex in tabs.indices) tabs[selectedTabIndex] else null

    suspend fun saveTab(index: Int): Result<Unit> {
        val tab = tabs.getOrNull(index) ?: return Result.failure(IllegalStateException("No document is open."))
        val location = tab.filePath ?: return Result.failure(IllegalStateException("Choose Save As to save this new document."))
        val uri = Uri.parse(location)
        return if (uri.scheme.equals("content", ignoreCase = true)) {
            val meta = ContentUriFiles.metadata(context, uri)
            if ((tab.initialSize != null && meta.size >= 0 && meta.size != tab.initialSize) ||
                (tab.initialModified != null && meta.lastModified != null && meta.lastModified != tab.initialModified)
            ) {
                Result.failure(IllegalStateException("The document changed outside the editor. Reopen it before saving to avoid overwriting those changes."))
            } else ContentUriFiles.writeText(context, uri, tab.content)
        } else {
            saveLocalFile(File(location), tab)
        }
    }

    BackHandler(enabled = currentTab?.isDirty == true) { showDiscardDialog = true }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = (currentTab?.title ?: stringResource(R.string.action_code_editor)) + if (currentTab?.isDirty == true) " ●" else "",
            canNavigateBack = true,
            onNavigateBack = {
                if (tabs.any { it.isDirty }) showDiscardDialog = true else onNavigateBack()
            },
            actions = {
                IconButton(onClick = { openDocumentLauncher.launch(arrayOf("text/*", "application/json", "application/xml")) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Open another text document")
                }
                IconButton(
                    enabled = currentTab?.undoStack?.isNotEmpty() == true,
                    onClick = {
                        currentTab?.let { tab ->
                            tabs[selectedTabIndex] = tab.copy(
                                content = tab.undoStack.last(),
                                isDirty = true,
                                undoStack = tab.undoStack.dropLast(1),
                                redoStack = boundedUndo(tab.redoStack, tab.content)
                            )
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo") }
                IconButton(
                    enabled = currentTab?.redoStack?.isNotEmpty() == true,
                    onClick = {
                        currentTab?.let { tab ->
                            tabs[selectedTabIndex] = tab.copy(
                                content = tab.redoStack.last(),
                                isDirty = true,
                                redoStack = tab.redoStack.dropLast(1),
                                undoStack = boundedUndo(tab.undoStack, tab.content)
                            )
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo") }
                IconButton(onClick = { showFindReplace = !showFindReplace }) {
                    Icon(Icons.Rounded.FindReplace, contentDescription = "Find and Replace")
                }
                IconButton(onClick = { if (fontSize > 9f) fontSize -= 1f }) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out")
                }
                IconButton(onClick = { if (fontSize < 24f) fontSize += 1f }) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In")
                }
                IconButton(
                    onClick = {
                        val index = selectedTabIndex
                        currentTab?.let { tab ->
                            if (tab.filePath == null) {
                                saveAsLauncher.launch(tab.title)
                            } else scope.launch {
                                val result = saveTab(index)
                                if (result.isSuccess) {
                                    tabs[index] = tabs[index].copy(isDirty = false, initialSize = if (tabs[index].filePath?.startsWith("content:") == true) ContentUriFiles.metadata(context, Uri.parse(tabs[index].filePath!!)).size else File(tabs[index].filePath!!).length(), initialModified = if (tabs[index].filePath?.startsWith("content:") == true) ContentUriFiles.metadata(context, Uri.parse(tabs[index].filePath!!)).lastModified else File(tabs[index].filePath!!).lastModified())
                                    Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                                } else Toast.makeText(context, result.exceptionOrNull()?.message ?: "Save failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = "Save file",
                        tint = if (currentTab?.isDirty == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (currentTab?.filePath == null) {
                    IconButton(onClick = { currentTab?.let { saveAsLauncher.launch(it.title) } }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save As")
                    }
                }
            }
        )

        // Multi-Document Tabs
        if (tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${tab.title}${if (tab.isDirty) " ●" else ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (tab.isDirty) tabPendingClose = index else {
                                            tabs.removeAt(index)
                                            if (selectedTabIndex >= tabs.size) selectedTabIndex = (tabs.size - 1).coerceAtLeast(0)
                                        }
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // Find and Replace Bar
        AnimatedVisibility(visible = showFindReplace) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            placeholder = { Text("Find text...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (findQuery.isNotEmpty() && currentTab != null) {
                                    val count = currentTab.content.split(findQuery).size - 1
                                    tabs[selectedTabIndex] = currentTab.copy(
                                        content = currentTab.content.replace(findQuery, replaceQuery),
                                        isDirty = true,
                                        undoStack = boundedUndo(currentTab.undoStack, currentTab.content),
                                        redoStack = emptyList()
                                    )
                                    Toast.makeText(context, "Replaced $count occurrences", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Replace All")
                        }
                    }
                }
            }
        }

        // Status Bar
        if (currentTab != null) {
            val lines = remember(currentTab.content) { currentTab.content.lines() }
            val lineCount = lines.size
            val charCount = currentTab.content.length

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row {
                        Text(
                            text = "Lines: $lineCount | Chars: $charCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentTab.isDirty) {
                            Text(
                                text = "  ● Modified",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = "UTF-8 | ${fontSize.toInt()}sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editor Area with Line Numbers
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
                    val ext = remember(currentTab.title) { currentTab.title.substringAfterLast(".", "txt") }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(verticalScroll)
            ) {
                // Line Number Column
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    for (i in 1..lineCount.coerceAtMost(5_000)) {
                        Text(
                            text = "$i",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Editable Code Viewport
                BasicTextField(
                    value = currentTab.content,
                    onValueChange = {
                        tabs[selectedTabIndex] = currentTab.copy(
                            content = it,
                            isDirty = true,
                            undoStack = boundedUndo(currentTab.undoStack, currentTab.content),
                            redoStack = emptyList()
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = VisualTransformation { text ->
                        TransformedText(SyntaxHighlighter.highlight(text.text, ext), OffsetMapping.Identity)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("One or more open documents have edits that have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") } }
        )
    }

    if (tabPendingClose >= 0) {
        AlertDialog(
            onDismissRequest = { tabPendingClose = -1 },
            title = { Text("Close unsaved document?") },
            text = { Text("Unsaved edits in this tab will be discarded.") },
            confirmButton = {
                TextButton(onClick = {
                    val index = tabPendingClose
                    tabPendingClose = -1
                    if (index in tabs.indices) {
                        tabs.removeAt(index)
                        if (tabs.isEmpty()) {
                            tabs.add(EditorTab("Untitled.txt", null, ""))
                            selectedTabIndex = 0
                        } else if (selectedTabIndex >= tabs.size) selectedTabIndex = tabs.lastIndex
                    }
                }) { Text("Close tab") }
            },
            dismissButton = { TextButton(onClick = { tabPendingClose = -1 }) { Text("Cancel") } }
        )
    }
}

private suspend fun saveLocalFile(target: File, tab: EditorTab): Result<Unit> = withContext(Dispatchers.IO) {
    var stage: File? = null
    var backup: File? = null
    try {
        if (!target.isFile || !target.canWrite()) throw IllegalStateException("File is not writable.")
        if (tab.initialSize != null && target.length() != tab.initialSize) {
            throw IllegalStateException("The file changed outside the editor. Reopen it before saving to avoid overwriting those changes.")
        }
        if (tab.initialModified != null && target.lastModified() != tab.initialModified) {
            throw IllegalStateException("The file changed outside the editor. Reopen it before saving to avoid overwriting those changes.")
        }
        val parent = target.parentFile ?: throw IllegalArgumentException("File has no parent folder.")
        stage = File(parent, ".${target.name}.${UUID.randomUUID()}.partial")
        backup = File(parent, ".${target.name}.${UUID.randomUUID()}.backup")
        FileOutputStream(stage).use { output -> output.write(tab.content.toByteArray(Charsets.UTF_8)); output.fd.sync() }
        if (!target.renameTo(backup)) throw IllegalStateException("Could not safely preserve the original file before saving.")
        if (!stage.renameTo(target)) {
            backup.renameTo(target)
            throw IllegalStateException("Could not commit the edited file.")
        }
        backup.delete()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        stage?.takeIf { it.exists() }?.delete()
        backup?.takeIf { it.exists() && !target.exists() }?.renameTo(target)
    }
}

private fun boundedUndo(history: List<String>, snapshot: String): List<String> {
    val result = (history + snapshot).takeLast(10).toMutableList()
    var totalChars = result.sumOf { it.length }
    while (result.size > 1 && totalChars > ContentUriFiles.MAX_TEXT_FILE_BYTES) {
        totalChars -= result.removeAt(0).length
    }
    return result
}
