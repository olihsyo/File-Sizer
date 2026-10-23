package com.filesizer.app.feature.storageanalyzer

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.StorageUsageCard

@Composable
fun StorageAnalyzerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (usedBytes, totalBytes) = remember {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            Pair(total - free, total)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        FileSizerTopBar(
            title = stringResource(R.string.nav_analyzer),
            canNavigateBack = false
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                StorageUsageCard(
                    usedBytes = usedBytes,
                    totalBytes = totalBytes
                )
            }

            item {
                Text(
                    text = "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                CategoryItem(
                    name = "Images",
                    icon = Icons.Rounded.Image,
                    iconColor = Color(0xFF3B82F6),
                    iconBg = Color(0xFFEFF6FF),
                    countText = "Scan to analyze"
                )
            }

            item {
                CategoryItem(
                    name = "Videos",
                    icon = Icons.Rounded.VideoLibrary,
                    iconColor = Color(0xFF8B5CF6),
                    iconBg = Color(0xFFF5F3FF),
                    countText = "Scan to analyze"
                )
            }

            item {
                CategoryItem(
                    name = "Audio & Music",
                    icon = Icons.Rounded.MusicNote,
                    iconColor = Color(0xFFEC4899),
                    iconBg = Color(0xFFFDF2F8),
                    countText = "Scan to analyze"
                )
            }

            item {
                CategoryItem(
                    name = "Archives (ZIP / 7z)",
                    icon = Icons.Rounded.FolderZip,
                    iconColor = Color(0xFFD97706),
                    iconBg = Color(0xFFFEF3C7),
                    countText = "Scan to analyze"
                )
            }

            item {
                CategoryItem(
                    name = "Installation Packages (APK)",
                    icon = Icons.Rounded.Widgets,
                    iconColor = Color(0xFF10B981),
                    iconBg = Color(0xFFECFDF5),
                    countText = "Scan to analyze"
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    countText: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBg)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
