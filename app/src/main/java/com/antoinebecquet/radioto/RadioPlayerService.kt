package com.antoinebecquet.radioto

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RadioPlayerService : Service() {

    companion object {
        private var playerInstance: ExoPlayer? = null
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

        mediaSession = MediaSessionCompat(this, "RadioPlayerService").apply {
            isActive = true
        }

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

    private fun loadCurrentStation(): Int {
        return sharedPreferences.getInt("current_station", 0)
    }

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
                    updateNotification()
                }
                "ACTION_SKIP_TO_PREVIOUS" -> {
                    changeStation(-1)
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun changeStation(direction: Int) {
        val newIndex = (currentStationIndex + direction + stations.size) % stations.size
        playStation(newIndex)
        saveCurrentStation(newIndex)
        updateNotification()
    }

    private fun playStation(index: Int) {
        if (index in stations.indices) {
            currentStationIndex = index
            val station = stations[currentStationIndex]
            player.setMediaItem(MediaItem.fromUri(station.streamUrl))
            player.prepare()
            player.playWhenReady = true
            saveCurrentStation(index)
            updateNotification()
        }
    }

    private fun updateNotification() {
        startForeground(1, createNotification())
    }

    private fun createNotification(): Notification {
        currentStationIndex = loadCurrentStation()
        val station = stations[currentStationIndex]
        val largeIcon = BitmapFactory.decodeResource(resources, resources.getIdentifier(station.logoResId, "drawable", packageName))

        return NotificationCompat.Builder(this, "RadioPlayerChannel")
            .setContentTitle("RadioTo - ${station.name}")
            .setContentText("Lecture en arrière-plan")
            .setSmallIcon(R.drawable.default_logo)
            .setLargeIcon(largeIcon)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        super.onDestroy()
    }
}
