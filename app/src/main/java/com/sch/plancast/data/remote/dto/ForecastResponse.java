package com.sch.plancast.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForecastResponse {

    // 예보 목록 데이터
    @SerializedName("list")
    private List<ForecastItem> forecastItems;

    // 예보 리스트 반환
    public List<ForecastItem> getForecastItems() {
        if (forecastItems == null) {
            return Collections.emptyList();
        }
        return forecastItems;
    }

    // 테스트용 응답 객체 생성
    public static ForecastResponse createForTest(List<ForecastItem> forecastItems) {
        ForecastResponse response = new ForecastResponse();
        response.forecastItems = forecastItems;
        return response;
    }

    // 날짜로 첫 번째 예보 항목 찾음
    public ForecastItem findFirstItemByDateForTest(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }

        for (ForecastItem forecastItem : getForecastItems()) {
            if (forecastItem != null && forecastItem.isSameDateForTest(date)) {
                return forecastItem;
            }
        }
        return null;
    }

    // 테스트용 예보 항목 추가
    public void addForecastItemForTest(ForecastItem forecastItem) {
        if (forecastItem == null) {
            return;
        }
        getMutableForecastItemsForTest().add(forecastItem);
    }

    // 내부 리스트 초기화 및 반환
    private List<ForecastItem> getMutableForecastItemsForTest() {
        if (forecastItems == null) {
            forecastItems = new ArrayList<>();
        }
        return forecastItems;
    }

    public static class ForecastItem {

        // 예보 시점 타임스탬프
        @SerializedName("dt")
        private Long dt;

        // 예보 시간 문자열
        @SerializedName("dt_txt")
        private String dateTimeText;

        // 기온 정보 객체
        @SerializedName("main")
        private Main main;

        // 날씨 상태 리스트
        @SerializedName("weather")
        private List<Weather> weather;

        // 풍속 정보 객체
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

        // 현재 기온 반환
        public Double getTemperature() {
            return main == null ? null : main.temperature;
        }

        // 주요 날씨 상태 반환
        public String getWeatherMain() {
            Weather firstWeather = getFirstWeather();
            return firstWeather == null ? "" : safeString(firstWeather.main);
        }

        // 날씨 상세 설명 반환
        public String getDescription() {
            Weather firstWeather = getFirstWeather();
            return firstWeather == null ? "" : safeString(firstWeather.description);
        }

        // 풍속 반환
        public Double getWindSpeed() {
            return wind == null ? null : wind.speed;
        }

        // 테스트용 항목 생성
        public static ForecastItem createForTest(
                String dateTimeText,
                String weatherMain,
                String description,
                double temperature,
                double windSpeed
        ) {
            ForecastItem forecastItem = new ForecastItem();
            forecastItem.dateTimeText = dateTimeText;

            Main main = new Main();
            main.temperature = temperature;
            forecastItem.main = main;

            Weather weatherItem = new Weather();
            weatherItem.main = weatherMain;
            weatherItem.description = description;
            forecastItem.weather = Collections.singletonList(weatherItem);

            Wind wind = new Wind();
            wind.speed = windSpeed;
            forecastItem.wind = wind;

            return forecastItem;
        }

        // 테스트용 비 예보 생성함
        public static ForecastItem createRainItemForTest(String date) {
            return createForTest(
                    date + " 12:00:00",
                    "Rain",
                    "비",
                    18.0,
                    2.0
            );
        }

        // 테스트용 비 정보 적용함
        public void applyFakeRainForTest() {
            if (main == null) {
                main = new Main();
            }
            main.temperature = 18.0;

            Weather weatherItem = getFirstWeather();
            if (weatherItem == null) {
                weatherItem = new Weather();
                weather = Collections.singletonList(weatherItem);
            }
            weatherItem.main = "Rain";
            weatherItem.description = "비";

            if (wind == null) {
                wind = new Wind();
            }
            wind.speed = 2.0;
        }

        // 동일 날짜 여부 확인함
        public boolean isSameDateForTest(String date) {
            return !safeString(date).isEmpty()
                    && !safeString(dateTimeText).isEmpty()
                    && dateTimeText.startsWith(date + " ");
        }

        // 첫 번째 날씨 객체 안전하게 반환함
        private Weather getFirstWeather() {
            if (weather == null || weather.isEmpty()) {
                return null;
            }
            return weather.get(0);
        }

        // null 방지 문자열 처리함
        private String safeString(String value) {
            return value == null ? "" : value;
        }
    }

    public static class Main {
        // 기온 데이터임
        @SerializedName("temp")
        private Double temperature;
    }

    public static class Weather {
        // 날씨 상태 코드임
        @SerializedName("main")
        private String main;

        // 날씨 설명 문자열임
        @SerializedName("description")
        private String description;
    }

    public static class Wind {
        // 풍속 데이터임
        @SerializedName("speed")
        private Double speed;
    }
}
