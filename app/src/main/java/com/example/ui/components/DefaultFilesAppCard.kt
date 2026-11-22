package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.system.DefaultFilesStatus
import com.example.ui.theme.EmeraldAccent

@Composable
fun DefaultFilesAppCard(
    status: DefaultFilesStatus,
    onTogglePickerHandler: (Boolean) -> Unit,
    onToggleDocumentsProvider: (Boolean) -> Unit,
    onOpenSystemDefaultSettings: () -> Unit,
    onTestSystemPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (status.isDefaultGetContentApp) 1.5.dp else 1.dp,
            color = if (status.isDefaultGetContentApp) {
                EmeraldAccent.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("default_files_app_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Surface(
                        color = if (status.isDefaultGetContentApp) {
                            EmeraldAccent.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (status.isDefaultGetContentApp) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.FolderCopy
                            },
                            contentDescription = null,
                            tint = if (status.isDefaultGetContentApp) {
                                EmeraldAccent
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "تطبيق الملفات الافتراضي في الموبايل",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (status.isDefaultGetContentApp) {
                                "FolderPush مفعّل حالياً كمنتقي الملفات الافتراضي"
                            } else {
                                "الافتراضي الحالي: ${status.currentDefaultAppLabel ?: "منتقي النظام"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.isDefaultGetContentApp) {
                                EmeraldAccent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Toggle 1: System GET_CONTENT Handler
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePickerHandler(!status.isPickerComponentEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تفعيل كخيار اختيار ملفات للنظام والمتصفحات",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "يُظهر FolderPush عند رفع ملفات من Chrome أو أي تطبيق لتختار مجلداً كاملاً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = status.isPickerComponentEnabled,
                        onCheckedChange = onTogglePickerHandler,
                        modifier = Modifier.testTag("toggle_default_picker_switch")
                    )
                }
            }

            // Toggle 2: System DocumentsProvider Root
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDocumentsProvider(!status.isDocumentsProviderEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "إظهار FolderPush داخل تطبيق الملفات (SAF Provider)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "يضيف FolderPush كمصدر ملفات داخل قائمة ملفات أندرويد الرسمية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = status.isDocumentsProviderEnabled,
                        onCheckedChange = onToggleDocumentsProvider,
                        modifier = Modifier.testTag("toggle_docs_provider_switch")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSystemDefaultSettings,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_default_apps_settings_btn")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تعيين كافتراضي بالنظام")
                }

                OutlinedButton(
                    onClick = onTestSystemPicker,
                    enabled = status.isPickerComponentEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("test_system_picker_btn")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("اختبار منتقي النظام")
                }
            }

            Text(
                text = "ملاحظة: لتعيين FolderPush كافتراضي في أندرويد، اضغط \"اختبار منتقي النظام\" أو افتح رفع ملف في أي متصفح ثم اختر FolderPush ← \"دوماً (Always)\"، أو عيّنه من إعدادات التطبيقات الافتراضية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultFilesAppSheet(
    status: DefaultFilesStatus,
    onTogglePickerHandler: (Boolean) -> Unit,
    onToggleDocumentsProvider: (Boolean) -> Unit,
    onOpenSystemDefaultSettings: () -> Unit,
    onTestSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("default_files_app_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "إعدادات تطبيق الملفات الافتراضي",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            DefaultFilesAppCard(
                status = status,
                onTogglePickerHandler = onTogglePickerHandler,
                onToggleDocumentsProvider = onToggleDocumentsProvider,
                onOpenSystemDefaultSettings = onOpenSystemDefaultSettings,
                onTestSystemPicker = onTestSystemPicker
            )
        }
    }
}
