package com.example.data.uploader

import android.content.Context
import android.net.Uri
import com.example.model.ScannedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPackager(private val context: Context) {

    suspend fun packFolderToZip(
        folder: ScannedFolder,
        archiveName: String,
        onProgress: (index: Int, total: Int, currentPath: String, bytesDone: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Clean previous exports older than 1 hour to keep cache tidy
        exportDir.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 3_600_000L) {
                old.delete()
            }
        }

        val sanitized = archiveName
            .ifBlank { folder.rootName }
            .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]"), "_")
            .removeSuffix(".zip")
            .ifBlank { "folder_archive" }

        val outFile = File(exportDir, "$sanitized.zip")
        val buffer = ByteArray(32 * 1024)
        var bytesDone = 0L

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putNextEntry(ZipEntry("${folder.rootName}/"))
            zos.closeEntry()

            folder.directories.forEach { dirRel ->
                currentCoroutineContext().ensureActive()
                val dirEntry = "${folder.rootName}/$dirRel/".replace("//", "/")
                zos.putNextEntry(ZipEntry(dirEntry))
                zos.closeEntry()
            }

            folder.files.forEachIndexed { idx, file ->
                currentCoroutineContext().ensureActive()
                onProgress(idx + 1, folder.files.size, file.relativePath, bytesDone)

                val entryName = "${folder.rootName}/${file.relativePath}".replace("//", "/")
                zos.putNextEntry(ZipEntry(entryName))
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        zos.write(buffer, 0, read)
                        bytesDone += read
                    }
                }
                zos.closeEntry()
            }
        }

        outFile
    }

    suspend fun copyZipToDestinationUri(sourceZip: File, targetUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            FileInputStream(sourceZip).use { input ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        } ?: error("تعذر فتح مسار الحفظ المختار")
    }
}
