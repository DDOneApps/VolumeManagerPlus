package moe.chensi.volume.data

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    var isPlayer: Boolean = false,
    var volume: Float = 1.0f,
    var hidden: Boolean = false,
    var disableVolumeButtons: Boolean = false
)

@Serializable
data class BubblePreferences(
    var sizeScale: Float = 1.0f,
    var horizontal: Float = 0.90f,
    var vertical: Float = 0.50f
)
