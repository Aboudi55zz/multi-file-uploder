package com.example.data.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.example.R
import com.example.data.sync.ActiveWorkspaceBridge
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import java.io.File
import java.io.FileNotFoundException

class FolderPushDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        val cursor = MatrixCursor(cols)
        val row = cursor.newRow()
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        row.add(Root.COLUMN_TITLE, "FolderPush")
        row.add(Root.COLUMN_SUMMARY, "مجلداتك وملفاتك جاهزة لـ ChatGPT و Claude والمتصفحات")
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_RECENTS)
        row.add(Root.COLUMN_MIME_TYPES, "*/*")
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return cursor
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val cursor = MatrixCursor(cols)
        when {
            documentId == ROOT_DOC_ID -> includeRootDoc(cursor)
            documentId == WS_ROOT_DOC_ID -> {
                val ws = ActiveWorkspaceBridge.getWorkspace()
                    ?: throw FileNotFoundException("Active workspace unavailable")
                includeWorkspaceRootDoc(cursor, ws)
            }
            documentId?.startsWith(WS_DIR_PREFIX) == true -> {
                val relDir = documentId.removePrefix(WS_DIR_PREFIX)
                includeWorkspaceDir(cursor, documentId, relDir.substringAfterLast('/'))
            }
            documentId?.startsWith(WS_FILE_PREFIX) == true -> {
                val file = resolveWorkspaceFile(documentId)
                    ?: throw FileNotFoundException("Workspace file not found: $documentId")
                includeWorkspaceFile(cursor, file)
            }
            else -> {
                val file = resolveSafeExportFile(documentId)
                    ?: throw FileNotFoundException("Document not found: $documentId")
                includeExportFile(cursor, file)
            }
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val cursor = MatrixCursor(cols)

        when {
            parentDocumentId == ROOT_DOC_ID -> {
                ActiveWorkspaceBridge.getWorkspace()?.let { ws ->
                    if (ws.files.isNotEmpty() || ws.directories.isNotEmpty()) {
                        includeWorkspaceRootDoc(cursor, ws)
                    }
                }
                val files = exportsDirectory().listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                for (file in files) {
                    includeExportFile(cursor, file)
                }
            }

            parentDocumentId == WS_ROOT_DOC_ID -> {
                val ws = ActiveWorkspaceBridge.getWorkspace() ?: return cursor
                populateWorkspaceChildren(cursor, ws, parentRelDir = "")
            }

            parentDocumentId?.startsWith(WS_DIR_PREFIX) == true -> {
                val ws = ActiveWorkspaceBridge.getWorkspace() ?: return cursor
                val relDir = parentDocumentId.removePrefix(WS_DIR_PREFIX)
                populateWorkspaceChildren(cursor, ws, parentRelDir = relDir)
            }
        }

        return cursor
    }

    override fun queryRecentDocuments(rootId: String?, projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val cursor = MatrixCursor(cols)
        if (rootId != ROOT_ID) return cursor

        ActiveWorkspaceBridge.getWorkspace()?.files?.take(20)?.forEach { file ->
            includeWorkspaceFile(cursor, file)
        }

        val files = exportsDirectory().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(20)
            .orEmpty()

        for (file in files) {
            includeExportFile(cursor, file)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (mode != null && mode.contains("w")) {
            throw FileNotFoundException("FolderPush documents are read-only")
        }

        if (documentId?.startsWith(WS_FILE_PREFIX) == true) {
            val wsFile = resolveWorkspaceFile(documentId)
                ?: throw FileNotFoundException("Workspace file not found: $documentId")
            val ctx = context ?: throw FileNotFoundException("Provider context unavailable")
            return runCatching {
                ctx.contentResolver.openFileDescriptor(wsFile.uri, "r")
            }.getOrNull() ?: throw FileNotFoundException("Unable to open descriptor for ${wsFile.relativePath}")
        }

        val file = resolveSafeExportFile(documentId)
            ?: throw FileNotFoundException("Document not found: $documentId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun populateWorkspaceChildren(
        cursor: MatrixCursor,
        ws: ScannedFolder,
        parentRelDir: String
    ) {
        val directChildDirs = ws.directories.filter { dir ->
            if (parentRelDir.isEmpty()) {
                !dir.contains('/')
            } else {
                dir.startsWith("$parentRelDir/") && !dir.removePrefix("$parentRelDir/").contains('/')
            }
        }
        for (dir in directChildDirs) {
            includeWorkspaceDir(cursor, "$WS_DIR_PREFIX$dir", dir.substringAfterLast('/'))
        }

        val directChildFiles = ws.files.filter { file ->
            val fileParent = file.relativePath.substringBeforeLast('/', "")
            fileParent == parentRelDir
        }
        for (file in directChildFiles) {
            includeWorkspaceFile(cursor, file)
        }
    }

    private fun resolveWorkspaceFile(documentId: String): ScannedFile? {
        val relPath = documentId.removePrefix(WS_FILE_PREFIX)
        val ws = ActiveWorkspaceBridge.getWorkspace() ?: return null
        return ws.files.firstOrNull { it.relativePath == relPath }
    }

    private fun exportsDirectory(): File {
        val ctx = context ?: throw FileNotFoundException("Provider context unavailable")
        return File(ctx.cacheDir, "exports").apply { mkdirs() }
    }

    private fun resolveSafeExportFile(documentId: String?): File? {
        if (documentId.isNullOrBlank() || !documentId.startsWith(FILE_DOC_PREFIX)) return null
        val fileName = documentId.removePrefix(FILE_DOC_PREFIX)
        if (!isSafeArchiveFileName(fileName)) return null
        val baseDir = exportsDirectory()
        val candidate = File(baseDir, fileName)
        val canonicalBase = baseDir.canonicalPath + File.separator
        if (!candidate.canonicalPath.startsWith(canonicalBase)) return null
        return if (candidate.exists() && candidate.isFile) candidate else null
    }

    private fun includeRootDoc(cursor: MatrixCursor) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_DISPLAY_NAME, "FolderPush")
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, 0L)
    }

    private fun includeWorkspaceRootDoc(cursor: MatrixCursor, ws: ScannedFolder) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, WS_ROOT_DOC_ID)
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_DISPLAY_NAME, "${ws.rootName} (${ws.files.size} ملف)")
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, ws.totalBytes)
    }

    private fun includeWorkspaceDir(cursor: MatrixCursor, docId: String, name: String) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, docId)
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_DISPLAY_NAME, name)
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, 0L)
    }

    private fun includeWorkspaceFile(cursor: MatrixCursor, file: ScannedFile) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, "$WS_FILE_PREFIX${file.relativePath}")
        row.add(Document.COLUMN_MIME_TYPE, file.mimeType.ifBlank { "application/octet-stream" })
        row.add(Document.COLUMN_DISPLAY_NAME, file.name)
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified)
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, file.sizeBytes)
    }

    private fun includeExportFile(cursor: MatrixCursor, file: File) {
        val row = cursor.newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, "$FILE_DOC_PREFIX${file.name}")
        row.add(Document.COLUMN_MIME_TYPE, "application/zip")
        row.add(Document.COLUMN_DISPLAY_NAME, file.name)
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, file.length())
    }

    companion object {
        private const val ROOT_ID = "folderpush_exports"
        private const val ROOT_DOC_ID = "root"
        private const val WS_ROOT_DOC_ID = "ws_root"
        private const val WS_DIR_PREFIX = "ws_dir:"
        private const val WS_FILE_PREFIX = "ws_file:"
        private const val FILE_DOC_PREFIX = "zip:"

        fun isSafeArchiveFileName(fileName: String): Boolean {
            return fileName.isNotBlank() &&
                !fileName.contains("/") &&
                !fileName.contains("\\") &&
                !fileName.contains("..") &&
                fileName.endsWith(".zip", ignoreCase = true)
        }

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
    }
}
