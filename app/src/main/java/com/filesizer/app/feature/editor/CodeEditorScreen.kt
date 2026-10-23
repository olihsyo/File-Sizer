package com.filesizer.app.feature.editor

import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorTab(
    val title: String,
    val filePath: String?,
    var content: String,
    var isDirty: Boolean = false
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

    // Initialize or load document
    LaunchedEffect(fileUri) {
        if (fileUri != null) {
            val file = File(fileUri)
            if (file.exists() && file.isFile) {
                withContext(Dispatchers.IO) {
                    try {
                        val text = file.readText()
                        withContext(Dispatchers.Main) {
                            val existingIndex = tabs.indexOfFirst { it.filePath == fileUri }
                            if (existingIndex >= 0) {
                                selectedTabIndex = existingIndex
                            } else {
                                tabs.add(EditorTab(title = file.name, filePath = fileUri, content = text))
                                selectedTabIndex = tabs.size - 1
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
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

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = currentTab?.title ?: stringResource(R.string.action_code_editor),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
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
                        currentTab?.let { tab ->
                            if (tab.filePath != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        File(tab.filePath).writeText(tab.content)
                                        withContext(Dispatchers.Main) {
                                            tab.isDirty = false
                                            Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Untitled document. Use Save As in file manager.", Toast.LENGTH_SHORT).show()
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
                                        tabs.removeAt(index)
                                        if (selectedTabIndex >= tabs.size) {
                                            selectedTabIndex = (tabs.size - 1).coerceAtLeast(0)
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
                                    currentTab.content = currentTab.content.replace(findQuery, replaceQuery)
                                    currentTab.isDirty = true
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
            val ext = remember(currentTab.filePath) { currentTab.filePath?.substringAfterLast(".", "") ?: "txt" }

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
                    for (i in 1..lineCount) {
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
                        currentTab.content = it
                        currentTab.isDirty = true
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
