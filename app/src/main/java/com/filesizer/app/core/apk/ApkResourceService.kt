package com.filesizer.app.core.apk

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.reandroid.archive.InputSource
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.value.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class ApkResourceValue(
    val resourceId: String,
    val type: String,
    val name: String,
    val configuration: String,
    val value: String
)

data class ApkResourceInspection(
    val packageName: String,
    val applicationLabel: String,
    val decodedManifest: String,
    val resourceCount: Int,
    val resources: List<ApkResourceValue>,
    val resourcesTruncated: Boolean
)

data class ApkRebuildResult(
    val apkFile: File,
    val packageName: String,
    val signingCertificateSha256: String
)

/** Resource inspection and a deliberately small, verified APK modification surface. */
object ApkResourceService {
    const val MAX_EDITABLE_APK_BYTES = 256L * 1024 * 1024
    private const val MAX_RESOURCE_PREVIEW_ITEMS = 300

    suspend fun inspect(apkFile: File): Result<ApkResourceInspection> = withContext(Dispatchers.IO) {
        try {
            requireReadableApk(apkFile)
            preflightArchive(apkFile)
            val result = ApkModule.loadApkFile(apkFile).use { module ->
                val manifest = module.androidManifest
                    ?: throw IllegalArgumentException("APK does not contain a readable Android manifest.")
                val packageName = manifest.packageName
                    ?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("APK manifest does not declare a package name.")
                val table = if (module.hasTableBlock()) module.getTableBlock(false) else null
                val labelFromReference = manifest.applicationLabelReference
                    ?.let { table?.getResource(it)?.get()?.valueAsString }
                val label = manifest.applicationLabelString
                    ?: labelFromReference
                    ?: packageName
                val resourceValues = ArrayList<ApkResourceValue>()
                val resourceCount = collectResources(table, resourceValues)
                ApkResourceInspection(
                    packageName = packageName,
                    applicationLabel = label,
                    decodedManifest = manifest.serializeToXml(),
                    resourceCount = resourceCount,
                    resources = resourceValues,
                    resourcesTruncated = resourceCount > resourceValues.size
                )
            }
            Result.success(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Changes only the application label, rebuilds a private working copy, strips stale
     * signatures, then signs and verifies the new APK with the explicitly selected key.
     */
    suspend fun rebuildWithApplicationLabel(
        context: Context,
        sourceApk: File,
        newLabel: String,
        keyStoreFile: File,
        keyStoreType: String,
        keyStorePassword: CharArray,
        keyAlias: String,
        keyPassword: CharArray
    ): Result<ApkRebuildResult> = withContext(Dispatchers.IO) {
        val operationContext = coroutineContext
        var unsignedFile: File? = null
        var signedFile: File? = null
        var keepSignedOutput = false
        try {
            requireEditableApk(sourceApk)
            preflightArchive(sourceApk)
            val label = newLabel.trim()
            require(label.isNotEmpty() && label.length <= 80 && label.none { Character.isISOControl(it) }) {
                "Application label must contain 1–80 printable characters."
            }
            require(keyAlias.isNotBlank()) { "Choose a signing-key alias." }
            require(keyStoreType == "JKS" || keyStoreType == "PKCS12") { "Unsupported keystore format." }
            operationContext.ensureActive()

            val signingMaterial = loadSigningMaterial(
                keyStoreFile = keyStoreFile,
                keyStoreType = keyStoreType,
                keyStorePassword = keyStorePassword,
                keyAlias = keyAlias.trim(),
                keyPassword = keyPassword
            )
            val expectedPackage = ApkModule.loadApkFile(sourceApk).use { module ->
                    val manifest = module.androidManifest
                        ?: throw IllegalArgumentException("APK does not contain a readable Android manifest.")
                    if (manifest.isSplit) {
                        throw IllegalArgumentException("Split APK modules are not supported. Select a complete base APK.")
                    }
                    val packageName = manifest.packageName
                        ?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("APK manifest does not declare a package name.")
                    manifest.setApplicationLabel(label)
                    module.refreshManifest()
                    module.refreshTable()
                    removeStaleV1Signatures(module)
                    module.setApkSignatureBlock(null)

                    val unsignedOutput = File.createTempFile("filesizer-apk-unsigned-", ".apk", context.cacheDir)
                    unsignedFile = unsignedOutput
                    if (!unsignedOutput.delete()) {
                        throw IllegalStateException("Could not prepare a temporary APK output.")
                    }
                    module.writeApk(unsignedOutput)
                    packageName
                }

                operationContext.ensureActive()
                val signedOutput = File.createTempFile("filesizer-apk-signed-", ".apk", context.cacheDir)
                signedFile = signedOutput
                val minSdk = readMinimumSdk(sourceApk).takeIf { it > 0 } ?: 1
                val signerConfig = ApkSigner.SignerConfig.Builder(
                    keyAlias.trim(),
                    signingMaterial.privateKey,
                    signingMaterial.certificates
                ).build()
                ApkSigner.Builder(listOf(signerConfig))
                    .setInputApk(unsignedFile)
                    .setOutputApk(signedOutput)
                    .setMinSdkVersion(minSdk)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setV4SigningEnabled(false)
                    .setCreatedBy("File Sizer")
                    .build()
                    .sign()

                operationContext.ensureActive()
                val verified = ApkVerifier.Builder(signedOutput)
                    .setMinCheckedPlatformVersion(minSdk)
                    .build()
                    .verify()
                check(verified.isVerified) {
                    "Rebuilt APK signature verification failed: ${verified.allErrors.joinToString { it.toString() }}"
                }
                val actualSigner = verified.signerCertificates.firstOrNull()
                    ?: throw IllegalStateException("Rebuilt APK has no verifiable signing certificate.")
                val expectedSignerFingerprint = sha256(signingMaterial.certificates.first().encoded)
                val actualSignerFingerprint = sha256(actualSigner.encoded)
                check(actualSignerFingerprint == expectedSignerFingerprint) {
                    "The rebuilt APK was signed with a different certificate than the selected key."
                }

                val packageInfo = context.packageManager.getPackageArchiveInfo(signedOutput.absolutePath, 0)
                    ?: throw IllegalStateException("Android could not parse the rebuilt APK.")
                check(packageInfo.packageName == expectedPackage) {
                    "Rebuilt APK package name did not match the original APK."
                }
                val applicationInfo = packageInfo.applicationInfo
                    ?: throw IllegalStateException("Android could not read the rebuilt APK application metadata.")
                applicationInfo.sourceDir = signedOutput.absolutePath
                applicationInfo.publicSourceDir = signedOutput.absolutePath
                val actualLabel = applicationInfo.loadLabel(context.packageManager).toString()
                check(actualLabel == label) { "The rebuilt APK label did not match the selected text." }
                keepSignedOutput = true
                Result.success(
                    ApkRebuildResult(
                        apkFile = signedOutput,
                        packageName = expectedPackage,
                        signingCertificateSha256 = actualSignerFingerprint
                    )
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            unsignedFile?.delete()
            if (!keepSignedOutput) signedFile?.delete()
            keyStorePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }

    fun removeStaleV1Signatures(module: ApkModule) {
        val entries = module.zipEntryMap.listInputSources().filter(::isV1SignatureEntry)
        entries.forEach { module.zipEntryMap.remove(it.alias) }
    }

    fun isV1SignatureEntry(source: InputSource): Boolean {
        val path = source.alias.replace('\\', '/').uppercase(Locale.ROOT)
        if (!path.startsWith("META-INF/")) return false
        val name = path.substringAfterLast('/')
        return name == "MANIFEST.MF" || name.endsWith(".SF") || name.endsWith(".RSA") ||
                name.endsWith(".DSA") || name.endsWith(".EC") || name.startsWith("SIG-")
    }

    private fun collectResources(table: TableBlock?, output: MutableList<ApkResourceValue>): Int {
        if (table == null) return 0
        var total = 0
        for (packageIndex in 0 until table.size()) {
            val packageBlock = table.get(packageIndex) ?: continue
            val resources = packageBlock.resources
            while (resources.hasNext()) {
                val resource = resources.next()
                val values = resource.iterator()
                while (values.hasNext()) {
                    total++
                    val entry = values.next()
                    if (output.size < MAX_RESOURCE_PREVIEW_ITEMS) {
                        output += ApkResourceValue(
                            resourceId = "0x%08X".format(Locale.ROOT, resource.resourceId),
                            type = resource.type,
                            name = resource.name,
                            configuration = entry.resConfig?.toString().orEmpty().ifBlank { "default" },
                            value = entry.valueAsString ?: entry.valueAsInteger?.toString() ?: entry.toString()
                        )
                    }
                }
            }
        }
        return total
    }

    private fun readMinimumSdk(apk: File): Int = ApkModule.loadApkFile(apk).use { module ->
        module.androidManifest?.minSdkVersion ?: 1
    }

    private suspend fun preflightArchive(apk: File) {
        val operationContext = currentCoroutineContext()
        java.util.zip.ZipFile(apk).use { zip ->
            val entries = zip.entries()
            var count = 0
            var expandedBytes = 0L
            while (entries.hasMoreElements()) {
                operationContext.ensureActive()
                val entry = entries.nextElement()
                count++
                require(count <= 20_000) { "APK has too many archive entries for safe resource inspection." }
                if (entry.size >= 0) {
                    expandedBytes = Math.addExact(expandedBytes, entry.size)
                    require(expandedBytes <= 2L * 1024 * 1024 * 1024) {
                        "APK expands beyond the safe resource-inspection limit."
                    }
                    if (entry.name == "AndroidManifest.xml" || entry.name == "resources.arsc") {
                        require(entry.size <= 64L * 1024 * 1024) {
                            "APK manifest or resource table exceeds the safe parsing limit."
                        }
                    }
                }
                if (entry.size > 0 && entry.compressedSize > 0) {
                    require(entry.size / entry.compressedSize.coerceAtLeast(1) <= 1_000) {
                        "APK contains an entry with an unsafe compression ratio."
                    }
                }
            }
        }
    }

    private fun loadSigningMaterial(
        keyStoreFile: File,
        keyStoreType: String,
        keyStorePassword: CharArray,
        keyAlias: String,
        keyPassword: CharArray
    ): SigningMaterial {
        require(keyStoreFile.isFile && keyStoreFile.canRead()) { "The selected keystore is unavailable." }
        val keyStore = KeyStore.getInstance(keyStoreType)
        FileInputStream(keyStoreFile).use { keyStore.load(it, keyStorePassword) }
        val privateKey = keyStore.getKey(keyAlias, keyPassword) as? PrivateKey
            ?: throw IllegalArgumentException("The selected alias does not contain a private signing key.")
        require(privateKey.algorithm.equals("RSA", ignoreCase = true) ||
                privateKey.algorithm.equals("EC", ignoreCase = true)) {
            "Only RSA or EC signing keys are supported."
        }
        val certificates = keyStore.getCertificateChain(keyAlias)
            ?.map { it as? X509Certificate ?: throw IllegalArgumentException("Keystore certificate is not X.509.") }
            .orEmpty()
        require(certificates.isNotEmpty()) { "The selected alias has no X.509 certificate chain." }
        return SigningMaterial(privateKey, certificates)
    }

    private fun requireEditableApk(file: File) {
        requireReadableApk(file)
        require(file.length() <= MAX_EDITABLE_APK_BYTES) {
            "APK exceeds the supported resource-edit limit (${MAX_EDITABLE_APK_BYTES / (1024 * 1024)} MiB)."
        }
        require(file.extension.equals("apk", ignoreCase = true)) { "Select an APK file." }
    }

    private fun requireReadableApk(file: File) {
        require(file.isFile && file.canRead()) { "APK file is unavailable or unreadable." }
        require(file.extension.equals("apk", ignoreCase = true)) { "Select an APK file." }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02X".format(it) }

    private data class SigningMaterial(
        val privateKey: PrivateKey,
        val certificates: List<X509Certificate>
    )
}
