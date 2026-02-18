package com.ambientvolumecontrol.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambientvolumecontrol.model.MonitoringState
import com.ambientvolumecontrol.service.MediaListenerService
import com.ambientvolumecontrol.service.MonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(MonitoringState())
    val state: StateFlow<MonitoringState> = _state.asStateFlow()

    private var service: MonitoringService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MonitoringService.LocalBinder
            service = localBinder.service
            bound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            _state.update { it.copy(isMonitoring = false) }
        }
    }

    init {
        // Check media session availability on init
        _state.update {
            it.copy(mediaSessionAvailable = MediaListenerService.isEnabled(application))
        }
    }

    private fun observeService() {
        val svc = service ?: return

        viewModelScope.launch {
            combine(
                svc.currentDb,
                svc.silenceDetected,
                svc.currentVolume,
                svc.maxVolume,
                svc.isMonitoring
            ) { db, silence, vol, max, monitoring ->
                { s: MonitoringState -> s.copy(currentDb = db, silenceDetected = silence, currentVolume = vol, maxVolume = max, isMonitoring = monitoring) }
            }.collect { patch -> _state.update(patch) }
        }

        viewModelScope.launch {
            combine(
                svc.volumeHistory,
                svc.lastAmbientDb,
                svc.currentSongTitle,
                svc.currentArtist,
                svc.detectionMode
            ) { history, ambient, title, artist, mode ->
                { s: MonitoringState -> s.copy(volumeHistory = history, lastAmbientDb = ambient, currentSongTitle = title, currentArtist = artist, detectionMode = mode) }
            }.collect { patch -> _state.update(patch) }
        }

        viewModelScope.launch {
            svc.mediaSessionAvailable.collect { available ->
                _state.update { it.copy(mediaSessionAvailable = available) }
            }
        }
    }

    fun startMonitoring() {
        val context = getApplication<Application>()
        val intent = Intent(context, MonitoringService::class.java)

        ContextCompat.startForegroundService(context, intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopMonitoring() {
        service?.stopMonitoring()
        if (bound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Not bound
            }
            bound = false
        }
        service = null
        _state.update { it.copy(isMonitoring = false) }
    }

    fun updateThreshold(db: Float) {
        _state.update { it.copy(silenceThresholdDb = db) }
        service?.silenceThresholdDb = db
    }

    fun updateTargetRatio(ratio: Float) {
        _state.update { it.copy(targetRatioDb = ratio) }
        service?.targetRatioDb = ratio
    }

    fun updateMinVolume(vol: Int) {
        _state.update { it.copy(minVolume = vol) }
        service?.minVolume = vol
    }

    fun openNotificationListenerSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun refreshMediaSessionAvailability() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(mediaSessionAvailable = MediaListenerService.isEnabled(context))
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (bound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Not bound
            }
        }
    }
}
