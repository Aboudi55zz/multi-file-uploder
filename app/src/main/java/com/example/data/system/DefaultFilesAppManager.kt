package com.example.data.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.FilePickerBridgeActivity
import com.example.data.provider.FolderPushDocumentsProvider

data class DefaultFilesStatus(
    val isPickerComponentEnabled: Boolean,
    val isDefaultGetContentApp: Boolean,
    val isDocumentsProviderEnabled: Boolean,
    val currentDefaultAppLabel: String?
)

object DefaultFilesAppManager {

    fun getStatus(context: Context): DefaultFilesStatus {
        val pm = context.packageManager
        val pickerComp = ComponentName(context, FilePickerBridgeActivity::class.java)
        val providerComp = ComponentName(context, FolderPushDocumentsProvider::class.java)

        val pickerState = pm.getComponentEnabledSetting(pickerComp)
        val isPickerEnabled = pickerState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val providerState = pm.getComponentEnabledSetting(providerComp)
        val isProviderEnabled = providerState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                getContentIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(getContentIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val resolvedPkg = resolved?.activityInfo?.packageName
        val isDefault = resolvedPkg == context.packageName
        val defaultLabel = resolved?.let {
            runCatching { it.loadLabel(pm).toString() }.getOrNull()
        }

        return DefaultFilesStatus(
            isPickerComponentEnabled = isPickerEnabled,
            isDefaultGetContentApp = isDefault,
            isDocumentsProviderEnabled = isProviderEnabled,
            currentDefaultAppLabel = defaultLabel
        )
    }

    fun setFilePickerHandlerEnabled(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(
            ComponentName(context, FilePickerBridgeActivity::class.java),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    fun setDocumentsProviderEnabled(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(
            ComponentName(context, FolderPushDocumentsProvider::class.java),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    fun openDefaultAppsSettings(context: Context): Result<Unit> {
        return runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.recoverCatching {
            openAppDetailsSettings(context).getOrThrow()
        }
    }

    fun openAppDetailsSettings(context: Context): Result<Unit> {
        return runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun buildGetContentProbeIntent(): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
    }
}
