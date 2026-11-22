package com.example.data.uploader

import android.content.Context
import android.net.Uri
import com.example.model.ConnectionCheckResult
import com.example.model.CustomWebConfig
import com.example.model.FileUploadLog
import com.example.model.LogStatus
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import com.example.model.UploadState
import com.example.model.WebUploadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class CustomWebUploader(
    private val context: Context,
    private val zipPackager: ZipPackager
) {
    private val client = HttpClientProvider.client

    fun validateEndpointUrl(rawUrl: String, allowCleartextHttp: Boolean): Result<HttpUrl> =
        Companion.validateEndpointUrl(rawUrl, allowCleartextHttp)

    suspend fun testEndpoint(config: CustomWebConfig): ConnectionCheckResult = withContext(Dispatchers.IO) {
        val urlResult = validateEndpointUrl(config.endpointUrl, config.allowCleartextHttp)
        if (urlResult.isFailure) {
            return@withContext ConnectionCheckResult(
                false,
                urlResult.exceptionOrNull()?.message ?: "رابط غير صالح"
            )
        }
        val url = urlResult.getOrThrow()

        val method = if (config.mode == WebUploadMode.WEBDAV_MIRROR) "OPTIONS" else "HEAD"
        val reqBuilder = Request.Builder().url(url).method(method, null)
        applyCustomAuth(reqBuilder, config)

        runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val davHeader = resp.header("DAV")
                val allowHeader = resp.header("Allow")
                val info = buildString {
                    append("استجاب الخادم برمز HTTP ${resp.code}")
                    if (!davHeader.isNullOrBlank()) append(" • WebDAV مدعوم ($davHeader)")
                    if (!allowHeader.isNullOrBlank()) append(" • Allow: $allowHeader")
                }
                ConnectionCheckResult(
                    isSuccess = resp.code in 200..405, // 405 on HEAD/OPTIONS still proves endpoint is reachable
                    message = info
                )
            }
        }.getOrElse { ex ->
            ConnectionCheckResult(false, "تعذر الاتصال بالخادم: ${ex.message ?: "خطأ شبكة"}")
        }
    }

    suspend fun uploadFolder(
        folder: ScannedFolder,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState = withContext(Dispatchers.IO) {
        val urlResult = validateEndpointUrl(config.endpointUrl, config.allowCleartextHttp)
        if (urlResult.isFailure) {
            return@withContext UploadState.Error(urlResult.exceptionOrNull()?.message ?: "رابط غير صالح")
        }
        if (folder.files.isEmpty() && folder.directories.isEmpty()) {
            return@withContext UploadState.Error("المجلد لا يحتوي على ملفات أو مجلدات فرعية للرفع")
        }

        val baseUrl = urlResult.getOrThrow()

        return@withContext when (config.mode) {
            WebUploadMode.MULTIPART_ZIP -> uploadAsMultipartZip(folder, baseUrl, config, onProgress)
            WebUploadMode.MULTIPART_PER_FILE -> uploadMultipartPerFile(folder, baseUrl, config, onProgress)
            WebUploadMode.MULTIPART_BATCH_TREE -> uploadMultipartBatchTree(folder, baseUrl, config, onProgress)
            WebUploadMode.WEBDAV_MIRROR -> uploadWebDavMirror(folder, baseUrl, config, onProgress)
            WebUploadMode.RAW_PUT_PER_FILE -> uploadRawPutPerFile(folder, baseUrl, config, onProgress)
        }
    }

    private suspend fun uploadAsMultipartZip(
        folder: ScannedFolder,
        baseUrl: HttpUrl,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState {
        val zipFile = zipPackager.packFolderToZip(folder, folder.rootName) { idx, total, path, bytes ->
            onProgress(
                UploadState.Uploading(
                    currentIndex = idx,
                    totalFiles = total,
                    currentFilePath = path,
                    bytesTransferred = bytes,
                    totalBytes = folder.totalBytes,
                    stageLabel = "ضغط المجلد في أرشيف ZIP قبل الرفع..."
                )
            )
        }

        onProgress(
            UploadState.Uploading(
                currentIndex = folder.files.size,
                totalFiles = folder.files.size,
                currentFilePath = zipFile.name,
                bytesTransferred = folder.totalBytes,
                totalBytes = folder.totalBytes,
                stageLabel = "إرسال حزمة ZIP إلى الموقع..."
            )
        )

        val field = config.fileFieldName.trim().ifBlank { "file" }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("folderName", folder.rootName)
            .addFormDataPart("fileCount", folder.files.size.toString())
            .addFormDataPart(
                field,
                zipFile.name,
                zipFile.asRequestBody("application/zip".toMediaType())
            )
            .build()

        val reqBuilder = Request.Builder().url(baseUrl).post(body)
        applyCustomAuth(reqBuilder, config)

        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val snippet = resp.body?.string().orEmpty().take(180)
                    return@use UploadState.Error("فشل الرفع (HTTP ${resp.code}): $snippet")
                }
                UploadState.Success(
                    summary = "تم ضغط ورفع ${folder.rootName} (${folder.files.size} ملف) بنجاح • HTTP ${resp.code}",
                    resultLink = baseUrl.toString(),
                    uploadedCount = folder.files.size,
                    totalBytes = folder.totalBytes,
                    exportedZipPath = zipFile.absolutePath
                )
            }
        }.getOrElse { ex ->
            UploadState.Error("فشل إرسال الطلب: ${ex.message ?: "خطأ شبكة"}")
        }
    }

    private suspend fun uploadMultipartPerFile(
        folder: ScannedFolder,
        baseUrl: HttpUrl,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState {
        val fileField = config.fileFieldName.trim().ifBlank { "file" }
        val pathField = config.pathFieldName.trim().ifBlank { "relativePath" }
        val logs = mutableListOf<FileUploadLog>()
        var bytesDone = 0L

        for ((idx, file) in folder.files.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                UploadState.Uploading(
                    currentIndex = idx + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = bytesDone,
                    totalBytes = folder.totalBytes,
                    stageLabel = "رفع عبر HTTP Multipart..."
                )
            )

            val fileBody = UriStreamingBody(context, file)
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(pathField, "${folder.rootName}/${file.relativePath}")
                .addFormDataPart("folderName", folder.rootName)
                .addFormDataPart(fileField, file.name, fileBody)
                .build()

            val reqBuilder = Request.Builder().url(baseUrl).post(multipart)
            applyCustomAuth(reqBuilder, config)

            val result = runCatching {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(120)}")
                    }
                    resp.code
                }
            }

            if (result.isFailure) {
                val err = result.exceptionOrNull()?.message ?: "فشل الطلب"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                return UploadState.Error(
                    "توقف عند ${file.relativePath}: $err",
                    uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                    logs = logs
                )
            }

            bytesDone += file.sizeBytes
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "HTTP ${result.getOrNull()}"))
        }

        return UploadState.Success(
            summary = "تم رفع ${folder.files.size} ملف إلى الموقع عبر Multipart POST",
            resultLink = baseUrl.toString(),
            uploadedCount = folder.files.size,
            totalBytes = bytesDone,
            logs = logs
        )
    }

    private suspend fun uploadMultipartBatchTree(
        folder: ScannedFolder,
        baseUrl: HttpUrl,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState {
        val fileField = config.fileFieldName.trim().ifBlank { "files" }
        val pathField = config.pathFieldName.trim().ifBlank { "relativePaths" }

        onProgress(
            UploadState.Uploading(
                currentIndex = 0,
                totalFiles = folder.files.size,
                currentFilePath = folder.rootName,
                bytesTransferred = 0L,
                totalBytes = folder.totalBytes,
                stageLabel = "تجهيز طلب Multipart الموحد (${folder.files.size} ملف و ${folder.directoryCount} مجلد)..."
            )
        )

        val dirArray = JSONArray().apply {
            folder.directories.forEach { put("${folder.rootName}/$it") }
        }
        val pathsArray = JSONArray().apply {
            folder.files.forEach { put("${folder.rootName}/${it.relativePath}") }
        }
        val manifestJson = JSONObject().apply {
            put("rootFolder", folder.rootName)
            put("fileCount", folder.files.size)
            put("directoryCount", folder.directoryCount)
            put("directories", dirArray)
            put("files", pathsArray)
        }.toString()

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("folderName", folder.rootName)
            .addFormDataPart("treeManifest", manifestJson)
            .addFormDataPart("directories", dirArray.toString())
            .addFormDataPart(pathField, pathsArray.toString())

        val logs = mutableListOf<FileUploadLog>()
        folder.files.forEachIndexed { idx, file ->
            currentCoroutineContext().ensureActive()
            onProgress(
                UploadState.Uploading(
                    currentIndex = idx + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = 0L,
                    totalBytes = folder.totalBytes,
                    stageLabel = "إرفاق الملفات والشجرة في الطلب الموحد..."
                )
            )
            multipartBuilder.addFormDataPart(
                fileField,
                "${folder.rootName}/${file.relativePath}",
                UriStreamingBody(context, file)
            )
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "مرفق في الحزمة"))
        }

        onProgress(
            UploadState.Uploading(
                currentIndex = folder.files.size,
                totalFiles = folder.files.size,
                currentFilePath = baseUrl.toString(),
                bytesTransferred = folder.totalBytes,
                totalBytes = folder.totalBytes,
                stageLabel = "جاري الإرسال إلى الخادم..."
            )
        )

        val reqBuilder = Request.Builder().url(baseUrl).post(multipartBuilder.build())
        applyCustomAuth(reqBuilder, config)

        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val snippet = resp.body?.string().orEmpty().take(180)
                    return@use UploadState.Error("فشل الرفع الموحد (HTTP ${resp.code}): $snippet")
                }
                UploadState.Success(
                    summary = "تم رفع المجلد كاملاً (${folder.files.size} ملف و ${folder.directoryCount} مجلد فرعي) في طلب واحد • HTTP ${resp.code}",
                    resultLink = baseUrl.toString(),
                    uploadedCount = folder.files.size,
                    totalBytes = folder.totalBytes,
                    logs = logs
                )
            }
        }.getOrElse { ex ->
            UploadState.Error("فشل إرسال الطلب الموحد: ${ex.message ?: "خطأ شبكة"}")
        }
    }

    private suspend fun uploadWebDavMirror(
        folder: ScannedFolder,
        baseUrl: HttpUrl,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState {
        val createdDirs = HashSet<String>()
        val logs = mutableListOf<FileUploadLog>()
        var bytesDone = 0L

        // Create root folder and all nested subdirectories (including empty ones) on WebDAV
        val rootRes = ensureWebDavDir(baseUrl, folder.rootName, config, createdDirs)
        if (rootRes.isFailure) {
            return UploadState.Error(rootRes.exceptionOrNull()?.message ?: "فشل إنشاء المجلد الجذري على WebDAV")
        }
        for (dirRel in folder.directories) {
            currentCoroutineContext().ensureActive()
            val dirRes = ensureWebDavDir(baseUrl, "${folder.rootName}/$dirRel", config, createdDirs)
            if (dirRes.isFailure) {
                return UploadState.Error(dirRes.exceptionOrNull()?.message ?: "فشل إنشاء المجلد الفرعي $dirRel")
            }
        }

        for ((idx, file) in folder.files.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                UploadState.Uploading(
                    currentIndex = idx + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = bytesDone,
                    totalBytes = folder.totalBytes,
                    stageLabel = "مزامنة المجلدات والملفات عبر WebDAV..."
                )
            )

            val fullRel = "${folder.rootName}/${file.relativePath}"
            val parentRel = fullRel.substringBeforeLast('/', "")
            if (parentRel.isNotEmpty()) {
                val dirResult = ensureWebDavDir(baseUrl, parentRel, config, createdDirs)
                if (dirResult.isFailure) {
                    val err = dirResult.exceptionOrNull()?.message ?: "فشل MKCOL"
                    logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                    return UploadState.Error(err, logs.count { it.status == LogStatus.SUCCESS }, logs)
                }
            }

            val targetUrl = appendPathSegments(baseUrl, fullRel)
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .put(UriStreamingBody(context, file))
            applyCustomAuth(reqBuilder, config)

            val putRes = runCatching {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}")
                    }
                    resp.code
                }
            }

            if (putRes.isFailure) {
                val err = putRes.exceptionOrNull()?.message ?: "فشل PUT"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                return UploadState.Error(
                    "فشل رفع ${file.relativePath} ($err)",
                    logs.count { it.status == LogStatus.SUCCESS },
                    logs
                )
            }

            bytesDone += file.sizeBytes
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "WebDAV HTTP ${putRes.getOrNull()}"))
        }

        return UploadState.Success(
            summary = "تم رفع ${folder.files.size} ملف إلى WebDAV مع كامل بنية المجلدات",
            resultLink = appendPathSegments(baseUrl, folder.rootName).toString(),
            uploadedCount = folder.files.size,
            totalBytes = bytesDone,
            logs = logs
        )
    }

    private suspend fun uploadRawPutPerFile(
        folder: ScannedFolder,
        baseUrl: HttpUrl,
        config: CustomWebConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState {
        val logs = mutableListOf<FileUploadLog>()
        var bytesDone = 0L

        for ((idx, file) in folder.files.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                UploadState.Uploading(
                    currentIndex = idx + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = bytesDone,
                    totalBytes = folder.totalBytes,
                    stageLabel = "رفع عبر HTTP PUT..."
                )
            )

            val targetUrl = appendPathSegments(baseUrl, "${folder.rootName}/${file.relativePath}")
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .put(UriStreamingBody(context, file))
            applyCustomAuth(reqBuilder, config)

            val res = runCatching {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.code
                }
            }

            if (res.isFailure) {
                val err = res.exceptionOrNull()?.message ?: "فشل PUT"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, err))
                return UploadState.Error(
                    "توقف عند ${file.relativePath}: $err",
                    logs.count { it.status == LogStatus.SUCCESS },
                    logs
                )
            }

            bytesDone += file.sizeBytes
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "HTTP ${res.getOrNull()}"))
        }

        return UploadState.Success(
            summary = "تم رفع ${folder.files.size} ملف عبر HTTP PUT",
            resultLink = baseUrl.toString(),
            uploadedCount = folder.files.size,
            totalBytes = bytesDone,
            logs = logs
        )
    }

    private fun ensureWebDavDir(
        baseUrl: HttpUrl,
        relDir: String,
        config: CustomWebConfig,
        cache: MutableSet<String>
    ): Result<Unit> {
        val parts = relDir.split("/").filter { it.isNotBlank() }
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            if (cache.contains(acc)) continue
            val dirUrl = appendPathSegments(baseUrl, acc)
            val reqBuilder = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", ByteArray(0).toRequestBody(null))
            applyCustomAuth(reqBuilder, config)

            val res = runCatching {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    // 201 Created, 405 Method Not Allowed (already exists), 301/302/409
                    if (resp.isSuccessful || resp.code == 405 || resp.code == 301) {
                        cache.add(acc)
                    } else {
                        error("فشل إنشاء مجلد WebDAV ($acc): HTTP ${resp.code}")
                    }
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        return Result.success(Unit)
    }

    private fun appendPathSegments(baseUrl: HttpUrl, relPath: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        relPath.split("/")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                builder.addEncodedPathSegment(
                    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                )
            }
        return builder.build()
    }

    private fun applyCustomAuth(builder: Request.Builder, config: CustomWebConfig) {
        val headerName = config.authHeaderName.trim()
        val headerVal = config.authHeaderValue.trim()
        if (headerName.isNotBlank() && headerVal.isNotBlank()) {
            builder.header(headerName, headerVal)
        }
    }

    private class UriStreamingBody(
        private val context: Context,
        private val file: ScannedFile
    ) : RequestBody() {
        override fun contentType(): MediaType {
            return file.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        }

        override fun contentLength(): Long {
            return if (file.sizeBytes > 0) file.sizeBytes else -1L
        }

        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(file.uri)?.source()?.use { src ->
                sink.writeAll(src)
            } ?: error("تعذر قراءة الملف ${file.name}")
        }
    }

    companion object {
        fun validateEndpointUrl(rawUrl: String, allowCleartextHttp: Boolean): Result<HttpUrl> {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("يرجى إدخال رابط الموقع أو الخادم"))
            }
            val parsed = trimmed.toHttpUrlOrNull()
                ?: return Result.failure(IllegalArgumentException("الرابط غير صالح. يجب أن يبدأ بـ https:// أو http://"))
            if (parsed.scheme == "http" && !allowCleartextHttp) {
                return Result.failure(
                    IllegalArgumentException("الرابط يستخدم http غير مشفر. استخدم https:// أو فعّل خيار السماح بـ HTTP المحلي.")
                )
            }
            return Result.success(parsed)
        }
    }
}
