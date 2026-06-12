package com.sch.plancast.data.repository;

import androidx.annotation.NonNull;

import com.sch.plancast.BuildConfig;
import com.sch.plancast.data.remote.OpenWeatherApi;
import com.sch.plancast.data.remote.RetrofitClient;
import com.sch.plancast.data.remote.dto.WeatherResponse;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private static final String UNITS_METRIC = "metric";
    private static final String LANGUAGE_KOREAN = "kr";

    private final OpenWeatherApi openWeatherApi;

    public WeatherRepository() {
        openWeatherApi = RetrofitClient.getInstance().create(OpenWeatherApi.class);
    }

    public void getCurrentWeather(double latitude, double longitude, WeatherCallback callback) {
        if (callback == null) {
            return;
        }

        String apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("OpenWeatherMap API Key가 설정되지 않았습니다");
            return;
        }

        openWeatherApi.getCurrentWeather(
                latitude,
                longitude,
                apiKey,
                UNITS_METRIC,
                LANGUAGE_KOREAN
        ).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (!response.isSuccessful()) {
                    callback.onError("날씨 정보를 불러오지 못했습니다: HTTP " + response.code());
                    return;
                }

                WeatherResponse weatherResponse = response.body();
                if (weatherResponse == null) {
                    callback.onError("날씨 응답이 비어 있습니다");
                    return;
                }

                WeatherInfo weatherInfo = WeatherInfo.from(weatherResponse);
                if (!weatherInfo.hasRequiredValues()) {
                    callback.onError("날씨 응답에 필요한 정보가 없습니다");
                    return;
                }

                callback.onSuccess(weatherInfo);
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable throwable) {
                callback.onError("날씨 조회 실패: " + getErrorMessage(throwable));
            }
        });
    }

    private String getErrorMessage(Throwable throwable) {
        if (throwable.getMessage() == null || throwable.getMessage().isEmpty()) {
            return "네트워크 오류";
        }
        return throwable.getMessage();
    }

    public interface WeatherCallback {
        void onSuccess(WeatherInfo weatherInfo);

        void onError(String errorMessage);
    }

    public static class WeatherInfo {

        private final String weatherStatus;
        private final Double currentTemperature;
        private final Double maxTemperature;
        private final Double minTemperature;
        private final Double windSpeed;

        private WeatherInfo(
                String weatherStatus,
                Double currentTemperature,
                Double maxTemperature,
                Double minTemperature,
                Double windSpeed
        ) {
            this.weatherStatus = weatherStatus;
            this.currentTemperature = currentTemperature;
            this.maxTemperature = maxTemperature;
            this.minTemperature = minTemperature;
            this.windSpeed = windSpeed;
        }

        private static WeatherInfo from(WeatherResponse response) {
            String description = response.getWeatherDescription();
            String main = response.getWeatherMain();
            String weatherStatus = description.isEmpty() ? main : description;

            return new WeatherInfo(
                    weatherStatus,
                    response.getCurrentTemperature(),
                    response.getMaxTemperature(),
                    response.getMinTemperature(),
                    response.getWindSpeed()
            );
        }

        public boolean hasRequiredValues() {
            return weatherStatus != null
                    && !weatherStatus.isEmpty()
                    && currentTemperature != null
                    && maxTemperature != null
                    && minTemperature != null
                    && windSpeed != null;
        }

        public String toDisplayText() {
            return String.format(
                    Locale.US,
                    "현재 날씨: %s\n현재 기온: %.1f℃\n최고/최저: %.1f℃ / %.1f℃\n풍속: %.1fm/s",
                    weatherStatus,
                    currentTemperature,
                    maxTemperature,
                    minTemperature,
                    windSpeed
            );
        }
    }
}
