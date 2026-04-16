package moe.chensi.volume.bubble

import kotlin.math.roundToInt

const val BASE_BUBBLE_SIZE_DP = 44f
const val MIN_BUBBLE_SIZE_DP = 28f
const val BUBBLE_SHADOW_PADDING_DP = 10f

data class BubbleLayout(
    val sizePx: Int,
    val xPx: Int,
    val yPx: Int
)

fun calculateBubbleLayout(
    widthPx: Int,
    heightPx: Int,
    density: Float,
    sizeScale: Float,
    horizontal: Float,
    vertical: Float
): BubbleLayout {
    val clampedSizeScale = sizeScale.coerceIn(0.7f, 1.8f)
    val clampedHorizontal = horizontal.coerceIn(0f, 1f)
    val clampedVertical = vertical.coerceIn(0f, 1f)

    val sizePx = (BASE_BUBBLE_SIZE_DP * clampedSizeScale * density).roundToInt()
        .coerceAtLeast((MIN_BUBBLE_SIZE_DP * density).roundToInt())

    val safeWidth = widthPx.coerceAtLeast(0)
    val safeHeight = heightPx.coerceAtLeast(0)
    val xPx = ((safeWidth - sizePx).coerceAtLeast(0) * clampedHorizontal).roundToInt()
    val yPx = ((safeHeight - sizePx).coerceAtLeast(0) * clampedVertical).roundToInt()

    return BubbleLayout(sizePx, xPx, yPx)
}
