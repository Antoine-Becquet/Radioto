package com.antoinebecquet.radioto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StationGroupAdapter(
    private var items: List<Any>,
    private val onStationClick: (RadioStation) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var currentlyPlayingId: String? = null

    companion object {
        private const val TYPE_STATION = 0
        private const val TYPE_GROUP = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is RadioStation -> TYPE_STATION
            is StationGroup -> TYPE_GROUP
            else -> throw IllegalArgumentException("Invalid type of data at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_STATION -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
                StationViewHolder(view)
            }
            TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station_group, parent, false)
                GroupViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is StationViewHolder -> {
                val station = items[position] as RadioStation
                holder.bind(station, station.id == currentlyPlayingId)
                holder.itemView.setOnClickListener { onStationClick(station) }
            }
            is GroupViewHolder -> {
                val group = items[position] as StationGroup
                holder.bind(group)
                holder.itemView.setOnClickListener { /* TODO: Handle group click */ }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setCurrentlyPlaying(stationId: String?) {
        currentlyPlayingId = stationId
        notifyDataSetChanged()
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stationName: TextView = itemView.findViewById(R.id.textStationName)
        private val stationIcon: ImageView = itemView.findViewById(R.id.logoImageView)

        fun bind(station: RadioStation, isPlaying: Boolean) {
            stationName.text = station.name
            Glide.with(itemView.context).load(station.iconUrl).into(stationIcon)
            itemView.isActivated = isPlaying
        }
    }

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val groupName: TextView = itemView.findViewById(R.id.groupName)
        private val groupIcon: ImageView = itemView.findViewById(R.id.groupIcon)

        fun bind(group: StationGroup) {
            groupName.text = group.name
            // For simplicity, using the icon of the first station in the group
            Glide.with(itemView.context).load(group.stations.first().iconUrl).into(groupIcon)
        }
    }
}