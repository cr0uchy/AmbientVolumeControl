package com.ambientvolumecontrol.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambientvolumecontrol.model.MonitoringState
import com.ambientvolumecontrol.service.MonitoringService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private fun observeService() {
        val svc = service ?: return

        viewModelScope.launch {
            svc.currentDb.collect { db ->
                _state.update { it.copy(currentDb = db) }
            }
        }
        viewModelScope.launch {
            svc.silenceDetected.collect { silence ->
                _state.update { it.copy(silenceDetected = silence) }
            }
        }
        viewModelScope.launch {
            svc.currentVolume.collect { vol ->
                _state.update { it.copy(currentVolume = vol) }
            }
        }
        viewModelScope.launch {
            svc.maxVolume.collect { max ->
                _state.update { it.copy(maxVolume = max) }
            }
        }
        viewModelScope.launch {
            svc.volumeHistory.collect { history ->
                _state.update { it.copy(volumeHistory = history) }
            }
        }
        viewModelScope.launch {
            svc.isMonitoring.collect { monitoring ->
                _state.update { it.copy(isMonitoring = monitoring) }
            }
        }
        viewModelScope.launch {
            svc.lastAmbientDb.collect { db ->
                _state.update { it.copy(lastAmbientDb = db) }
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
