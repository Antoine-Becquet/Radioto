package com.antoinebecquet.radioto.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RadioApiService {
    @GET("stations")
    suspend fun getStations(
        @Query("hidebroken") hideBroken: String = "true",
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: String = "true"
    ): List<ApiRadioStation>

    @GET("stations/bycountrycode/{countryCode}")
    suspend fun getStationsByCountry(
        @Path("countryCode") countryCode: String,
        @Query("hidebroken") hideBroken: String = "true",
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: String = "true"
    ): List<ApiRadioStation>

    @GET("countries")
    suspend fun getCountries(): List<ApiCountry>
}