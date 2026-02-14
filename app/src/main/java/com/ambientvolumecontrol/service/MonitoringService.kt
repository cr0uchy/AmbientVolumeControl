package com.ambientvolumecontrol.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ambientvolumecontrol.AmbientVolumeApp
import com.ambientvolumecontrol.MainActivity
import com.ambientvolumecontrol.R
import com.ambientvolumecontrol.audio.AmbientSampler
import com.ambientvolumecontrol.audio.AudioMonitor
import com.ambientvolumecontrol.audio.SilenceDetector
import com.ambientvolumecontrol.audio.VolumeController
import com.ambientvolumecontrol.model.VolumeChangeEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MonitoringService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var audioMonitor: AudioMonitor
    private lateinit var silenceDetector: SilenceDetector
    private lateinit var ambientSampler: AmbientSampler
    private lateinit var volumeController: VolumeController

    // State exposed to UI
    private val _currentDb = MutableStateFlow(0f)
    val currentDb: StateFlow<Float> = _currentDb

    private val _silenceDetected = MutableStateFlow(false)
    val silenceDetected: StateFlow<Boolean> = _silenceDetected

    private val _currentVolume = MutableStateFlow(0)
    val currentVolume: StateFlow<Int> = _currentVolume

    private val _maxVolume = MutableStateFlow(15)
    val maxVolume: StateFlow<Int> = _maxVolume

    private val _volumeHistory = MutableStateFlow<List<VolumeChangeEntry>>(emptyList())
    val volumeHistory: StateFlow<List<VolumeChangeEntry>> = _volumeHistory

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring

    private val _lastAmbientDb = MutableStateFlow<Float?>(null)
    val lastAmbientDb: StateFlow<Float?> = _lastAmbientDb

    // Settings
    var silenceThresholdDb: Float
        get() = silenceDetector.silenceThresholdDb
        set(value) { silenceDetector.silenceThresholdDb = value }

    var targetRatioDb: Float = 10f
    var minVolume: Int = 1

    // Binder
    inner class LocalBinder : Binder() {
        val service: MonitoringService get() = this@MonitoringService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        audioMonitor = AudioMonitor()
        silenceDetector = SilenceDetector()
        ambientSampler = AmbientSampler()
        volumeController = VolumeController(audioManager)

        _maxVolume.value = volumeController.maxVolume
        _currentVolume.value = volumeController.currentVolume
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Monitoring ambient noise..."))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        if (_isMonitoring.value) return

        _isMonitoring.value = true
        audioMonitor.start(serviceScope)

        // Main monitoring loop
        serviceScope.launch {
            var lastSilenceState: SilenceDetector.SilenceState =
                SilenceDetector.SilenceState.MusicPlaying

            audioMonitor.dbLevel.collect { db ->
                _currentDb.value = db
                silenceDetector.processDb(db)

                val currentState = silenceDetector.state.value
                _silenceDetected.value = currentState is SilenceDetector.SilenceState.SilenceDetected

                // When silence is first detected, sample ambient noise and adjust volume
                if (currentState is SilenceDetector.SilenceState.SilenceDetected &&
                    lastSilenceState is SilenceDetector.SilenceState.MusicPlaying
                ) {
                    // Sample ambient noise during the gap
                    val ambientDb = ambientSampler.sampleAmbientNoise(audioMonitor.dbLevel)

                    if (ambientDb != null) {
                        _lastAmbientDb.value = ambientDb

                        // Adjust volume based on ambient noise
                        val entry = volumeController.adjustVolume(
                            ambientDb = ambientDb,
                            targetRatioDb = targetRatioDb,
                            minVolume = minVolume
                        )

                        _currentVolume.value = volumeController.currentVolume

                        // Add to history (keep last 50 entries)
                        val history = _volumeHistory.value.toMutableList()
                        history.add(0, entry)
                        _volumeHistory.value = if (history.size > 50) {
                            history.take(50)
                        } else {
                            history
                        }

                        // Update notification
                        updateNotification(
                            "Ambient: ${ambientDb.toInt()} dB • Volume: ${entry.newVolume}/${_maxVolume.value}"
                        )
                    }
                }

                lastSilenceState = currentState
                _currentVolume.value = volumeController.currentVolume
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        audioMonitor.stop()
        silenceDetector.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        audioMonitor.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AmbientVolumeApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ambient Volume Control")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
