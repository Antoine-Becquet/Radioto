package com.antoinebecquet.radioto

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val stations: List<RadioStation>,
    private val onStationClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false) as CardView
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.bind(station, onStationClick)
    }

    override fun getItemCount(): Int = stations.size

    class StationViewHolder(private val cardView: CardView) : RecyclerView.ViewHolder(cardView) {
        fun bind(station: RadioStation, onClick: (RadioStation) -> Unit) {
            val textView = cardView.findViewById<TextView>(R.id.textStationName)
            val logoView = cardView.findViewById<ImageView>(R.id.logoImageView)

            textView.text = station.name

            // 🔹 Gestion du mode sombre pour la couleur du texte
            val isDarkMode = cardView.context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            textView.setTextColor(
                if (isDarkMode) ContextCompat.getColor(cardView.context, R.color.white)
                else ContextCompat.getColor(cardView.context, R.color.black)
            )

            try {
                logoView.setImageResource(station.logoResId)
            } catch (e: Exception) {
                Log.e("StationAdapter", "Image not found, using default logo")
                logoView.setImageResource(R.drawable.default_logo)
            }

            cardView.setOnClickListener {
                Log.d("StationAdapter", "Clicked on: ${station.name}")
                onClick(station)
            }
        }
    }
}
