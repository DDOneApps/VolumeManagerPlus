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
    var vertical: Float = 0.50f,
    var shadowEnabled: Boolean = true,
    var closeDelayMs: Long = 3000L,
    var animationStyle: BubbleAnimationStyle = BubbleAnimationStyle.Default
)

@Serializable
enum class BubbleAnimationStyle {
    Default,
    SlideInLeft,
    SlideInRight,
    Scale,
    Fade,
    Rotate
}
