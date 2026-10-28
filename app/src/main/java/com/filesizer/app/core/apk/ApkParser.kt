package com.filesizer.app.core.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.filesizer.app.core.operations.FileOperationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class ApkDetailsReport(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val sha256Hash: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val dexFilesCount: Int,
    val totalEntriesCount: Int,
    val resourceEntryCount: Int,
    val assetEntryCount: Int,
    val signingCertificateSha256: List<String>,
    val signingCertificateSubjects: List<String>,
    val manifestIsBinary: Boolean,
    val archiveEntries: List<String>,
    val appIcon: Drawable? = null
)

object ApkParser {

    suspend fun parseApk(context: Context, apkFile: File, sourceLabel: String? = null): Result<ApkDetailsReport> = withContext(Dispatchers.IO) {
        try {
            if (!apkFile.exists() || !apkFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("APK file does not exist or cannot be read."))
            }

            val pm = context.packageManager
            var flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_PERMISSIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags = flags or PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                run { flags = flags or PackageManager.GET_SIGNATURES }
            }

            val pkgInfo: PackageInfo? = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            if (pkgInfo == null) {
                return@withContext Result.failure(IllegalStateException("Failed to parse APK archive metadata. The file may be corrupt or invalid."))
            }

            // Assign sourceDir so resources and icons load properly
            val appInfo = pkgInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
            }

            val appName = appInfo?.loadLabel(pm)?.toString() ?: apkFile.nameWithoutExtension
            val appIcon = appInfo?.loadIcon(pm)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion ?: -1
            } else -1
            val targetSdk = appInfo?.targetSdkVersion ?: -1

            val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            val activities = pkgInfo.activities?.map { it.name } ?: emptyList()
            val services = pkgInfo.services?.map { it.name } ?: emptyList()
            val receivers = pkgInfo.receivers?.map { it.name } ?: emptyList()
            val providers = pkgInfo.providers?.map { it.name } ?: emptyList()

            // Count internal DEX and resources via ZipFile
            var dexCount = 0
            var totalEntries = 0
            var resourceEntries = 0
            var assetEntries = 0
            var manifestIsBinary = false
            val archiveEntryNames = mutableListOf<String>()
            try {
                ZipFile(apkFile).use { zip ->
                    totalEntries = zip.size()
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (archiveEntryNames.size < 250) archiveEntryNames += e.name
                        if (e.name.startsWith("classes") && e.name.endsWith(".dex")) {
                            dexCount++
                        }
                        if (e.name == "resources.arsc" || e.name.startsWith("res/")) resourceEntries++
                        if (e.name.startsWith("assets/")) assetEntries++
                        if (e.name == "AndroidManifest.xml") {
                            zip.getInputStream(e).use { input ->
                                val header = ByteArray(4)
                                manifestIsBinary = input.read(header) == 4 && header[0] != '<'.code.toByte()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.signatures?.toList().orEmpty()
            }
            val certificates = signatures.mapNotNull { signature ->
                runCatching {
                    CertificateFactory.getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(signature.toByteArray())) as? X509Certificate
                        ?: throw IllegalArgumentException("Signing certificate is not X.509")
                }.getOrNull()
            }
            val certificateSha256 = certificates.map { certificate ->
                MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                    .joinToString("") { "%02X".format(it) }
            }
            val certificateSubjects = certificates.map { it.subjectX500Principal.name }

            val hashResult = FileOperationService.calculateSha256(apkFile)
            val sha256 = hashResult.getOrNull() ?: "Unable to compute"

            val report = ApkDetailsReport(
                fileName = sourceLabel ?: apkFile.name,
                filePath = sourceLabel ?: apkFile.absolutePath,
                fileSize = apkFile.length(),
                sha256Hash = sha256,
                appName = appName,
                packageName = pkgInfo.packageName ?: "Unknown",
                versionName = pkgInfo.versionName ?: "Unknown",
                versionCode = versionCode.takeIf { it > 0 } ?: -1L,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissions,
                activities = activities,
                services = services,
                receivers = receivers,
                providers = providers,
                dexFilesCount = dexCount,
                totalEntriesCount = totalEntries,
                resourceEntryCount = resourceEntries,
                assetEntryCount = assetEntries,
                signingCertificateSha256 = certificateSha256,
                signingCertificateSubjects = certificateSubjects,
                manifestIsBinary = manifestIsBinary,
                archiveEntries = archiveEntryNames,
                appIcon = appIcon
            )

            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
