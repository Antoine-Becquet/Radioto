package com.antoinebecquet.radioto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var stations: List<RadioStation> = emptyList()
    private lateinit var stationLogoView: ImageView
    private lateinit var stationNameView: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var playerControlView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation des vues
        stationLogoView = findViewById(R.id.currentStationLogo)
        stationNameView = findViewById(R.id.currentStationName)
        playPauseButton = findViewById(R.id.playPauseButton)
        playerControlView = findViewById(R.id.playerControlView)

        // Configuration du RecyclerView en mode mosaïque
        val recyclerView = findViewById<RecyclerView>(R.id.stationRecyclerView)
        stations = loadStationsFromJson()
        val adapter = StationAdapter(stations) { position ->
            val station = stations[position]

            val clickAnimation = AlphaAnimation(0.5f, 1.0f)
            clickAnimation.duration = 150
            clickAnimation.repeatMode = Animation.REVERSE
            recyclerView.getChildAt(position)?.startAnimation(clickAnimation)

            val intent = Intent(this, RadioPlayerService::class.java).apply {
                action = "ACTION_CHANGE_STATION"
                putExtra("station_index", position)
            }
            startService(intent)

            stationNameView.text = station.name
            val resId = resources.getIdentifier(station.logoResId, "drawable", packageName)
            stationLogoView.setImageResource(resId)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.addItemDecoration(GridSpacingItemDecoration(2, 20, true))

        // Vérifier l'état d'ExoPlayer au démarrage
        val player = RadioPlayerService.playerInstance
        if (player != null) {
            Log.d("MainActivity", "ExoPlayer bien chargé")
            playerControlView.player = player
            playerControlView.setUseController(true)
            playerControlView.visibility = View.VISIBLE

            playPauseButton.setImageResource(if (player.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        } else {
            Log.e("MainActivity", "ExoPlayer non disponible !")
        }

        // Gestion du bouton Play/Pause
        playPauseButton.setOnClickListener {
            if (player != null) {
                player.playWhenReady = !player.playWhenReady
                playPauseButton.setImageResource(
                    if (player.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val player = RadioPlayerService.playerInstance
        if (player != null && playerControlView.player == null) {
            playerControlView.player = player
            playerControlView.setUseController(true)
            playerControlView.visibility = View.VISIBLE
        }
    }

    private fun loadStationsFromJson(): List<RadioStation> {
        val jsonText: String = try {
            resources.openRawResource(R.raw.stations).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e("MainActivity", "Erreur lors de la lecture de stations.json", e)
            "[]"
        }
        val gson = Gson()
        val stationListType = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson(jsonText, stationListType)
    }
}