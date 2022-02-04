package com.example.weather_app.network

import com.weatherapp.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    /* url -> https://api.openweathermap.org/data/2.5/weather?lat=35&lon=139&appid=API_KEY
    *           base url'den sonra yer alan parametreleri set etmek için ||*/
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String?,
        @Query("appid") appid: String?,
    ): Call<WeatherResponse>
}