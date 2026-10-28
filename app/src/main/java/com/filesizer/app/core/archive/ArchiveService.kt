package com.filesizer.app.core.archive

import com.filesizer.app.core.archive.model.ArchiveEntryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

data class ArchiveLimits(
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 512L * 1024 * 1024,
    val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxCompressionRatio: Long = 1_000
)

data class ArchiveFormatCapability(
    val extension: String,
    val list: Boolean,
    val extract: Boolean,
    val create: Boolean,
    val integrityTest: Boolean,
    val passwordProtection: Boolean = false,
    val notes: String
)

object ArchiveService {
    val supportedFormats = listOf(
        ArchiveFormatCapability(
            extension = "zip", list = true, extract = true, create = true, integrityTest = true,
            notes = "ZIP only. Encryption, password handling, split volumes, entry editing, TAR/7z/RAR and symlink metadata are unsupported."
        )
    )

    suspend fun listZipEntries(archiveFile: File): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        try {
            requireReadableArchive(archiveFile)
            val entries = mutableListOf<ArchiveEntryItem>()
            ZipFile(archiveFile).use { zip ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    operationContext.ensureActive()
                    if (entries.size >= ArchiveLimits().maxEntries) throw IllegalStateException("Archive exceeds the supported entry listing limit.")
                    val entry = enumeration.nextElement()
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun extractZipArchive(
        archiveFile: File,
        destinationDir: File,
        onProgress: ((extracted: Int, total: Int) -> Unit)? = null,
        limits: ArchiveLimits = ArchiveLimits()
    ): Result<Int> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        val stage = File(destinationDir, ".filesizer-extract-${UUID.randomUUID()}")
        val committed = mutableListOf<File>()
        try {
            requireReadableArchive(archiveFile)
            if (!destinationDir.exists() && !destinationDir.mkdirs()) throw IllegalStateException("Could not create extraction folder.")
            if (!destinationDir.isDirectory || !destinationDir.canWrite()) throw IllegalStateException("Extraction folder is not writable.")

            val entries = mutableListOf<ZipEntry>()
            val normalizedNames = HashSet<String>()
            ZipFile(archiveFile).use { zip ->
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    operationContext.ensureActive()
                    val entry = enumeration.nextElement()
                    if (entries.size >= limits.maxEntries) throw IllegalStateException("Archive exceeds the ${limits.maxEntries} entry safety limit.")
                    val target = ArchiveSecurity.resolveAndValidateEntry(stage, entry.name)
                    val normalized = target.canonicalPath
                    if (!normalizedNames.add(normalized)) throw IllegalStateException("Archive contains duplicate paths: ${entry.name}")
                    val out = ArchiveSecurity.resolveAndValidateEntry(destinationDir, entry.name)
                    if (out.exists()) throw IllegalStateException("Extraction would overwrite an existing item: ${entry.name}")
                    if (entry.size > limits.maxEntryBytes) throw IllegalStateException("Entry exceeds the extraction size limit: ${entry.name}")
                    if (entry.size > 0 && entry.compressedSize == 0L) throw IllegalStateException("Entry has an invalid compressed size: ${entry.name}")
                    if (entry.size >= 0 && entry.compressedSize > 0 && entry.size / entry.compressedSize > limits.maxCompressionRatio) {
                        throw IllegalStateException("Entry has a suspicious compression ratio: ${entry.name}")
                    }
                    entries.add(entry)
                }
            }
            if (!stage.mkdir()) throw IllegalStateException("Could not create a temporary extraction folder.")

            var totalWritten = 0L
            ZipFile(archiveFile).use { zip ->
                entries.forEachIndexed { index, entry ->
                    operationContext.ensureActive()
                    val target = ArchiveSecurity.resolveAndValidateEntry(stage, entry.name)
                    if (entry.isDirectory) {
                        if (!target.exists() && !target.mkdirs()) throw IllegalStateException("Could not create archive folder: ${entry.name}")
                    } else {
                        val parent = target.parentFile ?: throw IllegalStateException("Invalid entry path: ${entry.name}")
                        if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create folder for ${entry.name}")
                        var entryWritten = 0L
                        val crc = CRC32()
                        BufferedInputStream(zip.getInputStream(entry)).use { input ->
                            BufferedOutputStream(FileOutputStream(target)).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    operationContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    entryWritten += read
                                    totalWritten += read
                                    if (entryWritten > limits.maxEntryBytes || totalWritten > limits.maxTotalBytes) {
                                        throw IllegalStateException("Archive exceeds the configured extraction size limit.")
                                    }
                                    if (entry.compressedSize == 0L && entryWritten > 0L) throw IllegalStateException("Entry has an invalid compressed size: ${entry.name}")
                                    if (entry.compressedSize > 0 && entryWritten / entry.compressedSize > limits.maxCompressionRatio) {
                                        throw IllegalStateException("Entry has a suspicious compression ratio: ${entry.name}")
                                    }
                                    crc.update(buffer, 0, read)
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        if (entry.size >= 0 && entryWritten != entry.size) throw IllegalStateException("Entry size check failed: ${entry.name}")
                        if (entry.crc >= 0 && crc.value != entry.crc) throw IllegalStateException("Entry checksum check failed: ${entry.name}")
                    }
                    onProgress?.invoke(index + 1, entries.size)
                }
            }

            // Commit only after every entry has been fully written and verified. Any collision aborts the whole commit.
            val stagedEntries = stage.walkTopDown().filter { it != stage }.toList()
            val outputByStage = stagedEntries.associateWith { staged ->
                val relative = staged.relativeTo(stage).invariantSeparatorsPath
                ArchiveSecurity.resolveAndValidateEntry(destinationDir, relative)
            }
            outputByStage.values.forEach { output ->
                if (output.exists()) throw IllegalStateException("Extraction would overwrite an existing item: ${output.name}")
            }
            val directories = stagedEntries.filter { it.isDirectory }.sortedBy { it.relativeTo(stage).invariantSeparatorsPath.count { char -> char == '/' } }
            directories.forEach { stagedDir ->
                val output = outputByStage.getValue(stagedDir)
                if (!output.exists() && !output.mkdir()) throw IllegalStateException("Could not create extracted folder: ${output.name}")
                committed.add(output)
            }
            val files = stagedEntries.filter { it.isFile }
            files.forEach { stagedFile ->
                val output = outputByStage.getValue(stagedFile)
                output.parentFile?.let { parent -> if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create output folder.") }
                if (!stagedFile.renameTo(output)) throw IllegalStateException("Could not commit extracted file: ${output.name}")
                committed.add(output)
            }
            Result.success(entries.size)
        } catch (cancelled: CancellationException) {
            committed.asReversed().forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
            throw cancelled
        } catch (error: Exception) {
            committed.asReversed().forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
            Result.failure(error)
        } finally {
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    suspend fun createZipArchive(
        sources: List<File>,
        outputZipFile: File,
        compressionLevel: Int = 6,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        val temporaryOutput = File(outputZipFile.parentFile ?: File("."), ".${outputZipFile.name}.${UUID.randomUUID()}.partial")
        try {
            if (sources.isEmpty()) throw IllegalArgumentException("Select at least one item to archive.")
            val parent = outputZipFile.parentFile ?: throw IllegalArgumentException("Archive output must have a parent folder.")
            if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create archive destination.")
            if (!parent.isDirectory || !parent.canWrite()) throw IllegalStateException("Archive destination is not writable.")
            if (outputZipFile.exists()) throw IllegalStateException("Archive already exists. Choose another name to avoid overwriting it.")

            val allFilesToCompress = mutableListOf<Pair<File, String>>()
            val names = HashSet<String>()
            fun collect(file: File, relativePath: String) {
                operationContext.ensureActive()
                if (allFilesToCompress.size >= ArchiveLimits().maxEntries) throw IllegalStateException("Selection exceeds the ZIP entry safety limit.")
                if (!file.exists() || !file.canRead()) throw IllegalStateException("Source is unavailable: ${file.name}")
                val safe = relativePath.replace('\\', '/')
                if (safe.startsWith('/') || safe.split('/').any { it == ".." || it.isEmpty() }) throw IllegalArgumentException("Invalid archive entry name: $relativePath")
                if (!names.add(safe)) throw IllegalArgumentException("Duplicate archive entry name: $safe")
                if (file.isDirectory) {
                    val children = file.listFiles() ?: throw IllegalStateException("Could not read folder: ${file.name}")
                    if (children.isEmpty()) allFilesToCompress.add(file to "$safe/")
                    else children.forEach { child -> collect(child, "$safe/${child.name}") }
                } else allFilesToCompress.add(file to safe)
            }
            sources.forEach { collect(it, it.name) }

            val total = allFilesToCompress.size
            var current = 0
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temporaryOutput))).use { output ->
                output.setLevel(compressionLevel.coerceIn(0, 9))
                for ((file, pathInZip) in allFilesToCompress) {
                    operationContext.ensureActive()
                    val entry = ZipEntry(pathInZip).apply { time = file.lastModified() }
                    output.putNextEntry(entry)
                    if (file.isFile) {
                        BufferedInputStream(FileInputStream(file)).use { input ->
                            while (true) {
                                operationContext.ensureActive()
                                val length = input.read(buffer)
                                if (length < 0) break
                                output.write(buffer, 0, length)
                            }
                        }
                    }
                    output.closeEntry()
                    current++
                    onProgress?.invoke(current, total)
                }
            }
            if (!ZipFile(temporaryOutput).use { it.size() == total }) throw IllegalStateException("Created archive failed its entry-count check.")
            if (testZipIntegrity(temporaryOutput).getOrNull() != true) throw IllegalStateException("Created archive failed its integrity check.")
            if (outputZipFile.exists() || !temporaryOutput.renameTo(outputZipFile)) throw IllegalStateException("Could not commit archive output safely.")
            Result.success(outputZipFile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            if (temporaryOutput.exists()) temporaryOutput.delete()
        }
    }

    suspend fun testZipIntegrity(archiveFile: File, limits: ArchiveLimits = ArchiveLimits()): Result<Boolean> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        try {
            requireReadableArchive(archiveFile)
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0L
                var count = 0
                while (entries.hasMoreElements()) {
                    operationContext.ensureActive()
                    val entry = entries.nextElement()
                    count++
                    if (count > limits.maxEntries) throw IllegalStateException("Archive exceeds the integrity-test entry limit.")
                    val crc = CRC32()
                    var entryBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        while (true) {
                            operationContext.ensureActive()
                            val length = input.read(buffer)
                            if (length < 0) break
                            entryBytes += length
                            totalBytes += length
                            if (entryBytes > limits.maxEntryBytes || totalBytes > limits.maxTotalBytes) {
                                throw IllegalStateException("Archive exceeds the integrity-test size limit.")
                            }
                            crc.update(buffer, 0, length)
                        }
                    }
                    if (entryBytes > 0L && entry.compressedSize == 0L) throw IllegalStateException("Entry has an invalid compressed size: ${entry.name}")
                    if (entry.compressedSize > 0 && entryBytes / entry.compressedSize > limits.maxCompressionRatio) {
                        throw IllegalStateException("Entry has a suspicious compression ratio: ${entry.name}")
                    }
                    if (entry.crc >= 0 && crc.value != entry.crc) throw IllegalStateException("CRC mismatch for entry: ${entry.name}")
                }
            }
            Result.success(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun requireReadableArchive(file: File) {
        if (!file.isFile || !file.canRead()) throw IllegalArgumentException("Archive file does not exist or cannot be read.")
    }
}
