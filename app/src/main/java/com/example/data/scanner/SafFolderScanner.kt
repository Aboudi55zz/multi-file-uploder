package com.example.data.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.model.FileTypeRegistry
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

class SafFolderScanner(private val context: Context) {

    suspend fun scanFolder(
        treeUri: Uri,
        ignorePatterns: Set<String>
    ): ScannedFolder = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Temporary read grant is still valid for the current session
        }

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = queryDocumentName(treeUri, rootDocId) ?: "SelectedFolder"

        val files = mutableListOf<ScannedFile>()
        val directories = mutableListOf<String>()
        val visitedDirs = HashSet<String>()
        var skippedCount = 0
        var directoryCount = 0

        suspend fun traverse(dirDocId: String, currentRelPath: String) {
            currentCoroutineContext().ensureActive()
            if (!visitedDirs.add(dirDocId)) return

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val flagsIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val docId = cursor.getString(idIdx) ?: continue
                    val rawName = if (nameIdx >= 0 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null
                    val name = rawName?.takeIf { it.isNotBlank() }
                        ?: docId.substringAfterLast('/').substringAfterLast(':').ifBlank { "item" }
                    val rawMime = if (mimeIdx >= 0 && !cursor.isNull(mimeIdx)) cursor.getString(mimeIdx) ?: "" else ""
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
                    val modified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                    val docFlags = if (flagsIdx >= 0 && !cursor.isNull(flagsIdx)) cursor.getInt(flagsIdx) else 0

                    if (shouldIgnore(name, ignorePatterns)) {
                        skippedCount++
                        continue
                    }

                    val relPath = if (currentRelPath.isEmpty()) name else "$currentRelPath/$name"
                    if (FileTypeRegistry.isDirectoryMime(rawMime, docFlags)) {
                        directoryCount++
                        directories.add(relPath)
                        traverse(docId, relPath)
                    } else {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val resolvedMime = resolveMimeType(name, rawMime)
                        files.add(
                            ScannedFile(
                                uri = fileUri,
                                name = name,
                                relativePath = relPath,
                                sizeBytes = size,
                                mimeType = resolvedMime,
                                lastModified = modified
                            )
                        )
                    }
                }
            }
        }

        traverse(rootDocId, "")
        files.sortBy { it.relativePath.lowercase(Locale.ROOT) }
        directories.sortBy { it.lowercase(Locale.ROOT) }
        val totalBytes = files.sumOf { it.sizeBytes }

        ScannedFolder(
            treeUri = treeUri,
            rootName = rootName,
            files = files,
            directories = directories,
            skippedCount = skippedCount,
            directoryCount = directoryCount,
            totalBytes = totalBytes,
            activeIgnorePatterns = ignorePatterns
        )
    }

    suspend fun scanLooseFiles(uris: List<Uri>): List<ScannedFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ScannedFile>()
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        for (uri in uris) {
            currentCoroutineContext().ensureActive()
            var displayName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "file" } ?: "file"
            var sizeBytes = 0L

            runCatching {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameCol >= 0 && !cursor.isNull(nameCol)) {
                            displayName = cursor.getString(nameCol) ?: displayName
                        }
                        val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeCol >= 0 && !cursor.isNull(sizeCol)) {
                            sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                        }
                    }
                }
            }

            val rawMime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val resolvedMime = resolveMimeType(displayName, rawMime)

            result.add(
                ScannedFile(
                    uri = uri,
                    name = displayName,
                    relativePath = displayName,
                    sizeBytes = sizeBytes,
                    mimeType = resolvedMime,
                    lastModified = System.currentTimeMillis()
                )
            )
        }
        result
    }

    private fun queryDocumentName(treeUri: Uri, docId: String): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            treeUri.lastPathSegment?.substringAfterLast(':')
        }
    }

    private fun shouldIgnore(name: String, patterns: Set<String>): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { pattern ->
            val clean = pattern.trim()
            clean.isNotEmpty() && name.equals(clean, ignoreCase = false)
        }
    }

    private fun resolveMimeType(fileName: String, providerMime: String): String {
        return FileTypeRegistry.resolveMimeType(fileName, providerMime) { ext ->
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
