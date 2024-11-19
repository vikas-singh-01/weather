package com.singhez.weather.network


import com.singhez.weather.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface weatherService {

    @GET("2.5/weather")
    fun weather(
        @Query("lat") lat:Double,
        @Query("lon") lon:Double,
        @Query("units") units:String?,
        @Query("appid") appid:String
    ) : Call<WeatherResponse>

}