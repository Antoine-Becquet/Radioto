package com.antoinebecquet.radioto

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.ui.input.key.type
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

// Commandes personnalisées
private const val CUSTOM_COMMAND_CHANGE_STATION_ACTION = "com.antoinebecquet.radioto.CHANGE_STATION"
private const val CUSTOM_COMMAND_CHANGE_STATION_EXTRA_INDEX = "station_index"

class RadioPlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private var stations: List<RadioStation> = emptyList()
    private var currentStationIndex = -1 // Sera initialisé correctement dans onCreate
    private lateinit var sharedPreferences: SharedPreferences

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        @SuppressLint("UnsafeOptInUsageError")
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
            // ICI, addAllCommands() inclut Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM et Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            val playerCommands = Player.Commands.Builder().addAllCommands().build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_CHANGE_STATION_ACTION) {
                val stationIndex = args.getInt(CUSTOM_COMMAND_CHANGE_STATION_EXTRA_INDEX, -1)
                if (stationIndex != -1 && stationIndex < stations.size) {
                    Log.d("RadioPlayerService", "CustomCommand: Request to play station at index $stationIndex")
                    playStationAtIndex(stationIndex, fromUI = true)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else {
                    Log.w("RadioPlayerService", "CustomCommand: Invalid station index $stationIndex")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RadioPlayerService", "onCreate")

        sharedPreferences = getSharedPreferences("RadioPrefs", MODE_PRIVATE)
        stations = loadStationsFromJson()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("RadioPlayerService", "Player.Listener: onIsPlayingChanged: $isPlaying")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                mediaItem?.mediaId?.let { mediaId ->
                    val newIndex = stations.indexOfFirst { it.id == mediaId }
                    if (newIndex != -1) {
                        if (currentStationIndex != newIndex) {
                            Log.d("RadioPlayerService", "MediaItemTransition: New station index $newIndex for '${mediaItem.mediaMetadata.title}' (MediaID: $mediaId)")
                            currentStationIndex = newIndex
                            saveCurrentStationIndex(currentStationIndex)
                        } else {
                            0
                        }
                    } else {
                        Log.w("RadioPlayerService", "MediaItemTransition: MediaId $mediaId not found in stations list.")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("RadioPlayerService", "Player.Listener: Player Error: ${error.message}", error)
            }
        })

        val initialMediaItems = buildMediaItemsFromStations()

        if (initialMediaItems.isNotEmpty()) {
            val loadedStationIndex = loadCurrentStationIndex()
            // CORRECTION DÉFINITIVE de l'erreur if/else
            currentStationIndex = if (loadedStationIndex >= 0 && loadedStationIndex < initialMediaItems.size) {
                loadedStationIndex
            } else {
                0 // Jouer la première station si l'index sauvegardé n'est pas valide ou non trouvé
            }
            Log.d("RadioPlayerService", "Setting media items. Initial index to play: $currentStationIndex from ${initialMediaItems.size} items.")
            player.setMediaItems(initialMediaItems, currentStationIndex, C.TIME_UNSET)
            player.prepare()
            // player.playWhenReady = false; // Maintenir à false pour l'instant
        } else {
            Log.w("RadioPlayerService", "No stations loaded, player not prepared with playlist.")
            currentStationIndex = -1 // Assurer un état cohérent si aucune station
        }

        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent!!)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    private fun buildMediaItemsFromStations(): List<MediaItem> {
        return stations.map { station ->
            val artworkUri = getUriForDrawable(station.iconUrl)
            MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(getString(R.string.app_name))
                        .setStation(station.name)
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer != null && !currentPlayer.playWhenReady && currentPlayer.mediaItemCount > 0) {
            Log.d("RadioPlayerService", "onTaskRemoved: Player not playing and has items, stopping service.")
            stopSelf()
        } else {
            Log.d("RadioPlayerService", "onTaskRemoved: Player is playing, has no items, or service already stopping.")
        }
        // super.onTaskRemoved(rootIntent) // Optionnel, dépend si MediaSessionService fait qqchose d'important ici
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { action ->
            Log.d("RadioPlayerService", "onStartCommand - Action: $action")
            if (action == "ACTION_PLAY_FROM_NOTIFICATION_INDEX") {
                val stationIndex = intent.getIntExtra("station_index", -1)
                if (stationIndex != -1) {
                    playStationAtIndex(stationIndex, fromUI = false)
                }
            }
        }
        return Service.START_STICKY
    }

    private fun playStationAtIndex(index: Int, fromUI: Boolean = false) {
        if (index < 0 || index >= stations.size) {
            Log.e("RadioPlayerService", "playStationAtIndex - Index invalide: $index, stations count: ${stations.size}")
            return
        }

        if (player.mediaItemCount == 0 || index >= player.mediaItemCount) {
            Log.w("RadioPlayerService", "Player has no media items or index $index is out of bounds for player's item count ${player.mediaItemCount}. Re-populating playlist.")
            if (stations.isNotEmpty()) {
                val newMediaItems = buildMediaItemsFromStations() // CORRECTION: Utiliser la fonction dédiée
                if (newMediaItems.isNotEmpty() && index < newMediaItems.size) {
                    player.setMediaItems(newMediaItems, index, C.TIME_UNSET)
                    player.prepare()
                    currentStationIndex = index // Mettre à jour currentStationIndex
                } else {
                    Log.e("RadioPlayerService", "Failed to repopulate playlist or index still out of bounds.")
                    return
                }
            } else {
                Log.e("RadioPlayerService", "Stations list is empty, cannot repopulate player.")
                return
            }
        }

        val station = stations[index]
        Log.d("RadioPlayerService", "Requesting to play station: ${station.name} at index $index")

        // Vérifier si c'est déjà l'item courant et s'il est au bon index
        val currentItemInPlayer = player.currentMediaItem
        if (player.currentMediaItemIndex != index || currentItemInPlayer?.mediaId != station.id) {
            Log.d("RadioPlayerService", "Seeking to station index $index. Current player index: ${player.currentMediaItemIndex}, Current Media ID: ${currentItemInPlayer?.mediaId}, Target Media ID: ${station.id}")
            player.seekToDefaultPosition(index)
            player.prepare() // Important après un seek
        }

        if (fromUI || !player.isPlaying) {
            player.play()
            Log.d("RadioPlayerService", "Playback initiated/resumed for station: ${station.name}")
        }
    }


    private fun loadStationsFromJson(): List<RadioStation> {
        val jsonString: String
        try {
            Log.d("RadioPlayerService", "Loading stations.json from assets.")
            assets.open("stations.json").bufferedReader().use {
                jsonString = it.readText()
            }
            val typeToken = object : TypeToken<List<RadioStation>>() {}.type
            val loadedStations = Gson().fromJson<List<RadioStation>>(jsonString, typeToken)
            if (loadedStations == null) {
                Log.e("RadioPlayerService", "Gson returned null after parsing stations.json.")
                return emptyList()
            }
            Log.d("RadioPlayerService", "${loadedStations.size} stations parsed from JSON.")
            return loadedStations
        } catch (ioException: IOException) {
            Log.e("RadioPlayerService", "IOException during loading of stations.json", ioException)
        } catch (e: Exception) {
            Log.e("RadioPlayerService", "General exception during JSON parsing of stations", e)
        }
        return emptyList()
    }

    private fun saveCurrentStationIndex(index: Int) {
        if (index >= 0) {
            sharedPreferences.edit().putInt("currentStationIndex", index).apply()
            Log.d("RadioPlayerService", "Saved current station index: $index")
        }
    }

    private fun loadCurrentStationIndex(): Int {
        val loadedIndex = sharedPreferences.getInt("currentStationIndex", 0)
        Log.d("RadioPlayerService", "Loaded station index from SharedPreferences: $loadedIndex")
        return loadedIndex
    }

    private fun getUriForDrawable(logoResIdName: String?): Uri? {
        if (logoResIdName.isNullOrBlank()) {
            Log.w("RadioPlayerService", "getUriForDrawable: logoResIdName is null or blank. Attempting to use default_logo.")
            return getDefaultLogoUri()
        }
        var resourceId = resources.getIdentifier(logoResIdName, "drawable", packageName)
        if (resourceId == 0 && logoResIdName.contains(".")) {
            val nameWithoutExtension = logoResIdName.substringBeforeLast('.')
            Log.d("RadioPlayerService", "Drawable '$logoResIdName' not found, trying '$nameWithoutExtension'")
            resourceId = resources.getIdentifier(nameWithoutExtension, "drawable", packageName)
        }
        return if (resourceId != 0) {
            Log.d("RadioPlayerService", "Found drawable '$logoResIdName' (or version) with ID: $resourceId")
            Uri.parse("android.resource://$packageName/$resourceId")
        } else {
            Log.e("RadioPlayerService", "Drawable not found for name: '$logoResIdName'. Attempting to use default_logo.")
            getDefaultLogoUri()
        }
    }

    private fun getDefaultLogoUri(): Uri? {
        return try {
            val defaultLogoResId = R.drawable.default_logo
            Uri.parse("android.resource://$packageName/$defaultLogoResId")
        } catch (e: Exception) {
            Log.e("RadioPlayerService", "Default logo R.drawable.default_logo not found or error accessing it.", e)
            null
        }
    }

    override fun onDestroy() {
        Log.d("RadioPlayerService", "onDestroy - Releasing resources.")
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}