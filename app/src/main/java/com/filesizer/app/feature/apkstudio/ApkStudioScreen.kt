package com.filesizer.app.feature.apkstudio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar

@Composable
fun ApkStudioScreen(
    apkUri: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        FileSizerTopBar(
            title = stringResource(R.string.action_apk_studio),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "APK Package Inspection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                EmptyStateCard(
                    title = "No APK Selected",
                    description = "Choose an APK file from the file browser to inspect its manifest, activities, permissions, certificates, and assets.",
                    icon = Icons.Rounded.Widgets
                )
            }
        }
    }
}
