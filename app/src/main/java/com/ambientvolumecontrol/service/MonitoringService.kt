package com.ambientvolumecontrol.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ambientvolumecontrol.AmbientVolumeApp
import com.ambientvolumecontrol.MainActivity
import com.ambientvolumecontrol.R
import com.ambientvolumecontrol.audio.AmbientSampler
import com.ambientvolumecontrol.audio.AudioMonitor
import com.ambientvolumecontrol.audio.SilenceDetector
import com.ambientvolumecontrol.audio.VolumeController
import com.ambientvolumecontrol.model.DetectionMode
import com.ambientvolumecontrol.model.VolumeChangeEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    // Ambient reading captured during the end-of-song gap, applied on next song start
    private var pendingAmbientDb: Float? = null

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

    private val _currentSongTitle = MutableStateFlow<String?>(null)
    val currentSongTitle: StateFlow<String?> = _currentSongTitle

    private val _currentArtist = MutableStateFlow<String?>(null)
    val currentArtist: StateFlow<String?> = _currentArtist

    private val _detectionMode = MutableStateFlow(DetectionMode.SILENCE)
    val detectionMode: StateFlow<DetectionMode> = _detectionMode

    private val _mediaSessionAvailable = MutableStateFlow(false)
    val mediaSessionAvailable: StateFlow<Boolean> = _mediaSessionAvailable

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

        val hasMediaSession = MediaListenerService.isEnabled(this)
        _mediaSessionAvailable.value = hasMediaSession

        if (hasMediaSession) {
            _detectionMode.value = DetectionMode.MEDIA_SESSION
            startMediaSessionMonitoring()
        } else {
            _detectionMode.value = DetectionMode.SILENCE
            startSilenceMonitoring()
        }

        // Always collect dB for the meter display
        serviceScope.launch {
            audioMonitor.dbLevel.collect { db ->
                _currentDb.value = db
            }
        }
    }

    private fun startMediaSessionMonitoring() {
        // When a song starts, schedule a sample 2 seconds before it ends using the track duration.
        // The sample is taken while music is still playing (before the gap), so it picks up room
        // noise mixed with music — but this is consistent and avoids feedback from the volume
        // change itself. The result is stored and applied when the next songChanged fires.
        // Falls back to sampling immediately at song start if duration is unavailable.
        var sampleJob: Job? = null
        var lastHandledTitle: String? = null
        var lastHandledTimeMs: Long = 0L
        val dedupeWindowMs = 3000L

        serviceScope.launch {
            MediaListenerService.songChanged.collect { songInfo ->
                _currentSongTitle.value = songInfo.title
                _currentArtist.value = songInfo.artist

                // Ignore rapid-fire duplicate metadata events for the same track
                // (YouTube Music often fires onMetadataChanged 2-3 times per song change).
                // Allow the same title through if >3s have passed (song replayed intentionally).
                val now = System.currentTimeMillis()
                if (songInfo.title == lastHandledTitle && (now - lastHandledTimeMs) < dedupeWindowMs) {
                    Log.d("AVC_Monitor", "songChanged: duplicate event for '${songInfo.title}' — ignored")
                    return@collect
                }
                lastHandledTitle = songInfo.title
                lastHandledTimeMs = now

                Log.d("AVC_Monitor", "songChanged: new track '${songInfo.title}'")

                // Apply any pending ambient reading from the previous song's end-of-track sample
                val ambientDb = pendingAmbientDb?.also {
                    pendingAmbientDb = null
                    Log.d("AVC_Monitor", "Applying end-of-track sample: $it dB")
                } ?: run {
                    Log.d("AVC_Monitor", "No pending sample — sampling now (first song / no duration)")
                    ambientSampler.sampleAmbientNoise(audioMonitor.dbLevel)
                }

                if (ambientDb != null) {
                    _lastAmbientDb.value = ambientDb
                    val entry = volumeController.adjustVolume(
                        ambientDb = ambientDb,
                        targetRatioDb = targetRatioDb,
                        minVolume = minVolume
                    )
                    Log.d("AVC_Monitor", "Volume adjusted: ${entry.oldVolume} -> ${entry.newVolume}/${_maxVolume.value}")
                    _currentVolume.value = volumeController.currentVolume
                    addHistoryEntry(entry)
                    val title = songInfo.title ?: "Unknown"
                    updateNotification(
                        "Now: $title • Ambient: ${ambientDb.toInt()} dB • Vol: ${entry.newVolume}/${_maxVolume.value}"
                    )
                }

                // Schedule end-of-track sample for this song
                sampleJob?.cancel()
                val duration = songInfo.durationMs
                val position = songInfo.positionMs ?: 0L
                if (duration != null && duration > 0) {
                    val remaining = duration - position
                    val sampleLeadMs = 2000L
                    val delayMs = (remaining - sampleLeadMs).coerceAtLeast(0L)
                    Log.d("AVC_Monitor", "Scheduling end-of-track sample in ${delayMs}ms (remaining=${remaining}ms)")
                    sampleJob = serviceScope.launch {
                        delay(delayMs)
                        Log.d("AVC_Monitor", "End-of-track: sampling ambient noise now")
                        val endSample = ambientSampler.sampleAmbientNoise(audioMonitor.dbLevel)
                        Log.d("AVC_Monitor", "End-of-track sample: $endSample dB")
                        if (endSample != null) {
                            pendingAmbientDb = endSample
                        }
                    }
                } else {
                    Log.d("AVC_Monitor", "No duration for '${songInfo.title}' — will sample at next song start")
                }
            }
        }
    }

    private fun startSilenceMonitoring() {
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
                    val ambientDb = ambientSampler.sampleAmbientNoise(audioMonitor.dbLevel)

                    if (ambientDb != null) {
                        _lastAmbientDb.value = ambientDb

                        val entry = volumeController.adjustVolume(
                            ambientDb = ambientDb,
                            targetRatioDb = targetRatioDb,
                            minVolume = minVolume
                        )

                        _currentVolume.value = volumeController.currentVolume
                        addHistoryEntry(entry)

                        updateNotification(
                            "Ambient: ${ambientDb.toInt()} dB • Volume: ${entry.newVolume}/${_maxVolume.value}"
                        )
                    }
                }

                lastSilenceState = currentState
            }
        }
    }

    private fun addHistoryEntry(entry: VolumeChangeEntry) {
        val history = _volumeHistory.value.toMutableList()
        history.add(0, entry)
        _volumeHistory.value = if (history.size > 50) history.take(50) else history
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        audioMonitor.stop()
        silenceDetector.reset()
        pendingAmbientDb = null
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
