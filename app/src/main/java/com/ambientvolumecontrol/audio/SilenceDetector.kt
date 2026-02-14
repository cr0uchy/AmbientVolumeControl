package com.ambientvolumecontrol.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SilenceDetector(
    var silenceThresholdDb: Float = 35f,
    var silenceDurationMs: Long = 1500L
) {

    sealed class SilenceState {
        data object MusicPlaying : SilenceState()
        data object SilenceDetected : SilenceState()
    }

    private val _state = MutableStateFlow<SilenceState>(SilenceState.MusicPlaying)
    val state: StateFlow<SilenceState> = _state

    private var silenceStartTime: Long? = null
    private var hasEmittedSilence = false

    fun processDb(db: Float) {
        val now = System.currentTimeMillis()

        if (db < silenceThresholdDb) {
            // Sound is below threshold — potential silence/gap
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
            // Sound is above threshold — music is playing
            silenceStartTime = null
            hasEmittedSilence = false
            _state.value = SilenceState.MusicPlaying
        }
    }

    fun reset() {
        silenceStartTime = null
        hasEmittedSilence = false
        _state.value = SilenceState.MusicPlaying
    }
}
