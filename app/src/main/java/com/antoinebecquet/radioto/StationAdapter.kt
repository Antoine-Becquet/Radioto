package com.antoinebecquet.radioto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StationAdapter(
    private var stations: List<RadioStation>,
    private val onItemClick: (RadioStation, Int) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    private var currentlyPlayingIndex = -1

    fun updateStations(newStations: List<RadioStation>) {
        stations = newStations
        notifyDataSetChanged()
    }

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stationNameTextView: TextView = itemView.findViewById(R.id.textStationName)
        val stationLogoImageView: ImageView = itemView.findViewById(R.id.logoImageView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(stations[position], position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.stationNameTextView.text = station.name

        Glide.with(holder.itemView.context)
            .load(station.iconUrl)
            .placeholder(R.drawable.default_logo) // Assurez-vous d'avoir un drawable default_logo
            .error(R.drawable.default_logo)
            .into(holder.stationLogoImageView)

        holder.itemView.isActivated = (position == currentlyPlayingIndex)
    }

    override fun getItemCount(): Int = stations.size

    fun setCurrentlyPlaying(index: Int) {
        val previousIndex = currentlyPlayingIndex
        currentlyPlayingIndex = index
        if (previousIndex != -1) {
            notifyItemChanged(previousIndex)
        }
        if (currentlyPlayingIndex != -1) {
            notifyItemChanged(currentlyPlayingIndex)
        }
    }
}