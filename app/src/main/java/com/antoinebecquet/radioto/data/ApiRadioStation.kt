package com.antoinebecquet.radioto.data

import com.google.gson.annotations.SerializedName

data class ApiRadioStation(
    @SerializedName("stationuuid")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("url_resolved")
    val streamUrl: String,
    @SerializedName("favicon")
    val iconUrl: String
)