package com.filesizer.app.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.FileSizerTopBar

@Composable
fun CodeEditorScreen(
    fileUri: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var content by remember { mutableStateOf("// Welcome to File Sizer Code & Text Editor\n// Open or create files to begin editing.\n") }
    var isDirty by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        FileSizerTopBar(
            title = if (fileUri != null) fileUri.substringAfterLast("/") else stringResource(R.string.action_code_editor),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
                IconButton(
                    onClick = { isDirty = false }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = "Save file",
                        tint = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // Status bar showing line count and state
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Lines: ${content.lines().size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                if (isDirty) {
                    Text(
                        text = "● Unsaved edits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Code Editor text area
        BasicTextField(
            value = content,
            onValueChange = {
                content = it
                isDirty = true
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        )
    }
}
