package com.example.data.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.UploadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FolderSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncWorkerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_SYNC -> {
                FolderSyncCoordinator.cancelActiveSync("تم إلغاء المزامنة من الإشعار")
                syncWorkerJob?.cancel()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }

            else -> {
                val folderTitle = FolderSyncCoordinator.activeFolderName.value ?: "مجلد"
                val destTitle = FolderSyncCoordinator.activeDestination.value?.titleAr ?: "السحابة"
                val initialNotification = buildProgressNotification(
                    title = "جاري رفع $folderTitle ← $destTitle",
                    contentText = "بدء المزامنة في الخلفية...",
                    subText = null,
                    progressPercent = 0,
                    indeterminate = true
                )

                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID_PROGRESS,
                    initialNotification,
                    fgsType
                )

                if (!FolderSyncCoordinator.hasPendingOrActiveRequest()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                syncWorkerJob?.cancel()
                syncWorkerJob = serviceScope.launch {
                    var lastNotifyMs = 0L
                    var lastStage = ""

                    val progressObserverJob = launch {
                        FolderSyncCoordinator.uploadState.collectLatest { state ->
                            if (state is UploadState.Uploading) {
                                val now = System.currentTimeMillis()
                                val shouldUpdate = FolderSyncProgressFormatter.shouldNotifyUpdate(
                                    lastNotifiedAtMs = lastNotifyMs,
                                    nowMs = now,
                                    lastStage = lastStage,
                                    newStage = state.stageLabel,
                                    currentIndex = state.currentIndex,
                                    totalFiles = state.totalFiles
                                )
                                if (shouldUpdate) {
                                    lastNotifyMs = now
                                    lastStage = state.stageLabel
                                    val percent = FolderSyncProgressFormatter.computeProgressPercent(
                                        currentIndex = state.currentIndex,
                                        totalFiles = state.totalFiles,
                                        bytesTransferred = state.bytesTransferred,
                                        totalBytes = state.totalBytes
                                    )
                                    val folder = FolderSyncCoordinator.activeFolderName.value ?: "مجلد"
                                    val notif = buildProgressNotification(
                                        title = "$folder • ${state.stageLabel}",
                                        contentText = FolderSyncProgressFormatter.buildOngoingContentText(state),
                                        subText = state.currentFilePath,
                                        progressPercent = percent,
                                        indeterminate = state.totalFiles <= 0 || state.currentIndex == 0
                                    )
                                    notifySafely(NOTIFICATION_ID_PROGRESS, notif)
                                }
                            }
                        }
                    }

                    val finalState = FolderSyncCoordinator.executePendingSync(applicationContext)
                    progressObserverJob.cancel()

                    ServiceCompat.stopForeground(
                        this@FolderSyncService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE
                    )

                    postCompletionNotification(finalState)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        FolderSyncCoordinator.cancelActiveSync("انتهت مهلة المزامنة الطويلة للنظام — يمكنك استئناف الرفع")
        syncWorkerJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        postCompletionNotification(
            UploadState.Error("توقفت المزامنة في الخلفية بسبب حد وقت النظام (dataSync timeout)")
        )
        stopSelf(startId)
    }

    override fun onDestroy() {
        syncWorkerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_ID_PROGRESS,
            getString(R.string.sync_channel_progress_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.sync_channel_progress_desc)
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_ID_RESULT,
            getString(R.string.sync_channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.sync_channel_result_desc)
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(resultChannel)
    }

    private fun buildOpenAppPendingIntent(): PendingIntent {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, FolderSyncService::class.java).apply {
            action = ACTION_CANCEL_SYNC
        }
        return PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildProgressNotification(
        title: String,
        contentText: String,
        subText: String?,
        progressPercent: Int,
        indeterminate: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_PROGRESS)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(buildOpenAppPendingIntent())
            .setProgress(100, progressPercent, indeterminate)
            .addAction(
                0,
                getString(R.string.sync_notification_cancel),
                buildCancelPendingIntent()
            )

        if (!subText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n$subText"))
        }

        return builder.build()
    }

    private fun postCompletionNotification(finalState: UploadState) {
        val folderName = FolderSyncCoordinator.activeFolderName.value ?: "FolderPush"
        val (title, body) = when (finalState) {
            is UploadState.Success -> {
                "اكتمل رفع $folderName" to finalState.summary
            }
            is UploadState.Error -> {
                "توقف رفع $folderName" to finalState.message
            }
            else -> return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_RESULT)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildOpenAppPendingIntent())
            .build()

        notifySafely(NOTIFICATION_ID_RESULT, notification)
    }

    private fun notifySafely(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        }
    }

    companion object {
        const val ACTION_START_SYNC = "com.example.action.START_SYNC"
        const val ACTION_CANCEL_SYNC = "com.example.action.CANCEL_SYNC"

        const val CHANNEL_ID_PROGRESS = "folderpush_sync_progress"
        const val CHANNEL_ID_RESULT = "folderpush_sync_result"

        const val NOTIFICATION_ID_PROGRESS = 2001
        const val NOTIFICATION_ID_RESULT = 2002
    }
}
