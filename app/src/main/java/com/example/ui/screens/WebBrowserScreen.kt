package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Switch
import com.example.data.browser.BrowserDiscoveryHelper
import com.example.model.WebBrowserUploadPolicy
import com.example.ui.components.InstalledBrowsersSheet
import com.example.ui.components.rememberSafFolderPicker
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object WebBrowserUtils {
    val presetSites: List<Pair<String, String>> = listOf(
        "ChatGPT" to "https://chatgpt.com",
        "Claude" to "https://claude.ai",
        "Netlify Drop" to "https://app.netlify.com/drop",
        "GitHub" to "https://github.com",
        "Google Drive" to "https://drive.google.com",
        "File.io" to "https://www.file.io"
    )

    fun normalizeWebUrl(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال رابط الموقع"))
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("data:") ||
            lower.startsWith("content:")
        ) {
            return Result.failure(IllegalArgumentException("بروتوكول الرابط غير مسموح به لأسباب أمنية"))
        }
        val withScheme = if (lower.startsWith("http://") || lower.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("صيغة الرابط غير صالحة"))
        return Result.success(parsed.toString())
    }

    fun extractFirstHttpUrl(rawText: String): String? {
        val regex = Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)
        val match = regex.find(rawText)?.value ?: return null
        return normalizeWebUrl(match).getOrNull()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBrowserScreen(
    viewModel: MainViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val installedBrowsers by viewModel.installedBrowsers.collectAsStateWithLifecycle()
    val sharedTargetUrl by viewModel.browserTargetUrl.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf(sharedTargetUrl) }
    var currentLoadedUrl by remember { mutableStateOf(sharedTargetUrl) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var uploadPolicy by remember { mutableStateOf(WebBrowserUploadPolicy.ASK_EACH_TIME) }
    var isPackingZip by remember { mutableStateOf(false) }
    var stagingStatusMessage by remember { mutableStateOf<String?>(null) }
    var prefixFolderNamesForAi by remember { mutableStateOf(true) }
    var showBrowsersSheet by remember { mutableStateOf(false) }

    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var showChooserDialog by remember { mutableStateOf(false) }
    var chooserAcceptsMultiple by remember { mutableStateOf(true) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(sharedTargetUrl) {
        if (sharedTargetUrl.isNotBlank() && sharedTargetUrl != currentLoadedUrl) {
            urlInput = sharedTargetUrl
            currentLoadedUrl = sharedTargetUrl
            webViewRef?.loadUrl(sharedTargetUrl)
        }
    }

    val safFolderPicker = rememberSafFolderPicker(
        onFolderSelected = { uri ->
            viewModel.onFolderPicked(uri)
            onShowMessage("تم تجهيز المجلد للرفع عبر المتصفح")
        },
        onPickerError = onShowMessage
    )

    val mergeFolderPicker = rememberSafFolderPicker(
        onFolderSelected = { uri ->
            viewModel.onAdditionalFolderPicked(uri)
            onShowMessage("تم دمج المجلد الإضافي مع الملفات الجاهزة")
        },
        onPickerError = onShowMessage
    )

    val addExtraFilesToWorkspaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onExtraFilesPicked(uris)
        }
    }

    val manualMultipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val cb = pendingFileCallback
        pendingFileCallback = null
        if (uris.isNotEmpty()) {
            cb?.onReceiveValue(uris.toTypedArray())
        } else {
            cb?.onReceiveValue(null)
        }
    }

    fun deliverZipToWebView(callback: ValueCallback<Array<Uri>>) {
        scope.launch {
            isPackingZip = true
            stagingStatusMessage = "جاري ضغط المجلد والمجلدات الفرعية في ZIP لإرساله للموقع..."
            val res = viewModel.packSelectedFolderToTempZip()
            isPackingZip = false
            stagingStatusMessage = null
            res.onSuccess { zipFile ->
                val zipUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                callback.onReceiveValue(arrayOf(zipUri))
                onShowMessage("تم ضغط المجلد والمجلدات الفرعية في ${zipFile.name} وإرساله للموقع")
            }.onFailure { err ->
                callback.onReceiveValue(null)
                onShowMessage("فشل ضغط المجلد: ${err.message}")
            }
        }
    }

    fun deliverAllFolderFilesToWebView(
        callback: ValueCallback<Array<Uri>>,
        prefixFolderNames: Boolean = prefixFolderNamesForAi
    ) {
        val folder = viewModel.getEffectiveSelectedFolder()
        if (folder == null || folder.files.isEmpty()) {
            callback.onReceiveValue(null)
            onShowMessage("المجلد لا يحتوي على ملفات محددة لإرسالها")
            return
        }
        if (prefixFolderNames) {
            scope.launch {
                isPackingZip = true
                stagingStatusMessage = "جاري تجهيز ${folder.files.size} ملف بأسماء تحفظ مسار المجلدات..."
                val res = viewModel.prepareFilesForAiShare(prefixFolderNames = true)
                isPackingZip = false
                stagingStatusMessage = null
                res.onSuccess { stagedUris ->
                    callback.onReceiveValue(stagedUris.toTypedArray())
                    onShowMessage("تم حقن ${stagedUris.size} ملف مع حفظ أسماء المجلدات الفرعية في الموقع")
                }.onFailure { err ->
                    callback.onReceiveValue(null)
                    onShowMessage("تعذر تجهيز الملفات: ${err.message}")
                }
            }
        } else {
            callback.onReceiveValue(folder.files.map { it.uri }.toTypedArray())
            onShowMessage("تم حقن ${folder.files.size} ملف من المجلد ومجلداته الفرعية في الموقع")
        }
    }

    fun navigateToUrl(raw: String) {
        focusManager.clearFocus()
        WebBrowserUtils.normalizeWebUrl(raw).onSuccess { validUrl ->
            urlInput = validUrl
            currentLoadedUrl = validUrl
            viewModel.setBrowserTargetUrl(validUrl)
            webViewRef?.loadUrl(validUrl)
        }.onFailure { err ->
            onShowMessage(err.message ?: "رابط غير صالح")
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            webViewRef?.destroy()
        }
    }

    val ltrMonoStyle = TextStyle(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr)

    Column(modifier = modifier.fillMaxSize()) {
        // Active Folder Banner + Upload Policy
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = selectedFolder?.rootName ?: "لم يُختر مجلد بعد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = selectedFolder?.let {
                                    "${it.files.size} ملف • ${it.directoryCount} مجلد فرعي جاهز للموقع"
                                } ?: "اختر مجلداً ليتم حقنه عند الضغط على زر الرفع في أي موقع",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { safFolderPicker.launch(selectedFolder?.treeUri) },
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("browser_pick_folder_btn")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (selectedFolder == null) "اختيار مجلد" else "تغيير")
                    }
                }

                // URL Input Bar + Nav Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "تقدم")
                    }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = ltrMonoStyle,
                        placeholder = { Text("https://example.com", style = ltrMonoStyle) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { navigateToUrl(urlInput) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_url_input")
                    )
                    IconButton(
                        onClick = {
                            if (urlInput == currentLoadedUrl) {
                                webViewRef?.reload()
                            } else {
                                navigateToUrl(urlInput)
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("browser_go_btn")
                    ) {
                        Icon(
                            imageVector = if (urlInput == currentLoadedUrl) Icons.Default.Refresh else Icons.Default.Language,
                            contentDescription = "فتح الرابط"
                        )
                    }
                }

                // Quick Preset Websites + Device Browsers + Injection Policy
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = {
                                viewModel.refreshInstalledBrowsers()
                                showBrowsersSheet = true
                            },
                            label = { Text("متصفحات الجوال (${installedBrowsers.size})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("open_installed_browsers_chip")
                        )
                    }
                    items(WebBrowserUtils.presetSites) { (label, siteUrl) ->
                        AssistChip(
                            onClick = { navigateToUrl(siteUrl) },
                            label = { Text(label) }
                        )
                    }
                    items(WebBrowserUploadPolicy.entries) { policy ->
                        FilterChip(
                            selected = uploadPolicy == policy,
                            onClick = { uploadPolicy = policy },
                            label = { Text(policy.labelAr) }
                        )
                    }
                }

                if (isLoadingPage || isPackingZip) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { if (isPackingZip) 0.5f else (loadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isPackingZip) {
                            Text(
                                text = stagingStatusMessage ?: "جاري تجهيز المجلد والمجلدات الفرعية للموقع...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Embedded WebView with SAF Folder Interception
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = true
                        settings.setSupportMultipleWindows(false)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val scheme = request?.url?.scheme?.lowercase() ?: return false
                                return scheme != "http" && scheme != "https"
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoadingPage = true
                                url?.let {
                                    urlInput = it
                                    currentLoadedUrl = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoadingPage = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress >= 100) isLoadingPage = false
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                if (filePathCallback == null) return false
                                pendingFileCallback?.onReceiveValue(null)

                                val multiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                                val folder = selectedFolder

                                if (folder == null) {
                                    pendingFileCallback = filePathCallback
                                    chooserAcceptsMultiple = multiple
                                    showChooserDialog = true
                                    return true
                                }

                                when (uploadPolicy) {
                                    WebBrowserUploadPolicy.AUTO_PACK_ZIP -> {
                                        deliverZipToWebView(filePathCallback)
                                    }
                                    WebBrowserUploadPolicy.INJECT_ALL_FILES -> {
                                        if (multiple) {
                                            deliverAllFolderFilesToWebView(filePathCallback)
                                        } else {
                                            pendingFileCallback = filePathCallback
                                            chooserAcceptsMultiple = false
                                            showChooserDialog = true
                                        }
                                    }
                                    WebBrowserUploadPolicy.ASK_EACH_TIME -> {
                                        pendingFileCallback = filePathCallback
                                        chooserAcceptsMultiple = multiple
                                        showChooserDialog = true
                                    }
                                }
                                return true
                            }
                        }

                        loadUrl(currentLoadedUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("web_browser_webview")
            )

            if (isPackingZip) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text("جاري تجهيز المجلد للموقع...")
                    }
                }
            }
        }
    }

    if (showChooserDialog) {
        val folder = viewModel.getEffectiveSelectedFolder() ?: selectedFolder
        AlertDialog(
            onDismissRequest = {
                showChooserDialog = false
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = null
            },
            title = { Text("رفع مجلدات وملفات إلى الموقع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (folder != null) {
                        Text(
                            text = "طلب الموقع اختيار ملفات. الجاهز الآن: ${folder.rootName} (${folder.files.size} ملف و ${folder.directoryCount} مجلد فرعي).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (chooserAcceptsMultiple) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "تضمين مسار المجلد في اسم كل ملف (مناسب لـ ChatGPT و Claude)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Switch(
                                    checked = prefixFolderNamesForAi,
                                    onCheckedChange = { prefixFolderNamesForAi = it },
                                    modifier = Modifier.testTag("webview_prefix_folder_switch")
                                )
                            }
                            Button(
                                onClick = {
                                    val cb = pendingFileCallback
                                    pendingFileCallback = null
                                    showChooserDialog = false
                                    if (cb != null) {
                                        deliverAllFolderFilesToWebView(cb, prefixFolderNamesForAi)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("webview_inject_all_btn")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("إرسال كل الملفات دفعة واحدة (${folder.files.size} ملف)")
                            }
                        }
                        Button(
                            onClick = {
                                val cb = pendingFileCallback
                                pendingFileCallback = null
                                showChooserDialog = false
                                if (cb != null) deliverZipToWebView(cb)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webview_inject_zip_btn")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ضغط المجلد والمجلدات كـ ZIP وإرساله")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { mergeFolderPicker.launch(folder.treeUri) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("دمج مجلد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { addExtraFilesToWorkspaceLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إضافة ملفات", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Text(
                            text = "لم تختر مجلداً بعد. اختر مجلداً (أو ملفات) لدمجها وحقنها في ChatGPT أو Claude أو أي موقع.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                safFolderPicker.launch(null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار مجلد عبر SAF")
                        }
                        OutlinedButton(
                            onClick = {
                                addExtraFilesToWorkspaceLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إضافة ملفات متعددة للرفع معاً")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showChooserDialog = false
                            manualMultipleFilesLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فتح منتقي النظام مباشرة")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showChooserDialog = false
                        pendingFileCallback?.onReceiveValue(null)
                        pendingFileCallback = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showBrowsersSheet) {
        InstalledBrowsersSheet(
            browsers = installedBrowsers,
            targetUrl = currentLoadedUrl,
            hasSelectedFolder = selectedFolder != null,
            onRefresh = { viewModel.refreshInstalledBrowsers() },
            onOpenUrlInBrowser = { browser ->
                showBrowsersSheet = false
                BrowserDiscoveryHelper.openUrlInBrowser(context, currentLoadedUrl, browser.packageName)
                    .onFailure { err -> onShowMessage("تعذر فتح ${browser.appName}: ${err.message}") }
            },
            onPackZipAndOpenBrowser = { browser ->
                showBrowsersSheet = false
                scope.launch {
                    isPackingZip = true
                    val res = viewModel.packSelectedFolderToTempZip()
                    isPackingZip = false
                    res.onSuccess { zipFile ->
                        onShowMessage("تم تجهيز ${zipFile.name} — جاري فتح ${browser.appName}")
                        BrowserDiscoveryHelper.openUrlInBrowser(context, currentLoadedUrl, browser.packageName)
                    }.onFailure { err ->
                        onShowMessage("فشل ضغط المجلد: ${err.message}")
                    }
                }
            },
            onDismiss = { showBrowsersSheet = false }
        )
    }
}
