package com.sch.plancast.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class ForecastResponse {

    // OpenWeatherMap 5 Day / 3 Hour Forecast API의 예보 목록입니다.
    // 응답 JSON의 "list" 배열에는 3시간 간격 예보가 순서대로 들어옵니다.
    @SerializedName("list")
    private List<ForecastItem> forecastItems;

    // 외부 코드가 null 체크 없이 반복문을 돌릴 수 있도록 빈 리스트를 반환합니다.
    public List<ForecastItem> getForecastItems() {
        if (forecastItems == null) {
            return Collections.emptyList();
        }
        return forecastItems;
    }

    public static class ForecastItem {

        // Unix timestamp입니다. 예보 시점을 초 단위 숫자로 받을 때 사용합니다.
        @SerializedName("dt")
        private Long dt;

        // 사람이 읽기 쉬운 예보 시간 문자열입니다. 예: "2026-06-14 12:00:00"
        @SerializedName("dt_txt")
        private String dateTimeText;

        @SerializedName("main")
        private Main main;

        @SerializedName("weather")
        private List<Weather> weather;

        @SerializedName("wind")
        private Wind wind;

        public long getDt() {
            return dt == null ? 0L : dt;
        }

        public String getDateTimeText() {
            return safeString(dateTimeText);
        }

        public String getDtTxt() {
            return getDateTimeText();
        }

        public Double getTemperature() {
            return main == null ? null : main.temperature;
        }

        public String getWeatherMain() {
            Weather firstWeather = getFirstWeather();
            return firstWeather == null ? "" : safeString(firstWeather.main);
        }

        public String getDescription() {
            Weather firstWeather = getFirstWeather();
            return firstWeather == null ? "" : safeString(firstWeather.description);
        }

        public Double getWindSpeed() {
            return wind == null ? null : wind.speed;
        }

        // weather 배열이 비어 있거나 null이어도 앱이 죽지 않도록 첫 항목을 안전하게 꺼냅니다.
        private Weather getFirstWeather() {
            if (weather == null || weather.isEmpty()) {
                return null;
            }
            return weather.get(0);
        }

        private String safeString(String value) {
            return value == null ? "" : value;
        }
    }

    public static class Main {

        @SerializedName("temp")
        private Double temperature;
    }

    public static class Weather {

        @SerializedName("main")
        private String main;

        @SerializedName("description")
        private String description;
    }

    public static class Wind {

        @SerializedName("speed")
        private Double speed;
    }
}
