package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.browser.BrowserDiscoveryHelper
import com.example.data.browser.InstalledBrowser
import com.example.data.local.AppDatabase
import com.example.data.local.FolderPushRepository
import com.example.data.local.SavedTargetEntity
import com.example.data.local.SecureTokenStore
import com.example.data.local.UploadHistoryEntity
import com.example.data.scanner.SafFolderScanner
import com.example.data.sync.ActiveWorkspaceBridge
import com.example.data.sync.AiMultiFileBridge
import com.example.data.sync.FolderSyncCoordinator
import com.example.data.sync.FolderSyncRequest
import com.example.data.sync.FolderSyncService
import com.example.data.system.DefaultFilesAppManager
import com.example.data.system.DefaultFilesStatus
import com.example.data.uploader.CustomWebUploader
import com.example.data.uploader.GitHubUploader
import com.example.data.uploader.GoogleDriveUploader
import com.example.data.uploader.ZipPackager
import com.example.model.ConnectionCheckResult
import com.example.model.CustomWebConfig
import com.example.model.DefaultIgnoreRules
import com.example.model.DestinationType
import com.example.model.DriveConfig
import com.example.model.GitHubConfig
import com.example.model.ScannedFolder
import com.example.model.TreeSelectionHelper
import com.example.model.UploadState
import com.example.model.ZipConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val tokenStore = SecureTokenStore(appContext)
    private val repository = FolderPushRepository(
        historyDao = db.uploadHistoryDao(),
        targetDao = db.savedTargetDao(),
        tokenStore = tokenStore
    )

    private val scanner = SafFolderScanner(appContext)
    private val zipPackager = ZipPackager(appContext)
    private val gitHubUploader = GitHubUploader(appContext)
    private val driveUploader = GoogleDriveUploader(appContext)
    private val customWebUploader = CustomWebUploader(appContext, zipPackager)

    private val _selectedFolder = MutableStateFlow<ScannedFolder?>(null)
    val selectedFolder: StateFlow<ScannedFolder?> = _selectedFolder.asStateFlow()

    private val _selectedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val selectedDirectories: StateFlow<Set<String>> = _selectedDirectories.asStateFlow()

    private val _selectedFilePaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilePaths: StateFlow<Set<String>> = _selectedFilePaths.asStateFlow()

    private val _activeIgnoreRules = MutableStateFlow(DefaultIgnoreRules.presets.toSet())
    val activeIgnoreRules: StateFlow<Set<String>> = _activeIgnoreRules.asStateFlow()

    private val _selectedDestination = MutableStateFlow(DestinationType.GITHUB)
    val selectedDestination: StateFlow<DestinationType> = _selectedDestination.asStateFlow()

    private val _gitHubConfig = MutableStateFlow(
        GitHubConfig(token = repository.loadLastDraftToken("github"))
    )
    val gitHubConfig: StateFlow<GitHubConfig> = _gitHubConfig.asStateFlow()

    private val _driveConfig = MutableStateFlow(
        DriveConfig(accessToken = repository.loadLastDraftToken("drive"))
    )
    val driveConfig: StateFlow<DriveConfig> = _driveConfig.asStateFlow()

    private val _customWebConfig = MutableStateFlow(
        CustomWebConfig(authHeaderValue = repository.loadLastDraftToken("web"))
    )
    val customWebConfig: StateFlow<CustomWebConfig> = _customWebConfig.asStateFlow()

    private val _zipConfig = MutableStateFlow(ZipConfig())
    val zipConfig: StateFlow<ZipConfig> = _zipConfig.asStateFlow()

    val uploadState: StateFlow<UploadState> = FolderSyncCoordinator.uploadState
    val isSyncServiceActive: StateFlow<Boolean> = FolderSyncCoordinator.isSyncActive

    private val _connectionCheck = MutableStateFlow<ConnectionCheckResult?>(null)
    val connectionCheck: StateFlow<ConnectionCheckResult?> = _connectionCheck.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    val lastExportedZip: StateFlow<File?> = FolderSyncCoordinator.lastExportedZip

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _installedBrowsers = MutableStateFlow<List<InstalledBrowser>>(emptyList())
    val installedBrowsers: StateFlow<List<InstalledBrowser>> = _installedBrowsers.asStateFlow()

    private val _browserTargetUrl = MutableStateFlow("https://app.netlify.com/drop")
    val browserTargetUrl: StateFlow<String> = _browserTargetUrl.asStateFlow()

    private val _openBrowserSignal = MutableStateFlow(0)
    val openBrowserSignal: StateFlow<Int> = _openBrowserSignal.asStateFlow()

    private val _openUploadSignal = MutableStateFlow(0)
    val openUploadSignal: StateFlow<Int> = _openUploadSignal.asStateFlow()

    private val _defaultFilesStatus = MutableStateFlow(DefaultFilesAppManager.getStatus(appContext))
    val defaultFilesStatus: StateFlow<DefaultFilesStatus> = _defaultFilesStatus.asStateFlow()

    val savedTargets: StateFlow<List<SavedTargetEntity>> = repository.savedTargetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uploadHistory: StateFlow<List<UploadHistoryEntity>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshInstalledBrowsers()
    }

    fun refreshInstalledBrowsers() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedBrowsers.value = BrowserDiscoveryHelper.discoverInstalledBrowsers(appContext)
        }
    }

    fun setBrowserTargetUrl(url: String, autoNavigate: Boolean = false) {
        _browserTargetUrl.value = url
        if (autoNavigate) {
            _openBrowserSignal.value += 1
        }
    }

    fun refreshDefaultFilesStatus() {
        _defaultFilesStatus.value = DefaultFilesAppManager.getStatus(appContext)
    }

    fun setFilePickerHandlerEnabled(enabled: Boolean) {
        DefaultFilesAppManager.setFilePickerHandlerEnabled(appContext, enabled)
        refreshDefaultFilesStatus()
        _snackbarMessage.value = if (enabled) {
            "تم تفعيل FolderPush كخيار لمنتقي الملفات في النظام والمتصفحات"
        } else {
            "تم إيقاف ظهور FolderPush في منتقي الملفات بالنظام"
        }
    }

    fun setDocumentsProviderEnabled(enabled: Boolean) {
        DefaultFilesAppManager.setDocumentsProviderEnabled(appContext, enabled)
        refreshDefaultFilesStatus()
        _snackbarMessage.value = if (enabled) {
            "تم إظهار FolderPush كمصدر داخل تطبيق الملفات بالنظام"
        } else {
            "تم إخفاء FolderPush من قائمة مصادر تطبيق الملفات"
        }
    }

    fun notifyMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun selectDestination(type: DestinationType) {
        _selectedDestination.value = type
        _connectionCheck.value = null
        if (uploadState.value !is UploadState.Uploading) {
            FolderSyncCoordinator.resetIfNotActive()
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        scanUri(treeUri, _activeIgnoreRules.value, mergeIntoExisting = false)
    }

    fun onAdditionalFolderPicked(treeUri: Uri) {
        scanUri(treeUri, _activeIgnoreRules.value, mergeIntoExisting = true)
    }

    fun onExtraFilesPicked(uris: List<Uri>, autoNavigateToUpload: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            FolderSyncCoordinator.setUiState(UploadState.Scanning)
            runCatching {
                scanner.scanLooseFiles(uris)
            }.onSuccess { looseFiles ->
                val merged = TreeSelectionHelper.mergeExtraFiles(_selectedFolder.value, looseFiles)
                _selectedFolder.value = merged
                applySelectAll(merged)
                if (_zipConfig.value.archiveName.isBlank()) {
                    _zipConfig.value = _zipConfig.value.copy(archiveName = merged.rootName)
                }
                FolderSyncCoordinator.setUiState(UploadState.Idle)
                if (autoNavigateToUpload) {
                    _openUploadSignal.value += 1
                }
                _snackbarMessage.value = "تم التعرف على ${looseFiles.size} ملف وإضافتها للرفع"
            }.onFailure { err ->
                FolderSyncCoordinator.setUiState(
                    UploadState.Error("تعذر قراءة الملفات المختارة: ${err.message ?: "خطأ صلاحيات"}")
                )
            }
        }
    }

    fun rescanCurrentFolder() {
        val currentUri = _selectedFolder.value?.treeUri ?: return
        if (!DocumentsContract.isTreeUri(currentUri)) {
            _snackbarMessage.value = "العناصر الحالية ملفات فردية مختارة — يمكنك إضافة ملفات أو مجلد"
            return
        }
        scanUri(currentUri, _activeIgnoreRules.value, mergeIntoExisting = false)
    }

    fun toggleIgnoreRule(pattern: String) {
        val updated = _activeIgnoreRules.value.toMutableSet()
        if (updated.contains(pattern)) updated.remove(pattern) else updated.add(pattern)
        _activeIgnoreRules.value = updated
        _selectedFolder.value?.treeUri?.let { uri ->
            if (DocumentsContract.isTreeUri(uri)) {
                scanUri(uri, updated, mergeIntoExisting = false)
            }
        }
    }

    fun addCustomIgnoreRule(pattern: String) {
        val clean = pattern.trim()
        if (clean.isEmpty()) return
        val updated = _activeIgnoreRules.value + clean
        _activeIgnoreRules.value = updated
        _selectedFolder.value?.treeUri?.let { uri ->
            if (DocumentsContract.isTreeUri(uri)) {
                scanUri(uri, updated, mergeIntoExisting = false)
            }
        }
    }

    fun includeAllHiddenAndIgnored() {
        _activeIgnoreRules.value = emptySet()
        _selectedFolder.value?.treeUri?.let { uri ->
            if (DocumentsContract.isTreeUri(uri)) {
                scanUri(uri, emptySet(), mergeIntoExisting = false)
            }
        }
        _snackbarMessage.value = "تم تفعيل التعرف على كافة الملفات والمجلدات دون استبعاد"
    }

    fun restoreDefaultIgnoreRules() {
        val defaults = DefaultIgnoreRules.presets.toSet()
        _activeIgnoreRules.value = defaults
        _selectedFolder.value?.treeUri?.let { uri ->
            if (DocumentsContract.isTreeUri(uri)) {
                scanUri(uri, defaults, mergeIntoExisting = false)
            }
        }
        _snackbarMessage.value = "تمت استعادة قواعد الاستبعاد الافتراضية"
    }

    fun toggleSelectAllItems() {
        val folder = _selectedFolder.value ?: return
        val next = TreeSelectionHelper.toggleSelectAll(
            folder = folder,
            selectedDirectories = _selectedDirectories.value,
            selectedFiles = _selectedFilePaths.value
        )
        _selectedDirectories.value = next.selectedDirectories
        _selectedFilePaths.value = next.selectedFiles
        publishEffectiveWorkspace()
    }

    fun selectAllItems() {
        val folder = _selectedFolder.value ?: return
        applySelectAll(folder)
    }

    fun clearAllSelectedItems() {
        val next = TreeSelectionHelper.clearSelection()
        _selectedDirectories.value = next.selectedDirectories
        _selectedFilePaths.value = next.selectedFiles
        publishEffectiveWorkspace()
    }

    fun toggleDirectorySelection(dirRelPath: String) {
        val folder = _selectedFolder.value ?: return
        val next = TreeSelectionHelper.toggleDirectory(
            folder = folder,
            currentDirs = _selectedDirectories.value,
            currentFiles = _selectedFilePaths.value,
            dirRelPath = dirRelPath
        )
        _selectedDirectories.value = next.selectedDirectories
        _selectedFilePaths.value = next.selectedFiles
        publishEffectiveWorkspace()
    }

    fun toggleFileSelection(fileRelPath: String) {
        val folder = _selectedFolder.value ?: return
        val next = TreeSelectionHelper.toggleFile(
            folder = folder,
            currentDirs = _selectedDirectories.value,
            currentFiles = _selectedFilePaths.value,
            fileRelPath = fileRelPath
        )
        _selectedDirectories.value = next.selectedDirectories
        _selectedFilePaths.value = next.selectedFiles
        publishEffectiveWorkspace()
    }

    fun getEffectiveSelectedFolder(): ScannedFolder? {
        val folder = _selectedFolder.value ?: return null
        return TreeSelectionHelper.buildSelectedFolderSnapshot(
            folder = folder,
            selectedDirectories = _selectedDirectories.value,
            selectedFiles = _selectedFilePaths.value
        )
    }

    private fun applySelectAll(folder: ScannedFolder) {
        val all = TreeSelectionHelper.selectAll(folder)
        _selectedDirectories.value = all.selectedDirectories
        _selectedFilePaths.value = all.selectedFiles
        publishEffectiveWorkspace()
    }

    private fun publishEffectiveWorkspace() {
        ActiveWorkspaceBridge.publishWorkspace(getEffectiveSelectedFolder())
    }

    private fun scanUri(uri: Uri, ignoreRules: Set<String>, mergeIntoExisting: Boolean) {
        viewModelScope.launch {
            FolderSyncCoordinator.setUiState(UploadState.Scanning)
            runCatching {
                scanner.scanFolder(uri, ignoreRules)
            }.onSuccess { scanned ->
                val targetFolder = if (mergeIntoExisting) {
                    TreeSelectionHelper.mergeFolderIntoWorkspace(_selectedFolder.value, scanned)
                } else {
                    scanned
                }
                _selectedFolder.value = targetFolder
                applySelectAll(targetFolder)
                if (_zipConfig.value.archiveName.isBlank()) {
                    _zipConfig.value = _zipConfig.value.copy(archiveName = targetFolder.rootName)
                }
                FolderSyncCoordinator.setUiState(UploadState.Idle)
            }.onFailure { err ->
                FolderSyncCoordinator.setUiState(
                    UploadState.Error(
                        "تعذر قراءة المجلد: ${err.message ?: "خطأ صلاحيات SAF"}"
                    )
                )
            }
        }
    }

    fun updateGitHubConfig(updater: (GitHubConfig) -> GitHubConfig) {
        val next = updater(_gitHubConfig.value)
        if (next.token != _gitHubConfig.value.token) {
            repository.saveLastDraftToken("github", next.token)
        }
        _gitHubConfig.value = next
        _connectionCheck.value = null
    }

    fun updateDriveConfig(updater: (DriveConfig) -> DriveConfig) {
        val next = updater(_driveConfig.value)
        if (next.accessToken != _driveConfig.value.accessToken) {
            repository.saveLastDraftToken("drive", next.accessToken)
        }
        _driveConfig.value = next
        _connectionCheck.value = null
    }

    fun updateCustomWebConfig(updater: (CustomWebConfig) -> CustomWebConfig) {
        val next = updater(_customWebConfig.value)
        if (next.authHeaderValue != _customWebConfig.value.authHeaderValue) {
            repository.saveLastDraftToken("web", next.authHeaderValue)
        }
        _customWebConfig.value = next
        _connectionCheck.value = null
    }

    fun updateZipConfig(updater: (ZipConfig) -> ZipConfig) {
        _zipConfig.value = updater(_zipConfig.value)
    }

    fun verifyConnection() {
        if (_isCheckingConnection.value) return
        viewModelScope.launch {
            _isCheckingConnection.value = true
            val result = when (_selectedDestination.value) {
                DestinationType.GITHUB -> {
                    val cfg = _gitHubConfig.value
                    val res = gitHubUploader.verifyConnection(cfg.repoInput, cfg.token)
                    if (res.isSuccess && res.detectedDefaultBranch != null && cfg.branch == "main") {
                        _gitHubConfig.value = cfg.copy(branch = res.detectedDefaultBranch)
                    }
                    res
                }
                DestinationType.GOOGLE_DRIVE -> driveUploader.verifyToken(_driveConfig.value.accessToken)
                DestinationType.CUSTOM_WEB -> customWebUploader.testEndpoint(_customWebConfig.value)
                DestinationType.ZIP_EXPORT -> ConnectionCheckResult(
                    true,
                    "الضغط يتم محلياً على جهازك دون الحاجة لاتصال خادم"
                )
            }
            _connectionCheck.value = result
            _isCheckingConnection.value = false
        }
    }

    fun startUpload() {
        val rawFolder = _selectedFolder.value
        if (rawFolder == null) {
            _snackbarMessage.value = "اختر مجلداً أو ملفات من الجهاز أولاً"
            return
        }
        val folder = TreeSelectionHelper.buildSelectedFolderSnapshot(
            folder = rawFolder,
            selectedDirectories = _selectedDirectories.value,
            selectedFiles = _selectedFilePaths.value
        )
        if (folder.files.isEmpty() && folder.directories.isEmpty()) {
            _snackbarMessage.value = "اختر ملفاً أو مجلداً واحداً على الأقل أو اضغط «تحديد الكل»"
            return
        }

        val request = FolderSyncRequest(
            folder = folder,
            destination = _selectedDestination.value,
            gitHubConfig = _gitHubConfig.value,
            driveConfig = _driveConfig.value,
            customWebConfig = _customWebConfig.value,
            zipConfig = _zipConfig.value
        )
        FolderSyncCoordinator.enqueueSync(request)

        val serviceIntent = Intent(appContext, FolderSyncService::class.java).apply {
            action = FolderSyncService.ACTION_START_SYNC
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }.onFailure { err ->
            FolderSyncCoordinator.cancelActiveSync(
                "تعذر بدء خدمة المزامنة في الخلفية: ${err.message ?: "خطأ نظام"}"
            )
        }
    }

    fun cancelUpload() {
        FolderSyncCoordinator.cancelActiveSync("تم إلغاء العملية بواسطة المستخدم")
        runCatching {
            val cancelIntent = Intent(appContext, FolderSyncService::class.java).apply {
                action = FolderSyncService.ACTION_CANCEL_SYNC
            }
            appContext.startService(cancelIntent)
        }
    }

    fun resetUploadState() {
        FolderSyncCoordinator.resetIfNotActive()
    }

    fun saveExportedZipToUri(destUri: Uri) {
        val zipFile = lastExportedZip.value ?: return
        viewModelScope.launch {
            runCatching {
                zipPackager.copyZipToDestinationUri(zipFile, destUri)
            }.onSuccess {
                _snackbarMessage.value = "تم حفظ ملف ZIP في المسار المختار بنجاح"
            }.onFailure { err ->
                _snackbarMessage.value = "فشل حفظ الملف: ${err.message}"
            }
        }
    }

    suspend fun packSelectedFolderToTempZip(): Result<File> {
        val rawFolder = _selectedFolder.value
            ?: return Result.failure(IllegalStateException("لم يتم اختيار مجلد بعد"))
        val folder = TreeSelectionHelper.buildSelectedFolderSnapshot(
            folder = rawFolder,
            selectedDirectories = _selectedDirectories.value,
            selectedFiles = _selectedFilePaths.value
        )
        if (folder.files.isEmpty()) {
            return Result.failure(IllegalStateException("لا توجد ملفات محددة للضغط — اضغط «تحديد الكل»"))
        }
        return runCatching {
            val zipFile = zipPackager.packFolderToZip(
                folder = folder,
                archiveName = _zipConfig.value.archiveName.ifBlank { folder.rootName }
            ) { _, _, _, _ -> }
            FolderSyncCoordinator.setLastExportedZip(zipFile)
            zipFile
        }
    }

    suspend fun prepareFilesForAiShare(prefixFolderNames: Boolean = true): Result<List<Uri>> {
        val folder = getEffectiveSelectedFolder()
            ?: return Result.failure(IllegalStateException("اختر مجلداً أو ملفات أولاً"))
        if (folder.files.isEmpty()) {
            return Result.failure(IllegalStateException("لا توجد ملفات محددة للإرسال — اضغط «تحديد الكل»"))
        }
        return runCatching {
            val uris = AiMultiFileBridge.stageFilesForAiUpload(appContext, folder, prefixFolderNames)
            if (uris.isEmpty()) error("تعذر تجهيز الملفات للقراءة")
            uris
        }
    }

    fun saveCurrentAsTarget(profileName: String) {
        val cleanName = profileName.trim()
        if (cleanName.isEmpty()) {
            _snackbarMessage.value = "أدخل اسماً للوجهة قبل الحفظ"
            return
        }
        viewModelScope.launch {
            when (_selectedDestination.value) {
                DestinationType.GITHUB -> repository.saveGitHubTarget(cleanName, _gitHubConfig.value)
                DestinationType.GOOGLE_DRIVE -> repository.saveDriveTarget(cleanName, _driveConfig.value)
                DestinationType.CUSTOM_WEB -> repository.saveCustomWebTarget(cleanName, _customWebConfig.value)
                DestinationType.ZIP_EXPORT -> {
                    _snackbarMessage.value = "ضغط ZIP لا يحتاج لحفظ وجهة"
                    return@launch
                }
            }
            _snackbarMessage.value = "تم حفظ الوجهة \"$cleanName\" بأمان"
        }
    }

    fun applySavedTarget(target: SavedTargetEntity) {
        val type = runCatching { DestinationType.valueOf(target.destinationType) }
            .getOrDefault(DestinationType.GITHUB)
        _selectedDestination.value = type
        _connectionCheck.value = null
        when (type) {
            DestinationType.GITHUB -> _gitHubConfig.value = repository.loadGitHubConfig(target)
            DestinationType.GOOGLE_DRIVE -> _driveConfig.value = repository.loadDriveConfig(target)
            DestinationType.CUSTOM_WEB -> _customWebConfig.value = repository.loadCustomWebConfig(target)
            DestinationType.ZIP_EXPORT -> Unit
        }
        _snackbarMessage.value = "تم تحميل إعدادات \"${target.name}\""
    }

    fun deleteSavedTarget(target: SavedTargetEntity) {
        viewModelScope.launch {
            repository.deleteTarget(target)
            _snackbarMessage.value = "تم حذف الوجهة"
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _snackbarMessage.value = "تم مسح سجل الرفع"
        }
    }
}
