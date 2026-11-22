package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.scanner.SafFolderScanner
import com.example.model.DefaultIgnoreRules
import com.example.model.FileCategory
import com.example.model.FileTypeRegistry
import com.example.model.FileUploadLog
import com.example.model.LogStatus
import com.example.model.ScannedFolder
import com.example.model.TreeSelectionHelper
import com.example.model.UploadState
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderPreviewCard(
    folder: ScannedFolder?,
    pickerState: SafFolderPickerState,
    activeIgnoreRules: Set<String>,
    selectedDirectories: Set<String>,
    selectedFilePaths: Set<String>,
    isScanning: Boolean,
    onSelectPersistedFolder: (android.net.Uri) -> Unit,
    onPickExtraFiles: () -> Unit,
    onMergeAnotherFolder: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleDirectorySelection: (String) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onRescan: () -> Unit,
    onToggleIgnoreRule: (String) -> Unit,
    onAddCustomIgnoreRule: (String) -> Unit,
    onIncludeAllWithoutIgnore: () -> Unit = {},
    onRestoreDefaultIgnoreRules: () -> Unit = {}
) {
    var showTreePreview by remember { mutableStateOf(true) }
    var showIgnoreEditor by remember { mutableStateOf(false) }
    var customRuleText by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember(folder) { mutableStateOf<FileCategory?>(null) }
    var showDirsOnlyFilter by remember(folder) { mutableStateOf(false) }
    var showAllTreeItems by remember(folder) { mutableStateOf(false) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "1. مجلد الجهاز",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = folder?.rootName ?: "اختر أي مجلد بهيكله الكامل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (folder != null) {
                    IconButton(onClick = onRescan) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة فحص المجلد")
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "جاري مسح المجلد والملفات الفرعية...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SafFolderPickerSection(
                pickerState = pickerState,
                selectedTreeUri = folder?.treeUri,
                hasSelectedFolder = folder != null,
                onSelectPersistedFolder = onSelectPersistedFolder,
                onPickExtraFiles = onPickExtraFiles
            )

            if (folder != null) {
                val allSelected = remember(folder, selectedDirectories, selectedFilePaths) {
                    TreeSelectionHelper.isAllSelected(folder, selectedDirectories, selectedFilePaths)
                }
                val selectedBytes = remember(folder, selectedFilePaths) {
                    folder.files.filter { selectedFilePaths.contains(it.relativePath) }.sumOf { it.sizeBytes }
                }

                val categoryCounts = remember(folder.files) {
                    FileTypeRegistry.summarizeCategories(folder.files)
                }

                // Summary Badges
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBadge("${selectedFilePaths.size}/${folder.files.size} ملف")
                    StatBadge("${selectedDirectories.size}/${folder.directoryCount} مجلد فرعي")
                    StatBadge(SafFolderScanner.formatBytes(selectedBytes), mono = true)
                    if (categoryCounts.isNotEmpty()) {
                        StatBadge("${categoryCounts.size} أنواع ملفات")
                    }
                    if (folder.skippedCount > 0) {
                        StatBadge("مستبعد: ${folder.skippedCount}")
                    }
                }

                // One-tap toggle to include all hidden/ignored folders and files (.git, build, dotfiles, etc.)
                Surface(
                    color = if (activeIgnoreRules.isEmpty()) {
                        EmeraldAccent.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (activeIgnoreRules.isEmpty()) {
                                onRestoreDefaultIgnoreRules()
                            } else {
                                onIncludeAllWithoutIgnore()
                            }
                        }
                        .testTag("include_all_types_switch_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "تضمين كافة الملفات والمجلدات دون استبعاد",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (activeIgnoreRules.isEmpty()) {
                                    "مفعّل — يتعرف على كل الصيغ والمجلدات بما فيها .git و build والملفات المخفية"
                                } else {
                                    "يُستبعد حالياً ${activeIgnoreRules.size} مجلد نظامي (فعّله لقراءة الكل)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = activeIgnoreRules.isEmpty(),
                            onCheckedChange = { includeAll ->
                                if (includeAll) onIncludeAllWithoutIgnore() else onRestoreDefaultIgnoreRules()
                            },
                            modifier = Modifier.testTag("include_all_types_switch")
                        )
                    }
                }

                // Select All Bar (Selects Folders + Files Together)
                Surface(
                    color = if (allSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (allSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onToggleSelectAll)
                        .testTag("select_all_items_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onToggleSelectAll() },
                                modifier = Modifier.testTag("select_all_items_checkbox")
                            )
                            Column {
                                Text(
                                    text = "تحديد الكل (Select All) — مجلدات وملفات معاً",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (allSelected) {
                                        "جميع المجلدات (${folder.directoryCount}) والملفات (${folder.files.size}) محددة للرفع"
                                    } else {
                                        "اضغط لتحديد جميع المجلدات والملفات معاً دفعة واحدة"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickerState.launch(folder.treeUri) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("change_folder_button")
                    ) {
                        Text("تغيير المجلد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onMergeAnotherFolder,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("merge_folder_button")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة مجلد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickExtraFiles,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("add_files_to_folder_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة ملفات", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showTreePreview = !showTreePreview },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("toggle_tree_button")
                    ) {
                        Icon(
                            imageVector = if (showTreePreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (showTreePreview) "إخفاء القائمة" else "عرض وتحديد (${folder.directories.size + folder.files.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Ignore Rules Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showIgnoreEditor = !showIgnoreEditor }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "قواعد الاستبعاد التلقائي (${activeIgnoreRules.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showIgnoreEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showIgnoreEditor) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allChips = remember(activeIgnoreRules) {
                            (DefaultIgnoreRules.presets + activeIgnoreRules).distinct()
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allChips.forEach { rule ->
                                FilterChip(
                                    selected = activeIgnoreRules.contains(rule),
                                    onClick = { onToggleIgnoreRule(rule) },
                                    label = { Text(rule, fontFamily = MonoFontFamily) }
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customRuleText,
                                onValueChange = { customRuleText = it },
                                label = { Text("إضافة اسم مجلد/ملف للاستبعاد") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    onAddCustomIgnoreRule(customRuleText)
                                    customRuleText = ""
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة قاعدة استبعاد")
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showTreePreview) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (categoryCounts.isNotEmpty() || folder.directories.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null && !showDirsOnlyFilter,
                                        onClick = {
                                            selectedCategoryFilter = null
                                            showDirsOnlyFilter = false
                                        },
                                        label = { Text("الكل (${folder.directories.size + folder.files.size})") }
                                    )
                                }
                                if (folder.directories.isNotEmpty()) {
                                    item {
                                        FilterChip(
                                            selected = showDirsOnlyFilter,
                                            onClick = {
                                                showDirsOnlyFilter = !showDirsOnlyFilter
                                                if (showDirsOnlyFilter) selectedCategoryFilter = null
                                            },
                                            label = { Text("مجلدات (${folder.directories.size})") }
                                        )
                                    }
                                }
                                items(categoryCounts.entries.toList(), key = { it.key.name }) { (cat, count) ->
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat && !showDirsOnlyFilter,
                                        onClick = {
                                            showDirsOnlyFilter = false
                                            selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                        },
                                        label = { Text("${cat.labelAr} ($count)") }
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val dirEntries = if (selectedCategoryFilter == null) {
                                    folder.directories.map { dir ->
                                        TreePreviewItem(
                                            relPath = dir,
                                            isDir = true,
                                            sizeBytes = 0L,
                                            typeBadge = "DIR"
                                        )
                                    }
                                } else {
                                    emptyList()
                                }
                                val fileEntries = if (!showDirsOnlyFilter) {
                                    folder.files.mapNotNull { file ->
                                        val cat = FileTypeRegistry.classify(file.name, file.mimeType)
                                        if (selectedCategoryFilter != null && cat != selectedCategoryFilter) {
                                            null
                                        } else {
                                            TreePreviewItem(
                                                relPath = file.relativePath,
                                                isDir = false,
                                                sizeBytes = file.sizeBytes,
                                                typeBadge = FileTypeRegistry.shortTypeBadge(file.name, file.mimeType)
                                            )
                                        }
                                    }
                                } else {
                                    emptyList()
                                }
                                val combinedTree = (dirEntries + fileEntries)
                                    .sortedBy { it.relPath.lowercase() }
                                val previewItems = if (showAllTreeItems) combinedTree else combinedTree.take(80)

                                previewItems.forEachIndexed { i, item ->
                                    val depth = item.relPath.count { it == '/' }.coerceAtMost(5)
                                    val displayName = item.relPath.substringAfterLast('/')
                                    val isChecked = if (item.isDir) {
                                        selectedDirectories.contains(item.relPath)
                                    } else {
                                        selectedFilePaths.contains(item.relPath)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (item.isDir) {
                                                    onToggleDirectorySelection(item.relPath)
                                                } else {
                                                    onToggleFileSelection(item.relPath)
                                                }
                                            }
                                            .padding(start = (depth * 10).dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = {
                                                    if (item.isDir) {
                                                        onToggleDirectorySelection(item.relPath)
                                                    } else {
                                                        onToggleFileSelection(item.relPath)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Icon(
                                                imageVector = if (item.isDir) {
                                                    Icons.Default.Folder
                                                } else {
                                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                                },
                                                contentDescription = null,
                                                tint = if (item.isDir) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (item.isDir) "$displayName/" else displayName,
                                                fontFamily = MonoFontFamily,
                                                fontWeight = if (item.isDir) FontWeight.SemiBold else FontWeight.Normal,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (!item.isDir) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.typeBadge,
                                                        fontFamily = MonoFontFamily,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(
                                                    text = SafFolderScanner.formatBytes(item.sizeBytes),
                                                    fontFamily = MonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    if (i < previewItems.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                                if (combinedTree.size > 80 && !showAllTreeItems) {
                                    TextButton(
                                        onClick = { showAllTreeItems = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("عرض جميع العناصر (${combinedTree.size}) — جميعها مشمولة بالرفع")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TreePreviewItem(
    val relPath: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val typeBadge: String
)

@Composable
private fun StatBadge(text: String, mono: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontFamily = if (mono) MonoFontFamily else MaterialTheme.typography.labelMedium.fontFamily,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun UploadProgressCard(
    state: UploadState.Uploading,
    isServiceActive: Boolean = true,
    hasNotificationPermission: Boolean = true,
    onCancel: () -> Unit
) {
    val progress = if (state.totalFiles > 0) {
        (state.currentIndex.toFloat() / state.totalFiles.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upload_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isServiceActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (hasNotificationPermission) {
                                "مزامنة الخلفية نشطة • إشعار التقدم المستمر ظاهر"
                            } else {
                                "مزامنة الخلفية نشطة (فعّل الإشعارات لعرض شريط التقدم)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.stageLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.currentIndex} / ${state.totalFiles}",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Text(
                text = state.currentFilePath,
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${SafFolderScanner.formatBytes(state.bytesTransferred)} / ${SafFolderScanner.formatBytes(state.totalBytes)}",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("cancel_upload_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إلغاء")
                }
            }
        }
    }
}

@Composable
fun SyncNotificationStatusBanner(
    hasPermission: Boolean,
    onRequestOrOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (hasPermission) {
            EmeraldAccent.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (hasPermission) EmeraldAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_notification_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (hasPermission) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = if (hasPermission) EmeraldAccent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (hasPermission) {
                            "المزامنة في الخلفية وإشعار التقدم المستمر جاهزان"
                        } else {
                            "إشعار تقدم الرفع المستمر غير مفعّل"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (hasPermission) {
                            "يستمر رفع المجلدات الكبيرة في الخلفية مع شريط تقدم وإلغاء مباشر من الإشعارات."
                        } else {
                            "فعّل إذن الإشعارات لمتابعة نسبة الرفع المستمرة وإلغاء العملية من شريط الإشعارات."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!hasPermission) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onRequestOrOpenSettings,
                    modifier = Modifier.testTag("enable_sync_notifications_btn")
                ) {
                    Text("تفعيل")
                }
            }
        }
    }
}

@Composable
fun UploadResultCard(
    state: UploadState,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onSaveZip: () -> Unit,
    onShareZip: () -> Unit,
    onDismiss: () -> Unit
) {
    var showLogs by remember { mutableStateOf(false) }

    when (state) {
        is UploadState.Success -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldAccent.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldAccent
                        )
                        Text(
                            text = "اكتملت العملية بنجاح",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!state.resultLink.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = state.resultLink,
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onCopyLink(state.resultLink) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط")
                                }
                                IconButton(onClick = { onOpenLink(state.resultLink) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح الرابط")
                                }
                            }
                        }
                    }

                    if (!state.exportedZipPath.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSaveZip,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("save_zip_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("حفظ ZIP")
                            }
                            OutlinedButton(
                                onClick = onShareZip,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("share_zip_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة للمتصفح")
                            }
                        }
                    }

                    ResultLogsFooter(
                        logs = state.logs,
                        showLogs = showLogs,
                        onToggleLogs = { showLogs = !showLogs },
                        onDismiss = onDismiss
                    )
                }
            }
        }

        is UploadState.Error -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "تعذر إتمام الرفع",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (state.uploadedSoFar > 0) {
                        Text(
                            text = "تم رفع ${state.uploadedSoFar} ملف قبل التوقف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    ResultLogsFooter(
                        logs = state.logs,
                        showLogs = showLogs,
                        onToggleLogs = { showLogs = !showLogs },
                        onDismiss = onDismiss
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun ResultLogsFooter(
    logs: List<FileUploadLog>,
    showLogs: Boolean,
    onToggleLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (logs.isNotEmpty()) {
            TextButton(onClick = onToggleLogs) {
                Text(if (showLogs) "إخفاء سجل الملفات" else "عرض سجل الملفات (${logs.size})")
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        TextButton(onClick = onDismiss) {
            Text("إغلاق")
        }
    }

    AnimatedVisibility(visible = showLogs && logs.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                logs.take(30).forEach { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = log.relativePath,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = log.detail,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (log.status == LogStatus.SUCCESS) EmeraldAccent else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
