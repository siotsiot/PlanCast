package com.sch.plancast.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class WeatherResponse {

    @SerializedName("weather")
    private List<Weather> weather;

    @SerializedName("main")
    private Main main;

    @SerializedName("wind")
    private Wind wind;

    public String getWeatherMain() {
        if (weather == null || weather.isEmpty() || weather.get(0) == null) {
            return "";
        }
        return safeString(weather.get(0).main);
    }

    public String getWeatherDescription() {
        if (weather == null || weather.isEmpty() || weather.get(0) == null) {
            return "";
        }
        return safeString(weather.get(0).description);
    }

    public Double getCurrentTemperature() {
        return main == null ? null : main.temperature;
    }

    public Double getMaxTemperature() {
        return main == null ? null : main.maxTemperature;
    }

    public Double getMinTemperature() {
        return main == null ? null : main.minTemperature;
    }

    public Double getWindSpeed() {
        return wind == null ? null : wind.speed;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public static class Weather {

        @SerializedName("main")
        private String main;

        @SerializedName("description")
        private String description;
    }

    public static class Main {

        @SerializedName("temp")
        private Double temperature;

        @SerializedName("temp_max")
        private Double maxTemperature;

        @SerializedName("temp_min")
        private Double minTemperature;
    }

    public static class Wind {

        @SerializedName("speed")
        private Double speed;
    }
}
