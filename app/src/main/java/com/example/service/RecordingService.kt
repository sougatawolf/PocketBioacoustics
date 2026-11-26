package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.WaveLoopApplication
import com.example.audio.GpsTagMode
import com.example.audio.RecordingState
import com.example.audio.RecordingStatus
import com.example.data.RecordingSchedule
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecordingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val app: WaveLoopApplication
        get() = application as WaveLoopApplication

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                promoteToForeground(app.recordingEngine.state.value)
                acquireWakeLock()
                serviceScope.launch {
                    val settings = app.preferencesRepository.settingsFlow.first()
                    val started = app.recordingEngine.startSession(settings)
                    if (started) {
                        if (settings.gpsTagMode != GpsTagMode.OFF && app.gpsTagManager.hasLocationPermission()) {
                            promoteToForeground(app.recordingEngine.state.value)
                        }
                        observeEngineState()
                    } else {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_PAUSE -> {
                app.recordingEngine.pauseSession()
            }
            ACTION_RESUME -> {
                app.recordingEngine.resumeSession()
            }
            ACTION_SPLIT -> {
                app.recordingEngine.requestManualSplit()
            }
            ACTION_TRIGGER_NOW -> {
                app.recordingEngine.triggerNextCycleNow()
            }
            ACTION_STOP -> {
                app.recordingEngine.stopSession()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeEngineState() {
        if (stateObserverJob != null) return
        stateObserverJob = serviceScope.launch {
            var sawActive = false
            app.recordingEngine.state.collect { recState ->
                if (recState.status != RecordingStatus.IDLE) {
                    sawActive = true
                    updateNotification(recState)
                } else if (sawActive) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun promoteToForeground(state: RecordingState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val includeLocation = state.gpsTagMode != GpsTagMode.OFF &&
                app.gpsTagManager.hasLocationPermission()
            val fgType = if (includeLocation) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                fgType
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: RecordingState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: RecordingState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = state.status == RecordingStatus.PAUSED
        val isLowBatteryPaused = state.status == RecordingStatus.PAUSED_LOW_BATTERY
        val isIntervalWaiting = state.status == RecordingStatus.WAITING_INTERVAL
        val isScheduleWaiting = state.status == RecordingStatus.WAITING_SCHEDULE
        val isArmedTrigger = state.status == RecordingStatus.ARMED_TRIGGER
        val isTriggeredRecording = state.status == RecordingStatus.RECORDING && state.triggerConfig.enabled
        val segElapsed = Formatters.formatDurationHms(state.currentSegmentElapsedMs)
        val recDurationText = Formatters.formatDurationShort(state.targetRecordingDurationSeconds)
        val intervalText = Formatters.formatDurationShort(state.targetIntervalSeconds)
        val batteryLevel = app.batteryMonitor.snapshot.value.levelPercent

        val title = when {
            isLowBatteryPaused -> "Low Battery Guard • Paused"
            isPaused -> "Paused • Segment #${state.segmentNumber}"
            isScheduleWaiting -> "Schedule Wait • ${state.schedule.mode.label}"
            isArmedTrigger -> "Armed • Trigger Monitoring"
            isIntervalWaiting -> "Interval Wait • Next Seg #${state.segmentNumber + 1}"
            isTriggeredRecording -> "Triggered REC • Segment #${state.segmentNumber}"
            else -> "Recording WAV • Segment #${state.segmentNumber}"
        }

        val body = when {
            isLowBatteryPaused ->
                "Battery at $batteryLevel% (< ${state.minBatteryThresholdPercent}% limit). WAV saved safely."
            isScheduleWaiting -> {
                val startText = state.nextScheduleWindowStartMinute?.let {
                    RecordingSchedule.formatTimeOfDayFull(it)
                } ?: state.schedule.summaryLabel()
                "Starts $startText (in ${Formatters.formatDurationHms(state.nextCycleCountdownMs)})"
            }
            isArmedTrigger -> {
                val band = state.triggerConfig.bandSummaryLabel(state.quality.sampleRateHz)
                val thresh = "${state.triggerConfig.amplitudeThresholdDb.toInt()} dBFS"
                val pre = if (state.triggerConfig.preRecordSeconds > 0) {
                    val bufferedSec = state.triggerPreRecordBufferedMs / 1000.0
                    String.format(
                        java.util.Locale.US,
                        "Pre %.1f/%ds",
                        bufferedSec,
                        state.triggerConfig.preRecordSeconds
                    )
                } else {
                    "No pre-rec"
                }
                "$band ≥ $thresh • $pre"
            }
            isIntervalWaiting -> "Next $recDurationText WAV in ${
                Formatters.formatDurationHms(state.nextCycleCountdownMs)
            }"
            isTriggeredRecording ->
                "$segElapsed • Auto-stop in ${
                    Formatters.formatDurationHms(state.triggerAutoStopRemainingMs)
                } if silent"
            state.continuousNoGap ->
                "$segElapsed / $recDurationText • Continuous WAV loop"
            else -> "$segElapsed / $recDurationText • Then wait $intervalText"
        }

        val pauseResumeAction = when {
            isLowBatteryPaused -> null
            isPaused -> NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Resume",
                servicePendingIntent(ACTION_RESUME, 1)
            )
            else -> NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                servicePendingIntent(ACTION_PAUSE, 2)
            )
        }

        val secondaryAction = when {
            isLowBatteryPaused || isScheduleWaiting -> null
            isIntervalWaiting || isArmedTrigger -> NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Record Now",
                servicePendingIntent(ACTION_TRIGGER_NOW, 3)
            )
            else -> NotificationCompat.Action(
                android.R.drawable.ic_menu_add,
                "Split",
                servicePendingIntent(ACTION_SPLIT, 3)
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            servicePendingIntent(ACTION_STOP, 4)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)

        if (pauseResumeAction != null) {
            builder.addAction(pauseResumeAction)
        }

        if (secondaryAction != null) {
            builder.addAction(secondaryAction)
        }

        return builder
            .addAction(stopAction)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WaveLoop::ContinuousRecordingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L + 10 * 60 * 1000L) // up to 24h+ safety cap
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "waveloop_recording_channel"
        private const val NOTIFICATION_ID = 4101

        const val ACTION_START = "com.example.waveloop.ACTION_START"
        const val ACTION_PAUSE = "com.example.waveloop.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.waveloop.ACTION_RESUME"
        const val ACTION_SPLIT = "com.example.waveloop.ACTION_SPLIT"
        const val ACTION_TRIGGER_NOW = "com.example.waveloop.ACTION_TRIGGER_NOW"
        const val ACTION_STOP = "com.example.waveloop.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }
    }
}
