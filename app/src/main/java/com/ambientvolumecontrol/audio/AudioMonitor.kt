package com.ambientvolumecontrol.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

class AudioMonitor {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private val _dbLevel = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 64)
    val dbLevel: SharedFlow<Float> = _dbLevel

    val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val state = audioRecord?.state
        Log.d("AVC_Audio", "AudioRecord state after init: $state (${if (state == AudioRecord.STATE_INITIALIZED) "OK" else "FAILED"})")
        audioRecord?.startRecording()
        Log.d("AVC_Audio", "Recording state: ${audioRecord?.recordingState}")

        scope.launch(Dispatchers.Default) {
            var logCounter = 0
            val buffer = ShortArray(minBufferSize / 2)
            while (isActive) {
                // Restart recording if another app (e.g. music player) temporarily took the mic
                if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d("AVC_Audio", "Recording stopped unexpectedly — restarting")
                    try { audioRecord?.startRecording() } catch (_: IllegalStateException) {}
                    Thread.sleep(200)
                    continue
                }
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val rms = computeRms(buffer, read)
                    val db = if (rms > 0) (20.0 * log10(rms)).toFloat().coerceIn(0f, 120f) else 0f
                    if (logCounter++ % 100 == 0) Log.d("AVC_Audio", "dB sample: $db")
                    _dbLevel.emit(db)
                }
            }
            Log.d("AVC_Audio", "Audio loop exited cleanly")
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped
        }
        audioRecord?.release()
        audioRecord = null
    }

    private fun computeRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / count)
    }
}
