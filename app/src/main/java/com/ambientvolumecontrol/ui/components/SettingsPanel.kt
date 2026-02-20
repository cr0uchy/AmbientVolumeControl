package com.ambientvolumecontrol.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ambientvolumecontrol.ui.theme.AccentTeal

@Composable
fun SettingsPanel(
    targetRatioDb: Float,
    minVolume: Int,
    maxVolume: Int,
    onRatioChange: (Float) -> Unit,
    onMinVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Target Ratio
            SliderSetting(
                label = "Music-to-Noise Ratio",
                value = targetRatioDb,
                valueText = "${if (targetRatioDb >= 0) "+" else ""}${targetRatioDb.toInt()} dB",
                range = -15f..25f,
                description = "How much louder (or quieter) the music should be compared to ambient noise",
                onValueChange = onRatioChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Minimum Volume
            SliderSetting(
                label = "Minimum Volume",
                value = minVolume.toFloat(),
                valueText = "$minVolume / $maxVolume",
                range = 1f..maxVolume.toFloat() / 2f,
                description = "Lowest volume the app will set (prevents muting)",
                onValueChange = { onMinVolumeChange(it.toInt()) }
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    description: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = AccentTeal
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AccentTeal,
                activeTrackColor = AccentTeal
            )
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
