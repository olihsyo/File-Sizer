package com.filesizer.app.feature.apkstudio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filesizer.app.R
import com.filesizer.app.core.apk.ApkDetailsReport
import com.filesizer.app.core.apk.ApkParser
import com.filesizer.app.core.apk.ApkResourceInspection
import com.filesizer.app.core.apk.ApkResourceService
import com.filesizer.app.core.storage.ContentUriFiles
import com.filesizer.app.core.designsystem.components.EmptyStateCard
import com.filesizer.app.core.designsystem.components.FileSizerTopBar
import com.filesizer.app.core.designsystem.components.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val MAX_KEYSTORE_IMPORT_BYTES = 16L * 1024 * 1024
private const val MAX_SIGNED_APK_EXPORT_BYTES = 768L * 1024 * 1024

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ApkStudioScreen(
    apkUri: String? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedApkUri by remember { mutableStateOf<String?>(null) }
    val activeApkUri = selectedApkUri ?: apkUri
    var report by remember { mutableStateOf<ApkDetailsReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isBuilding by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var resourceInspection by remember { mutableStateOf<ApkResourceInspection?>(null) }
    var resourceInspectionError by remember { mutableStateOf<String?>(null) }
    var workspaceApk by remember { mutableStateOf<File?>(null) }
    var sourceLabel by remember(activeApkUri) { mutableStateOf<String?>(null) }
    var editedApplicationLabel by remember { mutableStateOf("") }
    var keyStoreUri by remember { mutableStateOf<Uri?>(null) }
    var keyStoreName by remember { mutableStateOf<String?>(null) }
    var keyStoreType by remember { mutableStateOf("JKS") }
    var keyAlias by remember { mutableStateOf("") }
    var keyStorePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var buildStatus by remember { mutableStateOf<String?>(null) }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedApkUri = uri.toString()
            selectedTab = 0
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    val keyStorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keyStoreUri = uri
            keyStoreName = ContentUriFiles.metadata(context, uri).displayName
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            buildStatus = null
        }
    }
    val signedApkExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destination ->
        val sourceFile = workspaceApk
        val selectedKeyStore = keyStoreUri
        val currentKeyStoreName = keyStoreName
        val currentKeyStoreType = keyStoreType
        val currentAlias = keyAlias
        val storePassword = keyStorePassword
        val entryPassword = keyPassword
        val newLabel = editedApplicationLabel
        val packageName = report?.packageName ?: "modified-app"
        if (destination != null && sourceFile != null && selectedKeyStore != null && currentKeyStoreName != null) {
            isBuilding = true
            buildStatus = "Rebuilding APK and verifying its signature…"
            scope.launch {
                var importedKeyStore: File? = null
                var signedOutput: File? = null
                try {
                    importedKeyStore = ContentUriFiles.copyToCache(context, selectedKeyStore, MAX_KEYSTORE_IMPORT_BYTES).getOrThrow()
                    val result = ApkResourceService.rebuildWithApplicationLabel(
                        context = context,
                        sourceApk = sourceFile,
                        newLabel = newLabel,
                        keyStoreFile = importedKeyStore,
                        keyStoreType = currentKeyStoreType,
                        keyStorePassword = storePassword.toCharArray(),
                        keyAlias = currentAlias,
                        keyPassword = entryPassword.toCharArray()
                    ).getOrThrow()
                    signedOutput = result.apkFile
                    val outputHash = ContentUriFiles.exportFileVerified(
                        context = context,
                        source = result.apkFile,
                        destination = destination,
                        maxBytes = MAX_SIGNED_APK_EXPORT_BYTES
                    ).getOrThrow()
                    buildStatus = "Signed APK exported. Certificate SHA-256: ${result.signingCertificateSha256}\nOutput SHA-256: $outputHash"
                    Toast.makeText(context, "Signed APK saved", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    buildStatus = "APK rebuild failed: ${error.message ?: "Unknown error"}"
                    Toast.makeText(context, "APK rebuild failed", Toast.LENGTH_LONG).show()
                } finally {
                    importedKeyStore?.delete()
                    signedOutput?.delete()
                    keyStorePassword = ""
                    keyPassword = ""
                    isBuilding = false
                }
            }
        }
    }
    val reportExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val currentReport = report
        if (uri != null && currentReport != null) {
            val text = buildApkReport(currentReport)
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalStateException("Could not write to selected destination.")
                        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                }.onSuccess { Toast.makeText(context, "APK report saved", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Report export failed: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    LaunchedEffect(activeApkUri) {
        if (activeApkUri != null) {
            isLoading = true
            loadError = null
            var importedFile: File? = null
            try {
                val uri = Uri.parse(activeApkUri)
                val file = if (uri.scheme.equals("content", ignoreCase = true)) {
                    sourceLabel = ContentUriFiles.metadata(context, uri).displayName
                    ContentUriFiles.copyToCache(context, uri, ContentUriFiles.MAX_APK_IMPORT_BYTES).getOrThrow().also { importedFile = it }
                } else {
                    val original = if (uri.scheme.equals("file", ignoreCase = true)) File(uri.path ?: activeApkUri) else File(activeApkUri)
                    copyLocalApkToCache(context, original).also { importedFile = it }
                }
                require(file.isFile && file.canRead()) { "APK file is unavailable or unreadable." }
                val result = ApkParser.parseApk(context, file, sourceLabel)
                report = result.getOrNull()
                loadError = result.exceptionOrNull()?.message
                workspaceApk?.takeIf { it != file }?.delete()
                workspaceApk = file
                importedFile = null
                resourceInspection = null
                resourceInspectionError = null
                editedApplicationLabel = result.getOrNull()?.appName.orEmpty()
                buildStatus = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                loadError = error.message ?: "Could not inspect this APK."
                report = null
            } finally {
                importedFile?.delete()
                isLoading = false
            }
        } else {
            report = null
            workspaceApk?.delete()
            workspaceApk = null
        }
    }

    LaunchedEffect(workspaceApk, selectedTab) {
        val source = workspaceApk
        if (source != null && selectedTab == 3 && resourceInspection == null) {
            resourceInspectionError = null
            ApkResourceService.inspect(source)
                .onSuccess { resourceInspection = it }
                .onFailure { resourceInspectionError = it.message ?: "Resource inspection failed." }
        }
    }

    DisposableEffect(workspaceApk) {
        val ownedWorkspace = workspaceApk
        onDispose { ownedWorkspace?.delete() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FileSizerTopBar(
            title = report?.appName ?: stringResource(R.string.action_apk_studio),
            canNavigateBack = true,
            onNavigateBack = onNavigateBack,
            actions = {
                report?.let { currentReport ->
                    IconButton(onClick = { reportExporter.launch("${currentReport.packageName}-analysis.txt") }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Export APK analysis report")
                    }
                }
                IconButton(onClick = { apkPicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = "Choose APK file")
                }
            }
        )

        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator()
            }
        } else if (report == null) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    EmptyStateCard(
                        title = if (activeApkUri == null) "No APK Selected" else "APK inspection unavailable",
                        description = loadError ?: "Choose an APK file to inspect package metadata and declarations.",
                        icon = Icons.Rounded.Widgets
                    )
                }
                item {
                    Button(onClick = { apkPicker.launch(arrayOf("*/*")) }) {
                        Text("Choose APK")
                    }
                }
            }
        } else {
            val apk = report!!

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Banner
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Widgets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = apk.appName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = apk.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "v${apk.versionName} (${if (apk.versionCode >= 0) apk.versionCode else "unknown"})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatFileSize(apk.fileSize),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Tabs
                item {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Overview") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Permissions (${apk.permissions.size})") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Components (${apk.activities.size + apk.services.size})") }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Files (${apk.totalEntriesCount})") }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            ApkInfoCard(title = "SDK Requirements") {
                                InfoRow(label = "Minimum SDK", value = if (apk.minSdk >= 0) "Android ${apk.minSdk} (API ${apk.minSdk})" else "Unknown")
                                InfoRow(label = "Target SDK", value = if (apk.targetSdk >= 0) "Android ${apk.targetSdk} (API ${apk.targetSdk})" else "Unknown")
                            }
                        }

                        item {
                            ApkInfoCard(title = "Package Composition") {
                                InfoRow(label = "DEX Files", value = "${apk.dexFilesCount} classes.dex")
                                InfoRow(label = "Total Internal Entries", value = "${apk.totalEntriesCount} files")
                            InfoRow(label = "Resources", value = "${apk.resourceEntryCount} resource entries")
                            InfoRow(label = "Assets", value = "${apk.assetEntryCount} asset entries")
                            InfoRow(label = "AndroidManifest.xml", value = if (apk.manifestIsBinary) "Binary XML; decoded text is unavailable" else "Text XML")
                            }
                        }

                        item {
                            ApkInfoCard(title = "Integrity & Checksum") {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "SHA-256 Hash",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("APK SHA-256", apk.sha256Hash))
                                                Toast.makeText(context, "Hash copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = apk.sha256Hash,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        item {
                            ApkInfoCard(title = "Signing Certificate") {
                                if (apk.signingCertificateSha256.isEmpty()) {
                                    Text("Certificate details could not be parsed from this APK.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    apk.signingCertificateSha256.forEachIndexed { index, fingerprint ->
                                        Text("Signer ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                                        apk.signingCertificateSubjects.getOrNull(index)?.let { subject ->
                                            Text(subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            ApkInfoCard(title = "Supported edit: application label") {
                                Text(
                                    "Change the displayed application label, rebuild a private copy, then sign it with a keystore you choose. The original APK remains unchanged. The new signature will not match the original developer certificate, so the rebuilt APK cannot update an installation signed by that original key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = editedApplicationLabel,
                                    onValueChange = { editedApplicationLabel = it; buildStatus = null },
                                    label = { Text("Application label") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { keyStorePicker.launch(arrayOf("*/*")) },
                                    enabled = !isBuilding
                                ) {
                                    Text(if (keyStoreName == null) "Choose signing keystore" else "Change signing keystore")
                                }
                                keyStoreName?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = keyStoreType == "JKS",
                                        onClick = { keyStoreType = "JKS" },
                                        label = { Text("JKS") }
                                    )
                                    FilterChip(
                                        selected = keyStoreType == "PKCS12",
                                        onClick = { keyStoreType = "PKCS12" },
                                        label = { Text("PKCS12") }
                                    )
                                }
                                OutlinedTextField(
                                    value = keyAlias,
                                    onValueChange = { keyAlias = it },
                                    label = { Text("Key alias") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = keyStorePassword,
                                    onValueChange = { keyStorePassword = it },
                                    label = { Text("Keystore password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = keyPassword,
                                    onValueChange = { keyPassword = it },
                                    label = { Text("Key password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        signedApkExporter.launch("${apk.packageName}-filesizer-signed.apk")
                                    },
                                    enabled = !isBuilding && workspaceApk != null && keyStoreUri != null &&
                                            keyAlias.isNotBlank() && editedApplicationLabel.isNotBlank()
                                ) {
                                    if (isBuilding) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text("Rebuild and sign APK")
                                }
                                buildStatus?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    1 -> {
                        if (apk.permissions.isEmpty()) {
                            item {
                                Text("No permissions declared by this package.")
                            }
                        } else {
                            items(apk.permissions) { perm ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = perm.substringAfterLast("."),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            Text(
                                text = "Activities (${apk.activities.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(apk.activities) { act ->
                            ComponentRow(name = act)
                        }

                        if (apk.services.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Services (${apk.services.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            items(apk.services) { srv ->
                                ComponentRow(name = srv)
                            }
                        }
                        if (apk.receivers.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Receivers (${apk.receivers.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            items(apk.receivers) { ComponentRow(name = it) }
                        }
                        if (apk.providers.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Providers (${apk.providers.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            items(apk.providers) { ComponentRow(name = it) }
                        }
                    }
                    3 -> {
                        item {
                            ApkInfoCard(title = "Manifest and resource inspection") {
                                Text(
                                    "Manifest and resource parsing uses ARSCLib. Results are read-only here; the label editor in Overview is the only supported APK modification.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        resourceInspectionError?.let { error ->
                            item { Text("Resource inspection failed: $error", color = MaterialTheme.colorScheme.error) }
                        }
                        resourceInspection?.let { inspection ->
                            item {
                                ApkInfoCard(title = "Decoded AndroidManifest.xml") {
                                    Text(
                                        inspection.decodedManifest.take(MAX_MANIFEST_PREVIEW_CHARS),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 100,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (inspection.decodedManifest.length > MAX_MANIFEST_PREVIEW_CHARS) {
                                        Text("Showing the first $MAX_MANIFEST_PREVIEW_CHARS characters.", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            item {
                                ApkInfoCard(title = "Resource values (${inspection.resourceCount})") {
                                    Text("${inspection.packageName} · ${inspection.applicationLabel}", style = MaterialTheme.typography.labelSmall)
                                    if (inspection.resources.isEmpty()) Text("No resource values were exposed by the parser.")
                                }
                            }
                            items(inspection.resources) { resource ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${resource.type}/${resource.name} · ${resource.configuration}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(resource.value, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (inspection.resourcesTruncated) {
                                item { Text("Showing the first ${inspection.resources.size} resource values.", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        items(apk.archiveEntries) { entry ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(entry, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            }
                        }
                        if (apk.totalEntriesCount > apk.archiveEntries.size) {
                            item { Text("Showing the first ${apk.archiveEntries.size} entries.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_MANIFEST_PREVIEW_CHARS = 12_000

private suspend fun copyLocalApkToCache(context: Context, source: File): File = withContext(Dispatchers.IO) {
    val operationContext = currentCoroutineContext()
    require(source.isFile && source.canRead()) { "APK file is unavailable or unreadable." }
    require(source.extension.equals("apk", ignoreCase = true)) { "Select an APK file." }
    require(source.length() <= ContentUriFiles.MAX_APK_IMPORT_BYTES) { "APK exceeds the supported 768 MiB import limit." }
    val temp = File.createTempFile("filesizer-apk-workspace-", ".apk", context.cacheDir)
    try {
        FileInputStream(source).use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    operationContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > ContentUriFiles.MAX_APK_IMPORT_BYTES) throw IllegalArgumentException("APK exceeds the supported 768 MiB import limit.")
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
                require(copied == source.length()) { "APK changed while it was being imported." }
            }
        }
        temp
    } catch (error: Exception) {
        temp.delete()
        if (error is CancellationException) throw error
        throw error
    }
}

@Composable
fun ApkInfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ComponentRow(name: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ViewAgenda,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name.substringAfterLast("."),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildApkReport(report: ApkDetailsReport): String = buildString {
    appendLine("File Sizer APK analysis")
    appendLine("Application: ${report.appName}")
    appendLine("Package: ${report.packageName}")
    appendLine("Version: ${report.versionName} (${report.versionCode.takeIf { it >= 0 } ?: "unknown"})")
    appendLine("APK size: ${report.fileSize} bytes")
    appendLine("SHA-256: ${report.sha256Hash}")
    appendLine("Minimum SDK: ${report.minSdk.takeIf { it >= 0 } ?: "unknown"}")
    appendLine("Target SDK: ${report.targetSdk.takeIf { it >= 0 } ?: "unknown"}")
    appendLine("DEX files: ${report.dexFilesCount}")
    appendLine("Archive entries: ${report.totalEntriesCount}")
    appendLine("Declared permissions:")
    report.permissions.forEach { appendLine("  $it") }
    appendLine("Signing certificate SHA-256:")
    report.signingCertificateSha256.forEach { appendLine("  $it") }
    appendLine("Activities:")
    report.activities.forEach { appendLine("  $it") }
    appendLine("Services:")
    report.services.forEach { appendLine("  $it") }
    appendLine("Receivers:")
    report.receivers.forEach { appendLine("  $it") }
    appendLine("Providers:")
    report.providers.forEach { appendLine("  $it") }
    appendLine("Note: Binary manifest/resource decoding and APK rebuilding/signing are not supported by this build.")
}
