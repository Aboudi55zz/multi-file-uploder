package com.example.data.uploader

import android.content.Context
import android.net.Uri
import com.example.model.ConnectionCheckResult
import com.example.model.DriveConfig
import com.example.model.FileUploadLog
import com.example.model.LogStatus
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import com.example.model.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject

class GoogleDriveUploader(private val context: Context) {

    private val client = HttpClientProvider.client
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun verifyToken(accessToken: String): ConnectionCheckResult = withContext(Dispatchers.IO) {
        val token = accessToken.trim()
        if (token.isBlank()) {
            return@withContext ConnectionCheckResult(false, "يرجى إدخال رمز الوصول OAuth لـ Google Drive.")
        }

        val req = Request.Builder()
            .url("$DRIVE_API/about?fields=user(displayName,emailAddress)")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                return@withContext ConnectionCheckResult(
                    false,
                    "فشل التحقق (${resp.code}): ${msg ?: "تأكد من صلاحية OAuth Token ونطاق drive.file أو drive"}"
                )
            }
            val user = JSONObject(body).optJSONObject("user")
            val name = user?.optString("displayName").orEmpty()
            val email = user?.optString("emailAddress").orEmpty()
            ConnectionCheckResult(
                true,
                "متصل بحساب: ${listOf(name, email).filter { it.isNotBlank() }.joinToString(" • ")}"
            )
        }
    }

    suspend fun uploadFolder(
        folder: ScannedFolder,
        config: DriveConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState = withContext(Dispatchers.IO) {
        val token = config.accessToken.trim()
        if (token.isBlank()) {
            return@withContext UploadState.Error("رمز Google Drive OAuth Token مطلوب")
        }
        if (folder.files.isEmpty() && folder.directories.isEmpty()) {
            return@withContext UploadState.Error("المجلد المحدد لا يحتوي على ملفات أو مجلدات فرعية للرفع")
        }

        val folderIdCache = mutableMapOf<String, String>()
        val baseParentId = config.parentFolderId.trim().ifBlank { "root" }
        val logs = mutableListOf<FileUploadLog>()
        var bytesDone = 0L

        val rootDriveFolderId = if (config.createRootFolder) {
            onProgress(
                UploadState.Uploading(
                    currentIndex = 0,
                    totalFiles = folder.files.size,
                    currentFilePath = folder.rootName,
                    bytesTransferred = 0L,
                    totalBytes = folder.totalBytes,
                    stageLabel = "إنشاء المجلد الرئيسي في Google Drive..."
                )
            )
            val created = createDriveFolder(folder.rootName, baseParentId, token)
            if (created.isFailure) {
                return@withContext UploadState.Error(
                    created.exceptionOrNull()?.message ?: "فشل إنشاء المجلد في Google Drive"
                )
            }
            created.getOrThrow()
        } else {
            baseParentId
        }
        folderIdCache[""] = rootDriveFolderId

        // Ensure all nested subdirectories (including empty ones) exist in Drive
        for (dirRel in folder.directories) {
            currentCoroutineContext().ensureActive()
            val dirResult = ensureDriveDirectoryPath(dirRel, folderIdCache, token)
            if (dirResult.isFailure) {
                return@withContext UploadState.Error(
                    dirResult.exceptionOrNull()?.message ?: "فشل إنشاء المجلد الفرعي $dirRel"
                )
            }
        }

        for ((index, file) in folder.files.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                UploadState.Uploading(
                    currentIndex = index + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = bytesDone,
                    totalBytes = folder.totalBytes,
                    stageLabel = "رفع الملفات مع الحفاظ على المجلدات..."
                )
            )

            val parentRelDir = file.relativePath.substringBeforeLast('/', "")
            val parentIdResult = ensureDriveDirectoryPath(parentRelDir, folderIdCache, token)
            if (parentIdResult.isFailure) {
                val err = parentIdResult.exceptionOrNull()?.message ?: "فشل إنشاء المجلد الفرعي"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                return@withContext UploadState.Error(
                    err,
                    uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                    logs = logs
                )
            }

            val uploadRes = uploadSingleFileMultipart(
                file = file,
                parentId = parentIdResult.getOrThrow(),
                token = token
            )
            if (uploadRes.isFailure) {
                val err = uploadRes.exceptionOrNull()?.message ?: "فشل رفع الملف"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                return@withContext UploadState.Error(
                    "فشل رفع ${file.relativePath}: $err",
                    uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                    logs = logs
                )
            }

            bytesDone += file.sizeBytes
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "Drive ID: ${uploadRes.getOrThrow()}"))
        }

        val folderLink = if (rootDriveFolderId == "root") {
            "https://drive.google.com/drive/my-drive"
        } else {
            "https://drive.google.com/drive/folders/$rootDriveFolderId"
        }

        UploadState.Success(
            summary = "تم رفع ${folder.files.size} ملف إلى Google Drive بنفس شجرة المجلدات",
            resultLink = folderLink,
            uploadedCount = folder.files.size,
            totalBytes = bytesDone,
            logs = logs
        )
    }

    private fun ensureDriveDirectoryPath(
        relDir: String,
        cache: MutableMap<String, String>,
        token: String
    ): Result<String> {
        cache[relDir]?.let { return Result.success(it) }
        val segments = relDir.split("/").filter { it.isNotBlank() }
        var currentPath = ""
        var currentParentId = cache[""] ?: "root"

        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val cached = cache[currentPath]
            if (cached != null) {
                currentParentId = cached
            } else {
                val created = createDriveFolder(segment, currentParentId, token)
                if (created.isFailure) return created
                val newId = created.getOrThrow()
                cache[currentPath] = newId
                currentParentId = newId
            }
        }
        return Result.success(currentParentId)
    }

    private fun createDriveFolder(name: String, parentId: String, token: String): Result<String> {
        return runCatching {
            val metadata = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                if (parentId != "root") {
                    put("parents", JSONArray().apply { put(parentId) })
                }
            }.toString()

            val req = Request.Builder()
                .url("$DRIVE_API/files")
                .header("Authorization", "Bearer $token")
                .post(metadata.toRequestBody(jsonMedia))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    error("HTTP ${resp.code}: ${msg ?: "فشل إنشاء المجلد"}")
                }
                JSONObject(body).getString("id")
            }
        }
    }

    private fun uploadSingleFileMultipart(
        file: ScannedFile,
        parentId: String,
        token: String
    ): Result<String> {
        return runCatching {
            val metadata = JSONObject().apply {
                put("name", file.name)
                if (parentId != "root") {
                    put("parents", JSONArray().apply { put(parentId) })
                }
            }.toString()

            val fileBody = UriStreamingRequestBody(
                context = context,
                uri = file.uri,
                contentType = file.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType(),
                contentLength = file.sizeBytes
            )

            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody(jsonMedia))
                .addPart(fileBody)
                .build()

            val req = Request.Builder()
                .url(DRIVE_UPLOAD_API)
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    error("HTTP ${resp.code}: ${msg ?: "فشل الرفع"}")
                }
                JSONObject(body).getString("id")
            }
        }
    }

    private class UriStreamingRequestBody(
        private val context: Context,
        private val uri: Uri,
        private val contentType: MediaType,
        private val contentLength: Long
    ) : RequestBody() {
        override fun contentType(): MediaType = contentType
        override fun contentLength(): Long = if (contentLength > 0) contentLength else -1L
        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(uri)?.source()?.use { src ->
                sink.writeAll(src)
            } ?: error("تعذر فتح الملف للقراءة")
        }
    }

    companion object {
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    }
}
