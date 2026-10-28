package com.filesizer.app

import com.filesizer.app.core.apk.ApkResourceService
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkResourceServiceTest {
    @Test
    fun removesOldSignatureFilesButKeepsOtherMetaInfContent() {
        val module = ApkModule()
        module.zipEntryMap.add(ByteInputSource(byteArrayOf(1), "META-INF/MANIFEST.MF"))
        module.zipEntryMap.add(ByteInputSource(byteArrayOf(2), "META-INF/OLD.SF"))
        module.zipEntryMap.add(ByteInputSource(byteArrayOf(3), "META-INF/OLD.RSA"))
        module.zipEntryMap.add(ByteInputSource(byteArrayOf(4), "META-INF/services/example.Service"))
        module.zipEntryMap.add(ByteInputSource(byteArrayOf(5), "assets/META-INF/OLD.RSA"))

        ApkResourceService.removeStaleV1Signatures(module)

        assertFalse(module.zipEntryMap.contains("META-INF/MANIFEST.MF"))
        assertFalse(module.zipEntryMap.contains("META-INF/OLD.SF"))
        assertFalse(module.zipEntryMap.contains("META-INF/OLD.RSA"))
        assertTrue(module.zipEntryMap.contains("META-INF/services/example.Service"))
        assertTrue(module.zipEntryMap.contains("assets/META-INF/OLD.RSA"))
    }
}
