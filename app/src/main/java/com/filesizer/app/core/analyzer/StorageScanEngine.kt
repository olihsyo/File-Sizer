package com.filesizer.app.core.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.PriorityQueue
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class FileCategory(val displayName: String) {
    IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio & Music"), DOCUMENTS("Documents"),
    ARCHIVES("Archives (ZIP/7z)"), APKS("APKs & Packages"), OTHERS("Other Files")
}

data class ScannedFileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val category: FileCategory,
    val lastModified: Long
)

data class DuplicateFileGroup(
    val sizeBytes: Long,
    val sha256: String,
    val files: List<ScannedFileInfo>
)

data class StorageScanReport(
    val isScanning: Boolean = false,
    val scannedFilesCount: Int = 0,
    val scannedDirsCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val categorySizes: Map<FileCategory, Long> = FileCategory.values().associateWith { 0L },
    val categoryCounts: Map<FileCategory, Int> = FileCategory.values().associateWith { 0 },
    val largestFiles: List<ScannedFileInfo> = emptyList(),
    val potentialDuplicates: List<DuplicateFileGroup> = emptyList(),
    val unreadableDirectoriesCount: Int = 0
)

object StorageScanEngine {
    private const val MAX_DEPTH = 64
    private const val MAX_DUPLICATE_CANDIDATES = 20_000
    private const val DUPLICATE_MIN_BYTES = 1024L * 1024

    private val _scanReport = MutableStateFlow(StorageScanReport())
    val scanReport = _scanReport.asStateFlow()

    fun reset() { _scanReport.value = StorageScanReport() }

    suspend fun startScan(root: File) = withContext(Dispatchers.IO) {
        if (!root.exists() || !root.canRead()) return@withContext
        val operationContext = coroutineContext
        _scanReport.value = StorageScanReport(isScanning = true)

        var totalFiles = 0
        var totalDirs = 0
        var totalBytes = 0L
        var unreadableDirectories = 0
        val sizes = FileCategory.values().associateWith { 0L }.toMutableMap()
        val counts = FileCategory.values().associateWith { 0 }.toMutableMap()
        val largest = PriorityQueue<ScannedFileInfo>(25, compareBy { it.size })
        val sizeCandidates = HashMap<Long, MutableList<ScannedFileInfo>>()
        var candidateCount = 0
        val visitedDirectories = HashSet<String>()
        val duplicates = mutableListOf<DuplicateFileGroup>()

        fun publishPartial() {
            _scanReport.value = StorageScanReport(
                isScanning = true,
                scannedFilesCount = totalFiles,
                scannedDirsCount = totalDirs,
                totalSizeBytes = totalBytes,
                categorySizes = sizes.toMap(),
                categoryCounts = counts.toMap(),
                largestFiles = largest.toList().sortedByDescending { it.size },
                unreadableDirectoriesCount = unreadableDirectories
            )
        }

        suspend fun scanDirectory(directory: File, depth: Int) {
            operationContext.ensureActive()
            if (depth > MAX_DEPTH) return
            val canonical = runCatching { directory.canonicalPath }.getOrNull() ?: return
            if (!visitedDirectories.add(canonical)) return
            totalDirs++
            val children = directory.listFiles()
            if (children == null) {
                unreadableDirectories++
                return
            }
            for (file in children) {
                operationContext.ensureActive()
                if (file.isDirectory) {
                    scanDirectory(file, depth + 1)
                } else if (file.isFile) {
                    totalFiles++
                    val length = file.length()
                    totalBytes += length
                    val category = categorize(file.extension)
                    sizes[category] = (sizes[category] ?: 0L) + length
                    counts[category] = (counts[category] ?: 0) + 1
                    val info = ScannedFileInfo(file.name, file.absolutePath, length, category, file.lastModified())
                    if (largest.size < 25) largest.add(info)
                    else if (largest.peek()?.let { length > it.size } == true) {
                        largest.poll()
                        largest.add(info)
                    }
                    if (length >= DUPLICATE_MIN_BYTES && candidateCount < MAX_DUPLICATE_CANDIDATES) {
                        sizeCandidates.getOrPut(length) { mutableListOf() }.add(info)
                        candidateCount++
                    }
                    if (totalFiles % 100 == 0) publishPartial()
                }
            }
        }

        try {
            scanDirectory(root, 0)
            for ((size, candidates) in sizeCandidates) {
                operationContext.ensureActive()
                if (candidates.size < 2) continue
                val byHash = HashMap<String, MutableList<ScannedFileInfo>>()
                for (candidate in candidates) {
                    operationContext.ensureActive()
                    val hash = hashFile(candidate.path) ?: continue
                    byHash.getOrPut(hash) { mutableListOf() }.add(candidate)
                }
                byHash.forEach { (hash, files) ->
                    if (files.size > 1) duplicates += DuplicateFileGroup(size, hash, files.toList())
                }
            }
        } finally {
            _scanReport.value = StorageScanReport(
                isScanning = false,
                scannedFilesCount = totalFiles,
                scannedDirsCount = totalDirs,
                totalSizeBytes = totalBytes,
                categorySizes = sizes.toMap(),
                categoryCounts = counts.toMap(),
                largestFiles = largest.toList().sortedByDescending { it.size },
                potentialDuplicates = duplicates.toList(),
                unreadableDirectoriesCount = unreadableDirectories
            )
        }
    }

    private suspend fun hashFile(path: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(File(path)).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val length = input.read(buffer)
                    if (length < 0) break
                    digest.update(buffer, 0, length)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    fun categorize(extension: String): FileCategory = when (extension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> FileCategory.IMAGES
        "mp4", "mkv", "avi", "mov", "webm", "flv" -> FileCategory.VIDEOS
        "mp3", "wav", "flac", "ogg", "m4a", "aac" -> FileCategory.AUDIO
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> FileCategory.DOCUMENTS
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileCategory.ARCHIVES
        "apk", "aab", "xapk", "apks" -> FileCategory.APKS
        else -> FileCategory.OTHERS
    }
}
