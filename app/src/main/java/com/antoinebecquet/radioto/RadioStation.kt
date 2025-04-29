package com.antoinebecquet.radioto

import com.google.gson.annotations.SerializedName

data class RadioStation(
    @SerializedName("name") val name: String,
    @SerializedName("streamUrl") val streamUrl: String,
    @SerializedName("logoResId") val logoResId: String
)
