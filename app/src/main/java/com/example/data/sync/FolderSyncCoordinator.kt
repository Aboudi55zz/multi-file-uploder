package com.example.data.sync

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.FolderPushRepository
import com.example.data.local.SecureTokenStore
import com.example.data.local.UploadHistoryEntity
import com.example.data.scanner.SafFolderScanner
import com.example.data.uploader.CustomWebUploader
import com.example.data.uploader.GitHubUploader
import com.example.data.uploader.GoogleDriveUploader
import com.example.data.uploader.ZipPackager
import com.example.model.CustomWebConfig
import com.example.model.DestinationType
import com.example.model.DriveConfig
import com.example.model.GitHubConfig
import com.example.model.ScannedFolder
import com.example.model.UploadState
import com.example.model.ZipConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class FolderSyncRequest(
    val folder: ScannedFolder,
    val destination: DestinationType,
    val gitHubConfig: GitHubConfig,
    val driveConfig: DriveConfig,
    val customWebConfig: CustomWebConfig,
    val zipConfig: ZipConfig
)

object FolderSyncProgressFormatter {

    fun computeProgressPercent(
        currentIndex: Int,
        totalFiles: Int,
        bytesTransferred: Long,
        totalBytes: Long
    ): Int {
        if (totalFiles <= 0) return 0
        val fileFraction = (currentIndex.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
        val combinedFraction = if (totalBytes > 0L) {
            val byteFraction = (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            ((fileFraction * 0.65f) + (byteFraction * 0.35f)).coerceIn(0f, 1f)
        } else {
            fileFraction
        }
        return (combinedFraction * 100f).toInt().coerceIn(0, 100)
    }

    fun buildOngoingContentText(state: UploadState.Uploading): String {
        val transferred = SafFolderScanner.formatBytes(state.bytesTransferred)
        val total = SafFolderScanner.formatBytes(state.totalBytes)
        return "${state.currentIndex}/${state.totalFiles} ملف • $transferred / $total"
    }

    fun shouldNotifyUpdate(
        lastNotifiedAtMs: Long,
        nowMs: Long,
        lastStage: String,
        newStage: String,
        currentIndex: Int,
        totalFiles: Int,
        minIntervalMs: Long = 350L
    ): Boolean {
        if (lastStage != newStage) return true
        if (currentIndex <= 1 || (totalFiles > 0 && currentIndex >= totalFiles)) return true
        return (nowMs - lastNotifiedAtMs) >= minIntervalMs
    }
}

object FolderSyncCoordinator {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _lastExportedZip = MutableStateFlow<File?>(null)
    val lastExportedZip: StateFlow<File?> = _lastExportedZip.asStateFlow()

    private val _isSyncActive = MutableStateFlow(false)
    val isSyncActive: StateFlow<Boolean> = _isSyncActive.asStateFlow()

    private val _activeFolderName = MutableStateFlow<String?>(null)
    val activeFolderName: StateFlow<String?> = _activeFolderName.asStateFlow()

    private val _activeDestination = MutableStateFlow<DestinationType?>(null)
    val activeDestination: StateFlow<DestinationType?> = _activeDestination.asStateFlow()

    private val pendingRequest = AtomicReference<FolderSyncRequest?>(null)

    @Volatile
    private var activeExecutionJob: Job? = null

    @Volatile
    private var cancelReasonOverride: String? = null

    fun setUiState(state: UploadState) {
        if (!_isSyncActive.value) {
            _uploadState.value = state
        }
    }

    fun resetIfNotActive() {
        if (!_isSyncActive.value && _uploadState.value !is UploadState.Uploading) {
            _uploadState.value = UploadState.Idle
        }
    }

    fun setLastExportedZip(file: File?) {
        _lastExportedZip.value = file
    }

    fun enqueueSync(request: FolderSyncRequest) {
        activeExecutionJob?.cancel()
        cancelReasonOverride = null
        pendingRequest.set(request)
        _activeFolderName.value = request.folder.rootName
        _activeDestination.value = request.destination
        _isSyncActive.value = true
        _uploadState.value = UploadState.Uploading(
            currentIndex = 0,
            totalFiles = request.folder.files.size,
            currentFilePath = request.folder.rootName,
            bytesTransferred = 0L,
            totalBytes = request.folder.totalBytes,
            stageLabel = "بدء مزامنة ${request.destination.titleAr} في الخلفية..."
        )
    }

    fun hasPendingOrActiveRequest(): Boolean = pendingRequest.get() != null || _isSyncActive.value

    fun cancelActiveSync(reason: String = "تم إلغاء العملية بواسطة المستخدم") {
        cancelReasonOverride = reason
        pendingRequest.set(null)
        activeExecutionJob?.cancel()
        _isSyncActive.value = false
        if (_uploadState.value is UploadState.Uploading) {
            _uploadState.value = UploadState.Error(reason)
        }
    }

    suspend fun executePendingSync(context: Context): UploadState = withContext(Dispatchers.IO) {
        val request = pendingRequest.getAndSet(null)
            ?: return@withContext _uploadState.value

        val appContext = context.applicationContext
        activeExecutionJob = currentCoroutineContext()[Job]
        _isSyncActive.value = true
        _activeFolderName.value = request.folder.rootName
        _activeDestination.value = request.destination

        val zipPackager = ZipPackager(appContext)
        val gitHubUploader = GitHubUploader(appContext)
        val driveUploader = GoogleDriveUploader(appContext)
        val customWebUploader = CustomWebUploader(appContext, zipPackager)
        val db = AppDatabase.getInstance(appContext)
        val repository = FolderPushRepository(
            historyDao = db.uploadHistoryDao(),
            targetDao = db.savedTargetDao(),
            tokenStore = SecureTokenStore(appContext)
        )

        var finalState: UploadState = UploadState.Idle
        try {
            finalState = when (request.destination) {
                DestinationType.GITHUB -> {
                    gitHubUploader.uploadFolder(request.folder, request.gitHubConfig) { progress ->
                        _uploadState.value = progress
                    }
                }
                DestinationType.GOOGLE_DRIVE -> {
                    driveUploader.uploadFolder(request.folder, request.driveConfig) { progress ->
                        _uploadState.value = progress
                    }
                }
                DestinationType.CUSTOM_WEB -> {
                    customWebUploader.uploadFolder(request.folder, request.customWebConfig) { progress ->
                        _uploadState.value = progress
                    }
                }
                DestinationType.ZIP_EXPORT -> {
                    val zipFile = zipPackager.packFolderToZip(
                        folder = request.folder,
                        archiveName = request.zipConfig.archiveName.ifBlank { request.folder.rootName }
                    ) { idx, total, path, bytesDone ->
                        _uploadState.value = UploadState.Uploading(
                            currentIndex = idx,
                            totalFiles = total,
                            currentFilePath = path,
                            bytesTransferred = bytesDone,
                            totalBytes = request.folder.totalBytes,
                            stageLabel = "ضغط المجلد في ملف ZIP..."
                        )
                    }
                    _lastExportedZip.value = zipFile
                    UploadState.Success(
                        summary = "تم ضغط ${request.folder.files.size} ملف في ${zipFile.name} (${SafFolderScanner.formatBytes(zipFile.length())})",
                        resultLink = null,
                        uploadedCount = request.folder.files.size,
                        totalBytes = zipFile.length(),
                        exportedZipPath = zipFile.absolutePath
                    )
                }
            }

            _uploadState.value = finalState
            persistHistory(repository, request, finalState)
        } catch (_: CancellationException) {
            val cancelled = UploadState.Error(cancelReasonOverride ?: "تم إلغاء العملية بواسطة المستخدم")
            finalState = cancelled
            _uploadState.value = cancelled
            persistHistory(repository, request, cancelled, overrideStatus = "CANCELLED")
        } catch (ex: Exception) {
            val err = UploadState.Error(ex.message ?: "حدث خطأ غير متوقع أثناء المزامنة في الخلفية")
            finalState = err
            _uploadState.value = err
            persistHistory(repository, request, err)
        } finally {
            activeExecutionJob = null
            _isSyncActive.value = false
        }

        finalState
    }

    private suspend fun persistHistory(
        repository: FolderPushRepository,
        request: FolderSyncRequest,
        state: UploadState,
        overrideStatus: String? = null
    ) {
        val folder = request.folder
        val dest = request.destination
        val targetLabel = when (dest) {
            DestinationType.GITHUB -> request.gitHubConfig.repoInput.ifBlank { "GitHub" }
            DestinationType.GOOGLE_DRIVE -> request.driveConfig.parentFolderId.ifBlank { "My Drive" }
            DestinationType.CUSTOM_WEB -> request.customWebConfig.endpointUrl.ifBlank { "Custom Web" }
            DestinationType.ZIP_EXPORT -> request.zipConfig.archiveName.ifBlank { folder.rootName } + ".zip"
        }

        val status = overrideStatus ?: when (state) {
            is UploadState.Success -> "SUCCESS"
            is UploadState.Error -> if (state.uploadedSoFar > 0) "PARTIAL" else "FAILED"
            else -> "FAILED"
        }

        val summary = when (state) {
            is UploadState.Success -> state.summary
            is UploadState.Error -> state.message
            else -> ""
        }

        val link = (state as? UploadState.Success)?.resultLink

        repository.recordUpload(
            UploadHistoryEntity(
                folderName = folder.rootName,
                destinationType = dest.name,
                targetLabel = targetLabel,
                fileCount = folder.files.size,
                totalBytes = folder.totalBytes,
                status = status,
                resultLink = link,
                summary = summary
            )
        )
    }
}
