package com.antoinebecquet.radioto

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antoinebecquet.radioto.data.ApiCountry
import com.antoinebecquet.radioto.data.RetrofitInstance
import com.bumptech.glide.Glide
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var stationRecyclerView: RecyclerView
    private lateinit var stationAdapter: StationAdapter
    private var allStations: List<RadioStation> = emptyList()
    private var displayedStations: List<RadioStation> = emptyList()
    private var countries: List<ApiCountry> = emptyList()
    private var selectedCountry: ApiCountry? = null

    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private lateinit var searchView: SearchView
    private lateinit var filterButton: Button

    // Player Info Views
    private lateinit var playerContainer: ConstraintLayout
    private lateinit var playerLogoImageView: ImageView
    private lateinit var playerStationNameTextView: TextView
    private lateinit var playPauseButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stationRecyclerView = findViewById(R.id.stationRecyclerView)
        searchView = findViewById(R.id.searchView)
        filterButton = findViewById(R.id.filterButton)

        // Player Info Views
        playerContainer = findViewById(R.id.playerContainer)
        playerLogoImageView = findViewById(R.id.playerLogoImageView)
        playerStationNameTextView = findViewById(R.id.playerStationNameTextView)
        playPauseButton = findViewById(R.id.playPauseButton)

        setupRecyclerView()
        setupAdapter()
        loadStationsFromApi() // Load popular stations by default
        loadCountriesFromApi()
        setupSearch()
        setupFilter()
        setupPlayPauseButton()
    }

    private fun setupRecyclerView() {
        val spanCount = 2 // Nombre de colonnes
        val spacing = resources.getDimensionPixelSize(R.dimen.grid_spacing) // 8dp, à définir dans les ressources
        val includeEdge = true
        stationRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        stationRecyclerView.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge))
    }

    private fun setupAdapter() {
        stationAdapter = StationAdapter(emptyList()) { station, _ ->
            mediaController?.let {
                val mediaItem = MediaItem.Builder()
                    .setMediaId(station.id)
                    .setUri(station.streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(station.name)
                            .setArtworkUri(Uri.parse(station.iconUrl))
                            .build()
                    )
                    .build()
                it.setMediaItem(mediaItem)
                it.prepare()
                it.play()
            }
        }
        stationRecyclerView.adapter = stationAdapter
    }

    private fun loadStationsFromApi(countryCode: String? = null) {
        lifecycleScope.launch {
            try {
                val apiStations = if (countryCode != null) {
                    RetrofitInstance.api.getStationsByCountry(countryCode)
                } else {
                    RetrofitInstance.api.getStations()
                }
                allStations = apiStations.map { apiStation ->
                    RadioStation(
                        id = apiStation.id,
                        name = apiStation.name,
                        streamUrl = apiStation.streamUrl,
                        iconUrl = apiStation.iconUrl
                    )
                }
                applySearchFilter()
                updateCurrentlyPlaying()
            } catch (e: IOException) {
                Log.e("MainActivity", "IOException, you might not have an internet connection.", e)
            } catch (e: HttpException) {
                Log.e("MainActivity", "HttpException, unexpected response.", e)
            }
        }
    }

    private fun loadCountriesFromApi() {
        lifecycleScope.launch {
            try {
                countries = RetrofitInstance.api.getCountries()
            } catch (e: IOException) {
                Log.e("MainActivity", "IOException, you might not have an internet connection.", e)
            } catch (e: HttpException) {
                Log.e("MainActivity", "HttpException, unexpected response.", e)
            }
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                applySearchFilter()
                return true
            }
        })
    }

    private fun applySearchFilter() {
        val query = searchView.query.toString()
        displayedStations = allStations.filter { station ->
            station.name.contains(query, ignoreCase = true)
        }
        stationAdapter.updateStations(displayedStations)
    }

    private fun setupFilter() {
        filterButton.setOnClickListener {
            val countryNames = countries.map { it.name }.toTypedArray()
            val selectedIndex = countries.indexOf(selectedCountry)

            AlertDialog.Builder(this)
                .setTitle("Select a Country")
                .setSingleChoiceItems(countryNames, selectedIndex) { dialog, which ->
                    selectedCountry = countries[which]
                    searchView.setQuery("", false) // Clear search
                    loadStationsFromApi(selectedCountry?.code)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupPlayPauseButton() {
        playPauseButton.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, RadioPlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(playerListener)
            updateCurrentlyPlaying()
            updatePlayerInfo(mediaController?.currentMediaItem)
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        mediaController?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentlyPlaying(mediaItem?.mediaId)
            updatePlayerInfo(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                playerContainer.visibility = View.VISIBLE
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                if (mediaController?.playbackState == Player.STATE_IDLE || mediaController?.playbackState == Player.STATE_ENDED) {
                    playerContainer.visibility = View.GONE
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("MainActivity", "Player Error: ${error.message}", error)
        }
    }

    private fun updateCurrentlyPlaying(mediaId: String? = mediaController?.currentMediaItem?.mediaId) {
        mediaId?.let {
            val playingIndex = displayedStations.indexOfFirst { it.id == mediaId }
            if (playingIndex != -1) {
                stationAdapter.setCurrentlyPlaying(playingIndex)
            }
        }
    }

    private fun updatePlayerInfo(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            playerContainer.visibility = View.GONE
            return
        }
        playerContainer.visibility = View.VISIBLE
        val metadata = mediaItem.mediaMetadata
        playerStationNameTextView.text = metadata.title
        Glide.with(this)
            .load(metadata.artworkUri)
            .placeholder(R.drawable.default_logo) // Assurez-vous d'avoir ce drawable
            .error(R.drawable.default_logo) // Assurez-vous d'avoir ce drawable
            .into(playerLogoImageView)
    }
}