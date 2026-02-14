package com.ambientvolumecontrol.audio

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull

class AmbientSampler(
    var sampleDurationMs: Long = 800L
) {

    /**
     * Collects dB readings for [sampleDurationMs] and returns the average.
     * Should be called when silence is detected (gap between songs).
     */
    suspend fun sampleAmbientNoise(dbFlow: SharedFlow<Float>): Float? {
        val samples = mutableListOf<Float>()

        withTimeoutOrNull(sampleDurationMs) {
            dbFlow.collect { db ->
                samples.add(db)
            }
        }

        return if (samples.isNotEmpty()) {
            // Use RMS averaging for more accurate dB representation
            val sumSquares = samples.fold(0.0) { acc, db ->
                val linear = Math.pow(10.0, db.toDouble() / 20.0)
                acc + linear * linear
            }
            val rmsLinear = Math.sqrt(sumSquares / samples.size)
            (20.0 * Math.log10(rmsLinear.coerceAtLeast(1.0))).toFloat()
        } else {
            null
        }
    }
}
