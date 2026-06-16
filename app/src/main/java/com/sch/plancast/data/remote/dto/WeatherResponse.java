package com.sch.plancast.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class WeatherResponse {

    // 날씨 상태 리스트
    @SerializedName("weather")
    private List<Weather> weather;

    // 기온 정보
    @SerializedName("main")
    private Main main;

    // 풍속 정보
    @SerializedName("wind")
    private Wind wind;

    // 주요 날씨 상태 반환
    public String getWeatherMain() {
        if (weather == null || weather.isEmpty() || weather.get(0) == null) {
            return "";
        }
        return safeString(weather.get(0).main);
    }

    // 날씨 설명 반환
    public String getWeatherDescription() {
        if (weather == null || weather.isEmpty() || weather.get(0) == null) {
            return "";
        }
        return safeString(weather.get(0).description);
    }

    // 현재 기온 반환
    public Double getCurrentTemperature() {
        return main == null ? null : main.temperature;
    }

    // 최고 기온 반환
    public Double getMaxTemperature() {
        return main == null ? null : main.maxTemperature;
    }

    // 최저 기온 반환
    public Double getMinTemperature() {
        return main == null ? null : main.minTemperature;
    }

    // 풍속 반환
    public Double getWindSpeed() {
        return wind == null ? null : wind.speed;
    }

    // null 방지 문자열 처리함
    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public static class Weather {
        @SerializedName("main")
        private String main;

        @SerializedName("description")
        private String description; // 상태 설명
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
