package com.ambientvolumecontrol.audio

import android.media.AudioManager
import android.util.Log
import com.ambientvolumecontrol.model.VolumeChangeEntry

class VolumeController(private val audioManager: AudioManager) {

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    val currentVolume: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /**
     * Adjusts the media volume based on the ambient noise level.
     *
     * @param ambientDb The measured ambient noise in dB
     * @param targetRatioDb How many dB above ambient the music should be
     * @param minVolume Minimum volume step to set (avoid muting)
     * @return A [VolumeChangeEntry] describing the change made
     */
    fun adjustVolume(
        ambientDb: Float,
        targetRatioDb: Float,
        minVolume: Int = 1
    ): VolumeChangeEntry {
        val max = maxVolume
        val oldVolume = currentVolume

        // Desired music level = ambient noise + target ratio
        val desiredDb = ambientDb + targetRatioDb

        // Map dB range (30..90) to volume steps (0..maxVolume)
        // 30 dB = quiet room, 90 dB = very loud venue
        val normalizedDb = ((desiredDb - 30f) / 60f).coerceIn(0f, 1f)
        val newVolume = (normalizedDb * max).toInt().coerceIn(minVolume, max)

        if (newVolume != oldVolume) {
            try {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    newVolume,
                    0 // No flags — don't show system volume UI
                )
            } catch (e: SecurityException) {
                Log.e("AVC_Volume", "Permission denied setting volume: ${e.message}")
            } catch (e: Exception) {
                Log.e("AVC_Volume", "Failed to set volume: ${e.message}")
            }
        }

        return VolumeChangeEntry(
            timestamp = System.currentTimeMillis(),
            ambientDb = ambientDb,
            oldVolume = oldVolume,
            newVolume = newVolume
        )
    }
}
