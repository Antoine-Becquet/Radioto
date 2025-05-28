package com.antoinebecquet.radioto

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.input.key.type
// import androidx.compose.ui.input.key.type // Import non utilisé
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerControlView
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var stationRecyclerView: RecyclerView
    private lateinit var stationAdapter: StationAdapter
    private var stations: List<RadioStation> = emptyList()

    private lateinit var playerControlView: PlayerControlView
    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerControlView = findViewById(R.id.playerControlView)
        stationRecyclerView = findViewById(R.id.stationRecyclerView)

        stations = loadStationsFromJson()
        Log.d("MainActivity", "Nombre de stations chargées initialement: ${stations.size}")
        if (stations.isEmpty()) {
            Log.e("MainActivity", "AUCUNE STATION CHARGÉE. Vérifiez stations.json.")
        }

        Log.d("MainActivity", "Initialisation de StationAdapter avec ${stations.size} stations.")
        stationAdapter = StationAdapter(stations) { station, _ ->
            mediaController?.let { controller ->
                val currentMediaId = controller.currentMediaItem?.mediaId
                Log.d("MainActivity", "Clic sur la station: ${station.name} (ID: ${station.id}). Actuellement joué (ID): $currentMediaId")

                if (currentMediaId != station.id) {
                    // Construire un MediaItem minimal. L'ID et l'URI sont les plus importants.
                    // Le service utilisera les métadonnées complètes de sa propre instance de MediaItem.
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(station.id) // L'ID est la clé pour que le service trouve l'item dans sa playlist
                        .setUri(station.streamUrl)    // L'URI est aussi généralement nécessaire pour setMediaItem
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(station.name) // Bon à avoir, même si le service l'a aussi
                                // .setArtist(getString(R.string.app_name)) // Optionnel ici
                                // .setArtworkUri(getUriForDrawableByName(station.logoResId)) // Optionnel ici
                                .build()
                        )
                        .build()

                    Log.d("MainActivity", "Appel de controller.setMediaItem pour l'ID: ${station.id}")
                    controller.setMediaItem(mediaItem)
                    controller.prepare() // S'assurer que le player est prêt pour cet item
                    controller.play()    // Démarrer ou reprendre la lecture
                } else {
                    // C'est la même station, on bascule lecture/pause
                    if (controller.isPlaying) {
                        controller.pause()
                        Log.d("MainActivity", "Mise en pause de la station: ${station.name}")
                    } else {
                        controller.play()
                        Log.d("MainActivity", "Reprise de la lecture de la station: ${station.name}")
                    }
                }
            } ?: Log.w("MainActivity", "MediaController non disponible lors du clic sur la station.")
        }
        stationRecyclerView.adapter = stationAdapter
        // Si vous utilisez un LayoutManager en XML, cette ligne n'est pas nécessaire ici.
        // Exemple: stationRecyclerView.layoutManager = GridLayoutManager(this, 2) // Si vous voulez une grille
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, RadioPlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                playerControlView.player = mediaController // Lier le PlayerControlView au MediaController

                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("MainActivity", "Controller Listener: onIsPlayingChanged: $isPlaying")
                        // PlayerControlView se met à jour automatiquement.
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val newTitle = mediaItem?.mediaMetadata?.title ?: "Inconnu"
                        Log.d("MainActivity", "Controller Listener: onMediaItemTransition vers '${newTitle}' (ID: ${mediaItem?.mediaId}). Raison: $reason")
                        mediaItem?.mediaId?.let { currentMediaId ->
                            val playingIndex = stations.indexOfFirst { it.id == currentMediaId }
                            if (playingIndex != -1) {
                                Log.d("MainActivity", "Mise à jour de l'adapter pour l'index: $playingIndex")
                                stationAdapter.setCurrentlyPlaying(playingIndex)
                            } else {
                                Log.w("MainActivity", "MediaId '$currentMediaId' non trouvé dans la liste des stations de l'UI.")
                                stationAdapter.setCurrentlyPlaying(-1) // Réinitialiser si non trouvé
                            }
                        } ?: stationAdapter.setCurrentlyPlaying(-1) // Aucun mediaId, réinitialiser
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("MainActivity", "Controller Listener: Player Error: ${error.message}", error)
                        // Afficher un message d'erreur à l'utilisateur si nécessaire
                    }
                })

                // Mettre à jour l'UI pour la station potentiellement déjà en cours de lecture
                val currentPlayingItem = mediaController?.currentMediaItem
                currentPlayingItem?.mediaId?.let { currentMediaId ->
                    val playingIndex = stations.indexOfFirst { it.id == currentMediaId }
                    if (playingIndex != -1) {
                        Log.d("MainActivity", "onStart - Station actuelle (ID: $currentMediaId) trouvée à l'index: $playingIndex. Mise à jour de l'adapter.")
                        stationAdapter.setCurrentlyPlaying(playingIndex)
                    } else {
                        Log.w("MainActivity", "onStart - Station actuelle (ID: $currentMediaId) non trouvée dans la liste des stations de l'UI.")
                        // stationAdapter.setCurrentlyPlaying(-1) // Optionnel: Réinitialiser si l'item joué n'est pas dans la liste affichée
                    }
                }

            } catch (e: Exception) { // Inclut InterruptedException et ExecutionException
                Log.e("MainActivity", "Erreur lors de la connexion ou de la configuration du MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        playerControlView.player = null // Détacher le lecteur du PlayerControlView
        // Laisser MediaController gérer la libération de la connexion avec releaseFuture
        // Il est important de ne libérer que si controllerFuture a été initialisée.
        if (this::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        mediaController = null // Nettoyer la référence
        Log.d("MainActivity", "onStop - MediaController Future released et référence nettoyée.")
    }

    // getUriForDrawableByName n'est plus directement utilisé dans la logique de changement de station
    // pour construire le MediaItem, car le MediaItem complet avec son artworkUri est
    // géré par le RadioPlayerService.
    // Vous pouvez le conserver si vous en avez d'autres usages ou pour référence.
    private fun getUriForDrawableByName(drawableName: String?): Uri? {
        if (drawableName.isNullOrBlank()) {
            Log.w("MainActivity", "getUriForDrawableByName: Nom du drawable est null ou vide.")
            return null // Ou retourner un URI vers un drawable par défaut si nécessaire ailleurs
        }
        // Tenter de trouver le drawable tel quel
        var resourceId = resources.getIdentifier(drawableName, "drawable", packageName)

        // Si non trouvé et contient une extension, tenter sans l'extension
        if (resourceId == 0 && drawableName.contains(".")) {
            val nameWithoutExtension = drawableName.substringBeforeLast('.')
            Log.d("MainActivity", "getUriForDrawableByName: Non trouvé comme '$drawableName', tentative avec '$nameWithoutExtension'")
            resourceId = resources.getIdentifier(nameWithoutExtension, "drawable", packageName)
        }

        return if (resourceId != 0) {
            Log.d("MainActivity", "getUriForDrawableByName: Trouvé '$drawableName' (ou version sans extension) avec ID: $resourceId")
            Uri.parse("android.resource://$packageName/$resourceId")
        } else {
            Log.e("MainActivity", "getUriForDrawableByName: Drawable non trouvé pour le nom: '$drawableName'.")
            null // Ou retourner un URI vers un drawable par défaut
        }
    }

    private fun loadStationsFromJson(): List<RadioStation> {
        val jsonString: String
        try {
            Log.d("MainActivity", "Tentative de chargement de stations.json depuis les assets.")
            assets.open("stations.json").bufferedReader().use {
                jsonString = it.readText()
            }
            // Log.d("MainActivity", "Contenu JSON brut: $jsonString") // Peut être verbeux
            val typeToken = object : TypeToken<List<RadioStation>>() {}.type
            val loadedStations = Gson().fromJson<List<RadioStation>>(jsonString, typeToken)
            if (loadedStations == null) {
                Log.e("MainActivity", "Gson a retourné null après parsing de stations.json.")
                return emptyList()
            }
            Log.d("MainActivity", "${loadedStations.size} stations parsées depuis JSON.")
            return loadedStations
        } catch (ioException: IOException) {
            Log.e("MainActivity", "IOException lors du chargement des stations depuis JSON", ioException)
        } catch (e: Exception) { // Attrape JsonSyntaxException etc.
            Log.e("MainActivity", "Exception générale lors du parsing JSON des stations", e)
        }
        return emptyList()
    }
}