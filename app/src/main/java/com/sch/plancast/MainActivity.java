package com.sch.plancast;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.remote.dto.ForecastResponse;
import com.sch.plancast.data.repository.ScheduleRepository;
import com.sch.plancast.data.repository.WeatherRepository;
import com.sch.plancast.domain.WeatherAdviceResult;
import com.sch.plancast.domain.WeatherAdvisor;
import com.sch.plancast.location.LocationProvider;
import com.sch.plancast.notification.AlarmScheduler;
import com.sch.plancast.notification.DailyWeatherCheckReceiver;
import com.sch.plancast.ui.schedule.ScheduleAdapter;
import com.sch.plancast.ui.schedule.ScheduleFormActivity;
import com.sch.plancast.ui.weather.DailyForecastAdapter;
import com.sch.plancast.ui.weather.DailyForecastItem;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String WEATHER_CHECK_TAG = "PlanCastWeatherCheck";
    private static final int MAX_FORECAST_DAYS = 5;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1002;

    private ScheduleRepository scheduleRepository;
    private WeatherRepository weatherRepository;
    private WeatherAdvisor weatherAdvisor;
    private ScheduleAdapter scheduleAdapter;
    private DailyForecastAdapter dailyForecastAdapter;
    private LocationProvider locationProvider;
    private View weatherContentView;
    private View scheduleContentView;
    private FloatingActionButton addScheduleButton;
    private TextView locationStatusTextView;
    private TextView tempTextView;
    private TextView weatherInfoTextView;
    private TextView weatherRiskTextView;
    private TextView weatherRecommendationTextView;
    private TextView forecastEmptyTextView;
    private TextView selectedDateTextView;
    private TextView emptyTextView;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // 엣지 투 에지 시스템바 패딩 적용함
        View mainView = findViewById(R.id.main);
        int initialLeftPadding = mainView.getPaddingLeft();
        int initialTopPadding = mainView.getPaddingTop();
        int initialRightPadding = mainView.getPaddingRight();
        int initialBottomPadding = mainView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    initialLeftPadding + systemBars.left,
                    initialTopPadding + systemBars.top,
                    initialRightPadding + systemBars.right,
                    initialBottomPadding + systemBars.bottom
            );
            return insets;
        });

        // 저장소 및 도메인 객체 초기화함
        scheduleRepository = new ScheduleRepository(this);
        weatherRepository = new WeatherRepository();
        weatherAdvisor = new WeatherAdvisor();
        locationProvider = new LocationProvider(this);
        selectedDate = formatDate(System.currentTimeMillis());

        // UI 뷰 컴포넌트 초기화함
        weatherContentView = findViewById(R.id.weatherContentView);
        scheduleContentView = findViewById(R.id.scheduleContentView);
        locationStatusTextView = findViewById(R.id.locationStatusTextView);
        tempTextView = findViewById(R.id.tempTextView);
        weatherInfoTextView = findViewById(R.id.weatherInfoTextView);
        weatherRiskTextView = findViewById(R.id.weatherRiskTextView);
        weatherRecommendationTextView = findViewById(R.id.weatherRecommendationTextView);
        forecastEmptyTextView = findViewById(R.id.forecastEmptyTextView);
        selectedDateTextView = findViewById(R.id.selectedDateTextView);
        emptyTextView = findViewById(R.id.emptyTextView);

        // 예보 리스트 어댑터 및 리사이클러뷰 설정함
        RecyclerView forecastRecyclerView = findViewById(R.id.forecastRecyclerView);
        dailyForecastAdapter = new DailyForecastAdapter();
        forecastRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        forecastRecyclerView.setAdapter(dailyForecastAdapter);

        // 일정 리스트 어댑터 및 리사이클러뷰 설정함
        RecyclerView scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView);
        scheduleAdapter = new ScheduleAdapter(schedule -> {
            Intent intent = new Intent(MainActivity.this, ScheduleFormActivity.class);
            intent.putExtra(ScheduleFormActivity.EXTRA_SCHEDULE_ID, schedule.getId());
            startActivity(intent);
        });
        scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        scheduleRecyclerView.setAdapter(scheduleAdapter);

        // 달력 날짜 변경 시 일정 새로고침함
        android.widget.CalendarView calendarView = findViewById(R.id.calendarView);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            updateSelectedDateLabel();
            loadSchedulesBySelectedDate();
        });

        // 일정 추가 버튼 클릭 시 폼 이동함
        addScheduleButton = findViewById(R.id.addScheduleButton);
        addScheduleButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ScheduleFormActivity.class);
            intent.putExtra(ScheduleFormActivity.EXTRA_DATE, selectedDate);
            startActivity(intent);
        });

        // 테스트 알림 실행 버튼 설정함
        Button testNotificationButton = findViewById(R.id.testNotificationButton);
        testNotificationButton.setOnClickListener(view -> {
            Log.d(WEATHER_CHECK_TAG, "테스트 알림 예약 버튼 클릭됨");
            requestNotificationPermissionIfNeeded();
            new AlarmScheduler().scheduleDailyWeatherCheckForTest(MainActivity.this);
            Toast.makeText(
                    MainActivity.this,
                    "1분 뒤 테스트 알림이 실행됩니다.",
                    Toast.LENGTH_SHORT
            ).show();
            Log.d(WEATHER_CHECK_TAG, "테스트 알림 예약 Toast 표시 완료");
        });

        // 하단 탭 및 초기 상태 설정함
        setupBottomNavigation();
        updateSelectedDateLabel();
        checkLocationPermissionAndLoadLocation();
        new AlarmScheduler().scheduleDailyWeatherCheck(this);
    }

    // 하단 탭 네비게이션 설정함
    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_weather) {
                showWeatherTab();
                return true;
            }
            if (itemId == R.id.navigation_schedule) {
                showScheduleTab();
                return true;
            }
            return false;
        });

        bottomNavigationView.setSelectedItemId(R.id.navigation_weather);
        showWeatherTab();
    }

    // 날씨 탭 표시함
    private void showWeatherTab() {
        weatherContentView.setVisibility(View.VISIBLE);
        scheduleContentView.setVisibility(View.GONE);
        addScheduleButton.setVisibility(View.GONE);
    }

    // 일정 탭 표시함
    private void showScheduleTab() {
        weatherContentView.setVisibility(View.GONE);
        scheduleContentView.setVisibility(View.VISIBLE);
        addScheduleButton.setVisibility(View.VISIBLE);
        loadSchedulesBySelectedDate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSchedulesBySelectedDate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduleRepository != null) {
            scheduleRepository.shutdown();
        }
    }

    // 권한 요청 결과 처리함
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
                handleNotificationPermissionResult(grantResults);
            }
            return;
        }

        if (isLocationPermissionGranted(grantResults)) {
            loadCurrentLocation();
        } else {
            showLocationPermissionDeniedMessage();
        }
        requestNotificationPermissionIfNeeded();
    }

    // 위치 권한 확인 및 위치 로드함
    private void checkLocationPermissionAndLoadLocation() {
        if (hasLocationPermission()) {
            loadCurrentLocation();
            requestNotificationPermissionIfNeeded();
            return;
        }

        locationStatusTextView.setText("위치 권한을 요청하고 있습니다.");
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationPermissionGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    // 알림 권한 필요한 경우 요청함
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST_CODE
        );
    }

    private void handleNotificationPermissionResult(int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "야외 일정 알림을 받을 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(
                this,
                "알림 권한이 없어 야외 일정 알림이 표시되지 않을 수 있습니다.",
                Toast.LENGTH_LONG
        ).show();
    }

    // 현재 기기 위치 및 날씨 정보 불러옴
    private void loadCurrentLocation() {
        locationStatusTextView.setText("📍 위치 확인 중...");
        weatherInfoTextView.setText("현재 위치를 확인한 뒤 날씨를 불러옵니다.");
        showWeatherAdviceUnavailable();
        showForecastLoading();
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override
            public void onLocationReceived(double latitude, double longitude) {
                saveLastLocation(latitude, longitude);
                new Thread(() -> {
                    String address = getAddressFromLocation(latitude, longitude);
                    runOnUiThread(() -> {
                        locationStatusTextView.setText(address);
                        loadCurrentWeather(latitude, longitude);
                        loadForecast(latitude, longitude);
                    });
                }).start();
            }

            @Override
            public void onLocationError(String errorMessage) {
                runOnUiThread(() -> {
                    locationStatusTextView.setText("📍 위치 확인 실패");
                    weatherInfoTextView.setText("위치를 확인할 수 없어 날씨를 불러오지 않았습니다.");
                    showWeatherAdviceUnavailable();
                    showForecastUnavailable("위치를 확인할 수 없어 5일 예보를 불러오지 않았습니다.");
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 마지막 위치 정보 저장함
    private void saveLastLocation(double latitude, double longitude) {
        SharedPreferences sharedPreferences = getSharedPreferences(
                DailyWeatherCheckReceiver.WEATHER_PREFS_NAME,
                MODE_PRIVATE
        );

        sharedPreferences.edit()
                .putBoolean(DailyWeatherCheckReceiver.KEY_HAS_LAST_LOCATION, true)
                .putString(DailyWeatherCheckReceiver.KEY_LAST_LATITUDE, String.valueOf(latitude))
                .putString(DailyWeatherCheckReceiver.KEY_LAST_LONGITUDE, String.valueOf(longitude))
                .apply();
    }

    // 좌표를 주소 문자열로 변환함
    private String getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder("📍 ");
                if (address.getAdminArea() != null) {
                    sb.append(address.getAdminArea()).append(" ");
                }
                if (address.getLocality() != null) {
                    sb.append(address.getLocality());
                } else if (address.getSubLocality() != null) {
                    sb.append(address.getSubLocality());
                }
                return sb.toString().trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "📍 위치 확인 중...";
    }

    // 현재 날씨 API 호출함
    private void loadCurrentWeather(double latitude, double longitude) {
        weatherInfoTextView.setText("현재 날씨를 불러오고 있습니다.");
        weatherRepository.getCurrentWeather(latitude, longitude, new WeatherRepository.WeatherCallback() {
            @Override
            public void onSuccess(WeatherRepository.WeatherInfo weatherInfo) {
                runOnUiThread(() -> {
                    if (tempTextView != null) {
                        tempTextView.setText(String.format(Locale.US, "%.1f°", weatherInfo.getCurrentTemperature()));
                    }
                    String infoText = String.format(Locale.US, "%s\n현재 기온 %.1f°  |  풍속 %.1fm/s",
                            weatherInfo.getWeatherStatus(),
                            weatherInfo.getCurrentTemperature(),
                            weatherInfo.getWindSpeed());
                    weatherInfoTextView.setText(infoText);
                    updateWeatherAdvice(weatherInfo);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (tempTextView != null) {
                        tempTextView.setText("--°");
                    }
                    weatherInfoTextView.setText(errorMessage);
                    showWeatherAdviceUnavailable();
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 5일 일기 예보 API 호출함
    private void loadForecast(double latitude, double longitude) {
        showForecastLoading();
        weatherRepository.getForecast(latitude, longitude, new WeatherRepository.ForecastCallback() {
            @Override
            public void onSuccess(ForecastResponse forecastResponse) {
                runOnUiThread(() -> {
                    List<DailyForecastItem> forecastItems = createDailyForecastItems(forecastResponse);
                    dailyForecastAdapter.submitList(forecastItems);
                    if (forecastItems.isEmpty()) {
                        forecastEmptyTextView.setVisibility(View.VISIBLE);
                        forecastEmptyTextView.setText("표시할 5일 예보가 없습니다.");
                    } else {
                        forecastEmptyTextView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showForecastUnavailable(errorMessage));
            }
        });
    }

    // 예보 데이터를 날짜별 요약 항목으로 변환함
    private List<DailyForecastItem> createDailyForecastItems(ForecastResponse forecastResponse) {
        if (forecastResponse == null) {
            return Collections.emptyList();
        }

        Map<String, DailyForecastSummary> dailySummaries = new LinkedHashMap<>();
        for (ForecastResponse.ForecastItem forecastItem : forecastResponse.getForecastItems()) {
            String date = extractForecastDate(forecastItem);
            Double temperature = forecastItem == null ? null : forecastItem.getTemperature();
            if (date == null || temperature == null) {
                continue;
            }

            DailyForecastSummary summary = dailySummaries.get(date);
            if (summary == null) {
                summary = new DailyForecastSummary(date);
                dailySummaries.put(date, summary);
            }
            summary.add(forecastItem);
        }

        List<DailyForecastItem> displayItems = new ArrayList<>();
        for (DailyForecastSummary summary : dailySummaries.values()) {
            if (displayItems.size() >= MAX_FORECAST_DAYS) {
                break;
            }
            if (summary.hasTemperature()) {
                displayItems.add(summary.toDisplayItem());
            }
        }
        return displayItems;
    }

    // 예보 항목에서 날짜 문자열 추출함
    private String extractForecastDate(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null) {
            return null;
        }

        String dateTimeText = forecastItem.getDtTxt();
        if (dateTimeText == null || dateTimeText.length() < 10) {
            return null;
        }
        return dateTimeText.substring(0, 10);
    }

    // 예보 항목의 위험 여부 확인함
    private boolean isRiskyForecastItem(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null) {
            return false;
        }

        return weatherAdvisor.advise(
                forecastItem.getWeatherMain(),
                forecastItem.getDescription(),
                forecastItem.getTemperature(),
                forecastItem.getWindSpeed()
        ).hasRisk();
    }

    // 대표 예보 항목을 교체해야 하는지 판단함
    private boolean shouldReplaceRepresentative(
            ForecastResponse.ForecastItem candidate,
            boolean candidateHasRisk,
            ForecastResponse.ForecastItem current,
            boolean currentHasRisk
    ) {
        if (current == null) {
            return true;
        }
        if (candidateHasRisk && !currentHasRisk) {
            return true;
        }
        if (!candidateHasRisk && currentHasRisk) {
            return false;
        }
        return getNoonDistanceMinutes(candidate) < getNoonDistanceMinutes(current);
    }

    // 정오와의 시간 차이 계산함
    private int getNoonDistanceMinutes(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null || forecastItem.getDtTxt().length() < 16) {
            return Integer.MAX_VALUE;
        }

        try {
            int hour = Integer.parseInt(forecastItem.getDtTxt().substring(11, 13));
            int minute = Integer.parseInt(forecastItem.getDtTxt().substring(14, 16));
            int totalMinutes = hour * 60 + minute;
            return Math.abs(totalMinutes - 12 * 60);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    // 날짜 문자열에서 요일 추출함
    private String getDayOfWeek(String dateText) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        inputFormat.setLenient(false);
        SimpleDateFormat outputFormat = new SimpleDateFormat("E요일", Locale.KOREAN);

        try {
            Date date = inputFormat.parse(dateText);
            return date == null ? "" : outputFormat.format(date);
        } catch (Exception exception) {
            return "";
        }
    }

    // 예보 항목의 날씨 설명 텍스트 반환함
    private String getForecastDescription(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null) {
            return "날씨 정보 없음";
        }

        String description = forecastItem.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }

        String weatherMain = forecastItem.getWeatherMain();
        if (weatherMain != null && !weatherMain.trim().isEmpty()) {
            return weatherMain;
        }
        return "날씨 정보 없음";
    }

    // 상세 예보 텍스트 생성함
    private String getForecastDetailText(boolean hasRisk, double windSpeed) {
        if (Double.isNaN(windSpeed)) {
            return hasRisk ? "위험 주의" : "풍속 정보 없음";
        }

        if (hasRisk) {
            return String.format(Locale.US, "위험 주의 · 풍속 %.1fm/s", windSpeed);
        }
        return String.format(Locale.US, "풍속 %.1fm/s", windSpeed);
    }

    // 날씨 조언 UI 갱신함
    private void updateWeatherAdvice(WeatherRepository.WeatherInfo weatherInfo) {
        WeatherAdviceResult adviceResult = weatherAdvisor.advise(
                weatherInfo.getWeatherMain(),
                weatherInfo.getWeatherDescription(),
                weatherInfo.getCurrentTemperature(),
                weatherInfo.getWindSpeed()
        );
        weatherRiskTextView.setText(adviceResult.getRiskMessage());
        weatherRecommendationTextView.setText(adviceResult.getRecommendedItems());
    }

    // 날씨 정보 부재 시 UI 상태 처리함
    private void showWeatherAdviceUnavailable() {
        weatherRiskTextView.setText("날씨 정보를 불러온 뒤 확인할 수 있습니다.");
        weatherRecommendationTextView.setText("날씨 정보를 불러온 뒤 확인할 수 있습니다.");
    }

    // 예보 로딩 상태 표시함
    private void showForecastLoading() {
        if (dailyForecastAdapter != null) {
            dailyForecastAdapter.submitList(Collections.emptyList());
        }
        if (forecastEmptyTextView != null) {
            forecastEmptyTextView.setVisibility(View.VISIBLE);
            forecastEmptyTextView.setText("5일 예보를 불러오는 중입니다.");
        }
    }

    // 예보 정보를 불러올 수 없을 때 UI 처리함
    private void showForecastUnavailable(String message) {
        if (dailyForecastAdapter != null) {
            dailyForecastAdapter.submitList(Collections.emptyList());
        }
        if (forecastEmptyTextView != null) {
            forecastEmptyTextView.setVisibility(View.VISIBLE);
            forecastEmptyTextView.setText(message);
        }
    }

    // 위치 권한 거부 시 UI 상태 표시함
    private void showLocationPermissionDeniedMessage() {
        String message = "위치 권한이 거부되어 현재 위치 기반 날씨를 사용할 수 없습니다.";
        locationStatusTextView.setText(message);
        if (tempTextView != null) {
            tempTextView.setText("--°");
        }
        weatherInfoTextView.setText("위치 권한을 허용하면 날씨 정보를 확인할 수 있습니다.");
        showWeatherAdviceUnavailable();
        showForecastUnavailable("위치 권한을 허용하면 5일 예보를 확인할 수 있습니다.");
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // 선택된 날짜의 일정 목록 로드함
    private void loadSchedulesBySelectedDate() {
        String requestedDate = selectedDate;
        scheduleRepository.getByDate(requestedDate, new ScheduleRepository.RepositoryCallback<List<ScheduleEntity>>() {
            @Override
            public void onSuccess(List<ScheduleEntity> schedules) {
                runOnUiThread(() -> {
                    if (!requestedDate.equals(selectedDate)) {
                        return;
                    }
                    scheduleAdapter.submitList(schedules);
                    emptyTextView.setVisibility(schedules.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "일정을 불러오지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    // 선택된 날짜 레이블 갱신함
    private void updateSelectedDateLabel() {
        selectedDateTextView.setText(selectedDate + " 일정");
    }

    // 타임스탬프를 날짜 문자열로 변환함
    private String formatDate(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timeMillis));
    }

    // 일일 예보 요약을 관리하는 내부 클래스임
    private class DailyForecastSummary {

        private final String date;
        private double maxTemperature = -Double.MAX_VALUE;
        private double minTemperature = Double.MAX_VALUE;
        private double maxWindSpeed = Double.NaN;
        private boolean hasRisk;
        private boolean representativeHasRisk;
        private ForecastResponse.ForecastItem representativeItem;

        DailyForecastSummary(String date) {
            this.date = date;
        }

        // 특정 시간대의 예보 데이터 추가함
        void add(ForecastResponse.ForecastItem forecastItem) {
            Double temperature = forecastItem.getTemperature();
            if (temperature != null) {
                maxTemperature = Math.max(maxTemperature, temperature);
                minTemperature = Math.min(minTemperature, temperature);
            }

            Double windSpeed = forecastItem.getWindSpeed();
            if (windSpeed != null && (Double.isNaN(maxWindSpeed) || windSpeed > maxWindSpeed)) {
                maxWindSpeed = windSpeed;
            }

            boolean itemHasRisk = isRiskyForecastItem(forecastItem);
            hasRisk = hasRisk || itemHasRisk;
            if (shouldReplaceRepresentative(
                    forecastItem,
                    itemHasRisk,
                    representativeItem,
                    representativeHasRisk
            )) {
                representativeItem = forecastItem;
                representativeHasRisk = itemHasRisk;
            }
        }

        // 유효한 기온 정보 보유 여부 확인함
        boolean hasTemperature() {
            return maxTemperature > -Double.MAX_VALUE && minTemperature < Double.MAX_VALUE;
        }

        // UI 표시용 일일 예보 항목으로 변환함
        DailyForecastItem toDisplayItem() {
            return new DailyForecastItem(
                    date,
                    getDayOfWeek(date),
                    getForecastDescription(representativeItem),
                    maxTemperature,
                    minTemperature,
                    getForecastDetailText(hasRisk, maxWindSpeed)
            );
        }
    }
}
