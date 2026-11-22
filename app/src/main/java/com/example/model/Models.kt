package com.example.model

import android.net.Uri

enum class DestinationType(val titleAr: String, val subtitleAr: String) {
    GITHUB(
        titleAr = "GitHub",
        subtitleAr = "رفع المجلد كـ Commit واحد"
    ),
    GOOGLE_DRIVE(
        titleAr = "Google Drive",
        subtitleAr = "حفظ المجلدات الفرعية والملفات"
    ),
    CUSTOM_WEB(
        titleAr = "أي موقع / API",
        subtitleAr = "HTTP Multipart أو WebDAV أو PUT"
    ),
    ZIP_EXPORT(
        titleAr = "حزمة ZIP",
        subtitleAr = "ضغط المجلد ومشاركته للمتصفح"
    )
}

enum class WebUploadMode(val labelAr: String, val descriptionAr: String) {
    MULTIPART_PER_FILE(
        labelAr = "Multipart لكل ملف",
        descriptionAr = "يرسل كل ملف مع مساره النسبي (relativePath) عبر POST"
    ),
    MULTIPART_BATCH_TREE(
        labelAr = "Multipart موحد للمجلد",
        descriptionAr = "يرسل جميع الملفات والمجلدات الفرعية وخريطة المسارات JSON في طلب POST واحد"
    ),
    MULTIPART_ZIP(
        labelAr = "Multipart كملف ZIP",
        descriptionAr = "يضغط المجلد في ZIP ويرفعه في طلب POST واحد"
    ),
    WEBDAV_MIRROR(
        labelAr = "WebDAV (مجلدات حقيقية)",
        descriptionAr = "ينشئ المجلدات بـ MKCOL ويرفع الملفات بـ PUT (Nextcloud/NAS/cPanel)"
    ),
    RAW_PUT_PER_FILE(
        labelAr = "PUT مباشر بالمسار",
        descriptionAr = "يرفع كل ملف إلى URL/relativePath عبر طلب HTTP PUT"
    )
}

enum class WebBrowserUploadPolicy(val labelAr: String) {
    ASK_EACH_TIME("سؤال عند كل رفع"),
    INJECT_ALL_FILES("إرسال كل الملفات"),
    AUTO_PACK_ZIP("ضغط ZIP تلقائي")
}

data class ScannedFile(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val lastModified: Long
)

data class ScannedFolder(
    val treeUri: Uri,
    val rootName: String,
    val files: List<ScannedFile>,
    val directories: List<String> = emptyList(),
    val skippedCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    val activeIgnorePatterns: Set<String>
)

data class GitHubConfig(
    val repoInput: String = "",
    val branch: String = "main",
    val targetSubPath: String = "",
    val commitMessage: String = "Upload folder from mobile via FolderPush",
    val token: String = ""
)

data class DriveConfig(
    val parentFolderId: String = "",
    val createRootFolder: Boolean = true,
    val accessToken: String = ""
)

data class CustomWebConfig(
    val endpointUrl: String = "",
    val mode: WebUploadMode = WebUploadMode.MULTIPART_PER_FILE,
    val fileFieldName: String = "file",
    val pathFieldName: String = "relativePath",
    val authHeaderName: String = "Authorization",
    val authHeaderValue: String = "",
    val allowCleartextHttp: Boolean = false
)

data class ZipConfig(
    val archiveName: String = ""
)

enum class LogStatus {
    SUCCESS,
    FAILED,
    SKIPPED
}

data class FileUploadLog(
    val relativePath: String,
    val status: LogStatus,
    val detail: String
)

sealed interface UploadState {
    data object Idle : UploadState
    data object Scanning : UploadState
    data class Uploading(
        val currentIndex: Int,
        val totalFiles: Int,
        val currentFilePath: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val stageLabel: String
    ) : UploadState

    data class Success(
        val summary: String,
        val resultLink: String?,
        val uploadedCount: Int,
        val totalBytes: Long,
        val exportedZipPath: String? = null,
        val logs: List<FileUploadLog> = emptyList()
    ) : UploadState

    data class Error(
        val message: String,
        val uploadedSoFar: Int = 0,
        val logs: List<FileUploadLog> = emptyList()
    ) : UploadState
}

data class ConnectionCheckResult(
    val isSuccess: Boolean,
    val message: String,
    val availableBranches: List<String> = emptyList(),
    val detectedDefaultBranch: String? = null
)

object DefaultIgnoreRules {
    val presets: List<String> = listOf(
        ".git",
        "node_modules",
        ".gradle",
        "build",
        ".DS_Store",
        "Thumbs.db",
        "__pycache__"
    )
}
