package com.ambientvolumecontrol.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Continuously tracks ambient noise level by maintaining a rolling window
 * of dB readings and using the lower percentile as the ambient estimate.
 *
 * When music is playing, the mic hears music + ambient. The quietest
 * readings in the window (brief dips, pauses, quieter passages) are
 * closest to the true ambient level.
 */
class AmbientTracker(
    private val windowSize: Int = 200,
    private val percentile: Float = 0.15f
) {

    private val readings = ArrayDeque<Float>(windowSize)

    private val _ambientDb = MutableStateFlow(0f)
    val ambientDb: StateFlow<Float> = _ambientDb

    /**
     * Start collecting dB readings from the audio monitor.
     * Call this once when monitoring starts.
     */
    fun start(scope: CoroutineScope, dbFlow: SharedFlow<Float>) {
        scope.launch {
            dbFlow.collect { db ->
                synchronized(readings) {
                    readings.addLast(db)
                    if (readings.size > windowSize) {
                        readings.removeFirst()
                    }
                    _ambientDb.value = computePercentile()
                }
            }
        }
    }

    fun reset() {
        synchronized(readings) {
            readings.clear()
            _ambientDb.value = 0f
        }
    }

    /**
     * Returns the [percentile] value of the current readings window.
     * For example, with percentile = 0.15, returns the value below which
     * 15% of readings fall — a good estimate of ambient noise floor.
     */
    private fun computePercentile(): Float {
        if (readings.isEmpty()) return 0f
        val sorted = readings.toList().sorted()
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}
