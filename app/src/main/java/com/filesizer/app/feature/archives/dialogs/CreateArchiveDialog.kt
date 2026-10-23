package com.filesizer.app.feature.archives.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.filesizer.app.R

@Composable
fun CreateArchiveDialog(
    defaultName: String = "Archive.zip",
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (archiveName: String, compressionLevel: Int) -> Unit
) {
    var archiveName by remember { mutableStateOf(defaultName) }
    var compressionLevel by remember { mutableIntStateOf(6) } // 1 = Fast, 6 = Normal, 9 = Maximum
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Compress $itemCount Item(s)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = {
                        archiveName = it
                        isError = false
                    },
                    label = { Text("Archive Name (.zip)") },
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) { { Text("Please provide a valid filename") } } else null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Compression Level",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = compressionLevel == 1,
                        onClick = { compressionLevel = 1 },
                        label = { Text("Fast") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = compressionLevel == 6,
                        onClick = { compressionLevel = 6 },
                        label = { Text("Normal") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = compressionLevel == 9,
                        onClick = { compressionLevel = 9 },
                        label = { Text("Max") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = archiveName.trim()
                    if (trimmed.isEmpty()) {
                        isError = true
                    } else {
                        val finalName = if (trimmed.endsWith(".zip", ignoreCase = true)) trimmed else "$trimmed.zip"
                        onConfirm(finalName, compressionLevel)
                    }
                }
            ) {
                Text(text = stringResource(R.string.action_compress))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}
