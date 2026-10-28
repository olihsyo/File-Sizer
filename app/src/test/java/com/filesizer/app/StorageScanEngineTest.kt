package com.filesizer.app

import com.filesizer.app.core.analyzer.FileCategory
import com.filesizer.app.core.analyzer.StorageScanEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageScanEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileCategorization() {
        assertEquals(FileCategory.IMAGES, StorageScanEngine.categorize("jpg"))
        assertEquals(FileCategory.IMAGES, StorageScanEngine.categorize("png"))
        assertEquals(FileCategory.VIDEOS, StorageScanEngine.categorize("mp4"))
        assertEquals(FileCategory.AUDIO, StorageScanEngine.categorize("mp3"))
        assertEquals(FileCategory.DOCUMENTS, StorageScanEngine.categorize("pdf"))
        assertEquals(FileCategory.ARCHIVES, StorageScanEngine.categorize("zip"))
        assertEquals(FileCategory.ARCHIVES, StorageScanEngine.categorize("7z"))
        assertEquals(FileCategory.APKS, StorageScanEngine.categorize("apk"))
        assertEquals(FileCategory.OTHERS, StorageScanEngine.categorize("xyz123"))
    }

    @Test
    fun testStorageScanRecursive() = runBlocking {
        val root = tempFolder.root
        val imgFolder = File(root, "DCIM").apply { mkdirs() }
        File(imgFolder, "photo1.jpg").apply { writeBytes(ByteArray(1024)) }
        File(imgFolder, "photo2.png").apply { writeBytes(ByteArray(2048)) }

        val musicFolder = File(root, "Music").apply { mkdirs() }
        File(musicFolder, "song.mp3").apply { writeBytes(ByteArray(4096)) }

        StorageScanEngine.reset()
        StorageScanEngine.startScan(root)

        val report = StorageScanEngine.scanReport.value
        assertEquals(3, report.scannedFilesCount)
        assertEquals(3072L, report.categorySizes[FileCategory.IMAGES])
        assertEquals(4096L, report.categorySizes[FileCategory.AUDIO])
        assertEquals(2, report.categoryCounts[FileCategory.IMAGES])
        assertEquals(1, report.categoryCounts[FileCategory.AUDIO])
        assertEquals(3, report.largestFiles.size)
        assertEquals("song.mp3", report.largestFiles[0].name)
    }

    @Test
    fun duplicateCandidatesRequireMatchingContentHash() = runBlocking {
        val root = tempFolder.root
        val bytes = ByteArray(1024 * 1024 + 8) { (it % 239).toByte() }
        File(root, "copy-a.bin").writeBytes(bytes)
        File(root, "copy-b.bin").writeBytes(bytes.copyOf())
        File(root, "same-size-different-content.bin").writeBytes(ByteArray(bytes.size) { 7 })

        StorageScanEngine.reset()
        StorageScanEngine.startScan(root)

        val groups = StorageScanEngine.scanReport.value.potentialDuplicates
        assertEquals(1, groups.size)
        assertEquals(bytes.size.toLong(), groups.single().sizeBytes)
        assertEquals(setOf("copy-a.bin", "copy-b.bin"), groups.single().files.map { it.name }.toSet())
    }
}
