package com.filesizer.app

import com.filesizer.app.core.operations.ConflictPolicy
import com.filesizer.app.core.operations.FileOperationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOperationServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCreateFolder() = runBlocking {
        val root = tempFolder.root
        val result = FileOperationService.createFolder(root, "TestFolder")
        assertTrue(result.isSuccess)
        val created = result.getOrNull()!!
        assertTrue(created.exists())
        assertTrue(created.isDirectory)
        assertEquals("TestFolder", created.name)
    }

    @Test
    fun testRenameItem() = runBlocking {
        val root = tempFolder.root
        val original = File(root, "original.txt").apply { writeText("Hello File Sizer") }
        assertTrue(original.exists())

        val result = FileOperationService.renameItem(original, "renamed.txt")
        assertTrue(result.isSuccess)
        val renamed = result.getOrNull()!!
        assertFalse(original.exists())
        assertTrue(renamed.exists())
        assertEquals("renamed.txt", renamed.name)
        assertEquals("Hello File Sizer", renamed.readText())
    }

    @Test
    fun testCalculateSha256() = runBlocking {
        val root = tempFolder.root
        val testFile = File(root, "hash_test.txt").apply { writeText("hello world") }
        // SHA-256 of "hello world" is b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        val result = FileOperationService.calculateSha256(testFile)
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun testCopyAndConflictRename() = runBlocking {
        val root = tempFolder.root
        val srcDir = File(root, "source").apply { mkdirs() }
        val destDir = File(root, "dest").apply { mkdirs() }

        val file1 = File(srcDir, "doc.txt").apply { writeText("v1") }
        // Pre-create conflicting doc.txt in dest
        File(destDir, "doc.txt").apply { writeText("dest_v1") }

        val copyResult = FileOperationService.copyItems(
            sources = listOf(file1),
            targetDir = destDir,
            policy = ConflictPolicy.RENAME_NEW
        )
        assertTrue(copyResult.isSuccess)
        assertEquals(1, copyResult.getOrNull())

        // Conflicting file should be preserved, and new file should be doc (1).txt
        assertTrue(File(destDir, "doc.txt").exists())
        assertEquals("dest_v1", File(destDir, "doc.txt").readText())
        assertTrue(File(destDir, "doc (1).txt").exists())
        assertEquals("v1", File(destDir, "doc (1).txt").readText())
    }

    @Test
    fun testDeleteRecursive() = runBlocking {
        val root = tempFolder.root
        val parent = File(root, "to_delete").apply { mkdirs() }
        File(parent, "child.txt").apply { writeText("nested content") }
        File(parent, "subfolder").apply { mkdirs() }

        val delResult = FileOperationService.deleteItems(listOf(parent))
        assertTrue(delResult.isSuccess)
        assertFalse(parent.exists())
    }

    @Test
    fun replaceKeepsOriginalWhenStagedCopyFails() = runBlocking {
        val root = tempFolder.root
        val sourceDir = File(root, "source").apply { mkdirs() }
        val destinationDir = File(root, "destination").apply { mkdirs() }
        File(sourceDir, "report.txt").writeBytes(ByteArray(256 * 1024) { (it % 251).toByte() })
        val existing = File(destinationDir, "report.txt").apply { writeText("keep this original") }

        val result = FileOperationService.copyItems(
            sources = listOf(File(sourceDir, "report.txt")),
            targetDir = destinationDir,
            policy = ConflictPolicy.REPLACE,
            onProgress = { if (it.completedBytes > 0L) throw IllegalStateException("simulated write failure") }
        )

        assertTrue(result.isFailure)
        assertEquals("keep this original", existing.readText())
        assertTrue(destinationDir.listFiles().orEmpty().none { it.name.endsWith(".partial") || it.name.endsWith(".backup") })
    }

    @Test
    fun invalidNamesWithPathSeparatorsAreRejected() = runBlocking {
        val result = FileOperationService.createFolder(tempFolder.root, "../outside")
        assertTrue(result.isFailure)
        assertFalse(File(tempFolder.root.parentFile, "outside").exists())
    }

    @Test
    fun replacementCommitsOnlyAfterVerifiedCopy() = runBlocking {
        val root = tempFolder.root
        val source = File(root, "incoming.txt").apply { writeText("new version") }
        val destination = File(root, "destination").apply { mkdirs() }
        File(destination, source.name).writeText("old version")

        val result = FileOperationService.copyItems(listOf(source), destination, ConflictPolicy.REPLACE)

        assertTrue(result.isSuccess)
        assertEquals("new version", File(destination, source.name).readText())
        assertTrue(destination.listFiles().orEmpty().none { it.name.endsWith(".partial") || it.name.endsWith(".backup") })
    }
}
