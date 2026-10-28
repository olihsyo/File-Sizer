package com.filesizer.app

import com.filesizer.app.core.archive.ArchiveSecurity
import com.filesizer.app.core.archive.ArchiveService
import com.filesizer.app.core.archive.ArchiveLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSlipDefenseRejectsEscapingPaths() {
        val destDir = tempFolder.newFolder("safe_extract_dir")

        // Safe relative path should succeed
        val validFile = ArchiveSecurity.resolveAndValidateEntry(destDir, "subfolder/hello.txt")
        assertTrue(validFile.canonicalPath.startsWith(destDir.canonicalPath))

        // Path traversal should throw SecurityException
        var caughtException = false
        try {
            ArchiveSecurity.resolveAndValidateEntry(destDir, "../../../evil.sh")
        } catch (e: SecurityException) {
            caughtException = true
        }
        assertTrue("Expected SecurityException on Zip Slip traversal", caughtException)

        // Traversal with nested dots
        var caughtNested = false
        try {
            ArchiveSecurity.resolveAndValidateEntry(destDir, "nested/../../outside.txt")
        } catch (e: SecurityException) {
            caughtNested = true
        }
        assertTrue("Expected SecurityException on nested traversal", caughtNested)
    }

    @Test
    fun testCreateAndListAndExtractArchive() = runBlocking {
        val root = tempFolder.root
        val sourceDir = File(root, "docs_to_pack").apply { mkdirs() }
        val file1 = File(sourceDir, "readme.txt").apply { writeText("File Sizer Archive Test") }
        val subDir = File(sourceDir, "sub").apply { mkdirs() }
        val file2 = File(subDir, "info.json").apply { writeText("{\"version\": 2}") }

        val zipOut = File(root, "test_output.zip")

        // 1. Create Archive
        val createResult = ArchiveService.createZipArchive(
            sources = listOf(file1, file2),
            outputZipFile = zipOut
        )
        assertTrue(createResult.isSuccess)
        assertTrue(zipOut.exists())

        // 2. List Archive Entries
        val listResult = ArchiveService.listZipEntries(zipOut)
        assertTrue(listResult.isSuccess)
        val entries = listResult.getOrNull()!!
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "readme.txt" })
        assertTrue(entries.any { it.name == "info.json" })

        // 3. Test Integrity
        val integrityResult = ArchiveService.testZipIntegrity(zipOut)
        assertTrue(integrityResult.isSuccess)
        assertTrue(integrityResult.getOrNull() == true)

        // 4. Extract Archive
        val extractDir = File(root, "extracted_output").apply { mkdirs() }
        val extractResult = ArchiveService.extractZipArchive(zipOut, extractDir)
        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrNull())

        val extracted1 = File(extractDir, "readme.txt")
        val extracted2 = File(extractDir, "info.json")
        assertTrue(extracted1.exists())
        assertEquals("File Sizer Archive Test", extracted1.readText())
        assertTrue(extracted2.exists())
        assertEquals("{\"version\": 2}", extracted2.readText())
    }

    @Test
    fun extractionRefusesToOverwriteExistingFiles() = runBlocking {
        val root = tempFolder.root
        val source = File(root, "source.txt").apply { writeText("replacement") }
        val archive = File(root, "safe.zip")
        assertTrue(ArchiveService.createZipArchive(listOf(source), archive).isSuccess)
        val destination = tempFolder.newFolder("destination")
        val existing = File(destination, "source.txt").apply { writeText("preserve original") }

        val result = ArchiveService.extractZipArchive(archive, destination)

        assertTrue(result.isFailure)
        assertEquals("preserve original", existing.readText())
    }

    @Test
    fun extractionEnforcesUncompressedSizeLimit() = runBlocking {
        val root = tempFolder.root
        val source = File(root, "large.txt").apply { writeBytes(ByteArray(32) { 0 }) }
        val archive = File(root, "limited.zip")
        assertTrue(ArchiveService.createZipArchive(listOf(source), archive).isSuccess)
        val destination = tempFolder.newFolder("limited_destination")

        val result = ArchiveService.extractZipArchive(
            archive,
            destination,
            limits = ArchiveLimits(maxEntries = 10, maxEntryBytes = 16, maxTotalBytes = 32, maxCompressionRatio = 1_000)
        )

        assertTrue(result.isFailure)
        assertTrue(destination.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun extractionRejectsZipSlipWithoutWritingOutsideDestination() = runBlocking {
        val root = tempFolder.root
        val archive = File(root, "malicious.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.txt"))
            zip.write("not written".toByteArray())
            zip.closeEntry()
        }
        val destination = tempFolder.newFolder("safe")

        val result = ArchiveService.extractZipArchive(archive, destination)

        assertTrue(result.isFailure)
        assertTrue(destination.listFiles().orEmpty().isEmpty())
        assertFalse(File(root, "outside.txt").exists())
    }

    @Test
    fun createZipRefusesToOverwriteExistingArchive() = runBlocking {
        val root = tempFolder.root
        val source = File(root, "source.txt").apply { writeText("data") }
        val existing = File(root, "existing.zip").apply { writeText("keep") }

        val result = ArchiveService.createZipArchive(listOf(source), existing)

        assertTrue(result.isFailure)
        assertEquals("keep", existing.readText())
    }
}
