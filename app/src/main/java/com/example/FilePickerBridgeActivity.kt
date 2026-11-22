package com.example

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.scanner.SafFolderScanner
import com.example.data.sync.ActiveWorkspaceBridge
import com.example.data.sync.AiMultiFileBridge
import com.example.data.sync.AiUploadBridgeUtils
import com.example.data.uploader.ZipPackager
import com.example.model.DefaultIgnoreRules
import com.example.model.FileTypeRegistry
import com.example.model.ScannedFolder
import com.example.model.TreeSelectionHelper
import com.example.ui.components.SafFolderPickerSection
import com.example.ui.components.rememberSafFolderPicker
import com.example.ui.theme.FolderPushTheme
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch
import java.io.File

/**
 * Handles system ACTION_GET_CONTENT / ACTION_PICK requests from mobile browsers and other apps,
 * letting users return an entire folder (packed as ZIP or multiple files) directly as the picked result.
 */
class FilePickerBridgeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true
        val extraMimeTypes = intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        val requestedMime = when {
            !extraMimeTypes.isNullOrEmpty() -> extraMimeTypes.joinToString(", ")
            else -> intent?.type ?: "*/*"
        }
        val callerLabel = AiUploadBridgeUtils.detectCallerLabel(
            callerPackage = callingActivity?.packageName,
            referrerHost = referrer?.host
        )

        setContent {
            FolderPushTheme {
                FilePickerBridgeScreen(
                    allowMultiple = allowMultiple,
                    requestedMime = requestedMime,
                    callerLabel = callerLabel,
                    onDeliverSingleUri = { uri ->
                        val result = Intent().apply {
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                    onDeliverMultipleUris = { uris ->
                        val result = Intent().apply {
                            if (uris.size == 1) {
                                data = uris.first()
                            } else {
                                val clip = ClipData.newRawUri("FolderPushFiles", uris.first())
                                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                clipData = clip
                            }
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePickerBridgeScreen(
    allowMultiple: Boolean,
    requestedMime: String,
    callerLabel: String?,
    onDeliverSingleUri: (Uri) -> Unit,
    onDeliverMultipleUris: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { SafFolderScanner(context) }
    val zipPackager = remember { ZipPackager(context) }

    val preloadedWorkspace = remember { ActiveWorkspaceBridge.getWorkspace() }
    var scannedFolder by remember { mutableStateOf<ScannedFolder?>(preloadedWorkspace) }
    var selectedDirs by remember {
        mutableStateOf(
            preloadedWorkspace?.let { TreeSelectionHelper.selectAll(it).selectedDirectories }.orEmpty()
        )
    }
    var selectedFiles by remember {
        mutableStateOf(
            preloadedWorkspace?.let { TreeSelectionHelper.selectAll(it).selectedFiles }.orEmpty()
        )
    }
    var prefixFolderNamesForAi by remember { mutableStateOf(true) }
    var includeAllWithoutIgnore by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun activeBridgeIgnoreSet(): Set<String> {
        return if (includeAllWithoutIgnore) emptySet() else DefaultIgnoreRules.presets.toSet()
    }

    fun applyScannedAndSelectAll(folder: ScannedFolder) {
        scannedFolder = folder
        val all = TreeSelectionHelper.selectAll(folder)
        selectedDirs = all.selectedDirectories
        selectedFiles = all.selectedFiles
    }

    val existingZips = remember {
        File(context.cacheDir, "exports").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    val safFolderPicker = rememberSafFolderPicker(
        onFolderSelected = { treeUri ->
            scope.launch {
                isBusy = true
                errorText = null
                statusText = "جاري مسح المجلد والمجلدات الفرعية..."
                runCatching {
                    scanner.scanFolder(treeUri, activeBridgeIgnoreSet())
                }.onSuccess { folder ->
                    applyScannedAndSelectAll(folder)
                    statusText = null
                }.onFailure { err ->
                    errorText = err.message ?: "تعذر قراءة المجلد"
                    statusText = null
                }
                isBusy = false
            }
        },
        onPickerError = { errorText = it }
    )

    val mergeFolderPicker = rememberSafFolderPicker(
        onFolderSelected = { treeUri ->
            scope.launch {
                isBusy = true
                errorText = null
                statusText = "جاري دمج المجلد الإضافي..."
                runCatching {
                    scanner.scanFolder(treeUri, activeBridgeIgnoreSet())
                }.onSuccess { folder ->
                    applyScannedAndSelectAll(TreeSelectionHelper.mergeFolderIntoWorkspace(scannedFolder, folder))
                    statusText = null
                }.onFailure { err ->
                    errorText = err.message ?: "تعذر قراءة المجلد الإضافي"
                    statusText = null
                }
                isBusy = false
            }
        },
        onPickerError = { errorText = it }
    )

    val addExtraFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isBusy = true
                errorText = null
                runCatching {
                    scanner.scanLooseFiles(uris)
                }.onSuccess { files ->
                    applyScannedAndSelectAll(TreeSelectionHelper.mergeExtraFiles(scannedFolder, files))
                }.onFailure { err ->
                    errorText = err.message ?: "تعذر قراءة الملفات"
                }
                isBusy = false
            }
        }
    }

    val singleFileFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onDeliverSingleUri(uri)
    }

    val multiFileFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onDeliverMultipleUris(uris)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = callerLabel?.let { "إرسال مجلدات وملفات إلى $it" }
                                ?: "اختيار مجلد أو ملفات متعددة",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (allowMultiple) {
                                "الرفع المتعدد مفعّل (ClipData) • $requestedMime"
                            } else {
                                "النوع المطلوب: $requestedMime"
                            },
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "إلغاء")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "متوافق مع ChatGPT و Claude: أرسل كل ملفات المجلدات المختارة دفعة واحدة، أو اضغطها في ملف ZIP واحد يحفظ كامل الهيكل.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "تقديم مجلد كامل للتطبيق أو المتصفح",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اختر أي مجلد من هاتفك ليتم ضغطه كـ ZIP أو إرسال جميع ملفاته مباشرة إلى المتصفح الذي طلب اختيار ملف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SafFolderPickerSection(
                            pickerState = safFolderPicker,
                            selectedTreeUri = scannedFolder?.treeUri,
                            hasSelectedFolder = scannedFolder != null,
                            onSelectPersistedFolder = { treeUri ->
                                scope.launch {
                                    isBusy = true
                                    runCatching {
                                        scanner.scanFolder(treeUri, activeBridgeIgnoreSet())
                                    }.onSuccess { applyScannedAndSelectAll(it) }
                                        .onFailure { errorText = it.message }
                                    isBusy = false
                                }
                            },
                            onPickExtraFiles = { addExtraFilesLauncher.launch(arrayOf("*/*")) }
                        )

                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            statusText?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        errorText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }

                        scannedFolder?.let { folder ->
                            val allSelected = TreeSelectionHelper.isAllSelected(folder, selectedDirs, selectedFiles)
                            val filteredSnapshot = TreeSelectionHelper.buildSelectedFolderSnapshot(
                                folder = folder,
                                selectedDirectories = selectedDirs,
                                selectedFiles = selectedFiles
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folder.rootName, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "محدد: ${filteredSnapshot.files.size}/${folder.files.size} ملف • ${filteredSnapshot.directoryCount}/${folder.directoryCount} مجلد • ${SafFolderScanner.formatBytes(filteredSnapshot.totalBytes)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    OutlinedButton(onClick = { safFolderPicker.launch(folder.treeUri) }) {
                                        Text("تغيير")
                                    }
                                }
                            }

                            // Select All Bar (Folders + Files together)
                            Surface(
                                color = if (allSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val next = TreeSelectionHelper.toggleSelectAll(folder, selectedDirs, selectedFiles)
                                        selectedDirs = next.selectedDirectories
                                        selectedFiles = next.selectedFiles
                                    }
                                    .testTag("bridge_select_all_row")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Checkbox(
                                            checked = allSelected,
                                            onCheckedChange = {
                                                val next = TreeSelectionHelper.toggleSelectAll(folder, selectedDirs, selectedFiles)
                                                selectedDirs = next.selectedDirectories
                                                selectedFiles = next.selectedFiles
                                            },
                                            modifier = Modifier.testTag("bridge_select_all_checkbox")
                                        )
                                        Text(
                                            text = "تحديد الكل (Select All) — مجلدات وملفات معاً",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(Icons.Default.SelectAll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Interactive Folder & File Checklist
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val combinedItems = remember(folder) {
                                    val dirs = folder.directories.map { Triple(it, true, "") }
                                    val files = folder.files.map {
                                        Triple(it.relativePath, false, FileTypeRegistry.shortTypeBadge(it.name, it.mimeType))
                                    }
                                    (dirs + files).sortedBy { it.first.lowercase() }.take(80)
                                }
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    combinedItems.forEachIndexed { idx, (relPath, isDir, badge) ->
                                        val checked = if (isDir) selectedDirs.contains(relPath) else selectedFiles.contains(relPath)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val next = if (isDir) {
                                                        TreeSelectionHelper.toggleDirectory(folder, selectedDirs, selectedFiles, relPath)
                                                    } else {
                                                        TreeSelectionHelper.toggleFile(folder, selectedDirs, selectedFiles, relPath)
                                                    }
                                                    selectedDirs = next.selectedDirectories
                                                    selectedFiles = next.selectedFiles
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = {
                                                    val next = if (isDir) {
                                                        TreeSelectionHelper.toggleDirectory(folder, selectedDirs, selectedFiles, relPath)
                                                    } else {
                                                        TreeSelectionHelper.toggleFile(folder, selectedDirs, selectedFiles, relPath)
                                                    }
                                                    selectedDirs = next.selectedDirectories
                                                    selectedFiles = next.selectedFiles
                                                },
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Icon(
                                                imageVector = if (isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                                contentDescription = null,
                                                tint = if (isDir) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (isDir) "$relPath/" else relPath,
                                                fontFamily = MonoFontFamily,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isDir && badge.isNotEmpty()) {
                                                Text(
                                                    text = badge,
                                                    fontFamily = MonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (idx < combinedItems.lastIndex) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { mergeFolderPicker.launch(folder.treeUri) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة مجلد", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { addExtraFilesLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة ملفات", maxLines = 1)
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val nextValue = !includeAllWithoutIgnore
                                        includeAllWithoutIgnore = nextValue
                                        val treeUri = folder.treeUri
                                        if (android.provider.DocumentsContract.isTreeUri(treeUri)) {
                                            scope.launch {
                                                isBusy = true
                                                runCatching {
                                                    scanner.scanFolder(
                                                        treeUri,
                                                        if (nextValue) emptySet() else DefaultIgnoreRules.presets.toSet()
                                                    )
                                                }.onSuccess { applyScannedAndSelectAll(it) }
                                                isBusy = false
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "تضمين كافة الملفات والمجلدات دون استبعاد",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "يقرأ كل أنواع الملفات والمجلدات بما فيها .git و build والمخفية",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = includeAllWithoutIgnore,
                                        onCheckedChange = { checked ->
                                            includeAllWithoutIgnore = checked
                                            val treeUri = folder.treeUri
                                            if (android.provider.DocumentsContract.isTreeUri(treeUri)) {
                                                scope.launch {
                                                    isBusy = true
                                                    runCatching {
                                                        scanner.scanFolder(
                                                            treeUri,
                                                            if (checked) emptySet() else DefaultIgnoreRules.presets.toSet()
                                                        )
                                                    }.onSuccess { applyScannedAndSelectAll(it) }
                                                    isBusy = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { prefixFolderNamesForAi = !prefixFolderNamesForAi }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "تضمين مسار المجلد في اسم كل ملف",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "يحوّل src/App.kt إلى src_App.kt ليفهم ChatGPT و Claude موقع كل ملف داخل المجلد",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = prefixFolderNamesForAi,
                                        onCheckedChange = { prefixFolderNamesForAi = it }
                                    )
                                }
                            }

                            if (allowMultiple && filteredSnapshot.files.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            statusText = "جاري تجهيز جميع ملفات المجلدات للإرسال المتعدد..."
                                            runCatching {
                                                AiMultiFileBridge.stageFilesForAiUpload(
                                                    context = context,
                                                    folder = filteredSnapshot,
                                                    prefixFolderNames = prefixFolderNamesForAi
                                                )
                                            }.onSuccess { stagedUris ->
                                                if (stagedUris.isEmpty()) {
                                                    errorText = "تعذر قراءة الملفات المختارة"
                                                } else {
                                                    onDeliverMultipleUris(stagedUris)
                                                }
                                            }.onFailure { err ->
                                                errorText = err.message ?: "فشل تجهيز الملفات"
                                            }
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("bridge_send_multi_ai_btn")
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("إرسال الكل دفعة واحدة (${filteredSnapshot.files.size} ملف)")
                                }
                            }

                            val zipModifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("bridge_send_zip_btn")
                            if (allowMultiple && filteredSnapshot.files.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            statusText = "جاري ضغط المحدد وإرساله..."
                                            runCatching {
                                                val zipFile = zipPackager.packFolderToZip(filteredSnapshot, filteredSnapshot.rootName) { _, _, _, _ -> }
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    zipFile
                                                )
                                            }.onSuccess { zipUri ->
                                                onDeliverSingleUri(zipUri)
                                            }.onFailure { err ->
                                                errorText = err.message ?: "فشل إنشاء الأرشيف"
                                            }
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy && filteredSnapshot.files.isNotEmpty(),
                                    modifier = zipModifier
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ضغط المجلدات والملفات كـ ZIP (${filteredSnapshot.files.size} ملف)")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            statusText = "جاري ضغط المحدد وإرساله..."
                                            runCatching {
                                                val zipFile = zipPackager.packFolderToZip(filteredSnapshot, filteredSnapshot.rootName) { _, _, _, _ -> }
                                                FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    zipFile
                                                )
                                            }.onSuccess { zipUri ->
                                                onDeliverSingleUri(zipUri)
                                            }.onFailure { err ->
                                                errorText = err.message ?: "فشل إنشاء الأرشيف"
                                            }
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy && filteredSnapshot.files.isNotEmpty(),
                                    modifier = zipModifier
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("ضغط المجلدات والملفات كـ ZIP (${filteredSnapshot.files.size} ملف)")
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        if (allowMultiple) {
                            multiFileFallback.launch(arrayOf("*/*"))
                        } else {
                            singleFileFallback.launch(arrayOf("*/*"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (allowMultiple) "اختيار ملفات من كافة الأنواع (*/*)" else "اختيار أي ملف من الجهاز (*/*)")
                }
            }

            if (existingZips.isNotEmpty()) {
                item {
                    Text(
                        text = "حزم ZIP جاهزة مسبقاً:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(existingZips, key = { it.name }) { zip ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(zip.name, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    SafFolderScanner.formatBytes(zip.length()),
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        zip
                                    )
                                    onDeliverSingleUri(uri)
                                }
                            ) {
                                Text("اختيار")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
