package com.antoinebecquet.radioto

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.compose.ui.semantics.text
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlin.text.isNullOrBlank

class StationAdapter(
    private val stations: List<RadioStation>, // Modifié pour ne pas être nullable, gérer la nullité en amont
    private val onItemClick: (RadioStation, Int) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    private var currentlyPlayingIndex = -1

    init {
        Log.d("StationAdapter", "Adaptateur initialisé avec ${stations.size} stations.")
    }

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stationNameTextView: TextView = itemView.findViewById(R.id.textStationName)
        val stationLogoImageView: ImageView = itemView.findViewById(R.id.logoImageView)
        val stationCardView: CardView = itemView.findViewById(R.id.stationCardView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position < stations.size) { // Vérifier la taille
                    onItemClick(stations[position], position)
                } else {
                    Log.w("StationAdapter", "Clic sur une position invalide: $position, taille stations: ${stations.size}")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        Log.d("StationAdapter", "onCreateViewHolder appelé pour viewType: $viewType")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        if (position < 0 || position >= stations.size) {
            Log.e("StationAdapter", "onBindViewHolder: Position invalide $position, taille ${stations.size}. Ne rien faire.")
            return
        }
        val station = stations[position]
        Log.d("StationAdapter", "onBindViewHolder appelé pour position: $position, station: ${station.name}")

        holder.stationNameTextView.text = station.name

        // --- DÉBUT LOGS DE DÉBOGAGE POUR GLIDE ---
        val context = holder.itemView.context
        Log.d("StationAdapter", "Binding station: ${station.name}, logoResIdName à chercher: '${station.logoResId}'")

        var logoResId = 0 // Initialiser à 0 (non trouvé)
        if (!station.logoResId.isNullOrBlank()) {
            logoResId = context.resources.getIdentifier(
                station.logoResId, "drawable", context.packageName
            )
            Log.d("StationAdapter", "Pour '${station.logoResId}', getIdentifier a retourné: $logoResId (0 signifie non trouvé)")
        } else {
            Log.w("StationAdapter", "logoResIdName est null ou vide pour la station: ${station.name}")
        }
        // --- FIN LOGS DE DÉBOGAGE POUR GLIDE ---

        Glide.with(context)
            .load(if (logoResId != 0) logoResId else R.drawable.default_logo) // default_logo doit exister
            .placeholder(R.drawable.default_logo) // default_logo doit exister
            .error(R.drawable.default_logo)       // default_logo doit exister
            .into(holder.stationLogoImageView)

        if (position == currentlyPlayingIndex) {
            holder.stationCardView.setCardBackgroundColor(Color.LTGRAY)
            Log.d("StationAdapter", "Highlighting station: ${station.name} à la position $position")
        } else {
            holder.stationCardView.setCardBackgroundColor(com.google.android.material.R.attr.colorSurface) // Ou la couleur par défaut
        }
    }

    override fun getItemCount(): Int {
        val count = stations.size
        Log.d("StationAdapter", "getItemCount() retourne: $count")
        return count
    }

    fun setCurrentlyPlaying(index: Int) {
        Log.d("StationAdapter", "setCurrentlyPlaying appelé avec index: $index (précédent: $currentlyPlayingIndex)")
        val previousPlayingIndex = currentlyPlayingIndex
        currentlyPlayingIndex = if (index >= 0 && index < stations.size) index else -1 // Valider l'index

        // Notifier le changement pour l'ancien élément s'il était valide
        if (previousPlayingIndex != -1 && previousPlayingIndex < itemCount) {
            // itemCount est sûr ici
            notifyItemChanged(previousPlayingIndex)
        }
        // Notifier le changement pour le nouvel élément s'il est valide
        if (currentlyPlayingIndex != -1) { // Pas besoin de vérifier < itemCount car déjà validé
            notifyItemChanged(currentlyPlayingIndex)
        }
        Log.d("StationAdapter", "setCurrentlyPlaying: nouvel index $currentlyPlayingIndex, ancien $previousPlayingIndex. Notification des changements.")
    }
}