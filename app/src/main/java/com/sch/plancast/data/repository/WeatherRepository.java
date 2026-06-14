package com.sch.plancast.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.sch.plancast.BuildConfig;
import com.sch.plancast.data.remote.OpenWeatherApi;
import com.sch.plancast.data.remote.RetrofitClient;
import com.sch.plancast.data.remote.dto.ForecastResponse;
import com.sch.plancast.data.remote.dto.WeatherResponse;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WeatherRepository {

    private static final String TAG = "WeatherRepository";
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
            callback.onError("날씨 API 키가 설정되지 않았습니다. local.properties의 OPEN_WEATHER_MAP_API_KEY를 확인하세요.");
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
                    callback.onError(getHttpErrorMessage(response.code()));
                    return;
                }

                WeatherResponse weatherResponse = response.body();
                if (weatherResponse == null) {
                    callback.onError("날씨 서버 응답이 비어 있습니다. 잠시 후 다시 시도하세요.");
                    return;
                }

                WeatherInfo weatherInfo = WeatherInfo.from(weatherResponse);
                if (!weatherInfo.hasRequiredValues()) {
                    callback.onError("날씨 응답에 필요한 기온 또는 풍속 정보가 없습니다.");
                    return;
                }

                callback.onSuccess(weatherInfo);
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable throwable) {
                callback.onError(getNetworkErrorMessage(throwable));
            }
        });
    }

    public void getForecast(double latitude, double longitude, ForecastCallback callback) {
        if (callback == null) {
            return;
        }

        String apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("날씨 API 키가 설정되지 않았습니다. local.properties의 OPEN_WEATHER_MAP_API_KEY를 확인하세요.");
            return;
        }

        // 5일 예보 API도 현재 날씨 API와 동일하게 metric/kr 옵션을 사용합니다.
        // API Key는 BuildConfig에서만 가져오며, 로그나 코드에 직접 노출하지 않습니다.
        openWeatherApi.getForecast(
                latitude,
                longitude,
                apiKey,
                UNITS_METRIC,
                LANGUAGE_KOREAN
        ).enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(@NonNull Call<ForecastResponse> call, @NonNull Response<ForecastResponse> response) {
                if (!response.isSuccessful()) {
                    callback.onError(getHttpErrorMessage(response.code()));
                    return;
                }

                ForecastResponse forecastResponse = response.body();
                if (forecastResponse == null) {
                    callback.onError("날씨 서버 응답이 비어 있습니다. 잠시 후 다시 시도하세요.");
                    return;
                }

                // 테스트용 로그입니다. API Key는 출력하지 않고 예보 항목 개수만 확인합니다.
                Log.d(TAG, "Forecast response count: " + forecastResponse.getForecastItems().size());
                callback.onSuccess(forecastResponse);
            }

            @Override
            public void onFailure(@NonNull Call<ForecastResponse> call, @NonNull Throwable throwable) {
                callback.onError(getNetworkErrorMessage(throwable));
            }
        });
    }

    private String getHttpErrorMessage(int code) {
        if (code == 401) {
            return "날씨 API 인증에 실패했습니다. API Key 활성화 상태를 확인하세요. (HTTP 401)";
        }
        return "날씨 서버 응답을 받지 못했습니다. 잠시 후 다시 시도하세요. (HTTP " + code + ")";
    }

    private String getNetworkErrorMessage(Throwable throwable) {
        if (throwable.getMessage() == null || throwable.getMessage().isEmpty()) {
            return "네트워크 연결을 확인한 뒤 다시 시도하세요.";
        }
        return "네트워크 연결을 확인한 뒤 다시 시도하세요. (" + throwable.getMessage() + ")";
    }

    public interface WeatherCallback {
        void onSuccess(WeatherInfo weatherInfo);

        void onError(String errorMessage);
    }

    public interface ForecastCallback {
        void onSuccess(ForecastResponse forecastResponse);

        void onError(String errorMessage);
    }

    public static class WeatherInfo {

        private final String weatherStatus;
        private final String weatherMain;
        private final String weatherDescription;
        private final Double currentTemperature;
        private final Double maxTemperature;
        private final Double minTemperature;
        private final Double windSpeed;

        private WeatherInfo(
                String weatherStatus,
                String weatherMain,
                String weatherDescription,
                Double currentTemperature,
                Double maxTemperature,
                Double minTemperature,
                Double windSpeed
        ) {
            this.weatherStatus = weatherStatus;
            this.weatherMain = weatherMain;
            this.weatherDescription = weatherDescription;
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
                    main,
                    description,
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

        public String getWeatherStatus() {
            return weatherStatus;
        }

        public String getWeatherMain() {
            return weatherMain;
        }

        public String getWeatherDescription() {
            return weatherDescription;
        }

        public Double getCurrentTemperature() {
            return currentTemperature;
        }

        public Double getMaxTemperature() {
            return maxTemperature;
        }

        public Double getMinTemperature() {
            return minTemperature;
        }

        public Double getWindSpeed() {
            return windSpeed;
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
