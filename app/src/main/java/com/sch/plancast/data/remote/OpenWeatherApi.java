package com.sch.plancast.data.remote;

import com.sch.plancast.data.remote.dto.ForecastResponse;
import com.sch.plancast.data.remote.dto.WeatherResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenWeatherApi {

    // 현재 날씨 API. 메인 화면에서 현재 위치의 실시간 날씨를 보여줄 때 사용함.
    @GET("data/2.5/weather")
    Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double latitude,
            @Query("lon") double longitude,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String language
    );

    // 5일 / 3시간 단위 예보 API.
    @GET("data/2.5/forecast")
    Call<ForecastResponse> getForecast(
            @Query("lat") double latitude,
            @Query("lon") double longitude,
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String language
    );
}
