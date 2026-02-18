package com.ambientvolumecontrol.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class MediaListenerService : NotificationListenerService() {

    companion object {
        private val _songChanged = MutableSharedFlow<SongInfo>(
            replay = 1,
            extraBufferCapacity = 16
        )
        val songChanged: SharedFlow<SongInfo> = _songChanged

        private val _playbackState = MutableSharedFlow<Int>(
            replay = 1,
            extraBufferCapacity = 16
        )
        val playbackState: SharedFlow<Int> = _playbackState

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val componentName = ComponentName(context, MediaListenerService::class.java)
            return flat.split(":").any {
                ComponentName.unflattenFromString(it) == componentName
            }
        }
    }

    data class SongInfo(
        val title: String?,
        val artist: String?,
        val packageName: String?
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var mediaSessionManager: MediaSessionManager
    private val activeControllers = mutableMapOf<String, MediaController>()
    private val mediaCallbacks = mutableMapOf<String, MediaController.Callback>()

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        mediaSessionManager.addOnActiveSessionsChangedListener(
            sessionListener,
            ComponentName(this, MediaListenerService::class.java)
        )
        updateControllers()
    }

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateControllers(controllers)
        }

    private fun updateControllers(controllers: List<MediaController>? = null) {
        val activeList = controllers ?: try {
            mediaSessionManager.getActiveSessions(
                ComponentName(this, MediaListenerService::class.java)
            )
        } catch (_: SecurityException) {
            emptyList()
        }

        // Remove old callbacks
        activeControllers.forEach { (pkg, controller) ->
            mediaCallbacks[pkg]?.let { controller.unregisterCallback(it) }
        }
        activeControllers.clear()
        mediaCallbacks.clear()

        // Register new callbacks
        activeList.forEach { controller ->
            val pkg = controller.packageName ?: return@forEach
            val callback = createCallback(controller)
            controller.registerCallback(callback)
            activeControllers[pkg] = controller
            mediaCallbacks[pkg] = callback
        }
    }

    private fun createCallback(controller: MediaController) =
        object : MediaController.Callback() {

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata ?: return
                val info = SongInfo(
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    packageName = controller.packageName
                )
                serviceScope.launch { _songChanged.emit(info) }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state ?: return
                serviceScope.launch { _playbackState.emit(state.state) }
            }

            override fun onSessionDestroyed() {
                val pkg = controller.packageName ?: return
                controller.unregisterCallback(this)
                activeControllers.remove(pkg)
                mediaCallbacks.remove(pkg)
            }
        }

    // Required overrides for NotificationListenerService
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        activeControllers.forEach { (pkg, controller) ->
            mediaCallbacks[pkg]?.let { controller.unregisterCallback(it) }
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
