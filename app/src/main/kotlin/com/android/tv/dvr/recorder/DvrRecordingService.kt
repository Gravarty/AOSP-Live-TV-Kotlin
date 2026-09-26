package com.android.tv.dvr.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import com.android.tv.InputSessionManager
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.TvSingletons
import com.android.tv.common.util.Clock
import com.android.tv.dvr.WritableDvrDataManager
import com.android.tv.util.RecurringRunner
import java.util.concurrent.TimeUnit

/**
 * Vordergrunddienst während (anstehender) Aufnahmen; räumt täglich alte Pläne auf.
 * Android 14: startForeground mit Diensttyp "specialUse" (im Manifest deklarieren).
 */
class DvrRecordingService : Service() {
    private lateinit var reaperRunner: RecurringRunner
    private lateinit var sessionManager: InputSessionManager
    internal var isRecording = false
    private var foreground = false
    private lateinit var contentTitle: String
    private lateinit var contentTextRecording: String
    private lateinit var contentTextLoading: String

    internal val onRecordingSessionChangeListener = InputSessionManager.OnRecordingSessionChangeListener { create, count ->
        isRecording = count > 0
        if (create) startForeground(true) else stopForegroundIfNotRecordingInternal()
    }

    override fun onCreate() {
        Starter.start(this)
        super.onCreate()
        instance = this
        val singletons = TvSingletons.getSingletons(this)
        val dataManager = singletons.getDvrDataManager() as WritableDvrDataManager
        sessionManager = singletons.getInputSessionManager()
        sessionManager.addOnRecordingSessionChangeListener(onRecordingSessionChangeListener)
        reaperRunner = RecurringRunner(this, TimeUnit.DAYS.toMillis(1), ScheduledProgramReaper(dataManager, Clock.SYSTEM), null)
        reaperRunner.start()
        contentTitle = getString(R.string.dvr_notification_content_title)
        contentTextRecording = getString(R.string.dvr_notification_content_text_recording)
        contentTextLoading = getString(R.string.dvr_notification_content_text_loading)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { startForeground(it.getBooleanExtra(EXTRA_START_FOR_RECORDING, false)) }
        return START_STICKY
    }

    override fun onDestroy() {
        reaperRunner.stop()
        sessionManager.removeRecordingSessionChangeListener(onRecordingSessionChangeListener)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal fun stopForegroundIfNotRecordingInternal() {
        if (foreground && !isRecording) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }

    private fun startForeground(hasUpcomingRecording: Boolean) {
        if (!foreground || hasUpcomingRecording) {
            foreground = true
            val notification = Notification.Builder(this, DVR_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(if (hasUpcomingRecording) contentTextRecording else contentTextLoading)
                .setSmallIcon(R.drawable.ic_dvr)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ONGOING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ONGOING_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(DVR_NOTIFICATION_CHANNEL_ID, getString(R.string.dvr_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val DVR_NOTIFICATION_CHANNEL_ID = "dvr_notification_channel"
        private const val ONGOING_NOTIFICATION_ID = 1
        internal const val EXTRA_START_FOR_RECORDING = "start_for_recording"
        private var instance: DvrRecordingService? = null

        @MainThread
        @JvmStatic
        fun startForegroundService(context: Context, startForRecording: Boolean) {
            val current = instance
            if (current == null) {
                context.startForegroundService(Intent(context, DvrRecordingService::class.java).putExtra(EXTRA_START_FOR_RECORDING, startForRecording))
            } else {
                current.startForeground(startForRecording)
            }
        }

        @MainThread
        @JvmStatic
        fun stopForegroundIfNotRecording() {
            instance?.stopForegroundIfNotRecordingInternal()
        }
    }
}
