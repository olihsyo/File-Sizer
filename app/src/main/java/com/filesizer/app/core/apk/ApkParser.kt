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
    val dexFilesCount: Int,
    val totalEntriesCount: Int,
    val appIcon: Drawable? = null
)

object ApkParser {

    suspend fun parseApk(context: Context, apkFile: File): Result<ApkDetailsReport> = withContext(Dispatchers.IO) {
        try {
            if (!apkFile.exists() || !apkFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("APK file does not exist or cannot be read."))
            }

            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PERMISSIONS

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
                appInfo?.minSdkVersion ?: 0
            } else 0
            val targetSdk = appInfo?.targetSdkVersion ?: 0

            val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
            val activities = pkgInfo.activities?.map { it.name } ?: emptyList()
            val services = pkgInfo.services?.map { it.name } ?: emptyList()
            val receivers = pkgInfo.receivers?.map { it.name } ?: emptyList()

            // Count internal DEX and resources via ZipFile
            var dexCount = 0
            var totalEntries = 0
            try {
                ZipFile(apkFile).use { zip ->
                    totalEntries = zip.size()
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.startsWith("classes") && e.name.endsWith(".dex")) {
                            dexCount++
                        }
                    }
                }
            } catch (_: Exception) {}

            val hashResult = FileOperationService.calculateSha256(apkFile)
            val sha256 = hashResult.getOrNull() ?: "Unable to compute"

            val report = ApkDetailsReport(
                fileName = apkFile.name,
                filePath = apkFile.absolutePath,
                fileSize = apkFile.length(),
                sha256Hash = sha256,
                appName = appName,
                packageName = pkgInfo.packageName ?: "Unknown",
                versionName = pkgInfo.versionName ?: "Unknown",
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = permissions,
                activities = activities,
                services = services,
                receivers = receivers,
                dexFilesCount = dexCount,
                totalEntriesCount = totalEntries,
                appIcon = appIcon
            )

            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
