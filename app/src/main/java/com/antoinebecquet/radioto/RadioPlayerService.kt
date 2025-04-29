package com.antoinebecquet.radioto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.support.v4.media.session.MediaSessionCompat

class RadioPlayerService : Service() {

    companion object {
        var playerInstance: ExoPlayer? = null
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var stations: List<RadioStation> = listOf()
    private var currentStationIndex = 0
    private lateinit var sharedPreferences: SharedPreferences

    private val player: ExoPlayer by lazy {
        playerInstance ?: ExoPlayer.Builder(this).build().also { playerInstance = it }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences("RadioPrefs", MODE_PRIVATE)
        stations = loadStationsFromJson()
        currentStationIndex = loadCurrentStation()

        mediaSession = MediaSessionCompat(this, "RadioPlayerService").apply { isActive = true }

        // Mise à jour automatique du bouton Play/Pause
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updateNotification()
            }
        })

        playStation(currentStationIndex)
        startForeground(1, createNotification())
    }

    private fun loadStationsFromJson(): List<RadioStation> {
        val inputStream = resources.openRawResource(R.raw.stations)
        val jsonText = inputStream.bufferedReader().use { it.readText() }
        val gson = Gson()
        val stationListType = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson(jsonText, stationListType)
    }

    private fun saveCurrentStation(index: Int) {
        sharedPreferences.edit().putInt("current_station", index).apply()
    }

    private fun loadCurrentStation(): Int = sharedPreferences.getInt("current_station", 0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "ACTION_PLAY" -> {
                    player.playWhenReady = true
                    updateNotification()
                }
                "ACTION_PAUSE" -> {
                    player.playWhenReady = false
                    updateNotification()
                }
                "ACTION_SKIP_TO_NEXT" -> {
                    changeStation(1)
                }
                "ACTION_SKIP_TO_PREVIOUS" -> {
                    changeStation(-1)
                }
                "ACTION_CHANGE_STATION" -> {
                    val newIndex = intent.getIntExtra("station_index", -1)
                    if (newIndex != -1 && newIndex in stations.indices) {
                        playStation(newIndex)
                    } else {
                        Log.e("RadioPlayerService", "Index de station invalide : $newIndex")
                    }
                }
                else -> {
                    Log.w("RadioPlayerService", "Action non gérée : $action")
                }
            }
        }
        return START_STICKY
    }

    // Changement de station plus fluide
    private fun changeStation(direction: Int) {
        val newIndex = (currentStationIndex + direction + stations.size) % stations.size
        playStation(newIndex)
        saveCurrentStation(newIndex)
    }

    // Gestion améliorée pour éviter les coupures brutales
    private fun playStation(index: Int) {
        if (index in stations.indices) {
            if (player.isPlaying) player.stop() // Arrêter proprement
            currentStationIndex = index
            val station = stations[currentStationIndex]
            try {
                player.setMediaItem(MediaItem.fromUri(station.streamUrl))
                player.prepare()
                player.playWhenReady = true
                saveCurrentStation(index)
            } catch (e: Exception) {
                Log.e("RadioPlayerService", "Erreur de lecture, station non disponible : ${station.name}")
                changeStation(1) // Passer automatiquement à la station suivante
            }
            updateNotification()
        }
    }

    private fun pendingIntentForAction(action: String): PendingIntent {
        val intent = Intent(this, RadioPlayerService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun updateNotification() {
        startForeground(1, createNotification())
    }

    private fun createNotification(): Notification {
        val station = stations[currentStationIndex]

        // Restaurer le logo de la station pour le fond de la notification
        val resId = applicationContext.resources.getIdentifier(
            station.logoResId,
            "drawable",
            applicationContext.packageName
        )
        val largeIcon = BitmapFactory.decodeResource(applicationContext.resources, resId)

        // Garder default_logo pour le petit icône
        val smallIconId = R.drawable.default_logo

        val channelId = "RadioPlayerChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Radio Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("RadioTo - ${station.name}")
            .setContentText("Lecture en arrière-plan")
            .setSmallIcon(smallIconId) // Petit logo fixe
            .setLargeIcon(largeIcon) // Fond de notification avec le logo de la station
            .addAction(
                R.drawable.ic_previous,
                "Précédent",
                pendingIntentForAction("ACTION_SKIP_TO_PREVIOUS")
            )
            .addAction(
                if (player.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play,
                "Play/Pause",
                pendingIntentForAction(if (player.playWhenReady) "ACTION_PAUSE" else "ACTION_PLAY")
            )
            .addAction(
                R.drawable.ic_next,
                "Suivant",
                pendingIntentForAction("ACTION_SKIP_TO_NEXT")
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }
}