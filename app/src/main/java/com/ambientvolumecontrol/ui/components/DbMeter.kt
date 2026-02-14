package com.ambientvolumecontrol.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambientvolumecontrol.ui.theme.DbGreen
import com.ambientvolumecontrol.ui.theme.DbOrange
import com.ambientvolumecontrol.ui.theme.DbRed
import com.ambientvolumecontrol.ui.theme.DbYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DbMeter(
    db: Float,
    modifier: Modifier = Modifier,
    silenceDetected: Boolean = false,
    lastAmbientDb: Float? = null
) {
    val animatedDb by animateFloatAsState(
        targetValue = db.coerceIn(0f, 120f),
        animationSpec = tween(durationMillis = 150),
        label = "db"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .aspectRatio(1.6f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawGauge(animatedDb)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedDb.toInt()}",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = getDbColor(animatedDb)
            )
            Text(
                text = "dB",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (silenceDetected) {
                Text(
                    text = "GAP DETECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (lastAmbientDb != null) {
                Text(
                    text = "Ambient: ${lastAmbientDb.toInt()} dB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawGauge(db: Float) {
    val strokeWidth = 20f
    val padding = strokeWidth / 2 + 16f
    val arcWidth = size.width - padding * 2
    val arcHeight = size.height * 1.4f

    val topLeft = Offset(padding, size.height * 0.15f)

    // Background arc
    drawArc(
        color = Color(0xFF2A2A2A),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Green zone (0-40 dB) = 0-60 degrees
    drawArc(
        color = DbGreen.copy(alpha = 0.7f),
        startAngle = 180f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Yellow zone (40-70 dB) = 60-105 degrees
    drawArc(
        color = DbYellow.copy(alpha = 0.7f),
        startAngle = 240f,
        sweepAngle = 45f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Orange zone (70-90 dB) = 105-135 degrees
    drawArc(
        color = DbOrange.copy(alpha = 0.7f),
        startAngle = 285f,
        sweepAngle = 30f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Red zone (90-120 dB) = 135-180 degrees
    drawArc(
        color = DbRed.copy(alpha = 0.7f),
        startAngle = 315f,
        sweepAngle = 45f,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(arcWidth, arcHeight),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Needle
    val needleAngle = 180f + (db.coerceIn(0f, 120f) / 120f) * 180f
    val angleRad = needleAngle * PI.toFloat() / 180f
    val centerX = topLeft.x + arcWidth / 2
    val centerY = topLeft.y + arcHeight / 2
    val needleLength = arcWidth / 2 - strokeWidth - 8f

    val needleEndX = centerX + needleLength * cos(angleRad)
    val needleEndY = centerY + needleLength * sin(angleRad)

    drawLine(
        color = Color.White,
        start = Offset(centerX, centerY),
        end = Offset(needleEndX, needleEndY),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Center dot
    drawCircle(
        color = Color.White,
        radius = 8f,
        center = Offset(centerX, centerY)
    )
}

private fun getDbColor(db: Float): Color = when {
    db < 40f -> DbGreen
    db < 70f -> DbYellow
    db < 90f -> DbOrange
    else -> DbRed
}
