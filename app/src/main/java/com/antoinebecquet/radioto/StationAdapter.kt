package com.antoinebecquet.radioto

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val stations: List<RadioStation>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<StationAdapter.StationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = stations[position]
        holder.textStationName.text = station.name

        // Conversion de la chaîne de logo en identifiant de ressource (Int)
        val resId = holder.itemView.context.resources.getIdentifier(
            station.logoResId, "drawable", holder.itemView.context.packageName
        )
        holder.logoImageView.setImageResource(resId)

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount(): Int = stations.size

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Correspond aux ID définis dans le layout item_station.xml
        val logoImageView: ImageView = itemView.findViewById(R.id.logoImageView)
        val textStationName: TextView = itemView.findViewById(R.id.textStationName)
    }
}
