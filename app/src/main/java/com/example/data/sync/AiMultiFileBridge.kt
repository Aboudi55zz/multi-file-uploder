package com.example.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

object ActiveWorkspaceBridge {
    private val activeWorkspace = AtomicReference<ScannedFolder?>(null)

    fun publishWorkspace(folder: ScannedFolder?) {
        activeWorkspace.set(folder)
    }

    fun getWorkspace(): ScannedFolder? = activeWorkspace.get()
}

object AiUploadBridgeUtils {

    fun detectCallerLabel(callerPackage: String?, referrerHost: String?): String? {
        val combined = listOfNotNull(callerPackage, referrerHost)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (combined.isBlank()) return null

        return when {
            combined.contains("openai") || combined.contains("chatgpt") -> "ChatGPT"
            combined.contains("anthropic") || combined.contains("claude") -> "Claude"
            combined.contains("chrome") -> "Google Chrome"
            combined.contains("firefox") || combined.contains("mozilla") -> "Firefox"
            combined.contains("sbrowser") -> "Samsung Internet"
            combined.contains("emmx") || combined.contains("edge") -> "Microsoft Edge"
            combined.contains("opera") -> "Opera"
            combined.contains("brave") -> "Brave"
            else -> callerPackage
        }
    }

    fun buildFlattenedFileName(relativePath: String, rootName: String? = null): String {
        val cleanRel = relativePath.trim().trim('/')
        val combined = if (!rootName.isNullOrBlank() &&
            rootName != "ملفات مختارة" &&
            rootName != "ملفات ومجلدات مختارة" &&
            !cleanRel.startsWith("$rootName/")
        ) {
            "$rootName/$cleanRel"
        } else {
            cleanRel
        }

        return combined
            .replace('/', '_')
            .replace('\\', '_')
            .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "file" }
    }

    fun isAiAssistantPackageOrHost(callerLabel: String?): Boolean {
        return callerLabel == "ChatGPT" || callerLabel == "Claude"
    }
}

object AiMultiFileBridge {

    suspend fun stageFilesForAiUpload(
        context: Context,
        folder: ScannedFolder,
        prefixFolderNames: Boolean
    ): List<Uri> = withContext(Dispatchers.IO) {
        val batchRoot = File(context.cacheDir, "ai_batch").apply { mkdirs() }

        // Clean batches older than 30 minutes
        batchRoot.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 1_800_000L) {
                old.deleteRecursively()
            }
        }

        val sessionDir = File(batchRoot, "batch_${System.currentTimeMillis()}").apply { mkdirs() }
        val usedNames = HashSet<String>()
        val buffer = ByteArray(32 * 1024)
        val uris = mutableListOf<Uri>()

        for (file in folder.files) {
            currentCoroutineContext().ensureActive()
            val desiredName = if (prefixFolderNames) {
                AiUploadBridgeUtils.buildFlattenedFileName(file.relativePath, folder.rootName)
            } else {
                sanitizeSingleName(file.name)
            }

            val uniqueName = ensureUniqueFileName(desiredName, usedNames)
            val stagedFile = File(sessionDir, uniqueName)

            val copied = copyUriToFile(context, file, stagedFile, buffer)
            if (copied) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    stagedFile
                )
                uris.add(uri)
            }
        }

        uris
    }

    fun buildShareMultipleToAiIntent(
        uris: List<Uri>,
        folderSummaryTitle: String
    ): Intent {
        val action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        return Intent(action).apply {
            type = "*/*"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            putExtra(Intent.EXTRA_SUBJECT, "ملفات ومجلدات: $folderSummaryTitle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun sanitizeSingleName(name: String): String {
        return name
            .replace('/', '_')
            .replace('\\', '_')
            .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF]"), "_")
            .ifBlank { "file" }
    }

    private fun ensureUniqueFileName(desired: String, usedNames: MutableSet<String>): String {
        if (usedNames.add(desired)) return desired
        val base = desired.substringBeforeLast('.', desired)
        val ext = desired.substringAfterLast('.', "")
        var idx = 2
        while (true) {
            val candidate = if (ext.isNotEmpty() && desired.contains('.')) {
                "${base}_$idx.$ext"
            } else {
                "${desired}_$idx"
            }
            if (usedNames.add(candidate)) return candidate
            idx++
        }
    }

    private fun copyUriToFile(
        context: Context,
        scannedFile: ScannedFile,
        destFile: File,
        buffer: ByteArray
    ): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(scannedFile.uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return false
            true
        }.getOrDefault(false)
    }
}
