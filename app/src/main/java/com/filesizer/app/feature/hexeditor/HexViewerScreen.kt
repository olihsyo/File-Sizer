package com.filesizer.app.feature.hexeditor

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class HexRow(
    val offset: Long,
    val hexString: String,
    val asciiString: String
)

@Composable
fun HexViewerScreen(
    filePath: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rows by remember { mutableStateOf<List<HexRow>>(emptyList()) }
    var totalBytesLoaded by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    val fileObj = remember(filePath) { if (filePath != null) File(filePath) else null }

    LaunchedEffect(filePath) {
        if (fileObj != null && fileObj.exists() && fileObj.isFile) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    // Safe limit: load up to first 64KB as specified in PRD safeguard rules
                    val maxBytes = 64 * 1024
                    val buffer = ByteArray(maxBytes)
                    val bytesRead = FileInputStream(fileObj).use { it.read(buffer) }

                    if (bytesRead > 0) {
                        val parsedRows = mutableListOf<HexRow>()
                        var offset = 0L
                        for (i in 0 until bytesRead step 16) {
                            val chunkLen = (bytesRead - i).coerceAtMost(16)
                            val hexBuilder = StringBuilder()
                            val asciiBuilder = StringBuilder()

                            for (j in 0 until chunkLen) {
                                val b = buffer[i + j]
                                hexBuilder.append("%02X ".format(b))
                                val c = b.toInt().toChar()
                                if (c in ' '..'~') {
                                    asciiBuilder.append(c)
                                } else {
                                    asciiBuilder.append('.')
                                }
                            }

                            // Pad row if less than 16 bytes
                            if (chunkLen < 16) {
                                for (p in chunkLen until 16) {
                                    hexBuilder.append("   ")
                                }
                            }

                            parsedRows.add(HexRow(offset, hexBuilder.toString().trimEnd(), asciiBuilder.toString()))
                            offset += 16
                        }
                        rows = parsedRows
                        totalBytesLoaded = bytesRead
                    }
                } catch (_: Exception) {}
            }
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = fileObj?.name ?: stringResource(R.string.action_hex_viewer),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack
        )

        // Header info bar
        if (fileObj != null) {
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
                        text = "Loaded: ${formatFileSize(totalBytesLoaded.toLong())} of ${formatFileSize(fileObj.length())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Read-Only Mode (64KB Buffer)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        } else if (fileObj == null || rows.isEmpty()) {
            EmptyStateCard(
                title = "No File Loaded",
                description = "Select any binary or text file to inspect its raw byte offsets and ASCII structure.",
                icon = Icons.Rounded.DataArray
            )
        } else {
            val horizontalScroll = rememberScrollState()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .horizontalScroll(horizontalScroll)
                    .padding(8.dp)
            ) {
                // Table header
                item {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Offset    ",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  ",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ASCII",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                itemsIndexed(rows) { _, row ->
                    Row(modifier = Modifier.padding(vertical = 1.dp, horizontal = 8.dp)) {
                        Text(
                            text = "%08X: ".format(row.offset),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${row.hexString}  ",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = row.asciiString,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
