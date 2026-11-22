package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionCheckResult
import com.example.model.CustomWebConfig
import com.example.model.DriveConfig
import com.example.model.GitHubConfig
import com.example.model.WebUploadMode
import com.example.model.ZipConfig
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubConfigForm(
    config: GitHubConfig,
    onUpdate: ((GitHubConfig) -> GitHubConfig) -> Unit,
    connectionCheck: ConnectionCheckResult?,
    isChecking: Boolean,
    onVerify: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val ltrMonoStyle = TextStyle(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = config.repoInput,
            onValueChange = { value -> onUpdate { it.copy(repoInput = value) } },
            label = { Text("المستودع (owner/repo أو رابط GitHub)") },
            placeholder = { Text("username/my-project", style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_repo_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = config.branch,
                onValueChange = { value -> onUpdate { it.copy(branch = value) } },
                label = { Text("الفرع (Branch)") },
                placeholder = { Text("main", style = ltrMonoStyle) },
                singleLine = true,
                textStyle = ltrMonoStyle,
                modifier = Modifier
                    .weight(1f)
                    .testTag("github_branch_input")
            )
            OutlinedTextField(
                value = config.targetSubPath,
                onValueChange = { value -> onUpdate { it.copy(targetSubPath = value) } },
                label = { Text("مجلد فرعي (اختياري)") },
                placeholder = { Text("src/mobile", style = ltrMonoStyle) },
                singleLine = true,
                textStyle = ltrMonoStyle,
                modifier = Modifier
                    .weight(1f)
                    .testTag("github_subpath_input")
            )
        }

        if (connectionCheck != null && connectionCheck.availableBranches.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                connectionCheck.availableBranches.take(6).forEach { branchName ->
                    FilterChip(
                        selected = config.branch == branchName,
                        onClick = { onUpdate { it.copy(branch = branchName) } },
                        label = { Text(branchName, fontFamily = MonoFontFamily) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = config.commitMessage,
            onValueChange = { value -> onUpdate { it.copy(commitMessage = value) } },
            label = { Text("رسالة الـ Commit") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_commit_input")
        )

        OutlinedTextField(
            value = config.token,
            onValueChange = { value -> onUpdate { it.copy(token = value) } },
            label = { Text("GitHub Personal Access Token") },
            placeholder = { Text("ghp_... أو github_pat_...", style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إظهار أو إخفاء التوكن"
                        )
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "طريقة الحصول على GitHub Token"
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_token_input")
        )

        ConnectionVerifyRow(
            buttonLabel = "فحص المستودع والتوكن",
            connectionCheck = connectionCheck,
            isChecking = isChecking,
            onVerify = onVerify
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("كيف تحصل على GitHub Token؟") },
            text = {
                Text(
                    "1. افتح GitHub → Settings → Developer settings → Personal access tokens.\n" +
                        "2. أنشئ Fine-grained token أو Classic token.\n" +
                        "3. امنحه صلاحية Contents (Read & Write) للمستودع المطلوب.\n" +
                        "4. انسخ الرمز والصقه هنا — يُحفظ مشفراً داخل جهازك فقط."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("حسناً")
                }
            }
        )
    }
}

@Composable
fun DriveConfigForm(
    config: DriveConfig,
    onUpdate: ((DriveConfig) -> DriveConfig) -> Unit,
    connectionCheck: ConnectionCheckResult?,
    isChecking: Boolean,
    onVerify: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val ltrMonoStyle = TextStyle(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = config.accessToken,
            onValueChange = { value -> onUpdate { it.copy(accessToken = value) } },
            label = { Text("Google Drive OAuth Access Token") },
            placeholder = { Text("ya29.a0...", style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "إظهار أو إخفاء التوكن"
                        )
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "طريقة الحصول على Google Drive Token"
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drive_token_input")
        )

        OutlinedTextField(
            value = config.parentFolderId,
            onValueChange = { value -> onUpdate { it.copy(parentFolderId = value) } },
            label = { Text("معرّف المجلد الأب في Drive (اختياري)") },
            placeholder = { Text("اتركه فارغاً للرفع في My Drive", style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drive_parent_id_input")
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdate { it.copy(createRootFolder = !it.createRootFolder) } }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إنشاء مجلد رئيسي باسم المجلد",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "يحفظ جميع المجلدات الفرعية والملفات داخله تلقائياً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = config.createRootFolder,
                    onCheckedChange = { checked -> onUpdate { it.copy(createRootFolder = checked) } }
                )
            }
        }

        ConnectionVerifyRow(
            buttonLabel = "التحقق من حساب Drive",
            connectionCheck = connectionCheck,
            isChecking = isChecking,
            onVerify = onVerify
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("كيفية الحصول على رمز Google Drive") },
            text = {
                Text(
                    "لرفع المجلدات مباشرة إلى Google Drive عبر Drive API v3:\n\n" +
                        "1. افتح developers.google.com/oauthplayground.\n" +
                        "2. اختر Drive API v3 ثم النطاق:\nhttps://www.googleapis.com/auth/drive.file\n" +
                        "3. اضغط Authorize APIs ثم Exchange authorization code for tokens.\n" +
                        "4. انسخ Access token والصقه هنا."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("فهمت")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomWebConfigForm(
    config: CustomWebConfig,
    onUpdate: ((CustomWebConfig) -> CustomWebConfig) -> Unit,
    connectionCheck: ConnectionCheckResult?,
    isChecking: Boolean,
    onVerify: () -> Unit,
    onOpenWebBrowser: () -> Unit = {},
    installedBrowsersCount: Int = 0,
    onShowInstalledBrowsers: () -> Unit = {}
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }
    val ltrMonoStyle = TextStyle(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenWebBrowser,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("open_web_browser_btn")
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("متصفح الرفع")
            }
            OutlinedButton(
                onClick = onShowInstalledBrowsers,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("show_device_browsers_btn")
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("متصفحاتي ($installedBrowsersCount)")
            }
        }

        Text(
            text = "اختر بروتوكول الرفع للموقع أو الخادم:",
            style = MaterialTheme.typography.labelLarge
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WebUploadMode.entries.forEach { mode ->
                FilterChip(
                    selected = config.mode == mode,
                    onClick = { onUpdate { it.copy(mode = mode) } },
                    label = { Text(mode.labelAr) },
                    modifier = Modifier.testTag("web_mode_${mode.name}")
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = config.mode.descriptionAr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }

        OutlinedTextField(
            value = config.endpointUrl,
            onValueChange = { value -> onUpdate { it.copy(endpointUrl = value) } },
            label = {
                Text(
                    if (config.mode == WebUploadMode.WEBDAV_MIRROR)
                        "رابط مجلد WebDAV (مثل Nextcloud/NAS)"
                    else
                        "رابط نقطة الرفع (Endpoint URL)"
                )
            },
            placeholder = {
                Text(
                    if (config.mode == WebUploadMode.WEBDAV_MIRROR)
                        "https://cloud.example.com/remote.php/dav/files/user/"
                    else
                        "https://api.example.com/upload",
                    style = ltrMonoStyle
                )
            },
            singleLine = true,
            textStyle = ltrMonoStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("web_endpoint_input")
        )

        OutlinedTextField(
            value = config.authHeaderValue,
            onValueChange = { value -> onUpdate { it.copy(authHeaderValue = value) } },
            label = { Text("ترويسة المصادقة (${config.authHeaderName}) — اختياري") },
            placeholder = { Text("Bearer token... أو Basic ...", style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(
                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "إظهار أو إخفاء الترويسة"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("web_auth_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "إعدادات متقدمة (أسماء الحقول / HTTP المحلي)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = config.fileFieldName,
                        onValueChange = { v -> onUpdate { it.copy(fileFieldName = v) } },
                        label = { Text("اسم حقل الملف") },
                        singleLine = true,
                        textStyle = ltrMonoStyle,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = config.pathFieldName,
                        onValueChange = { v -> onUpdate { it.copy(pathFieldName = v) } },
                        label = { Text("اسم حقل المسار") },
                        singleLine = true,
                        textStyle = ltrMonoStyle,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = config.authHeaderName,
                    onValueChange = { v -> onUpdate { it.copy(authHeaderName = v) } },
                    label = { Text("اسم Header المصادقة") },
                    singleLine = true,
                    textStyle = ltrMonoStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "السماح بـ http:// للشبكة المحلية (LAN)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = config.allowCleartextHttp,
                        onCheckedChange = { c -> onUpdate { it.copy(allowCleartextHttp = c) } }
                    )
                }
            }
        }

        ConnectionVerifyRow(
            buttonLabel = "اختبار استجابة الخادم",
            connectionCheck = connectionCheck,
            isChecking = isChecking,
            onVerify = onVerify
        )
    }
}

@Composable
fun ZipExportConfigForm(
    config: ZipConfig,
    defaultFolderName: String,
    onUpdate: ((ZipConfig) -> ZipConfig) -> Unit
) {
    val ltrMonoStyle = TextStyle(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = config.archiveName,
            onValueChange = { v -> onUpdate { it.copy(archiveName = v) } },
            label = { Text("اسم ملف الأرشيف (بدون .zip)") },
            placeholder = { Text(defaultFolderName.ifBlank { "my-folder" }, style = ltrMonoStyle) },
            singleLine = true,
            textStyle = ltrMonoStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("zip_name_input")
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "الحل الذكي لأي موقع لا يقبل رفع المجلدات من متصفح الجوال (مثل cPanel أو Netlify أو WordPress): " +
                    "اضغط المجلد في ملف ZIP نظيف مع استبعاد node_modules و .git ثم شاركه أو ارفعه مباشرة.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ConnectionVerifyRow(
    buttonLabel: String,
    connectionCheck: ConnectionCheckResult?,
    isChecking: Boolean,
    onVerify: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onVerify,
            enabled = !isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("verify_connection_btn")
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جاري الفحص...")
            } else {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }

        AnimatedVisibility(visible = connectionCheck != null) {
            connectionCheck?.let { res ->
                Surface(
                    color = if (res.isSuccess) {
                        EmeraldAccent.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (res.isSuccess) EmeraldAccent else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (res.isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
