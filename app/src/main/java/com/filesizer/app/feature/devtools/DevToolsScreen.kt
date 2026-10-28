package com.filesizer.app.feature.devtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.devtools.DevToolsService
import com.filesizer.app.core.storage.ContentUriFiles
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = "Developer Utilities",
            canNavigateBack = true,
            onNavigateBack = onNavigateBack
        )

        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Hashes") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Base64 & URL") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("JSON Tools") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("XML Tools") })
            Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Text Diff") })
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        HashCalculatorCard(context = context)
                    }
                }
                1 -> {
                    item {
                        EncoderDecoderCard(context = context)
                    }
                }
                2 -> {
                    item {
                        JsonFormatterCard(context = context)
                    }
                }
                3 -> item { XmlFormatterCard() }
                4 -> item { TextDiffCard() }
            }
        }
    }
}

@Composable
private fun XmlFormatterCard() {
    var rawXml by remember { mutableStateOf("<fileSizer><version>2</version></fileSizer>") }
    var formatted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("XML Formatter & Validator", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(rawXml, { rawXml = it; error = "" }, label = { Text("XML input") }, modifier = Modifier.fillMaxWidth().height(160.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val result = DevToolsService.formatXml(rawXml)
                formatted = result.getOrNull().orEmpty()
                error = result.exceptionOrNull()?.message.orEmpty()
            }) { Text("Format & Validate") }
            if (error.isNotEmpty()) Text("Invalid XML: $error", color = MaterialTheme.colorScheme.error)
            if (formatted.isNotEmpty()) Text(formatted, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun TextDiffCard() {
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Line-by-line text comparison", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(before, { before = it }, label = { Text("Original text") }, modifier = Modifier.fillMaxWidth().height(120.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(after, { after = it }, label = { Text("Updated text") }, modifier = Modifier.fillMaxWidth().height(120.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { output = DevToolsService.diffText(before, after) }) { Text("Compare") }
            if (output.isNotEmpty()) Text(output, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            Text("Comparison is positional by line and is not a minimal patch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HashCalculatorCard(context: Context) {
    var inputText by remember { mutableStateOf("Hello File Sizer") }
    var selectedAlgo by remember { mutableStateOf("SHA-256") }
    var fileHash by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf("") }
    var isHashingFile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileName = ContentUriFiles.metadata(context, uri).displayName
            isHashingFile = true
            scope.launch {
                val result = ContentUriFiles.calculateHash(context, uri, selectedAlgo)
                fileHash = result.getOrElse { "Error: ${it.message}" }
                isHashingFile = false
            }
        }
    }
    val hash = remember(inputText, selectedAlgo) {
        if (inputText.isEmpty()) "" else DevToolsService.calculateHash(inputText, selectedAlgo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Hash Generator",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Input Text") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MD5", "SHA-1", "SHA-256", "SHA-512").forEach { algo ->
                    FilterChip(
                        selected = selectedAlgo == algo,
                        onClick = { selectedAlgo = algo },
                        label = { Text(algo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Computed $selectedAlgo Hash:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hash,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Hash", hash))
                        Toast.makeText(context, "Hash copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Hash")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !isHashingFile) {
                Text(if (isHashingFile) "Calculating file hash…" else "Select a file to hash")
            }
            if (selectedFileName.isNotEmpty()) {
                Text(selectedFileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fileHash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun EncoderDecoderCard(context: Context) {
    var text by remember { mutableStateOf("File Sizer") }
    var output by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Encoder & Decoder",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Input String") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { output = DevToolsService.base64Encode(text) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Base64 Enc")
                }
                Button(
                    onClick = { output = DevToolsService.base64Decode(text) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Base64 Dec")
                }
                Button(
                    onClick = { output = DevToolsService.urlEncode(text) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("URL Enc")
                }
            }

            if (output.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Result:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Result", output))
                            Toast.makeText(context, "Result copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
    }
}

@Composable
fun JsonFormatterCard(context: Context) {
    var rawJson by remember { mutableStateOf("{\"name\":\"File Sizer\",\"version\":2,\"features\":[\"manager\",\"archive\",\"apk\"]}") }
    var formatted by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "JSON Formatter & Validator",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = rawJson,
                onValueChange = {
                    rawJson = it
                    isError = false
                },
                label = { Text("Raw JSON String") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val res = DevToolsService.formatJson(rawJson)
                    if (res.isSuccess) {
                        formatted = res.getOrNull() ?: ""
                        isError = false
                    } else {
                        formatted = "Syntax Error: ${res.exceptionOrNull()?.message}"
                        isError = true
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Format & Validate")
            }

            if (formatted.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isError) "Validation Failed:" else "Formatted JSON (2 spaces):",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isError) {
                        IconButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("JSON", formatted))
                                Toast.makeText(context, "Formatted JSON copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
