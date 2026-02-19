package com.antoinebecquet.radioto.data

import com.google.gson.annotations.SerializedName

data class ApiCountry(
    @SerializedName("name")
    val name: String,
    @SerializedName("iso_3166_1")
    val code: String
)
