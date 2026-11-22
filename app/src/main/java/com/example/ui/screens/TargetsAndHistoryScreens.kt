package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedTargetEntity
import com.example.data.local.UploadHistoryEntity
import com.example.data.scanner.SafFolderScanner
import com.example.data.system.DefaultFilesAppManager
import com.example.model.DestinationType
import com.example.ui.components.DefaultFilesAppCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TargetsScreen(
    viewModel: MainViewModel,
    onTargetLoadedNavigateUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedTargets by viewModel.savedTargets.collectAsStateWithLifecycle()
    val defaultFilesStatus by viewModel.defaultFilesStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val systemPickerTestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshDefaultFilesStatus()
        val data = result.data
        val pickedCount = when {
            data?.clipData != null -> data.clipData?.itemCount ?: 0
            data?.data != null -> 1
            else -> 0
        }
        if (pickedCount > 0) {
            viewModel.notifyMessage("تمت تجربة منتقي النظام واستلام $pickedCount عنصر بنجاح")
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DefaultFilesAppCard(
                    status = defaultFilesStatus,
                    onTogglePickerHandler = viewModel::setFilePickerHandlerEnabled,
                    onToggleDocumentsProvider = viewModel::setDocumentsProviderEnabled,
                    onOpenSystemDefaultSettings = {
                        DefaultFilesAppManager.openDefaultAppsSettings(context).onFailure {
                            viewModel.notifyMessage("تعذر فتح إعدادات التطبيقات الافتراضية على هذا الجهاز")
                        }
                    },
                    onTestSystemPicker = {
                        runCatching {
                            systemPickerTestLauncher.launch(DefaultFilesAppManager.buildGetContentProbeIntent())
                        }.onFailure {
                            viewModel.notifyMessage("تعذر فتح منتقي الملفات في النظام")
                        }
                    }
                )
            }

            item {
                Text(
                    text = "الوجهات المحفوظة",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "احفظ إعدادات مستودعات GitHub ومجلدات Drive وخوادم الويب مع تشفير الرموز محلياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (savedTargets.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Bookmarks,
                        title = "لا توجد وجهات محفوظة بعد",
                        subtitle = "من تبويب رفع مجلد، اضغط على أيقونة الحفظ بجانب إعدادات GitHub أو Drive أو أي موقع لتثبيتها هنا."
                    )
                }
            } else {
                items(savedTargets, key = { it.id }) { target ->
                    SavedTargetCard(
                        target = target,
                        onUse = {
                            viewModel.applySavedTarget(target)
                            onTargetLoadedNavigateUpload()
                        },
                        onDelete = { viewModel.deleteSavedTarget(target) }
                    )
                }
            }

            item {
                QuickReferenceGuideCard()
            }
        }
    }
}

@Composable
private fun SavedTargetCard(
    target: SavedTargetEntity,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    val dest = runCatching { DestinationType.valueOf(target.destinationType) }
        .getOrDefault(DestinationType.GITHUB)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUse)
            .testTag("saved_target_${target.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = iconForDestination(dest),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dest.titleAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onUse,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("استخدام")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف الوجهة",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickReferenceGuideCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "دليل الربط السريع لكل وجهة",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            GuideItem(
                title = "GitHub (Commit واحد)",
                body = "أنشئ Personal Access Token بصلاحية Contents (Write)، وسيتم رفع جميع ملفات المجلد ومجلداته الفرعية في Commit واحد نظيف."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            GuideItem(
                title = "Google Drive (شجرة المجلدات)",
                body = "استخدم OAuth Access Token بنطاق drive.file لإنشاء المجلدات الفرعية تلقائياً ورفع الملفات بداخلها."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            GuideItem(
                title = "أي موقع أو استضافة (WebDAV / HTTP / ZIP)",
                body = "لخوادم Nextcloud و ownCloud و cPanel WebDAV اختر وضع WebDAV. ولمواقع الرفع العادية في المتصفح استخدم ضغط ZIP النظيف."
            )
        }
    }
}

@Composable
private fun GuideItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "سجل عمليات الرفع",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${history.size} عملية مسجلة محلياً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_history_btn")
                        ) {
                            Text("مسح السجل", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = "السجل فارغ",
                        subtitle = "ستظهر هنا جميع المجلدات التي قمت برفعها أو ضغطها مع روابطها وتفاصيلها."
                    )
                }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onCopyLink = { link ->
                            clipboard.setText(AnnotatedString(link))
                            onShowMessage("تم نسخ الرابط")
                        },
                        onOpenLink = { link ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }.onFailure {
                                onShowMessage("تعذر فتح الرابط")
                            }
                        },
                        onDelete = { viewModel.deleteHistoryItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: UploadHistoryEntity,
    onCopyLink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDelete: () -> Unit
) {
    val dest = runCatching { DestinationType.valueOf(item.destinationType) }
        .getOrDefault(DestinationType.GITHUB)
    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(item.timestamp))
    val (statusLabel, statusColor) = when (item.status) {
        "SUCCESS" -> "مكتمل" to EmeraldAccent
        "PARTIAL" -> "جزئي" to AmberWarning
        "CANCELLED" -> "ملغى" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "فشل" to MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = iconForDestination(dest),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = item.folderName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${dest.titleAr} • ${item.targetLabel}",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.fileCount} ملف • ${SafFolderScanner.formatBytes(item.totalBytes)} • $dateStr",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (!item.resultLink.isNullOrBlank()) {
                        IconButton(onClick = { onCopyLink(item.resultLink) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onOpenLink(item.resultLink) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح الرابط", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف السجل", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
