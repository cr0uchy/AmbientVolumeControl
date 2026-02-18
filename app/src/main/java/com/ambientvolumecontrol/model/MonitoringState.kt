package com.ambientvolumecontrol.model

data class MonitoringState(
    val isMonitoring: Boolean = false,
    val currentDb: Float = 0f,
    val silenceDetected: Boolean = false,
    val currentVolume: Int = 0,
    val maxVolume: Int = 15,
    val silenceThresholdDb: Float = 35f,
    val targetRatioDb: Float = 10f,
    val minVolume: Int = 1,
    val volumeHistory: List<VolumeChangeEntry> = emptyList(),
    val lastAmbientDb: Float? = null,
    val currentSongTitle: String? = null,
    val currentArtist: String? = null,
    val detectionMode: DetectionMode = DetectionMode.SILENCE,
    val mediaSessionAvailable: Boolean = false
)

enum class DetectionMode {
    SILENCE,
    MEDIA_SESSION
}
