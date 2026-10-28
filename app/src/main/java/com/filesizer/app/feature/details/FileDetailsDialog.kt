package com.filesizer.app.feature.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.formatFileSize
import com.filesizer.app.core.operations.FileOperationService
import com.filesizer.app.core.database.DatabaseRepository
import com.filesizer.app.core.storage.ContentUriFiles
import com.filesizer.app.core.storage.DocumentTreeProvider
import com.filesizer.app.feature.filemanager.FileItem
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileDetailsDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var sha256Hash by remember { mutableStateOf<String?>(null) }
    var isCalculatingHash by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var providerCanWrite by remember { mutableStateOf<Boolean?>(null) }
    val repository = remember(context) { DatabaseRepository(context) }
    val isContentUri = remember(item.path) { Uri.parse(item.path).scheme.equals("content", ignoreCase = true) }

    val fileObj = remember(item.path) { File(item.path) }
    val dateFormatted = remember(item.lastModified) {
        if (item.lastModified <= 0L) "Unavailable from this storage provider" else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(item.lastModified))
        }
    }

    LaunchedEffect(item.path) {
        isFavorite = repository.isFavorite(item.path)
        providerCanWrite = if (isContentUri) {
            DocumentTreeProvider.getDocument(context, Uri.parse(item.path)).getOrNull()?.canWrite
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.action_details),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Favorite", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = {
                        coroutineScope.launch {
                            isFavorite = repository.toggleFavorite(item.name, item.path, item.isDirectory)
                        }
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DetailRow(label = "Name", value = item.name)
                DetailRow(label = "Type", value = if (item.isDirectory) "Folder" else item.extension.uppercase().ifEmpty { "File" })
                DetailRow(
                    label = "Size",
                    value = if (item.isDirectory) "Directory" else if (item.size < 0L) "Unavailable from this storage provider" else "${formatFileSize(item.size)} (${item.size} bytes)"
                )
                DetailRow(label = "Path", value = item.path)
                DetailRow(label = "Modified", value = dateFormatted)
                val access = if (isContentUri) {
                    if (providerCanWrite != null) "Read: provider granted | Write: ${if (providerCanWrite == true) "available" else "unavailable"}"
                    else "Read/write access is controlled by the selected document provider."
                } else "Read: ${if (fileObj.canRead()) "Yes" else "No"} | Write: ${if (fileObj.canWrite()) "Yes" else "No"}"
                DetailRow(label = "Access", value = access)

                if (!item.isDirectory) {
                    Spacer(modifier = Modifier.height(6.dp))

                    if (sha256Hash != null) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SHA-256 Checksum",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256Hash))
                                        Toast.makeText(context, "Checksum copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy Hash",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = sha256Hash!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isCalculatingHash = true
                                coroutineScope.launch {
                                    val result = if (isContentUri) {
                                        ContentUriFiles.calculateHash(context, Uri.parse(item.path))
                                    } else FileOperationService.calculateSha256(fileObj)
                                    sha256Hash = result.getOrNull() ?: "Calculation failed"
                                    isCalculatingHash = false
                                }
                            },
                            enabled = !isCalculatingHash,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCalculatingHash) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Calculating SHA-256...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compute SHA-256 Checksum")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
