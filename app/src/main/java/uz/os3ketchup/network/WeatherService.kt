package uz.os3ketchup.network

import retrofit2.Call

import retrofit2.http.GET
import retrofit2.http.Query
import uz.os3ketchup.my_models.MyWeatherResponse

interface WeatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat:Double,
        @Query("lon") lon:Double,
        @Query("units") units:String,
        @Query("appid") appid:String?
    ): Call<MyWeatherResponse>
}