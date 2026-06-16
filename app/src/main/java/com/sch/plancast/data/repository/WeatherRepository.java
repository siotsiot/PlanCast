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

// OpenWeatherMap API 호출을 감싸는 Repository 클래스임.
// Activity나 Receiver는 Retrofit을 직접 다루지 않고 이 클래스를 통해 결과만 전달받는다.
public class WeatherRepository {

    private static final String TAG = "WeatherRepository";
    private static final String UNITS_METRIC = "metric";
    private static final String LANGUAGE_KOREAN = "kr";

    private final OpenWeatherApi openWeatherApi;

    public WeatherRepository() {
        // API 서비스 객체 생성함
        openWeatherApi = RetrofitClient.getInstance().create(OpenWeatherApi.class);
    }

    // 현재 위치의 현재 날씨를 조회함.
    // API Key 누락, HTTP 오류, 네트워크 실패, null 응답을 사용자 안내 문구로 변환한다.
    public void getCurrentWeather(double latitude, double longitude, WeatherCallback callback) {
        if (callback == null) {
            return;
        }

        // BuildConfig에서 API 키 가져옴
        String apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("날씨 API 키가 설정되지 않았습니다. local.properties의 OPEN_WEATHER_MAP_API_KEY를 확인하세요.");
            return;
        }

        // 현재 날씨 정보 비동기로 요청함
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

    // 현재 위치의 5일 예보를 조회함.
    // OpenWeatherMap Forecast API는 3시간 단위 예보 list를 반환한다.
    public void getForecast(double latitude, double longitude, ForecastCallback callback) {
        if (callback == null) {
            return;
        }

        String apiKey = BuildConfig.OPEN_WEATHER_MAP_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("날씨 API 키가 설정되지 않았습니다. local.properties의 OPEN_WEATHER_MAP_API_KEY를 확인하세요.");
            return;
        }

        // 5일 일기 예보 데이터 요청함.
        // Current Weather API와 같은 RetrofitClient를 재사용하되 endpoint만 forecast로 다르다.
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
        // 응답 코드별 에러 메시지 반환
        if (code == 401) {
            return "날씨 API 인증에 실패했습니다. API Key 활성화 상태를 확인하세요. (HTTP 401)";
        }
        return "날씨 서버 응답을 받지 못했습니다. 잠시 후 다시 시도하세요. (HTTP " + code + ")";
    }

    private String getNetworkErrorMessage(Throwable throwable) {
        // 네트워크 상태에 따른 에러 메시지 생성함
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

    // WeatherResponse DTO를 화면과 도메인 로직에서 쓰기 쉬운 형태로 변환한 객체임.
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
            // API 응답 데이터를 앱 내부용 객체로 변환함
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
            // 필수 데이터 유무 확인함
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
            // 화면 표시용 텍스트로 변환함
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
