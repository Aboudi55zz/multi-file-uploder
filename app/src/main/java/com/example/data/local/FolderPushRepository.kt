package com.example.data.local

import com.example.model.CustomWebConfig
import com.example.model.DestinationType
import com.example.model.DriveConfig
import com.example.model.GitHubConfig
import com.example.model.WebUploadMode
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID

class FolderPushRepository(
    private val historyDao: UploadHistoryDao,
    private val targetDao: SavedTargetDao,
    private val tokenStore: SecureTokenStore
) {
    val historyFlow: Flow<List<UploadHistoryEntity>> = historyDao.observeHistory()
    val savedTargetsFlow: Flow<List<SavedTargetEntity>> = targetDao.observeTargets()

    suspend fun recordUpload(entry: UploadHistoryEntity) {
        historyDao.insert(entry)
    }

    suspend fun deleteHistoryItem(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun saveGitHubTarget(name: String, config: GitHubConfig) {
        val tokenKey = "target_gh_${UUID.randomUUID()}"
        tokenStore.saveToken(tokenKey, config.token)
        val json = JSONObject().apply {
            put("repoInput", config.repoInput)
            put("branch", config.branch)
            put("targetSubPath", config.targetSubPath)
            put("commitMessage", config.commitMessage)
        }.toString()
        targetDao.insert(
            SavedTargetEntity(
                name = name,
                destinationType = DestinationType.GITHUB.name,
                configJson = json,
                tokenKey = tokenKey
            )
        )
    }

    suspend fun saveDriveTarget(name: String, config: DriveConfig) {
        val tokenKey = "target_gd_${UUID.randomUUID()}"
        tokenStore.saveToken(tokenKey, config.accessToken)
        val json = JSONObject().apply {
            put("parentFolderId", config.parentFolderId)
            put("createRootFolder", config.createRootFolder)
        }.toString()
        targetDao.insert(
            SavedTargetEntity(
                name = name,
                destinationType = DestinationType.GOOGLE_DRIVE.name,
                configJson = json,
                tokenKey = tokenKey
            )
        )
    }

    suspend fun saveCustomWebTarget(name: String, config: CustomWebConfig) {
        val tokenKey = "target_web_${UUID.randomUUID()}"
        tokenStore.saveToken(tokenKey, config.authHeaderValue)
        val json = JSONObject().apply {
            put("endpointUrl", config.endpointUrl)
            put("mode", config.mode.name)
            put("fileFieldName", config.fileFieldName)
            put("pathFieldName", config.pathFieldName)
            put("authHeaderName", config.authHeaderName)
            put("allowCleartextHttp", config.allowCleartextHttp)
        }.toString()
        targetDao.insert(
            SavedTargetEntity(
                name = name,
                destinationType = DestinationType.CUSTOM_WEB.name,
                configJson = json,
                tokenKey = tokenKey
            )
        )
    }

    suspend fun deleteTarget(target: SavedTargetEntity) {
        tokenStore.deleteToken(target.tokenKey)
        targetDao.deleteById(target.id)
    }

    fun loadGitHubConfig(target: SavedTargetEntity): GitHubConfig {
        val json = runCatching { JSONObject(target.configJson) }.getOrElse { JSONObject() }
        return GitHubConfig(
            repoInput = json.optString("repoInput", ""),
            branch = json.optString("branch", "main"),
            targetSubPath = json.optString("targetSubPath", ""),
            commitMessage = json.optString("commitMessage", "Upload folder from mobile via FolderPush"),
            token = tokenStore.getToken(target.tokenKey)
        )
    }

    fun loadDriveConfig(target: SavedTargetEntity): DriveConfig {
        val json = runCatching { JSONObject(target.configJson) }.getOrElse { JSONObject() }
        return DriveConfig(
            parentFolderId = json.optString("parentFolderId", ""),
            createRootFolder = json.optBoolean("createRootFolder", true),
            accessToken = tokenStore.getToken(target.tokenKey)
        )
    }

    fun loadCustomWebConfig(target: SavedTargetEntity): CustomWebConfig {
        val json = runCatching { JSONObject(target.configJson) }.getOrElse { JSONObject() }
        val modeName = json.optString("mode", WebUploadMode.MULTIPART_PER_FILE.name)
        val mode = runCatching { WebUploadMode.valueOf(modeName) }
            .getOrDefault(WebUploadMode.MULTIPART_PER_FILE)
        return CustomWebConfig(
            endpointUrl = json.optString("endpointUrl", ""),
            mode = mode,
            fileFieldName = json.optString("fileFieldName", "file"),
            pathFieldName = json.optString("pathFieldName", "relativePath"),
            authHeaderName = json.optString("authHeaderName", "Authorization"),
            authHeaderValue = tokenStore.getToken(target.tokenKey),
            allowCleartextHttp = json.optBoolean("allowCleartextHttp", false)
        )
    }

    fun saveLastDraftToken(slot: String, value: String) {
        tokenStore.saveToken("draft_$slot", value)
    }

    fun loadLastDraftToken(slot: String): String {
        return tokenStore.getToken("draft_$slot")
    }
}
