package com.example.data.uploader

import android.content.Context
import android.util.Base64
import com.example.model.ConnectionCheckResult
import com.example.model.FileUploadLog
import com.example.model.GitHubConfig
import com.example.model.LogStatus
import com.example.model.ScannedFile
import com.example.model.ScannedFolder
import com.example.model.UploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GitHubUploader(private val context: Context) {

    private val client = HttpClientProvider.client
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun parseOwnerRepo(input: String): Pair<String, String>? = Companion.parseOwnerRepo(input)

    suspend fun verifyConnection(repoInput: String, token: String): ConnectionCheckResult =
        withContext(Dispatchers.IO) {
            val parsed = parseOwnerRepo(repoInput)
                ?: return@withContext ConnectionCheckResult(
                    false,
                    "صيغة المستودع غير صحيحة. اكتب owner/repo أو رابط GitHub."
                )
            if (token.isBlank()) {
                return@withContext ConnectionCheckResult(false, "يرجى إدخال رمز GitHub (Personal Access Token).")
            }

            val (owner, repo) = parsed
            val repoReq = Request.Builder()
                .url("$GITHUB_API/repos/$owner/$repo")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val repoJson = client.newCall(repoReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    return@withContext ConnectionCheckResult(
                        false,
                        "فشل التحقق (${resp.code}): ${msg ?: "تأكد من اسم المستودع وصلاحيات التوكن"}"
                    )
                }
                JSONObject(body)
            }

            val defaultBranch = repoJson.optString("default_branch", "main")
            val isPrivate = repoJson.optBoolean("private", false)

            val branches = mutableListOf<String>()
            val branchReq = Request.Builder()
                .url("$GITHUB_API/repos/$owner/$repo/branches?per_page=30")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            runCatching {
                client.newCall(branchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val arr = JSONArray(resp.body?.string().orEmpty())
                        for (i in 0 until arr.length()) {
                            branches.add(arr.getJSONObject(i).getString("name"))
                        }
                    }
                }
            }

            val visibility = if (isPrivate) "خاص (Private)" else "عام (Public)"
            val branchSummary = if (branches.isEmpty()) "مستودع فارغ (سيتم تهيئته عند الرفع)"
            else "${branches.size} فروع متاحة"

            ConnectionCheckResult(
                isSuccess = true,
                message = "متصل بالمستودع $owner/$repo • $visibility • $branchSummary",
                availableBranches = branches,
                detectedDefaultBranch = defaultBranch
            )
        }

    suspend fun uploadFolder(
        folder: ScannedFolder,
        config: GitHubConfig,
        onProgress: (UploadState.Uploading) -> Unit
    ): UploadState = withContext(Dispatchers.IO) {
        val parsed = parseOwnerRepo(config.repoInput)
            ?: return@withContext UploadState.Error("صيغة المستودع غير صحيحة. استخدم owner/repo")
        val token = config.token.trim()
        if (token.isBlank()) {
            return@withContext UploadState.Error("رمز GitHub Token مطلوب للرفع")
        }
        if (folder.files.isEmpty()) {
            return@withContext UploadState.Error("المجلد المحدد لا يحتوي على أي ملفات قابلة للرفع")
        }

        val oversized = folder.files.firstOrNull { it.sizeBytes > MAX_BLOB_BYTES }
        if (oversized != null) {
            return@withContext UploadState.Error(
                "الملف ${oversized.relativePath} أكبر من 25 MB (حد GitHub Data API عبر الهاتف)."
            )
        }

        val (owner, repo) = parsed
        val branch = config.branch.trim().ifBlank { "main" }
        val baseSubPath = config.targetSubPath.trim().trim('/')
        val commitMsg = config.commitMessage.trim().ifBlank { "Upload ${folder.rootName} via FolderPush" }

        val logs = mutableListOf<FileUploadLog>()
        var bytesDone = 0L

        fun targetRepoPath(file: ScannedFile): String {
            return if (baseSubPath.isEmpty()) file.relativePath else "$baseSubPath/${file.relativePath}"
        }

        // Check if target branch ref already exists
        var headCommitSha = getBranchHeadSha(owner, repo, branch, token)
        var startIndex = 0

        if (headCommitSha == null) {
            // Empty repo or new branch: initialize by uploading first file via Contents API
            val firstFile = folder.files.first()
            val firstPath = targetRepoPath(firstFile)
            onProgress(
                UploadState.Uploading(
                    currentIndex = 1,
                    totalFiles = folder.files.size,
                    currentFilePath = firstFile.relativePath,
                    bytesTransferred = 0L,
                    totalBytes = folder.totalBytes,
                    stageLabel = "تهيئة الفرع $branch بالملف الأول..."
                )
            )
            val initResult = putSingleFileContent(
                owner = owner,
                repo = repo,
                branch = branch,
                repoPath = firstPath,
                file = firstFile,
                commitMessage = if (folder.files.size == 1) commitMsg else "Initialize $branch with ${firstFile.name}",
                token = token
            )
            if (initResult.isFailure) {
                val err = initResult.exceptionOrNull()?.message ?: "فشل تهيئة المستودع"
                logs.add(FileUploadLog(firstFile.relativePath, LogStatus.FAILED, err))
                return@withContext UploadState.Error(err, uploadedSoFar = 0, logs = logs)
            }
            bytesDone += firstFile.sizeBytes
            logs.add(FileUploadLog(firstFile.relativePath, LogStatus.SUCCESS, "تم الرفع"))

            if (folder.files.size == 1) {
                val sha = initResult.getOrNull()
                val url = if (!sha.isNullOrBlank()) "https://github.com/$owner/$repo/commit/$sha"
                else "https://github.com/$owner/$repo/tree/$branch"
                return@withContext UploadState.Success(
                    summary = "تم رفع ${folder.files.size} ملف إلى $owner/$repo ($branch)",
                    resultLink = url,
                    uploadedCount = 1,
                    totalBytes = bytesDone,
                    logs = logs
                )
            }

            headCommitSha = getBranchHeadSha(owner, repo, branch, token)
                ?: return@withContext UploadState.Error(
                    "تعذر قراءة مرجع الفرع بعد التهيئة",
                    uploadedSoFar = 1,
                    logs = logs
                )
            startIndex = 1
        }

        val baseTreeSha = getCommitTreeSha(owner, repo, headCommitSha, token)
            ?: return@withContext UploadState.Error("تعذر قراءة الشجرة الأساسية للمستودع", logs = logs)

        val treeItems = JSONArray()

        for (idx in startIndex until folder.files.size) {
            currentCoroutineContext().ensureActive()
            val file = folder.files[idx]
            val repoPath = targetRepoPath(file)

            onProgress(
                UploadState.Uploading(
                    currentIndex = idx + 1,
                    totalFiles = folder.files.size,
                    currentFilePath = file.relativePath,
                    bytesTransferred = bytesDone,
                    totalBytes = folder.totalBytes,
                    stageLabel = "جاري رفع الملفات إلى GitHub..."
                )
            )

            val blobShaResult = createBlob(owner, repo, file, token)
            if (blobShaResult.isFailure) {
                val errMsg = blobShaResult.exceptionOrNull()?.message ?: "خطأ غير معروف"
                logs.add(FileUploadLog(file.relativePath, LogStatus.FAILED, errMsg))
                return@withContext UploadState.Error(
                    message = "فشل رفع ${file.relativePath}: $errMsg",
                    uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                    logs = logs
                )
            }

            val blobSha = blobShaResult.getOrThrow()
            treeItems.put(
                JSONObject().apply {
                    put("path", repoPath)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                }
            )
            bytesDone += file.sizeBytes
            logs.add(FileUploadLog(file.relativePath, LogStatus.SUCCESS, "Blob ${blobSha.take(7)}"))
        }

        onProgress(
            UploadState.Uploading(
                currentIndex = folder.files.size,
                totalFiles = folder.files.size,
                currentFilePath = "إنشاء Commit موحد...",
                bytesTransferred = folder.totalBytes,
                totalBytes = folder.totalBytes,
                stageLabel = "بناء الشجرة وإتمام Commit..."
            )
        )

        val newTreeSha = createTree(owner, repo, baseTreeSha, treeItems, token)
            ?: return@withContext UploadState.Error(
                "فشل إنشاء Git Tree على GitHub",
                uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                logs = logs
            )

        val newCommitSha = createCommit(owner, repo, commitMsg, newTreeSha, headCommitSha, token)
            ?: return@withContext UploadState.Error(
                "فشل إنشاء Git Commit على GitHub",
                uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                logs = logs
            )

        val refUpdated = updateBranchRef(owner, repo, branch, newCommitSha, token)
        if (!refUpdated) {
            return@withContext UploadState.Error(
                "تم إنشاء Commit لكن فشل تحديث الفرع $branch (تأكد من صلاحية Write)",
                uploadedSoFar = logs.count { it.status == LogStatus.SUCCESS },
                logs = logs
            )
        }

        UploadState.Success(
            summary = "تم رفع المجلد (${folder.files.size} ملف) في Commit واحد على الفرع $branch",
            resultLink = "https://github.com/$owner/$repo/commit/$newCommitSha",
            uploadedCount = folder.files.size,
            totalBytes = bytesDone,
            logs = logs
        )
    }

    private fun getBranchHeadSha(owner: String, repo: String, branch: String, token: String): String? {
        val encodedBranch = encodePath(branch)
        val req = Request.Builder()
            .url("$GITHUB_API/repos/$owner/$repo/git/ref/heads/$encodedBranch")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val json = JSONObject(resp.body?.string().orEmpty())
            json.optJSONObject("object")?.optString("sha")
        }
    }

    private fun getCommitTreeSha(owner: String, repo: String, commitSha: String, token: String): String? {
        val req = Request.Builder()
            .url("$GITHUB_API/repos/$owner/$repo/git/commits/$commitSha")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val json = JSONObject(resp.body?.string().orEmpty())
            json.optJSONObject("tree")?.optString("sha")
        }
    }

    private fun createBlob(owner: String, repo: String, file: ScannedFile, token: String): Result<String> {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة الملف من الجهاز")
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("content", b64)
                put("encoding", "base64")
            }.toString()

            val req = Request.Builder()
                .url("$GITHUB_API/repos/$owner/$repo/git/blobs")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .post(payload.toRequestBody(jsonMedia))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    error("HTTP ${resp.code}: ${msg ?: "فشل إنشاء Blob"}")
                }
                JSONObject(body).getString("sha")
            }
        }
    }

    private fun putSingleFileContent(
        owner: String,
        repo: String,
        branch: String,
        repoPath: String,
        file: ScannedFile,
        commitMessage: String,
        token: String
    ): Result<String?> {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                ?: error("تعذر قراءة الملف من الجهاز")
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            val encodedPath = encodePath(repoPath)
            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", b64)
                put("branch", branch)
            }.toString()

            val req = Request.Builder()
                .url("$GITHUB_API/repos/$owner/$repo/contents/$encodedPath")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toRequestBody(jsonMedia))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    error("HTTP ${resp.code}: ${msg ?: "فشل رفع الملف"}")
                }
                JSONObject(body).optJSONObject("commit")?.optString("sha")
            }
        }
    }

    private fun createTree(
        owner: String,
        repo: String,
        baseTreeSha: String,
        treeItems: JSONArray,
        token: String
    ): String? {
        val payload = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeItems)
        }.toString()

        val req = Request.Builder()
            .url("$GITHUB_API/repos/$owner/$repo/git/trees")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(payload.toRequestBody(jsonMedia))
            .build()

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            JSONObject(resp.body?.string().orEmpty()).optString("sha")
        }
    }

    private fun createCommit(
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parentSha: String,
        token: String
    ): String? {
        val payload = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", JSONArray().apply { put(parentSha) })
        }.toString()

        val req = Request.Builder()
            .url("$GITHUB_API/repos/$owner/$repo/git/commits")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(payload.toRequestBody(jsonMedia))
            .build()

        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            JSONObject(resp.body?.string().orEmpty()).optString("sha")
        }
    }

    private fun updateBranchRef(
        owner: String,
        repo: String,
        branch: String,
        commitSha: String,
        token: String
    ): Boolean {
        val payload = JSONObject().apply {
            put("sha", commitSha)
            put("force", false)
        }.toString()

        val req = Request.Builder()
            .url("$GITHUB_API/repos/$owner/$repo/git/refs/heads/${encodePath(branch)}")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .patch(payload.toRequestBody(jsonMedia))
            .build()

        return client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun encodePath(path: String): String {
        return path.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    companion object {
        private const val GITHUB_API = "https://api.github.com"
        private const val MAX_BLOB_BYTES = 25L * 1024L * 1024L

        fun parseOwnerRepo(input: String): Pair<String, String>? {
            val trimmed = input.trim().removeSuffix("/").removeSuffix(".git")
            val cleaned = when {
                trimmed.startsWith("https://github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/").trim('/')
                trimmed.startsWith("http://github.com/", ignoreCase = true) ->
                    trimmed.substringAfter("github.com/").trim('/')
                trimmed.startsWith("git@github.com:", ignoreCase = true) ->
                    trimmed.substringAfter("git@github.com:").trim('/')
                else -> trimmed.trim('/')
            }
            val parts = cleaned.split("/").filter { it.isNotBlank() }
            if (parts.size < 2) return null
            return parts[0] to parts[1]
        }
    }
}
