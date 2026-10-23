package com.filesizer.app.core.archive

import com.filesizer.app.core.archive.model.ArchiveEntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveService {

    suspend fun listZipEntries(archiveFile: File): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists() || !archiveFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Archive file does not exist or cannot be read."))
            }
            val entries = mutableListOf<ArchiveEntryItem>()
            ZipFile(archiveFile).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    val cleanPath = entry.name.trimEnd('/')
                    val name = cleanPath.substringAfterLast('/')
                    entries.add(
                        ArchiveEntryItem(
                            name = if (name.isEmpty()) cleanPath else name,
                            path = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            crc = entry.crc
                        )
                    )
                }
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractZipArchive(
        archiveFile: File,
        destinationDir: File,
        onProgress: ((extracted: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }

            var totalCount = 0
            ZipFile(archiveFile).use { zip ->
                totalCount = zip.size()
            }

            var extractedCount = 0
            val buffer = ByteArray(8192)

            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry
                while (zipEntry != null) {
                    val targetFile = ArchiveSecurity.resolveAndValidateEntry(destinationDir, zipEntry.name)

                    if (zipEntry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(targetFile)).use { bos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    extractedCount++
                    onProgress?.invoke(extractedCount, totalCount)
                    zipEntry = zis.nextEntry
                }
            }
            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createZipArchive(
        sources: List<File>,
        outputZipFile: File,
        compressionLevel: Int = 6,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputZipFile.parentFile?.mkdirs()
            val allFilesToCompress = mutableListOf<Pair<File, String>>()

            fun collect(file: File, relativePath: String) {
                if (file.isDirectory) {
                    val children = file.listFiles() ?: return
                    if (children.isEmpty()) {
                        // Empty directory
                        allFilesToCompress.add(Pair(file, "$relativePath/"))
                    } else {
                        for (child in children) {
                            collect(child, if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}")
                        }
                    }
                } else {
                    allFilesToCompress.add(Pair(file, relativePath))
                }
            }

            for (src in sources) {
                collect(src, src.name)
            }

            val total = allFilesToCompress.size
            var current = 0
            val buffer = ByteArray(8192)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                zos.setLevel(compressionLevel.coerceIn(0, 9))

                for ((file, pathInZip) in allFilesToCompress) {
                    if (file.isDirectory) {
                        val entry = ZipEntry(if (pathInZip.endsWith("/")) pathInZip else "$pathInZip/")
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        zos.closeEntry()
                    } else {
                        val entry = ZipEntry(pathInZip)
                        entry.time = file.lastModified()
                        entry.size = file.length()
                        zos.putNextEntry(entry)

                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                    }
                    current++
                    onProgress?.invoke(current, total)
                }
            }

            Result.success(outputZipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testZipIntegrity(archiveFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(8192)
            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
                var zipEntry: ZipEntry? = zis.nextEntry
                while (zipEntry != null) {
                    val crc = CRC32()
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        crc.update(buffer, 0, len)
                    }
                    if (zipEntry.crc != -1L && crc.value != zipEntry.crc) {
                        return@withContext Result.failure(IllegalStateException("CRC mismatch for entry: ${zipEntry.name}"))
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
