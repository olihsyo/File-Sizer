package com.filesizer.app.core.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class FileCategory(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio & Music"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives (ZIP/7z)"),
    APKS("APKs & Packages"),
    OTHERS("Other Files")
}

data class ScannedFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val category: FileCategory,
    val lastModified: Long
)

data class StorageScanReport(
    val isScanning: Boolean = false,
    val scannedFilesCount: Int = 0,
    val scannedDirsCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val categorySizes: Map<FileCategory, Long> = FileCategory.values().associateWith { 0L },
    val categoryCounts: Map<FileCategory, Int> = FileCategory.values().associateWith { 0 },
    val largestFiles: List<ScannedFileInfo> = emptyList(),
    val potentialDuplicates: Map<Long, List<ScannedFileInfo>> = emptyMap()
)

object StorageScanEngine {

    private val _scanReport = MutableStateFlow(StorageScanReport())
    val scanReport = _scanReport.asStateFlow()

    fun reset() {
        _scanReport.value = StorageScanReport()
    }

    suspend fun startScan(root: File) = withContext(Dispatchers.IO) {
        if (!root.exists() || !root.canRead()) return@withContext

        _scanReport.value = StorageScanReport(isScanning = true)

        var totalFiles = 0
        var totalDirs = 0
        var totalBytes = 0L
        val sizes = FileCategory.values().associateWith { 0L }.toMutableMap()
        val counts = FileCategory.values().associateWith { 0 }.toMutableMap()
        val allFiles = ArrayList<ScannedFileInfo>(500)
        val sizeGroupMap = HashMap<Long, MutableList<ScannedFileInfo>>()

        suspend fun scanDirectory(dir: File, depth: Int) {
            if (!coroutineContext.isActive || depth > 12) return
            totalDirs++

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (!coroutineContext.isActive) return

                if (file.isDirectory) {
                    scanDirectory(file, depth + 1)
                } else {
                    totalFiles++
                    val fileSize = file.length()
                    totalBytes += fileSize
                    val category = categorize(file.extension)

                    sizes[category] = (sizes[category] ?: 0L) + fileSize
                    counts[category] = (counts[category] ?: 0) + 1

                    val info = ScannedFileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        size = fileSize,
                        category = category,
                        lastModified = file.lastModified()
                    )

                    allFiles.add(info)

                    // Track potential duplicates for files > 1 MB
                    if (fileSize > 1024 * 1024) {
                        sizeGroupMap.getOrPut(fileSize) { ArrayList() }.add(info)
                    }

                    // Periodically update UI
                    if (totalFiles % 100 == 0) {
                        _scanReport.value = _scanReport.value.copy(
                            scannedFilesCount = totalFiles,
                            scannedDirsCount = totalDirs,
                            totalSizeBytes = totalBytes,
                            categorySizes = HashMap(sizes),
                            categoryCounts = HashMap(counts)
                        )
                    }
                }
            }
        }

        scanDirectory(root, 0)

        // Find top 25 largest files
        val largest = allFiles.sortedByDescending { it.size }.take(25)

        // Filter duplicates: groups with 2 or more files
        val duplicates = sizeGroupMap.filter { it.value.size >= 2 }

        _scanReport.value = StorageScanReport(
            isScanning = false,
            scannedFilesCount = totalFiles,
            scannedDirsCount = totalDirs,
            totalSizeBytes = totalBytes,
            categorySizes = sizes,
            categoryCounts = counts,
            largestFiles = largest,
            potentialDuplicates = duplicates
        )
    }

    fun categorize(extension: String): FileCategory {
        return when (extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> FileCategory.IMAGES
            "mp4", "mkv", "avi", "mov", "webm", "flv" -> FileCategory.VIDEOS
            "mp3", "wav", "flac", "ogg", "m4a", "aac" -> FileCategory.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> FileCategory.DOCUMENTS
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileCategory.ARCHIVES
            "apk", "aab", "xapk", "apks" -> FileCategory.APKS
            else -> FileCategory.OTHERS
        }
    }
}
