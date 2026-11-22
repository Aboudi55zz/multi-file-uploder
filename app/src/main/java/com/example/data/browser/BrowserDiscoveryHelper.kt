package com.example.data.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.example.ui.screens.WebBrowserUtils

data class InstalledBrowser(
    val packageName: String,
    val appName: String,
    val isDefault: Boolean,
    val engineNoteAr: String,
    val icon: Drawable?
)

object BrowserDiscoveryHelper {

    fun discoverInstalledBrowsers(context: Context): List<InstalledBrowser> {
        val pm = context.packageManager
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val defaultPackage = resolveDefaultBrowserPackage(pm, probeIntent)
        val candidates = queryBrowsers(pm, probeIntent)

        return candidates
            .asSequence()
            .filter { it.activityInfo?.packageName != null }
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                val drawable = runCatching { info.loadIcon(pm) }.getOrNull()
                InstalledBrowser(
                    packageName = pkg,
                    appName = label,
                    isDefault = pkg == defaultPackage,
                    engineNoteAr = classifyBrowserNote(pkg),
                    icon = drawable
                )
            }
            .sortedWith(
                compareByDescending<InstalledBrowser> { it.isDefault }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }

    fun openUrlInBrowser(
        context: Context,
        rawUrl: String,
        browserPackage: String? = null
    ): Result<Unit> {
        val normalized = WebBrowserUtils.normalizeWebUrl(rawUrl)
        if (normalized.isFailure) {
            return Result.failure(normalized.exceptionOrNull()!!)
        }
        val validUrl = normalized.getOrThrow()
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!browserPackage.isNullOrBlank()) {
                    setPackage(browserPackage)
                }
            }
            context.startActivity(intent)
        }
    }

    fun classifyBrowserNote(packageName: String): String {
        val pkg = packageName.lowercase()
        return when {
            pkg.contains("chrome") || pkg.contains("chromium") ->
                "محرك Chromium — يعطل رفع المجلدات في متصفح الجوال، استخدم ZIP أو متصفح FolderPush"
            pkg.contains("sec.android.app.sbrowser") || pkg.contains("samsung") ->
                "متصفح Samsung Internet — يدعم رفع الملفات أو ZIP، ولتمرير المجلد استخدم FolderPush"
            pkg.contains("firefox") || pkg.contains("fenix") || pkg.contains("mozilla") ->
                "محرك Gecko (Firefox) — لا يمرر بنية المجلدات الفرعية في الجوال، يفضّل حزمة ZIP"
            pkg.contains("emmx") || pkg.contains("edge") ->
                "Microsoft Edge — يدعم استقبال ZIP وروابط الرفع المباشر"
            pkg.contains("brave") ->
                "Brave Browser — متصفح Chromium بخصوصية عالية، يدعم استقبال ZIP وروابط الرفع"
            pkg.contains("opera") ->
                "Opera Browser — يدعم فتح روابط النتائج واستقبال ملفات ZIP"
            pkg.contains("duckduckgo") ->
                "DuckDuckGo — متصفح خصوصية، استخدم ضغط ZIP أو الرفع من داخل FolderPush"
            pkg.contains("kiwibrowser") || pkg.contains("vivaldi") ->
                "متصفح مبني على Chromium — يدعم استقبال حزم ZIP وروابط النتائج"
            else ->
                "متصفح إنترنت مثبت على الجهاز — جاهز لفتح المواقع أو رفع حزم ZIP"
        }
    }

    private fun resolveDefaultBrowserPackage(pm: PackageManager, probeIntent: Intent): String? {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                probeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName
    }

    private fun queryBrowsers(pm: PackageManager, probeIntent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                probeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
        }
    }
}
