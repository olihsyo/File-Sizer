package com.filesizer.app.feature.storageanalyzer

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filesizer.app.R
import com.filesizer.app.core.analyzer.FileCategory
import com.filesizer.app.core.analyzer.ScannedFileInfo
import com.filesizer.app.core.analyzer.StorageScanEngine
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.StorageUsageCard
import com.filesizer.app.core.designsystem.components.formatFileSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var scanJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }
    val scanReport by StorageScanEngine.scanReport.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

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
            // StatFs Overview Card
            item {
                StorageUsageCard(
                    usedBytes = usedBytes,
                    totalBytes = totalBytes
                )
            }

            // Scan Action & Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (scanReport.isScanning) "Scanning Storage..." else "Deep Storage Analysis",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (scanReport.isScanning) {
                                        "${scanReport.scannedFilesCount} files scanned (${formatFileSize(scanReport.totalSizeBytes)})"
                                    } else if (scanReport.scannedFilesCount > 0) {
                                        "Scanned ${scanReport.scannedFilesCount} files (${formatFileSize(scanReport.totalSizeBytes)})"
                                    } else {
                                        "Scan storage to calculate exact category sizes and find large files."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    if (scanReport.isScanning) {
                                        scanJob?.cancel()
                                        scanJob = null
                                    } else {
                                        scanJob = coroutineScope.launch {
                                            StorageScanEngine.startScan(Environment.getExternalStorageDirectory())
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (scanReport.isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (scanReport.isScanning) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop")
                                } else {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan")
                                }
                            }
                        }

                        if (scanReport.isScanning) {
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Tabs for Breakdown, Largest Files, Duplicates
            item {
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Categories") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Largest (${scanReport.largestFiles.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Duplicates (${scanReport.potentialDuplicates.size})") }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // Category Breakdown Cards
                    items(FileCategory.values()) { category ->
                        val bytes = scanReport.categorySizes[category] ?: 0L
                        val count = scanReport.categoryCounts[category] ?: 0
                        val (icon, color, bg) = getCategoryStyling(category)
                        CategoryProgressItem(
                            category = category,
                            icon = icon,
                            iconColor = color,
                            iconBg = bg,
                            bytes = bytes,
                            fileCount = count,
                            totalAnalyzedBytes = scanReport.totalSizeBytes
                        )
                    }
                }
                1 -> {
                    // Largest Files List
                    if (scanReport.largestFiles.isEmpty()) {
                        item {
                            EmptyStateNotice(text = "Run a storage scan to discover the largest files consuming disk space.")
                        }
                    } else {
                        items(scanReport.largestFiles) { file ->
                            LargestFileCard(file = file)
                        }
                    }
                }
                2 -> {
                    // Duplicate Candidates
                    if (scanReport.potentialDuplicates.isEmpty()) {
                        item {
                            EmptyStateNotice(text = "No duplicate file candidates detected yet. Run scan to compare files.")
                        }
                    } else {
                        items(scanReport.potentialDuplicates) { group ->
                            DuplicateGroupCard(size = group.sizeBytes, files = group.files, sha256 = group.sha256)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryProgressItem(
    category: FileCategory,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    bytes: Long,
    fileCount: Int,
    totalAnalyzedBytes: Long,
    modifier: Modifier = Modifier
) {
    val percent = if (totalAnalyzedBytes > 0) (bytes.toFloat() / totalAnalyzedBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(iconBg)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (fileCount > 0) "$fileCount files" else "0 files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = formatFileSize(bytes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (totalAnalyzedBytes > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = iconColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun LargestFileCard(file: ScannedFileInfo) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            val (icon, color) = getCategoryStyling(file.category).let { Pair(it.first, it.second) }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatFileSize(file.size),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DuplicateGroupCard(size: Long, files: List<ScannedFileInfo>, sha256: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${files.size} Duplicates",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "${formatFileSize(size)} each",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SHA-256 ${sha256.take(16)}…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            files.forEach { file ->
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateNotice(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun getCategoryStyling(category: FileCategory): Triple<ImageVector, Color, Color> {
    return when (category) {
        FileCategory.IMAGES -> Triple(Icons.Rounded.Image, Color(0xFF3B82F6), Color(0xFFEFF6FF))
        FileCategory.VIDEOS -> Triple(Icons.Rounded.VideoLibrary, Color(0xFF8B5CF6), Color(0xFFF5F3FF))
        FileCategory.AUDIO -> Triple(Icons.Rounded.MusicNote, Color(0xFFEC4899), Color(0xFFFDF2F8))
        FileCategory.DOCUMENTS -> Triple(Icons.AutoMirrored.Rounded.InsertDriveFile, Color(0xFFEA580C), Color(0xFFFFF7ED))
        FileCategory.ARCHIVES -> Triple(Icons.Rounded.FolderZip, Color(0xFFD97706), Color(0xFFFEF3C7))
        FileCategory.APKS -> Triple(Icons.Rounded.Widgets, Color(0xFF10B981), Color(0xFFECFDF5))
        FileCategory.OTHERS -> Triple(Icons.AutoMirrored.Rounded.InsertDriveFile, Color(0xFF64748B), Color(0xFFF1F5F9))
    }
}
