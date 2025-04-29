package com.antoinebecquet.radioto

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerControlView
import android.util.Log
import com.google.android.exoplayer2.Player

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var stationRecyclerView: RecyclerView
    private lateinit var playerControlView: PlayerControlView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Active le mode sombre selon le système
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        setContentView(R.layout.activity_main)

        Log.d("MainActivity", "App started successfully")

        // Changement automatique du fond en fonction du mode sombre
        val rootLayout = findViewById<View>(R.id.container)
        val isDarkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDarkMode) {
            rootLayout.setBackgroundColor(getColor(R.color.black)) // Fond sombre
        } else {
            rootLayout.setBackgroundColor(getColor(R.color.white)) // Fond clair
        }

        // Initialisation du lecteur ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerControlView = findViewById(R.id.playerControlView)
        playerControlView.player = player
        playerControlView.setShowTimeoutMs(0) // Garder les contrôles toujours visibles

        // Empêcher ExoPlayer de masquer les contrôles
        player.playWhenReady = true
        player.repeatMode = ExoPlayer.REPEAT_MODE_ALL

        // Ajout d'un listener pour forcer la visibilité des contrôles
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playerControlView.visibility = View.VISIBLE
            }
        })

        stationRecyclerView = findViewById(R.id.stationRecyclerView)
        stationRecyclerView.layoutManager = GridLayoutManager(this, 2)
        stationRecyclerView.adapter = StationAdapter(getStations()) { station ->
            selectStation(station)
        }
    }

    private fun getStations(): List<RadioStation> {
        return listOf(
            RadioStation("Radio France", "http://icecast.radiofrance.fr/franceculture-midfi.mp3", R.drawable.radio_france_logo),
            RadioStation("NRJ", "https://scdn.nrjaudio.fm/adwz2/fr/30001/mp3_128.mp3", R.drawable.nrj_logo),
            RadioStation("DEMO HTTPS", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", R.drawable.default_logo),
            RadioStation("Fun Radio", "http://streaming.funradio.fr/fun.mp3", R.drawable.fun_radio_logo),
            RadioStation("Skyrock", "http://icecast.skyrock.net/s/natio_aac_128k", R.drawable.skyrock_logo),
            RadioStation("Tendance Ouest", "http://tendanceouest-cotentin.ice.infomaniak.ch/cherbourg.mp3", R.drawable.tendance_logo),
            RadioStation("RFM", "http://rfm-live-mp3-128.scdn.arkena.com/rfm.mp3", R.drawable.rfm_logo)
        )
    }

    private fun selectStation(station: RadioStation) {
        Log.d("MainActivity", "Selected station: ${station.name}")

        // Démarrer le service multimédia pour jouer la station en arrière-plan
        val intent = Intent(this, RadioPlayerService::class.java).apply {
            putExtra("STATION_URL", station.streamUrl)
        }
        startService(intent)

        // Modifier l'affichage du lecteur
        val mediaItem = MediaItem.fromUri(station.streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        playerControlView.visibility = View.VISIBLE
        Log.d("MainActivity", "PlayerControlView visibility forced to VISIBLE after selecting station")
    }

    override fun onStop() {
        super.onStop()
        player.release()
        Log.d("MainActivity", "ExoPlayer released")
    }
}
