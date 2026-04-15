package moe.chensi.volume.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.chensi.volume.bubble.calculateBubbleLayout
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BubbleSettingsCard(
    sizeScale: Float,
    horizontal: Float,
    vertical: Float,
    onSizeScaleChange: (Float) -> Unit,
    onPositionChange: (Float, Float) -> Unit
) {
    val animatedScale = animateFloatAsState(sizeScale, spring(), label = "bubbleSize")
    val animatedHorizontal = animateFloatAsState(horizontal, spring(), label = "bubbleHorizontal")
    val animatedVertical = animateFloatAsState(vertical, spring(), label = "bubbleVertical")

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Quick Bubble", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Use this page to tune the bubble that appears with volume keys. Tap the bubble to open the full AppVolMgr overlay.",
                    style = MaterialTheme.typography.bodyMedium
                )

                BubblePreview(
                    sizeScale = animatedScale.value,
                    horizontal = animatedHorizontal.value,
                    vertical = animatedVertical.value
                )
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingSlider(
                    title = "Bubble size",
                    valueText = "${(sizeScale * 100).roundToInt()}%",
                    value = sizeScale,
                    range = 0.7f..1.8f,
                    onValueChange = onSizeScaleChange
                )

                SettingSlider(
                    title = "Horizontal position",
                    valueText = "${(horizontal * 100).roundToInt()}%",
                    value = horizontal,
                    range = 0f..1f,
                    onValueChange = { onPositionChange(it, vertical) }
                )

                SettingSlider(
                    title = "Vertical position",
                    valueText = "${(vertical * 100).roundToInt()}%",
                    value = vertical,
                    range = 0f..1f,
                    onValueChange = { onPositionChange(horizontal, it) }
                )
            }
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun BubblePreview(
    sizeScale: Float,
    horizontal: Float,
    vertical: Float
) {
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    val screenAspectRatio =
        (displayMetrics.heightPixels.toFloat() / displayMetrics.widthPixels.toFloat()).coerceIn(
            1.4f, 2.6f
        )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }.roundToInt()
        val maxPreviewHeightPx = with(density) { 420.dp.toPx() }.roundToInt()
        val widthPx = min(maxWidthPx, (maxPreviewHeightPx / screenAspectRatio).roundToInt())
        val heightPx = (widthPx * screenAspectRatio).roundToInt()
        val previewWidthDp = with(density) { widthPx.toDp() }
        val previewHeightDp = with(density) { heightPx.toDp() }

        val layout = calculateBubbleLayout(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density.density,
            sizeScale = sizeScale,
            horizontal = horizontal,
            vertical = vertical
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(previewWidthDp)
                    .height(previewHeightDp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(layout.xPx, layout.yPx) }
                        .size(with(density) { layout.sizePx.toDp() })
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
