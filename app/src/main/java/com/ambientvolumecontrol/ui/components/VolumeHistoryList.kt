package com.ambientvolumecontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ambientvolumecontrol.model.VolumeChangeEntry
import com.ambientvolumecontrol.model.VolumeDirection
import com.ambientvolumecontrol.ui.theme.VolumeDown
import com.ambientvolumecontrol.ui.theme.VolumeUnchanged
import com.ambientvolumecontrol.ui.theme.VolumeUp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VolumeHistoryList(
    entries: List<VolumeChangeEntry>,
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
                text = "Volume History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (entries.isEmpty()) {
                Text(
                    text = "No volume changes yet. Start monitoring and play music to see adjustments here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    VolumeHistoryItem(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun VolumeHistoryItem(entry: VolumeChangeEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val directionColor = when (entry.direction) {
        VolumeDirection.UP -> VolumeUp
        VolumeDirection.DOWN -> VolumeDown
        VolumeDirection.UNCHANGED -> VolumeUnchanged
    }
    val directionSymbol = when (entry.direction) {
        VolumeDirection.UP -> "\u25B2"      // up triangle
        VolumeDirection.DOWN -> "\u25BC"    // down triangle
        VolumeDirection.UNCHANGED -> "\u25CF" // dot
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Ambient dB
        Text(
            text = "${entry.ambientDb.toInt()} dB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Volume change with direction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.oldVolume}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " $directionSymbol ",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = directionColor
            )
            Text(
                text = "${entry.newVolume}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = directionColor
            )
        }
    }
}
