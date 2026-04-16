package moe.chensi.volume.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun BubbleSettingsCard(
    sizeScale: Float,
    horizontal: Float,
    vertical: Float,
    shadowEnabled: Boolean,
    onSizeScaleChange: (Float) -> Unit,
    onPositionChange: (Float, Float) -> Unit,
    onShadowEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Quick Bubble", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "The real floating bubble is shown live while this page is open, so changes reflect directly on screen.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bubble shadow", modifier = Modifier.weight(1f))
                    Switch(
                        checked = shadowEnabled,
                        onCheckedChange = onShadowEnabledChange
                    )
                }

                NumericPercentSetting(
                    title = "Bubble size",
                    value = sizeScale,
                    range = 0.7f..1.8f,
                    onValueChange = onSizeScaleChange
                )

                NumericPercentSetting(
                    title = "Horizontal position",
                    value = horizontal,
                    range = 0f..1f,
                    onValueChange = { onPositionChange(it, vertical) }
                )

                NumericPercentSetting(
                    title = "Vertical position",
                    value = vertical,
                    range = 0f..1f,
                    onValueChange = { onPositionChange(horizontal, it) }
                )
            }
        }
    }
}

@Composable
private fun NumericPercentSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    var text by remember { mutableStateOf(formatPercent(value * 100f)) }
    var editingText by remember { mutableStateOf(false) }

    LaunchedEffect(value, editingText) {
        if (!editingText) {
            text = formatPercent(value * 100f)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val normalizedInput = input.replace(',', '.')
                    text = normalizedInput
                    val parsed = normalizedInput.toFloatOrNull() ?: return@OutlinedTextField
                    onValueChange((parsed / 100f).coerceIn(range.start, range.endInclusive))
                },
                modifier = Modifier
                    .width(120.dp)
                    .onFocusChanged { editingText = it.isFocused },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = { Text("%") }
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

private fun formatPercent(value: Float): String {
    val fixed = String.format(Locale.US, "%.4f", value)
    return fixed.trimEnd('0').trimEnd('.')
}
