package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class PersistedSafFolder(
    val treeUri: Uri,
    val displayName: String,
    val persistedTime: Long
)

object SafFolderPickerUtils {

    fun loadPersistedFolders(context: Context): List<PersistedSafFolder> {
        return runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
                .sortedByDescending { it.persistedTime }
                .map { perm ->
                    val docId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }
                        .getOrDefault(perm.uri.lastPathSegment.orEmpty())
                    PersistedSafFolder(
                        treeUri = perm.uri,
                        displayName = extractFolderNameFromDocumentId(docId),
                        persistedTime = perm.persistedTime
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun persistReadPermission(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Provider did not grant persistable access; transient read permission still works
        }
    }

    fun releaseReadPermission(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Permission may have already been revoked by system
        }
    }

    fun extractFolderNameFromDocumentId(documentId: String): String {
        val afterColon = documentId.substringAfter(':', documentId)
        val trimmed = afterColon.trim('/')
        if (trimmed.isEmpty()) {
            return if (documentId.startsWith("primary")) "التخزين الداخلي" else documentId.ifBlank { "مجلد" }
        }
        return trimmed.substringAfterLast('/')
    }
}

@Stable
class SafFolderPickerState internal constructor(
    private val context: Context,
    private val launchInternal: (Uri?) -> Unit,
    private val onPickerError: (String) -> Unit,
    initialPersisted: List<PersistedSafFolder>
) {
    var persistedFolders by mutableStateOf(initialPersisted)
        internal set

    fun launch(initialUri: Uri? = null) {
        try {
            launchInternal(initialUri)
        } catch (_: ActivityNotFoundException) {
            onPickerError("لا يوجد مدير ملفات يدعم اختيار المجلدات (SAF) على هذا الجهاز")
        } catch (ex: SecurityException) {
            onPickerError("تعذر فتح منتقي المجلدات: ${ex.message ?: "خطأ صلاحيات"}")
        }
    }

    fun refreshPersistedFolders() {
        persistedFolders = SafFolderPickerUtils.loadPersistedFolders(context)
    }

    fun revokeFolderPermission(treeUri: Uri) {
        SafFolderPickerUtils.releaseReadPermission(context, treeUri)
        refreshPersistedFolders()
    }
}

@Composable
fun rememberSafFolderPicker(
    onFolderSelected: (Uri) -> Unit,
    onPickerError: (String) -> Unit = {}
): SafFolderPickerState {
    val context = LocalContext.current
    var stateRef: SafFolderPickerState? = null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafFolderPickerUtils.persistReadPermission(context, uri)
            stateRef?.refreshPersistedFolders()
            onFolderSelected(uri)
        }
    }

    val state = remember(context, launcher) {
        SafFolderPickerState(
            context = context,
            launchInternal = { initialUri -> launcher.launch(initialUri) },
            onPickerError = onPickerError,
            initialPersisted = SafFolderPickerUtils.loadPersistedFolders(context)
        )
    }
    stateRef = state
    return state
}

@Composable
fun SafFolderPickerSection(
    pickerState: SafFolderPickerState,
    selectedTreeUri: Uri?,
    hasSelectedFolder: Boolean,
    onSelectPersistedFolder: (Uri) -> Unit,
    onPickExtraFiles: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasSelectedFolder) {
            Button(
                onClick = { pickerState.launch(selectedTreeUri) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("pick_folder_button")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("اختيار مجلد من الجهاز")
            }

            if (onPickExtraFiles != null) {
                OutlinedButton(
                    onClick = onPickExtraFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("pick_files_initial_button")
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("أو اختيار ملفات متعددة")
                }
            }
        }

        if (pickerState.persistedFolders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "مجلدات مصرح لها سابقاً (SAF):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = pickerState.persistedFolders,
                        key = { it.treeUri.toString() }
                    ) { persisted ->
                        val isCurrent = persisted.treeUri == selectedTreeUri
                        InputChip(
                            selected = isCurrent,
                            onClick = { onSelectPersistedFolder(persisted.treeUri) },
                            label = { Text(persisted.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { pickerState.revokeFolderPermission(persisted.treeUri) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إلغاء صلاحية المجلد",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("persisted_saf_chip_${persisted.displayName}")
                        )
                    }
                }
            }
        }
    }
}
