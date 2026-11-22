package com.example

import com.example.data.browser.BrowserDiscoveryHelper
import com.example.data.provider.FolderPushDocumentsProvider
import com.example.data.scanner.SafFolderScanner
import com.example.data.sync.AiUploadBridgeUtils
import com.example.data.sync.FolderSyncProgressFormatter
import com.example.data.uploader.CustomWebUploader
import com.example.data.uploader.GitHubUploader
import com.example.model.FileCategory
import com.example.model.FileTypeRegistry
import com.example.model.TreeSelectionHelper
import com.example.model.UploadState
import com.example.ui.components.SafFolderPickerUtils
import com.example.ui.screens.WebBrowserUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseOwnerRepo_handlesShorthandAndUrls() {
        assertEquals("octocat" to "hello-world", GitHubUploader.parseOwnerRepo("octocat/hello-world"))
        assertEquals(
            "octocat" to "hello-world",
            GitHubUploader.parseOwnerRepo("https://github.com/octocat/hello-world.git")
        )
        assertEquals(
            "octocat" to "hello-world",
            GitHubUploader.parseOwnerRepo("git@github.com:octocat/hello-world.git")
        )
        assertNull(GitHubUploader.parseOwnerRepo("invalid-repo"))
    }

    @Test
    fun validateEndpointUrl_enforcesHttpsUnlessCleartextAllowed() {
        assertTrue(CustomWebUploader.validateEndpointUrl("https://upload.example.com/api", false).isSuccess)
        assertTrue(CustomWebUploader.validateEndpointUrl("http://192.168.1.50:8080/dav", false).isFailure)
        assertTrue(CustomWebUploader.validateEndpointUrl("http://192.168.1.50:8080/dav", true).isSuccess)
        assertTrue(CustomWebUploader.validateEndpointUrl("ftp://example.com", true).isFailure)
    }

    @Test
    fun normalizeWebUrl_andExtractFirstHttpUrl_workCorrectly() {
        assertEquals("https://example.com/", WebBrowserUtils.normalizeWebUrl("example.com").getOrNull())
        assertEquals("https://app.netlify.com/drop", WebBrowserUtils.normalizeWebUrl("https://app.netlify.com/drop").getOrNull())
        assertTrue(WebBrowserUtils.normalizeWebUrl("javascript:alert(1)").isFailure)
        assertTrue(WebBrowserUtils.normalizeWebUrl("file:///etc/passwd").isFailure)
        assertTrue(WebBrowserUtils.normalizeWebUrl("data:text/html,hi").isFailure)

        assertEquals(
            "https://github.com/octocat/hello-world",
            WebBrowserUtils.extractFirstHttpUrl("Check out https://github.com/octocat/hello-world on mobile")
        )
        assertNull(WebBrowserUtils.extractFirstHttpUrl("no links here"))
    }

    @Test
    fun classifyBrowserNote_identifiesMajorMobileBrowsers() {
        assertTrue(BrowserDiscoveryHelper.classifyBrowserNote("com.android.chrome").contains("Chromium"))
        assertTrue(BrowserDiscoveryHelper.classifyBrowserNote("org.mozilla.firefox").contains("Gecko"))
        assertTrue(BrowserDiscoveryHelper.classifyBrowserNote("com.sec.android.app.sbrowser").contains("Samsung"))
        assertTrue(BrowserDiscoveryHelper.classifyBrowserNote("com.microsoft.emmx").contains("Edge"))
    }

    @Test
    fun formatBytes_formatsReadableUnits() {
        assertEquals("512 B", SafFolderScanner.formatBytes(512))
        assertEquals("1.5 KB", SafFolderScanner.formatBytes(1536))
    }

    @Test
    fun extractFolderNameFromDocumentId_parsesSafTreeIds() {
        assertEquals(
            "MyProject",
            SafFolderPickerUtils.extractFolderNameFromDocumentId("primary:Documents/Projects/MyProject")
        )
        assertEquals(
            "Downloads",
            SafFolderPickerUtils.extractFolderNameFromDocumentId("primary:Downloads")
        )
        assertEquals(
            "التخزين الداخلي",
            SafFolderPickerUtils.extractFolderNameFromDocumentId("primary:")
        )
    }

    @Test
    fun isSafeArchiveFileName_rejectsPathTraversalAndNonZip() {
        assertTrue(FolderPushDocumentsProvider.isSafeArchiveFileName("project_archive.zip"))
        assertFalse(FolderPushDocumentsProvider.isSafeArchiveFileName("../secret.zip"))
        assertFalse(FolderPushDocumentsProvider.isSafeArchiveFileName("sub/archive.zip"))
        assertFalse(FolderPushDocumentsProvider.isSafeArchiveFileName("notes.txt"))
        assertFalse(FolderPushDocumentsProvider.isSafeArchiveFileName(""))
    }

    @Test
    fun syncProgressFormatter_computesProgressAndThrottlingCorrectly() {
        assertEquals(0, FolderSyncProgressFormatter.computeProgressPercent(0, 0, 0L, 0L))
        assertEquals(50, FolderSyncProgressFormatter.computeProgressPercent(5, 10, 500L, 1000L))
        assertEquals(100, FolderSyncProgressFormatter.computeProgressPercent(10, 10, 1000L, 1000L))

        val uploading = UploadState.Uploading(
            currentIndex = 3,
            totalFiles = 12,
            currentFilePath = "src/App.kt",
            bytesTransferred = 2048L,
            totalBytes = 4096L,
            stageLabel = "جاري الرفع"
        )
        assertTrue(FolderSyncProgressFormatter.buildOngoingContentText(uploading).contains("3/12"))

        // First file or stage change triggers immediately
        assertTrue(FolderSyncProgressFormatter.shouldNotifyUpdate(1000L, 1050L, "أ", "ب", 4, 10))
        assertTrue(FolderSyncProgressFormatter.shouldNotifyUpdate(1000L, 1050L, "أ", "أ", 1, 10))
        // Rapid mid-file update within interval is throttled
        assertFalse(FolderSyncProgressFormatter.shouldNotifyUpdate(1000L, 1100L, "أ", "أ", 5, 10))
        // Past interval or final file triggers update
        assertTrue(FolderSyncProgressFormatter.shouldNotifyUpdate(1000L, 1400L, "أ", "أ", 5, 10))
        assertTrue(FolderSyncProgressFormatter.shouldNotifyUpdate(1000L, 1050L, "أ", "أ", 10, 10))
    }

    @Test
    fun treeSelectionHelper_selectsAllFoldersAndFilesTogether() {
        val dirs = listOf("src", "src/ui")
        val files = listOf("README.md", "src/App.kt", "src/ui/Theme.kt")

        val all = TreeSelectionHelper.selectAll(dirs, files)
        assertTrue(TreeSelectionHelper.isAllSelected(dirs, files, all.selectedDirectories, all.selectedFiles))
        assertEquals(setOf("src", "src/ui"), all.selectedDirectories)
        assertEquals(setOf("README.md", "src/App.kt", "src/ui/Theme.kt"), all.selectedFiles)

        // Toggling "src" off clears "src", "src/ui", and both files inside "src/"
        val afterUncheckSrc = TreeSelectionHelper.toggleDirectory(
            directories = dirs,
            filePaths = files,
            currentDirs = all.selectedDirectories,
            currentFiles = all.selectedFiles,
            dirRelPath = "src"
        )
        assertFalse(
            TreeSelectionHelper.isAllSelected(
                dirs,
                files,
                afterUncheckSrc.selectedDirectories,
                afterUncheckSrc.selectedFiles
            )
        )
        assertEquals(emptySet<String>(), afterUncheckSrc.selectedDirectories)
        assertEquals(setOf("README.md"), afterUncheckSrc.selectedFiles)

        // Toggling Select All from partial/empty state re-selects all folders & files together
        val toggledAll = TreeSelectionHelper.toggleSelectAll(
            directories = dirs,
            filePaths = files,
            selectedDirectories = afterUncheckSrc.selectedDirectories,
            selectedFiles = afterUncheckSrc.selectedFiles
        )
        assertTrue(
            TreeSelectionHelper.isAllSelected(
                dirs,
                files,
                toggledAll.selectedDirectories,
                toggledAll.selectedFiles
            )
        )
        assertEquals(setOf("src", "src/ui"), toggledAll.selectedDirectories)
        assertEquals(3, toggledAll.selectedFiles.size)

        // Toggling Select All again clears all folders & files
        val cleared = TreeSelectionHelper.toggleSelectAll(
            directories = dirs,
            filePaths = files,
            selectedDirectories = toggledAll.selectedDirectories,
            selectedFiles = toggledAll.selectedFiles
        )
        assertEquals(emptySet<String>(), cleared.selectedDirectories)
        assertEquals(emptySet<String>(), cleared.selectedFiles)
    }

    @Test
    fun aiUploadBridgeUtils_detectsChatGptAndClaude_andFlattensFolderPaths() {
        assertEquals("ChatGPT", AiUploadBridgeUtils.detectCallerLabel("com.openai.chatgpt", null))
        assertEquals("Claude", AiUploadBridgeUtils.detectCallerLabel("com.anthropic.claude", null))
        assertEquals("ChatGPT", AiUploadBridgeUtils.detectCallerLabel(null, "chatgpt.com"))
        assertEquals("Claude", AiUploadBridgeUtils.detectCallerLabel(null, "claude.ai"))
        assertEquals("Google Chrome", AiUploadBridgeUtils.detectCallerLabel("com.android.chrome", null))

        assertTrue(AiUploadBridgeUtils.isAiAssistantPackageOrHost("ChatGPT"))
        assertTrue(AiUploadBridgeUtils.isAiAssistantPackageOrHost("Claude"))
        assertFalse(AiUploadBridgeUtils.isAiAssistantPackageOrHost("Google Chrome"))

        assertEquals(
            "Demo_src_ui_Theme.kt",
            AiUploadBridgeUtils.buildFlattenedFileName("src/ui/Theme.kt", "Demo")
        )
        assertEquals(
            "src_App.kt",
            AiUploadBridgeUtils.buildFlattenedFileName("src/App.kt", "ملفات مختارة")
        )

        val presetUrls = WebBrowserUtils.presetSites.map { it.second }
        assertTrue(presetUrls.contains("https://chatgpt.com"))
        assertTrue(presetUrls.contains("https://claude.ai"))
    }

    @Test
    fun fileTypeRegistry_recognizesAllFileCategoriesMimeTypesAndDirectories() {
        // Directory MIME types & SAF flags
        assertTrue(FileTypeRegistry.isDirectoryMime("vnd.android.document/directory"))
        assertTrue(FileTypeRegistry.isDirectoryMime("inode/directory"))
        assertTrue(FileTypeRegistry.isDirectoryMime("resource/folder"))
        assertFalse(FileTypeRegistry.isDirectoryMime("application/pdf"))

        // Code, extensionless, and hidden dotfiles
        assertEquals(FileCategory.CODE, FileTypeRegistry.classify("App.kt"))
        assertEquals(FileCategory.CODE, FileTypeRegistry.classify("Dockerfile"))
        assertEquals(FileCategory.CODE, FileTypeRegistry.classify("Makefile"))
        assertEquals(FileCategory.CONFIG_DATA, FileTypeRegistry.classify(".env"))
        assertEquals(FileCategory.CONFIG_DATA, FileTypeRegistry.classify(".gitignore"))
        assertEquals("text/x-kotlin", FileTypeRegistry.resolveMimeType("App.kt", null))
        assertEquals("text/x-dockerfile", FileTypeRegistry.resolveMimeType("Dockerfile", "application/octet-stream"))

        // Documents, Images, Audio, Video, Archives/Packages, 3D/Fonts, and Custom/Unknown
        assertEquals(FileCategory.DOCUMENT, FileTypeRegistry.classify("report.pdf"))
        assertEquals(FileCategory.DOCUMENT, FileTypeRegistry.classify("sheet.xlsx"))
        assertEquals(FileCategory.IMAGE, FileTypeRegistry.classify("vector.svg"))
        assertEquals(FileCategory.IMAGE, FileTypeRegistry.classify("photo.heic"))
        assertEquals(FileCategory.AUDIO, FileTypeRegistry.classify("voice.opus"))
        assertEquals(FileCategory.VIDEO, FileTypeRegistry.classify("clip.mkv"))
        assertEquals(FileCategory.ARCHIVE_PACKAGE, FileTypeRegistry.classify("app-release.apk"))
        assertEquals(FileCategory.ARCHIVE_PACKAGE, FileTypeRegistry.classify("archive.7z"))
        assertEquals(FileCategory.FONT_3D_BINARY, FileTypeRegistry.classify("scene.glb"))
        assertEquals(FileCategory.FONT_3D_BINARY, FileTypeRegistry.classify("font.woff2"))
        assertEquals(FileCategory.OTHER, FileTypeRegistry.classify("custom.xyz99"))
        assertEquals("application/octet-stream", FileTypeRegistry.resolveMimeType("custom.xyz99", ""))

        // Badge & summary
        assertEquals("APK", FileTypeRegistry.shortTypeBadge("app.apk"))
        assertEquals("IMG", FileTypeRegistry.shortTypeBadge("noext", "image/png"))
        val summary = FileTypeRegistry.summarizeFileEntries(
            listOf(
                "Main.kt" to null,
                "photo.png" to "image/png",
                "backup.zip" to "application/zip",
                "custom.abc" to null
            )
        )
        assertEquals(4, summary.size)
        assertEquals(1, summary[FileCategory.CODE])
        assertEquals(1, summary[FileCategory.IMAGE])
        assertEquals(1, summary[FileCategory.ARCHIVE_PACKAGE])
        assertEquals(1, summary[FileCategory.OTHER])
    }
}


