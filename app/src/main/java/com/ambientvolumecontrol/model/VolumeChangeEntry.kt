package com.ambientvolumecontrol.model

data class VolumeChangeEntry(
    val timestamp: Long,
    val ambientDb: Float,
    val oldVolume: Int,
    val newVolume: Int
) {
    val direction: VolumeDirection
        get() = when {
            newVolume > oldVolume -> VolumeDirection.UP
            newVolume < oldVolume -> VolumeDirection.DOWN
            else -> VolumeDirection.UNCHANGED
        }
}

enum class VolumeDirection {
    UP, DOWN, UNCHANGED
}
