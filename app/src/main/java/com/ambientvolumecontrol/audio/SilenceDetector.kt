package com.ambientvolumecontrol.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Detects the gap between songs using a relative drop from a rolling baseline.
 *
 * Rather than comparing against a fixed dB threshold (which fails when the room
 * is loud), this tracks a rolling average of recent dB readings while music is
 * playing and triggers "silence" when the level drops [dropDb] below that baseline.
 *
 * [silenceThresholdDb] is kept as a UI-exposed setting but is used as an absolute
 * floor — silence is detected when EITHER the level drops [dropDb] below baseline
 * OR falls below [silenceThresholdDb], whichever is higher.
 */
class SilenceDetector(
    var silenceThresholdDb: Float = 35f,
    var silenceDurationMs: Long = 1500L,
    var dropDb: Float = 15f
) {

    sealed class SilenceState {
        data object MusicPlaying : SilenceState()
        data object SilenceDetected : SilenceState()
    }

    private val _state = MutableStateFlow<SilenceState>(SilenceState.MusicPlaying)
    val state: StateFlow<SilenceState> = _state

    private var silenceStartTime: Long? = null
    private var hasEmittedSilence = false

    // Rolling baseline: exponential moving average of dB while playing
    private var baselineDb: Float? = null
    private val baselineAlpha = 0.05f  // slow-moving average

    fun processDb(db: Float) {
        val now = System.currentTimeMillis()

        // Update rolling baseline only while music appears to be playing
        val currentBaseline = baselineDb
        val effectiveThreshold = if (currentBaseline != null) {
            maxOf(silenceThresholdDb, currentBaseline - dropDb)
        } else {
            silenceThresholdDb
        }

        if (db < effectiveThreshold) {
            // Level dropped — potential gap between songs
            if (silenceStartTime == null) {
                silenceStartTime = now
                hasEmittedSilence = false
            }
            val elapsed = now - (silenceStartTime ?: now)
            if (elapsed >= silenceDurationMs && !hasEmittedSilence) {
                _state.value = SilenceState.SilenceDetected
                hasEmittedSilence = true
            }
        } else {
            // Music is playing — update baseline and reset silence timer
            baselineDb = if (currentBaseline == null) db
                         else currentBaseline + baselineAlpha * (db - currentBaseline)
            silenceStartTime = null
            hasEmittedSilence = false
            _state.value = SilenceState.MusicPlaying
        }
    }

    fun reset() {
        silenceStartTime = null
        hasEmittedSilence = false
        baselineDb = null
        _state.value = SilenceState.MusicPlaying
    }
}
