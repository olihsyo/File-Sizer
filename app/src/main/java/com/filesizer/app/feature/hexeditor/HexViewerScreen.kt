package com.filesizer.app.feature.hexeditor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.formatFileSize
import com.filesizer.app.core.storage.ContentUriFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class HexRow(val offset: Long, val hexString: String, val asciiString: String)

data class HexPage(
    val rows: List<HexRow>,
    val bytes: ByteArray,
    val totalBytes: Long,
    val fileName: String,
    val signature: String
)

object FileSignatureInspector {
    fun detect(bytes: ByteArray): String {
        fun startsWith(vararg expected: Int) = expected.indices.all { index ->
            index < bytes.size && (bytes[index].toInt() and 0xff) == expected[index]
        }
        return when {
            startsWith(0x50, 0x4b, 0x03, 0x04) || startsWith(0x50, 0x4b, 0x05, 0x06) -> "ZIP archive"
            startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "PNG image"
            startsWith(0xff, 0xd8, 0xff) -> "JPEG image"
            startsWith(0x25, 0x50, 0x44, 0x46, 0x2d) -> "PDF document"
            startsWith(0x1f, 0x8b) -> "GZIP stream"
            startsWith(0x7f, 0x45, 0x4c, 0x46) -> "ELF executable"
            bytes.size >= 4 && String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "dex\n" -> "DEX bytecode"
            bytes.size >= 4 && String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "\u0000asm" -> "WebAssembly module"
            else -> "Unknown or unrecognized signature"
        }
    }
}

@Composable
fun HexViewerScreen(
    filePath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isContentUri = remember(filePath) { Uri.parse(filePath.orEmpty()).scheme.equals("content", ignoreCase = true) }
    var page by remember(filePath) { mutableStateOf<HexPage?>(null) }
    var isLoading by remember(filePath) { mutableStateOf(false) }
    var startOffset by remember(filePath) { mutableStateOf(0L) }
    var offsetInput by remember(filePath) { mutableStateOf("0") }
    var searchInput by remember(filePath) { mutableStateOf("") }
    var searchMessage by remember(filePath) { mutableStateOf("") }
    var loadError by remember(filePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath, startOffset) {
        if (filePath == null) return@LaunchedEffect
        isLoading = true
        loadError = null
        page = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(filePath)
                val metadata = if (isContentUri) ContentUriFiles.metadata(context, uri) else null
                val file = if (isContentUri) null else File(if (uri.scheme.equals("file", ignoreCase = true)) uri.path ?: filePath else filePath)
                val totalBytes = metadata?.size?.coerceAtLeast(0L) ?: file?.length() ?: 0L
                require(isContentUri || (file?.isFile == true && file.canRead())) { "File is unavailable or unreadable." }
                if (startOffset > totalBytes && totalBytes > 0) throw IllegalArgumentException("Offset is past the end of the file.")
                val input: InputStream = (if (isContentUri) context.contentResolver.openInputStream(uri) else FileInputStream(file!!))
                    ?: throw IllegalStateException("Could not open selected file.")
                input.use { stream ->
                    skipExactly(stream, startOffset)
                    val buffer = ByteArray(64 * 1024)
                    var used = 0
                    while (used < buffer.size) {
                        val read = stream.read(buffer, used, buffer.size - used)
                        if (read < 0) break
                        used += read
                    }
                    val bytes = buffer.copyOf(used)
                    val rows = bytes.indices.step(16).map { offset ->
                        val rowLength = (bytes.size - offset).coerceAtMost(16)
                        val hex = (0 until rowLength).joinToString(" ") { index -> "%02X".format(bytes[offset + index]) }
                        val paddedHex = hex.padEnd(47, ' ')
                        val ascii = (0 until rowLength).joinToString("") { index ->
                            val char = (bytes[offset + index].toInt() and 0xff).toChar()
                            if (char in ' '..'~') char.toString() else "."
                        }
                        HexRow(startOffset + offset, paddedHex, ascii)
                    }
                    HexPage(rows, bytes, totalBytes, metadata?.displayName ?: file!!.name, FileSignatureInspector.detect(bytes.take(16).toByteArray()))
                }
            } catch (error: Exception) {
                loadError = error.message ?: "Could not read selected file."
                null
            }
        }
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = page?.fileName ?: "Hex Viewer",
            canNavigateBack = true,
            onNavigateBack = onNavigateBack
        )

        if (page != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Bytes ${formatFileSize(startOffset)}–${formatFileSize(startOffset + page!!.bytes.size)} of ${formatFileSize(page!!.totalBytes)} · read-only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Detected signature: ${page!!.signature}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = offsetInput,
                onValueChange = { offsetInput = it },
                label = { Text("Offset (decimal)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val offset = offsetInput.toLongOrNull()
                if (offset == null || offset < 0L) searchMessage = "Enter a valid non-negative offset."
                else { startOffset = offset; searchMessage = "" }
            }, modifier = Modifier.align(Alignment.CenterVertically)) { Text("Go") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("Search loaded bytes") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                val data = page?.bytes ?: byteArrayOf()
                val query = searchInput.toByteArray(Charsets.UTF_8)
                val index = if (query.isEmpty()) -1 else data.indexOfBytes(query)
                searchMessage = if (index >= 0) "Found at offset ${startOffset + index}" else "Not found in this 64 KiB page"
            }, modifier = Modifier.align(Alignment.CenterVertically)) { Text("Find") }
        }
        if (searchMessage.isNotEmpty()) {
            Text(searchMessage, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator() }
        } else if (page == null) {
            EmptyStateCard(
                title = if (filePath == null) "No File Loaded" else "File unavailable",
                description = loadError ?: "Select a file to inspect its raw bytes.",
                icon = Icons.Rounded.DataArray
            )
        } else if (page!!.rows.isEmpty()) {
            EmptyStateCard(title = "No bytes at this offset", description = "Change the offset to inspect another part of the file.", icon = Icons.Rounded.DataArray)
        } else {
            val horizontalScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).horizontalScroll(horizontalScroll).padding(8.dp)
            ) {
                item {
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(vertical = 4.dp, horizontal = 8.dp)) {
                        Text("Offset    ", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Text("00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  ", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("ASCII", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                itemsIndexed(page!!.rows) { _, row ->
                    Row(modifier = Modifier.padding(vertical = 1.dp, horizontal = 8.dp)) {
                        Text("%08X: ".format(row.offset), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = MaterialTheme.colorScheme.primary)
                        Text("${row.hexString}  ", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = MaterialTheme.colorScheme.onBackground)
                        Text(row.asciiString, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

private fun skipExactly(input: InputStream, count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) remaining -= skipped else if (input.read() < 0) throw IllegalArgumentException("Offset is past the end of the file.") else remaining--
    }
}

private fun ByteArray.indexOfBytes(query: ByteArray): Int {
    if (query.isEmpty() || query.size > size) return -1
    outer@ for (index in 0..size - query.size) {
        for (offset in query.indices) if (this[index + offset] != query[offset]) continue@outer
        return index
    }
    return -1
}
