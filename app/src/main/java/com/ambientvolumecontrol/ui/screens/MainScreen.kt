package com.ambientvolumecontrol.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ambientvolumecontrol.ui.components.DbMeter
import com.ambientvolumecontrol.ui.components.SettingsPanel
import com.ambientvolumecontrol.ui.components.VolumeHistoryList
import com.ambientvolumecontrol.ui.components.VolumeIndicator
import com.ambientvolumecontrol.ui.theme.AccentTeal
import com.ambientvolumecontrol.ui.theme.DbRed
import com.ambientvolumecontrol.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasAudioPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        } else {
            true
        }
        if (hasAudioPermission) {
            viewModel.startMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ambient Volume Control",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            StatusBadge(
                isMonitoring = state.isMonitoring,
                silenceDetected = state.silenceDetected
            )

            // dB Meter
            DbMeter(
                db = state.currentDb,
                silenceDetected = state.silenceDetected,
                lastAmbientDb = state.lastAmbientDb
            )

            // Volume indicator
            VolumeIndicator(
                current = state.currentVolume,
                max = state.maxVolume
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop button
            Button(
                onClick = {
                    if (state.isMonitoring) {
                        viewModel.stopMonitoring()
                    } else {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                },
                modifier = Modifier
                    .size(120.dp)
                    .padding(8.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isMonitoring) DbRed else AccentTeal,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = if (state.isMonitoring) "STOP" else "START",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings
            SettingsPanel(
                silenceThresholdDb = state.silenceThresholdDb,
                targetRatioDb = state.targetRatioDb,
                minVolume = state.minVolume,
                maxVolume = state.maxVolume,
                onThresholdChange = { viewModel.updateThreshold(it) },
                onRatioChange = { viewModel.updateTargetRatio(it) },
                onMinVolumeChange = { viewModel.updateMinVolume(it) }
            )

            // Volume History
            VolumeHistoryList(entries = state.volumeHistory)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusBadge(
    isMonitoring: Boolean,
    silenceDetected: Boolean
) {
    val (text, color) = when {
        !isMonitoring -> "Stopped" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        silenceDetected -> "Gap Detected - Sampling" to AccentTeal
        else -> "Monitoring" to Color(0xFF4CAF50)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}
