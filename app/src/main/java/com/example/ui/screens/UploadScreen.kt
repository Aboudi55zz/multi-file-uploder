package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.browser.BrowserDiscoveryHelper
import com.example.data.sync.AiMultiFileBridge
import com.example.model.DestinationType
import com.example.model.UploadState
import com.example.ui.components.CustomWebConfigForm
import com.example.ui.components.DriveConfigForm
import com.example.ui.components.FolderPreviewCard
import com.example.ui.components.GitHubConfigForm
import com.example.ui.components.InstalledBrowsersSheet
import com.example.ui.components.SyncNotificationStatusBanner
import com.example.ui.components.UploadProgressCard
import com.example.ui.components.UploadResultCard
import com.example.ui.components.ZipExportConfigForm
import com.example.ui.components.rememberSafFolderPicker
import com.example.ui.theme.CyanBright
import com.example.ui.theme.NavyDeep
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UploadScreen(
    viewModel: MainViewModel,
    onShowMessage: (String) -> Unit,
    onOpenWebBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val selectedDirectories by viewModel.selectedDirectories.collectAsStateWithLifecycle()
    val selectedFilePaths by viewModel.selectedFilePaths.collectAsStateWithLifecycle()
    val ignoreRules by viewModel.activeIgnoreRules.collectAsStateWithLifecycle()
    val destination by viewModel.selectedDestination.collectAsStateWithLifecycle()
    val gitHubConfig by viewModel.gitHubConfig.collectAsStateWithLifecycle()
    val driveConfig by viewModel.driveConfig.collectAsStateWithLifecycle()
    val customWebConfig by viewModel.customWebConfig.collectAsStateWithLifecycle()
    val zipConfig by viewModel.zipConfig.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val isSyncServiceActive by viewModel.isSyncServiceActive.collectAsStateWithLifecycle()
    val connectionCheck by viewModel.connectionCheck.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingConnection.collectAsStateWithLifecycle()
    val savedTargets by viewModel.savedTargets.collectAsStateWithLifecycle()
    val exportedZip by viewModel.lastExportedZip.collectAsStateWithLifecycle()
    val installedBrowsers by viewModel.installedBrowsers.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var targetProfileName by remember { mutableStateOf("") }
    var showBrowsersSheet by remember { mutableStateOf(false) }
    var prefixFolderNamesForAi by rememberSaveable { mutableStateOf(true) }
    var isPreparingAiShare by remember { mutableStateOf(false) }

    fun checkNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    var hasNotificationPermission by remember { mutableStateOf(checkNotificationGranted()) }
    var promptedNotifBeforeUpload by rememberSaveable { mutableStateOf(false) }
    var startUploadAfterPermissionResult by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotificationGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) {
            onShowMessage("ستعمل المزامنة في الخلفية، ويمكنك تفعيل الإشعارات لاحقاً لعرض شريط التقدم")
        }
        if (startUploadAfterPermissionResult) {
            startUploadAfterPermissionResult = false
            viewModel.startUpload()
        }
    }

    fun launchBackgroundSyncWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission &&
            !promptedNotifBeforeUpload
        ) {
            promptedNotifBeforeUpload = true
            startUploadAfterPermissionResult = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startUpload()
        }
    }

    val safPickerState = rememberSafFolderPicker(
        onFolderSelected = { uri -> viewModel.onFolderPicked(uri) },
        onPickerError = onShowMessage
    )

    val mergeFolderPicker = rememberSafFolderPicker(
        onFolderSelected = { uri -> viewModel.onAdditionalFolderPicked(uri) },
        onPickerError = onShowMessage
    )

    val extraFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onExtraFilesPicked(uris)
        }
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri: Uri? ->
        if (destUri != null) {
            viewModel.saveExportedZipToUri(destUri)
        }
    }

    val isUploading = uploadState is UploadState.Uploading
    val isScanning = uploadState is UploadState.Scanning

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroHeaderCard()
            }

            if (savedTargets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "وجهات محفوظة — تحميل سريع:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(savedTargets, key = { it.id }) { target ->
                                AssistChip(
                                    onClick = { viewModel.applySavedTarget(target) },
                                    label = { Text(target.name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = iconForDestination(
                                                runCatching { DestinationType.valueOf(target.destinationType) }
                                                    .getOrDefault(DestinationType.GITHUB)
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                FolderPreviewCard(
                    folder = selectedFolder,
                    pickerState = safPickerState,
                    activeIgnoreRules = ignoreRules,
                    selectedDirectories = selectedDirectories,
                    selectedFilePaths = selectedFilePaths,
                    isScanning = isScanning,
                    onSelectPersistedFolder = { uri -> viewModel.onFolderPicked(uri) },
                    onPickExtraFiles = { extraFilesLauncher.launch(arrayOf("*/*")) },
                    onMergeAnotherFolder = { mergeFolderPicker.launch(selectedFolder?.treeUri) },
                    onToggleSelectAll = { viewModel.toggleSelectAllItems() },
                    onToggleDirectorySelection = { viewModel.toggleDirectorySelection(it) },
                    onToggleFileSelection = { viewModel.toggleFileSelection(it) },
                    onRescan = { viewModel.rescanCurrentFolder() },
                    onToggleIgnoreRule = { viewModel.toggleIgnoreRule(it) },
                    onAddCustomIgnoreRule = { viewModel.addCustomIgnoreRule(it) },
                    onIncludeAllWithoutIgnore = { viewModel.includeAllHiddenAndIgnored() },
                    onRestoreDefaultIgnoreRules = { viewModel.restoreDefaultIgnoreRules() }
                )
            }

            if (selectedFolder != null) {
                item {
                    SendToAiAssistantsCard(
                        folderName = selectedFolder?.rootName.orEmpty(),
                        selectedFileCount = selectedFilePaths.size,
                        selectedDirCount = selectedDirectories.size,
                        prefixFolderNames = prefixFolderNamesForAi,
                        isPreparing = isPreparingAiShare,
                        onTogglePrefixFolderNames = { prefixFolderNamesForAi = it },
                        onShareToAiApps = {
                            if (selectedFilePaths.isEmpty()) {
                                onShowMessage("اختر ملفاً واحداً على الأقل أو اضغط «تحديد الكل»")
                                return@SendToAiAssistantsCard
                            }
                            scope.launch {
                                isPreparingAiShare = true
                                val result = viewModel.prepareFilesForAiShare(prefixFolderNamesForAi)
                                isPreparingAiShare = false
                                result.onSuccess { uris ->
                                    val shareIntent = AiMultiFileBridge.buildShareMultipleToAiIntent(
                                        uris = uris,
                                        folderSummaryTitle = selectedFolder?.rootName ?: "ملفات ومجلدات"
                                    )
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(
                                                shareIntent,
                                                "إرسال المجلدات والملفات إلى ChatGPT أو Claude"
                                            )
                                        )
                                    }.onFailure { err ->
                                        onShowMessage("تعذر فتح نافذة المشاركة: ${err.message}")
                                    }
                                }.onFailure { err ->
                                    onShowMessage(err.message ?: "تعذر تجهيز الملفات للإرسال")
                                }
                            }
                        },
                        onOpenChatGptWeb = {
                            viewModel.setBrowserTargetUrl("https://chatgpt.com", autoNavigate = true)
                        },
                        onOpenClaudeWeb = {
                            viewModel.setBrowserTargetUrl("https://claude.ai", autoNavigate = true)
                        }
                    )
                }
            }

            item {
                DestinationSelectorSection(
                    selected = destination,
                    onSelect = { viewModel.selectDestination(it) }
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "3. إعدادات ${destination.titleAr}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (destination != DestinationType.ZIP_EXPORT) {
                                IconButton(
                                    onClick = { showSaveDialog = true },
                                    modifier = Modifier.testTag("save_target_dialog_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "حفظ الوجهة للاستخدام لاحقاً",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        when (destination) {
                            DestinationType.GITHUB -> GitHubConfigForm(
                                config = gitHubConfig,
                                onUpdate = { viewModel.updateGitHubConfig(it) },
                                connectionCheck = connectionCheck,
                                isChecking = isChecking,
                                onVerify = { viewModel.verifyConnection() }
                            )
                            DestinationType.GOOGLE_DRIVE -> DriveConfigForm(
                                config = driveConfig,
                                onUpdate = { viewModel.updateDriveConfig(it) },
                                connectionCheck = connectionCheck,
                                isChecking = isChecking,
                                onVerify = { viewModel.verifyConnection() }
                            )
                            DestinationType.CUSTOM_WEB -> CustomWebConfigForm(
                                config = customWebConfig,
                                onUpdate = { viewModel.updateCustomWebConfig(it) },
                                connectionCheck = connectionCheck,
                                isChecking = isChecking,
                                onVerify = { viewModel.verifyConnection() },
                                onOpenWebBrowser = onOpenWebBrowser,
                                installedBrowsersCount = installedBrowsers.size,
                                onShowInstalledBrowsers = {
                                    viewModel.refreshInstalledBrowsers()
                                    showBrowsersSheet = true
                                }
                            )
                            DestinationType.ZIP_EXPORT -> ZipExportConfigForm(
                                config = zipConfig,
                                defaultFolderName = selectedFolder?.rootName.orEmpty(),
                                onUpdate = { viewModel.updateZipConfig(it) }
                            )
                        }
                    }
                }
            }

            item {
                SyncNotificationStatusBanner(
                    hasPermission = hasNotificationPermission,
                    onRequestOrOpenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (!promptedNotifBeforeUpload) {
                                promptedNotifBeforeUpload = true
                                startUploadAfterPermissionResult = false
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                runCatching { context.startActivity(settingsIntent) }.onFailure {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                )
            }

            item {
                when (val state = uploadState) {
                    is UploadState.Uploading -> {
                        UploadProgressCard(
                            state = state,
                            isServiceActive = isSyncServiceActive,
                            hasNotificationPermission = hasNotificationPermission,
                            onCancel = { viewModel.cancelUpload() }
                        )
                    }
                    is UploadState.Success, is UploadState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UploadResultCard(
                                state = state,
                                onCopyLink = { link ->
                                    clipboard.setText(AnnotatedString(link))
                                    onShowMessage("تم نسخ الرابط")
                                },
                                onOpenLink = { link ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                    }.onFailure {
                                        onShowMessage("تعذر فتح الرابط في المتصفح")
                                    }
                                },
                                onSaveZip = {
                                    val name = (exportedZip?.name ?: "${selectedFolder?.rootName ?: "folder"}.zip")
                                    createZipLauncher.launch(name)
                                },
                                onShareZip = {
                                    val fileToShare = exportedZip ?: (state as? UploadState.Success)?.exportedZipPath?.let { File(it) }
                                    if (fileToShare != null && fileToShare.exists()) {
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            fileToShare
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "مشاركة حزمة ZIP"))
                                    } else {
                                        onShowMessage("ملف ZIP غير متاح حالياً")
                                    }
                                },
                                onDismiss = { viewModel.resetUploadState() }
                            )
                            PrimaryLaunchButton(
                                destination = destination,
                                enabled = selectedFolder != null && !isUploading && !isScanning,
                                onClick = { launchBackgroundSyncWithNotificationCheck() }
                            )
                        }
                    }
                    else -> {
                        PrimaryLaunchButton(
                            destination = destination,
                            enabled = selectedFolder != null && !isUploading && !isScanning,
                            onClick = { launchBackgroundSyncWithNotificationCheck() }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("حفظ الوجهة الحالية") },
            text = {
                OutlinedTextField(
                    value = targetProfileName,
                    onValueChange = { targetProfileName = it },
                    label = { Text("اسم مختصر للوجهة") },
                    placeholder = { Text("مثال: مشروعي على GitHub") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCurrentAsTarget(targetProfileName)
                        targetProfileName = ""
                        showSaveDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showBrowsersSheet) {
        val targetSiteUrl = customWebConfig.endpointUrl.ifBlank { "https://app.netlify.com/drop" }
        InstalledBrowsersSheet(
            browsers = installedBrowsers,
            targetUrl = targetSiteUrl,
            hasSelectedFolder = selectedFolder != null,
            onRefresh = { viewModel.refreshInstalledBrowsers() },
            onOpenUrlInBrowser = { browser ->
                showBrowsersSheet = false
                BrowserDiscoveryHelper.openUrlInBrowser(context, targetSiteUrl, browser.packageName)
                    .onFailure { err -> onShowMessage("تعذر فتح ${browser.appName}: ${err.message}") }
            },
            onPackZipAndOpenBrowser = { browser ->
                showBrowsersSheet = false
                scope.launch {
                    val res = viewModel.packSelectedFolderToTempZip()
                    res.onSuccess { zipFile ->
                        onShowMessage("تم تجهيز ${zipFile.name} — جاري فتح ${browser.appName}")
                        BrowserDiscoveryHelper.openUrlInBrowser(context, targetSiteUrl, browser.packageName)
                    }.onFailure { err ->
                        onShowMessage("فشل ضغط المجلد: ${err.message}")
                    }
                }
            },
            onDismiss = { showBrowsersSheet = false }
        )
    }
}

@Composable
private fun HeroHeaderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_cloud_folder_hero_1795359930036),
            contentDescription = "رفع المجلدات من الجوال إلى السحابة",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NavyDeep.copy(alpha = 0.94f),
                            NavyDeep.copy(alpha = 0.78f),
                            NavyDeep.copy(alpha = 0.42f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = CyanBright.copy(alpha = 0.18f),
                shape = RoundedCornerShape(99.dp),
                border = BorderStroke(1.dp, CyanBright.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "بديل الحاسوب لرفع المجلدات",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanBright,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ارفع مجلداتك مباشرة من الجوال",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "يحافظ على كامل هيكل المجلدات الفرعية ويرفع إلى GitHub و Drive وأي موقع.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DestinationSelectorSection(
    selected: DestinationType,
    onSelect: (DestinationType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "2. اختر وجهة الرفع",
            style = MaterialTheme.typography.titleMedium
        )
        val entries = DestinationType.entries
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DestinationCard(
                    type = entries[0],
                    isSelected = selected == entries[0],
                    onClick = { onSelect(entries[0]) },
                    modifier = Modifier.weight(1f)
                )
                DestinationCard(
                    type = entries[1],
                    isSelected = selected == entries[1],
                    onClick = { onSelect(entries[1]) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DestinationCard(
                    type = entries[2],
                    isSelected = selected == entries[2],
                    onClick = { onSelect(entries[2]) },
                    modifier = Modifier.weight(1f)
                )
                DestinationCard(
                    type = entries[3],
                    isSelected = selected == entries[3],
                    onClick = { onSelect(entries[3]) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DestinationCard(
    type: DestinationType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("dest_card_${type.name}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = iconForDestination(type),
                contentDescription = type.titleAr,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = type.titleAr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = type.subtitleAr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PrimaryLaunchButton(
    destination: DestinationType,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag("start_upload_button")
    ) {
        Icon(
            imageVector = if (destination == DestinationType.ZIP_EXPORT) {
                Icons.Default.FolderZip
            } else {
                Icons.Default.RocketLaunch
            },
            contentDescription = null
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (destination == DestinationType.ZIP_EXPORT) {
                "ضغط المجلد في ملف ZIP الآن"
            } else {
                "بدء رفع المجلد إلى ${destination.titleAr}"
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

fun iconForDestination(type: DestinationType): ImageVector {
    return when (type) {
        DestinationType.GITHUB -> Icons.Default.Code
        DestinationType.GOOGLE_DRIVE -> Icons.Outlined.AddToDrive
        DestinationType.CUSTOM_WEB -> Icons.Default.Language
        DestinationType.ZIP_EXPORT -> Icons.Default.FolderZip
    }
}

@Composable
private fun SendToAiAssistantsCard(
    folderName: String,
    selectedFileCount: Int,
    selectedDirCount: Int,
    prefixFolderNames: Boolean,
    isPreparing: Boolean,
    onTogglePrefixFolderNames: (Boolean) -> Unit,
    onShareToAiApps: () -> Unit,
    onOpenChatGptWeb: () -> Unit,
    onOpenClaudeWeb: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_assistants_upload_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "إرسال مجلدات وملفات إلى ChatGPT و Claude",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "المحدد الآن من «$folderName»: $selectedFileCount ملف في $selectedDirCount مجلد. يمكنك إرسالها دفعة واحدة لتطبيق ChatGPT أو Claude أو فتحهما داخل المتصفح المدمج.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "تضمين مسار المجلد في أسماء الملفات (يحفظ الهيكل لـ ChatGPT و Claude)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Switch(
                    checked = prefixFolderNames,
                    onCheckedChange = onTogglePrefixFolderNames,
                    modifier = Modifier.testTag("ai_card_prefix_switch")
                )
            }

            Button(
                onClick = onShareToAiApps,
                enabled = !isPreparing && selectedFileCount > 0,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("share_to_ai_apps_btn")
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("جاري تجهيز $selectedFileCount ملف...")
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مشاركة الكل مع تطبيق ChatGPT / Claude ($selectedFileCount ملف)")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenChatGptWeb,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_chatgpt_web_btn")
                ) {
                    Text("فتح ChatGPT", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenClaudeWeb,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_claude_web_btn")
                ) {
                    Text("فتح Claude", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

