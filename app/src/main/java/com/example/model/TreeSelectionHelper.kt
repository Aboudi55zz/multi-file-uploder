package com.example.model

import android.net.Uri
import java.util.Locale

data class TreeSelectionState(
    val selectedDirectories: Set<String> = emptySet(),
    val selectedFiles: Set<String> = emptySet()
)

object TreeSelectionHelper {

    fun selectAll(folder: ScannedFolder): TreeSelectionState {
        return selectAll(folder.directories, folder.files.map { it.relativePath })
    }

    fun selectAll(directories: List<String>, filePaths: List<String>): TreeSelectionState {
        return TreeSelectionState(
            selectedDirectories = directories.toSet(),
            selectedFiles = filePaths.toSet()
        )
    }

    fun clearSelection(): TreeSelectionState = TreeSelectionState()

    fun isAllSelected(
        folder: ScannedFolder,
        selectedDirectories: Set<String>,
        selectedFiles: Set<String>
    ): Boolean {
        return isAllSelected(
            directories = folder.directories,
            filePaths = folder.files.map { it.relativePath },
            selectedDirectories = selectedDirectories,
            selectedFiles = selectedFiles
        )
    }

    fun isAllSelected(
        directories: List<String>,
        filePaths: List<String>,
        selectedDirectories: Set<String>,
        selectedFiles: Set<String>
    ): Boolean {
        val totalItems = directories.size + filePaths.size
        if (totalItems == 0) return false
        return directories.all { selectedDirectories.contains(it) } &&
            filePaths.all { selectedFiles.contains(it) }
    }

    fun toggleSelectAll(
        folder: ScannedFolder,
        selectedDirectories: Set<String>,
        selectedFiles: Set<String>
    ): TreeSelectionState {
        return toggleSelectAll(
            directories = folder.directories,
            filePaths = folder.files.map { it.relativePath },
            selectedDirectories = selectedDirectories,
            selectedFiles = selectedFiles
        )
    }

    fun toggleSelectAll(
        directories: List<String>,
        filePaths: List<String>,
        selectedDirectories: Set<String>,
        selectedFiles: Set<String>
    ): TreeSelectionState {
        return if (isAllSelected(directories, filePaths, selectedDirectories, selectedFiles)) {
            clearSelection()
        } else {
            selectAll(directories, filePaths)
        }
    }

    fun toggleDirectory(
        folder: ScannedFolder,
        currentDirs: Set<String>,
        currentFiles: Set<String>,
        dirRelPath: String
    ): TreeSelectionState {
        return toggleDirectory(
            directories = folder.directories,
            filePaths = folder.files.map { it.relativePath },
            currentDirs = currentDirs,
            currentFiles = currentFiles,
            dirRelPath = dirRelPath
        )
    }

    fun toggleDirectory(
        directories: List<String>,
        filePaths: List<String>,
        currentDirs: Set<String>,
        currentFiles: Set<String>,
        dirRelPath: String
    ): TreeSelectionState {
        val prefix = "$dirRelPath/"
        val nestedDirs = directories.filter { it == dirRelPath || it.startsWith(prefix) }
        val nestedFiles = filePaths.filter { it.startsWith(prefix) }

        val shouldSelect = !currentDirs.contains(dirRelPath)
        val nextDirs = currentDirs.toMutableSet()
        val nextFiles = currentFiles.toMutableSet()

        if (shouldSelect) {
            nextDirs.addAll(nestedDirs)
            nextFiles.addAll(nestedFiles)
            addAncestorDirectories(dirRelPath, directories, nextDirs)
        } else {
            nextDirs.removeAll(nestedDirs.toSet())
            nextFiles.removeAll(nestedFiles.toSet())
        }

        return TreeSelectionState(
            selectedDirectories = nextDirs,
            selectedFiles = nextFiles
        )
    }

    fun toggleFile(
        folder: ScannedFolder,
        currentDirs: Set<String>,
        currentFiles: Set<String>,
        fileRelPath: String
    ): TreeSelectionState {
        return toggleFile(
            directories = folder.directories,
            currentDirs = currentDirs,
            currentFiles = currentFiles,
            fileRelPath = fileRelPath
        )
    }

    fun toggleFile(
        directories: List<String>,
        currentDirs: Set<String>,
        currentFiles: Set<String>,
        fileRelPath: String
    ): TreeSelectionState {
        val nextDirs = currentDirs.toMutableSet()
        val nextFiles = currentFiles.toMutableSet()

        if (nextFiles.contains(fileRelPath)) {
            nextFiles.remove(fileRelPath)
        } else {
            nextFiles.add(fileRelPath)
            val parentDir = fileRelPath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                addAncestorDirectories(parentDir, directories, nextDirs)
                if (directories.contains(parentDir)) {
                    nextDirs.add(parentDir)
                }
            }
        }

        return TreeSelectionState(
            selectedDirectories = nextDirs,
            selectedFiles = nextFiles
        )
    }

    fun buildSelectedFolderSnapshot(
        folder: ScannedFolder,
        selectedDirectories: Set<String>,
        selectedFiles: Set<String>
    ): ScannedFolder {
        val filteredFiles = folder.files.filter { selectedFiles.contains(it.relativePath) }
        val requiredParentDirs = HashSet<String>()
        filteredFiles.forEach { file ->
            var cursor = file.relativePath.substringBeforeLast('/', "")
            while (cursor.isNotEmpty()) {
                requiredParentDirs.add(cursor)
                cursor = cursor.substringBeforeLast('/', "")
            }
        }
        val filteredDirs = folder.directories
            .filter { selectedDirectories.contains(it) || requiredParentDirs.contains(it) }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }

        return folder.copy(
            files = filteredFiles,
            directories = filteredDirs,
            directoryCount = filteredDirs.size,
            totalBytes = filteredFiles.sumOf { it.sizeBytes }
        )
    }

    fun mergeExtraFiles(
        existing: ScannedFolder?,
        extraFiles: List<ScannedFile>
    ): ScannedFolder {
        if (existing == null) {
            val sorted = extraFiles
                .distinctBy { it.relativePath }
                .sortedBy { it.relativePath.lowercase(Locale.ROOT) }
            return ScannedFolder(
                treeUri = sorted.firstOrNull()?.uri ?: Uri.EMPTY,
                rootName = "ملفات مختارة",
                files = sorted,
                directories = emptyList(),
                skippedCount = 0,
                directoryCount = 0,
                totalBytes = sorted.sumOf { it.sizeBytes },
                activeIgnorePatterns = emptySet()
            )
        }

        val mergedMap = LinkedHashMap<String, ScannedFile>()
        existing.files.forEach { mergedMap[it.relativePath] = it }

        extraFiles.forEach { file ->
            var candidatePath = file.relativePath
            if (mergedMap.containsKey(candidatePath) && mergedMap[candidatePath]?.uri != file.uri) {
                val base = candidatePath.substringBeforeLast('.')
                val ext = candidatePath.substringAfterLast('.', "")
                var counter = 2
                while (mergedMap.containsKey(candidatePath)) {
                    candidatePath = if (ext.isNotEmpty() && candidatePath.contains('.')) {
                        "${base}_$counter.$ext"
                    } else {
                        "${file.relativePath}_$counter"
                    }
                    counter++
                }
            }
            mergedMap[candidatePath] = file.copy(relativePath = candidatePath)
        }

        val sortedFiles = mergedMap.values.sortedBy { it.relativePath.lowercase(Locale.ROOT) }
        return existing.copy(
            files = sortedFiles,
            totalBytes = sortedFiles.sumOf { it.sizeBytes }
        )
    }

    fun mergeFolderIntoWorkspace(
        existing: ScannedFolder?,
        incoming: ScannedFolder
    ): ScannedFolder {
        if (existing == null) return incoming
        if (existing.treeUri == incoming.treeUri) return incoming

        val existingPrefixed = existing.directories.any { it == existing.rootName } ||
            existing.files.all { it.relativePath.startsWith("${existing.rootName}/") }

        val dirSet = LinkedHashSet<String>()
        val fileMap = LinkedHashMap<String, ScannedFile>()

        if (existingPrefixed || existing.rootName == "ملفات ومجلدات مختارة" || existing.rootName == "ملفات مختارة") {
            dirSet.addAll(existing.directories)
            existing.files.forEach { fileMap[it.relativePath] = it }
        } else {
            val basePrefix = existing.rootName
            dirSet.add(basePrefix)
            existing.directories.forEach { dirSet.add("$basePrefix/$it") }
            existing.files.forEach { f ->
                val p = "$basePrefix/${f.relativePath}"
                fileMap[p] = f.copy(relativePath = p)
            }
        }

        var incomingPrefix = incoming.rootName
        var idx = 2
        while (dirSet.contains(incomingPrefix)) {
            incomingPrefix = "${incoming.rootName}_$idx"
            idx++
        }
        dirSet.add(incomingPrefix)
        incoming.directories.forEach { dirSet.add("$incomingPrefix/$it") }
        incoming.files.forEach { f ->
            val p = "$incomingPrefix/${f.relativePath}"
            fileMap[p] = f.copy(relativePath = p)
        }

        val sortedDirs = dirSet.sortedBy { it.lowercase(Locale.ROOT) }
        val sortedFiles = fileMap.values.sortedBy { it.relativePath.lowercase(Locale.ROOT) }

        return existing.copy(
            rootName = "ملفات ومجلدات مختارة",
            files = sortedFiles,
            directories = sortedDirs,
            directoryCount = sortedDirs.size,
            skippedCount = existing.skippedCount + incoming.skippedCount,
            totalBytes = sortedFiles.sumOf { it.sizeBytes }
        )
    }

    private fun addAncestorDirectories(
        relDirPath: String,
        validDirectories: List<String>,
        target: MutableSet<String>
    ) {
        val validSet = validDirectories.toSet()
        var current = relDirPath
        while (current.isNotEmpty()) {
            if (validSet.contains(current)) {
                target.add(current)
            }
            current = current.substringBeforeLast('/', "")
        }
    }
}
