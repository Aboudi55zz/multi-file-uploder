package com.example.model

import android.provider.DocumentsContract
import java.util.Locale

enum class FileCategory(
    val labelAr: String,
    val shortBadge: String
) {
    CODE(labelAr = "برمجة ونصوص", shortBadge = "CODE"),
    CONFIG_DATA(labelAr = "إعدادات وبيانات", shortBadge = "DATA"),
    DOCUMENT(labelAr = "مستندات وكتب", shortBadge = "DOC"),
    IMAGE(labelAr = "صور وتصميم", shortBadge = "IMG"),
    AUDIO(labelAr = "صوتيات", shortBadge = "AUDIO"),
    VIDEO(labelAr = "فيديو", shortBadge = "VIDEO"),
    ARCHIVE_PACKAGE(labelAr = "أرشيف وحزم", shortBadge = "PKG"),
    FONT_3D_BINARY(labelAr = "خطوط و3D وثنائي", shortBadge = "BIN"),
    OTHER(labelAr = "ملفات أخرى", shortBadge = "FILE")
}

object FileTypeRegistry {

    private val extensionToMime: Map<String, String> = mapOf(
        // Code & Scripts
        "kt" to "text/x-kotlin",
        "kts" to "text/x-kotlin",
        "java" to "text/x-java-source",
        "py" to "text/x-python",
        "js" to "application/javascript",
        "mjs" to "application/javascript",
        "cjs" to "application/javascript",
        "ts" to "application/typescript",
        "tsx" to "application/typescript",
        "jsx" to "text/jsx",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "scss" to "text/x-scss",
        "sass" to "text/x-sass",
        "less" to "text/x-less",
        "c" to "text/x-c",
        "h" to "text/x-c",
        "cpp" to "text/x-c++src",
        "cc" to "text/x-c++src",
        "cxx" to "text/x-c++src",
        "hpp" to "text/x-c++hdr",
        "cs" to "text/x-csharp",
        "go" to "text/x-go",
        "rs" to "text/x-rust",
        "swift" to "text/x-swift",
        "dart" to "text/x-dart",
        "php" to "application/x-httpd-php",
        "rb" to "application/x-ruby",
        "sh" to "application/x-sh",
        "bash" to "application/x-sh",
        "zsh" to "application/x-sh",
        "bat" to "application/x-bat",
        "ps1" to "text/plain",
        "sql" to "application/sql",
        "graphql" to "application/graphql",
        "gql" to "application/graphql",
        "proto" to "text/plain",
        "vue" to "text/plain",
        "svelte" to "text/plain",
        "astro" to "text/plain",
        "r" to "text/plain",
        "lua" to "text/x-lua",
        "pl" to "text/x-perl",
        "scala" to "text/x-scala",
        "groovy" to "text/x-groovy",
        "gradle" to "text/plain",
        "cmake" to "text/plain",

        // Config, Structured Data & Databases
        "json" to "application/json",
        "json5" to "application/json",
        "jsonc" to "application/json",
        "xml" to "application/xml",
        "yaml" to "application/x-yaml",
        "yml" to "application/x-yaml",
        "toml" to "application/toml",
        "ini" to "text/plain",
        "cfg" to "text/plain",
        "conf" to "text/plain",
        "properties" to "text/plain",
        "env" to "text/plain",
        "gitignore" to "text/plain",
        "gitattributes" to "text/plain",
        "editorconfig" to "text/plain",
        "dockerignore" to "text/plain",
        "npmrc" to "text/plain",
        "csv" to "text/csv",
        "tsv" to "text/tab-separated-values",
        "db" to "application/vnd.sqlite3",
        "sqlite" to "application/vnd.sqlite3",
        "sqlite3" to "application/vnd.sqlite3",
        "parquet" to "application/octet-stream",
        "avro" to "application/octet-stream",
        "lock" to "text/plain",
        "log" to "text/plain",

        // Documents & E-Books
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "rtf" to "application/rtf",
        "txt" to "text/plain",
        "md" to "text/markdown",
        "mdx" to "text/markdown",
        "tex" to "application/x-tex",
        "epub" to "application/epub+zip",
        "mobi" to "application/x-mobipocket-ebook",
        "azw3" to "application/octet-stream",

        // Images & Vector/Design
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "tiff" to "image/tiff",
        "tif" to "image/tiff",
        "dng" to "image/x-adobe-dng",
        "raw" to "image/x-dcraw",
        "psd" to "image/vnd.adobe.photoshop",
        "ai" to "application/postscript",
        "eps" to "application/postscript",
        "fig" to "application/octet-stream",
        "sketch" to "application/octet-stream",
        "xd" to "application/octet-stream",

        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "m4a" to "audio/mp4",
        "wma" to "audio/x-ms-wma",
        "amr" to "audio/amr",
        "mid" to "audio/midi",
        "midi" to "audio/midi",

        // Video
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "flv" to "video/x-flv",
        "wmv" to "video/x-ms-wmv",
        "m4v" to "video/x-m4v",
        "3gp" to "video/3gpp",

        // Archives & Installable Packages
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "tgz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "zst" to "application/zstd",
        "apk" to "application/vnd.android.package-archive",
        "aab" to "application/octet-stream",
        "xapk" to "application/zip",
        "ipa" to "application/octet-stream",
        "jar" to "application/java-archive",
        "war" to "application/java-archive",
        "aar" to "application/zip",
        "deb" to "application/vnd.debian.binary-package",
        "rpm" to "application/x-rpm",
        "exe" to "application/vnd.microsoft.portable-executable",
        "msi" to "application/x-msi",
        "dmg" to "application/x-apple-diskimage",
        "iso" to "application/x-iso9660-image",
        "img" to "application/octet-stream",

        // Fonts, 3D, Certificates & Binaries
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "obj" to "model/obj",
        "stl" to "model/stl",
        "glb" to "model/gltf-binary",
        "gltf" to "model/gltf+json",
        "fbx" to "application/octet-stream",
        "blend" to "application/octet-stream",
        "wasm" to "application/wasm",
        "so" to "application/octet-stream",
        "dll" to "application/octet-stream",
        "bin" to "application/octet-stream",
        "dat" to "application/octet-stream",
        "pem" to "application/x-pem-file",
        "crt" to "application/x-x509-ca-cert",
        "cer" to "application/pkix-cert",
        "key" to "application/octet-stream",
        "p12" to "application/x-pkcs12",
        "jks" to "application/octet-stream",
        "keystore" to "application/octet-stream"
    )

    private val specialFileNamesToMime: Map<String, String> = mapOf(
        "makefile" to "text/x-makefile",
        "dockerfile" to "text/x-dockerfile",
        "jenkinsfile" to "text/x-groovy",
        "procfile" to "text/plain",
        "gradlew" to "text/x-shellscript",
        "license" to "text/plain",
        "readme" to "text/plain",
        "changelog" to "text/plain",
        "authors" to "text/plain",
        ".gitignore" to "text/plain",
        ".gitattributes" to "text/plain",
        ".gitmodules" to "text/plain",
        ".editorconfig" to "text/plain",
        ".dockerignore" to "text/plain",
        ".env" to "text/plain",
        ".env.example" to "text/plain",
        ".env.local" to "text/plain",
        ".htaccess" to "text/plain",
        ".npmrc" to "text/plain",
        ".prettierrc" to "application/json",
        ".eslintrc" to "application/json",
        ".babelrc" to "application/json",
        ".nomedia" to "application/octet-stream"
    )

    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "html", "htm", "css", "scss", "sass", "less", "c", "cpp", "cc", "cxx",
        "h", "hpp", "cs", "go", "rs", "swift", "dart", "php", "rb", "sh",
        "bash", "zsh", "bat", "ps1", "sql", "graphql", "gql", "proto", "vue",
        "svelte", "astro", "r", "lua", "pl", "scala", "groovy", "gradle", "cmake"
    )

    private val configDataExtensions = setOf(
        "json", "json5", "jsonc", "xml", "yaml", "yml", "toml", "ini", "cfg",
        "conf", "properties", "env", "gitignore", "gitattributes", "editorconfig",
        "dockerignore", "npmrc", "csv", "tsv", "db", "sqlite", "sqlite3",
        "parquet", "avro", "lock", "log"
    )

    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
        "rtf", "txt", "md", "mdx", "tex", "epub", "mobi", "azw3"
    )

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico", "heic", "heif",
        "avif", "tiff", "tif", "dng", "raw", "psd", "ai", "eps", "fig", "sketch", "xd"
    )

    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "aac", "ogg", "opus", "m4a", "wma", "amr", "mid", "midi"
    )

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "flv", "wmv", "m4v", "3gp"
    )

    private val archivePackageExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "apk", "aab",
        "xapk", "ipa", "jar", "war", "aar", "deb", "rpm", "exe", "msi", "dmg",
        "iso", "img"
    )

    private val font3dBinaryExtensions = setOf(
        "ttf", "otf", "woff", "woff2", "obj", "stl", "glb", "gltf", "fbx", "dae",
        "3ds", "blend", "wasm", "so", "dll", "dylib", "bin", "dat", "pem", "crt",
        "cer", "key", "p12", "jks", "keystore"
    )

    fun isDirectoryMime(rawMime: String?, flags: Int = 0): Boolean {
        val normalized = rawMime?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized == DocumentsContract.Document.MIME_TYPE_DIR.lowercase(Locale.ROOT) ||
            normalized == "inode/directory" ||
            normalized == "resource/folder" ||
            normalized == "application/x-directory"
        ) {
            return true
        }
        return (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0 && normalized.isEmpty()
    }

    fun resolveMimeType(
        fileName: String,
        providerMime: String?,
        systemLookup: ((String) -> String?)? = null
    ): String {
        val cleanProvider = providerMime?.trim().orEmpty()
        if (cleanProvider.isNotEmpty() &&
            !cleanProvider.equals("application/octet-stream", ignoreCase = true) &&
            !cleanProvider.equals("binary/octet-stream", ignoreCase = true)
        ) {
            return cleanProvider
        }

        val lowerName = fileName.trim().lowercase(Locale.ROOT)
        specialFileNamesToMime[lowerName]?.let { return it }

        val ext = extractExtension(fileName)
        if (ext.isNotEmpty()) {
            extensionToMime[ext]?.let { return it }
            systemLookup?.invoke(ext)?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return "application/octet-stream"
    }

    fun classify(fileName: String, mimeType: String? = null): FileCategory {
        val lowerName = fileName.trim().lowercase(Locale.ROOT)
        val ext = extractExtension(fileName)

        when {
            lowerName in setOf("makefile", "dockerfile", "jenkinsfile", "procfile", "gradlew") -> return FileCategory.CODE
            lowerName in setOf(".gitignore", ".gitattributes", ".gitmodules", ".editorconfig", ".dockerignore", ".env", ".env.example", ".env.local", ".htaccess", ".npmrc", ".prettierrc", ".eslintrc", ".babelrc") -> return FileCategory.CONFIG_DATA
            lowerName in setOf("license", "readme", "changelog", "authors") -> return FileCategory.DOCUMENT
            ext in codeExtensions -> return FileCategory.CODE
            ext in configDataExtensions -> return FileCategory.CONFIG_DATA
            ext in documentExtensions -> return FileCategory.DOCUMENT
            ext in imageExtensions -> return FileCategory.IMAGE
            ext in audioExtensions -> return FileCategory.AUDIO
            ext in videoExtensions -> return FileCategory.VIDEO
            ext in archivePackageExtensions -> return FileCategory.ARCHIVE_PACKAGE
            ext in font3dBinaryExtensions -> return FileCategory.FONT_3D_BINARY
        }

        val lowerMime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            lowerMime.startsWith("image/") -> FileCategory.IMAGE
            lowerMime.startsWith("audio/") -> FileCategory.AUDIO
            lowerMime.startsWith("video/") -> FileCategory.VIDEO
            lowerMime == "application/pdf" ||
                lowerMime.contains("word") ||
                lowerMime.contains("sheet") ||
                lowerMime.contains("presentation") ||
                lowerMime == "text/markdown" -> FileCategory.DOCUMENT
            lowerMime.contains("json") ||
                lowerMime.contains("yaml") ||
                lowerMime.contains("xml") ||
                lowerMime.contains("sqlite") ||
                lowerMime == "text/csv" -> FileCategory.CONFIG_DATA
            lowerMime.contains("zip") ||
                lowerMime.contains("tar") ||
                lowerMime.contains("rar") ||
                lowerMime.contains("7z") ||
                lowerMime.contains("gzip") ||
                lowerMime.contains("android.package-archive") -> FileCategory.ARCHIVE_PACKAGE
            lowerMime.startsWith("font/") -> FileCategory.FONT_3D_BINARY
            lowerMime.startsWith("text/") -> FileCategory.CODE
            else -> FileCategory.OTHER
        }
    }

    fun shortTypeBadge(fileName: String, mimeType: String? = null): String {
        val ext = extractExtension(fileName)
        if (ext.isNotEmpty() && ext.length <= 6) {
            return ext.uppercase(Locale.ROOT)
        }
        return classify(fileName, mimeType).shortBadge
    }

    fun summarizeCategories(files: List<ScannedFile>): Map<FileCategory, Int> {
        return summarizeFileEntries(files.map { it.name to it.mimeType })
    }

    fun summarizeFileEntries(entries: List<Pair<String, String?>>): Map<FileCategory, Int> {
        val counts = LinkedHashMap<FileCategory, Int>()
        for ((name, mime) in entries) {
            val cat = classify(name, mime)
            counts[cat] = (counts[cat] ?: 0) + 1
        }
        return counts
    }

    fun extractExtension(fileName: String): String {
        val trimmed = fileName.trim()
        val lastDot = trimmed.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == trimmed.length - 1) return ""
        return trimmed.substring(lastDot + 1).lowercase(Locale.ROOT)
    }
}
