package com.sch.plancast.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.remote.dto.ForecastResponse;
import com.sch.plancast.data.repository.ScheduleRepository;
import com.sch.plancast.data.repository.WeatherRepository;
import com.sch.plancast.domain.ForecastScheduleMatcher;
import com.sch.plancast.domain.ForecastScheduleRiskResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class DailyWeatherCheckReceiver extends BroadcastReceiver {

    public static final String ACTION_DAILY_WEATHER_CHECK = "com.sch.plancast.action.DAILY_WEATHER_CHECK";
    public static final String WEATHER_PREFS_NAME = "plancast_weather_preferences";
    public static final String KEY_LAST_LATITUDE = "last_latitude";
    public static final String KEY_LAST_LONGITUDE = "last_longitude";
    public static final String KEY_HAS_LAST_LOCATION = "has_last_location";

    private static final String TAG = "PlanCastWeatherCheck";
    private static final int DAILY_WEATHER_NOTIFICATION_ID = 8001;
    private static final String NOTIFICATION_TITLE = "PlanCast 날씨 알림";
    private static final String NOTIFICATION_CONTENT =
            "예정된 야외 일정 날짜에 위험 날씨가 예보되었습니다. 준비물을 확인하세요.";
    private static final String TEST_NOTIFICATION_TITLE = "PlanCast 테스트 알림";
    private static final String TEST_NOTIFICATION_CONTENT =
            "테스트 모드: 예정된 야외 일정 날짜에 위험 날씨가 예보된 상황을 가정했습니다.";

    // 발표/개발 검증용이며 최종 제출 전 false 권장입니다.
    // true이면 DB 조회와 ForecastScheduleMatcher를 거치지 않고 알림 표시 기능만 바로 확인합니다.
    private static final boolean DEBUG_FORCE_NOTIFICATION_ONLY = false;

    // 발표/개발 검증용이며 최종 제출 전 false 권장입니다.
    // true이면 실제 OpenWeatherMap Forecast API 응답 list에서 야외 일정 날짜의 예보를 Rain으로 바꿉니다.
    // 이 모드는 ForecastScheduleMatcher 위험 판단 로직까지 함께 검증할 때 사용합니다.
    private static final boolean DEBUG_USE_FAKE_RAIN_FORECAST = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        AtomicBoolean isFinished = new AtomicBoolean(false);
        Context appContext = context.getApplicationContext();

        try {
            Log.d(TAG, "Receiver 실행됨");
            Log.d(TAG, "DEBUG_FORCE_NOTIFICATION_ONLY 값: " + DEBUG_FORCE_NOTIFICATION_ONLY);
            Log.d(TAG, "DEBUG_USE_FAKE_RAIN_FORECAST 값: " + DEBUG_USE_FAKE_RAIN_FORECAST);

            if (DEBUG_FORCE_NOTIFICATION_ONLY) {
                Log.d(TAG, "강제 알림 모드 진입");
                showDebugRiskNotification(appContext, pendingResult, isFinished);
                return;
            }
            checkOutdoorScheduleWeather(appContext, pendingResult, isFinished);
        } catch (Exception exception) {
            Log.w(TAG, "Daily weather check failed before async work started.", exception);
            finishPendingResult(pendingResult, isFinished);
        }
    }

    private void showDebugRiskNotification(
            Context context,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        try {
            // 발표 시연 및 개발 검증용 경로입니다.
            // DB 일정, 저장 위치, Forecast API 결과와 관계없이 Receiver 알림 표시 자체를 확인할 수 있습니다.
            NotificationHelper notificationHelper = new NotificationHelper(context);
            boolean notificationShown = notificationHelper.showNotification(
                    DAILY_WEATHER_NOTIFICATION_ID,
                    TEST_NOTIFICATION_TITLE,
                    TEST_NOTIFICATION_CONTENT
            );
            Log.d(TAG, "테스트 알림 표시 요청 완료");
            Log.d(TAG, "강제 테스트 알림 실제 표시 여부: " + notificationShown);
            Log.d(TAG, "Debug risk notification shown.");
        } catch (Exception exception) {
            Log.w(TAG, "Failed to show debug risk notification.", exception);
        } finally {
            finishPendingResult(pendingResult, isFinished);
        }
    }

    private void checkOutdoorScheduleWeather(
            Context context,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        ScheduleRepository scheduleRepository = new ScheduleRepository(context);

        // Receiver에서는 UI를 띄울 수 없으므로, 이미 저장된 DB 일정만 조용히 조회합니다.
        // 결과는 Repository callback으로 돌아오며, 모든 경로에서 pendingResult.finish()를 호출해야 합니다.
        scheduleRepository.getOutdoorSchedulesWithinFiveDays(new ScheduleRepository.RepositoryCallback<List<ScheduleEntity>>() {
            @Override
            public void onSuccess(List<ScheduleEntity> schedules) {
                scheduleRepository.shutdown();

                try {
                    Log.d(TAG, "야외 일정 개수: " + (schedules == null ? 0 : schedules.size()));

                    if (schedules == null || schedules.isEmpty()) {
                        Log.d(TAG, "No outdoor schedules to check.");
                        finishPendingResult(pendingResult, isFinished);
                        return;
                    }

                    LastLocation lastLocation = getLastLocation(context);
                    if (lastLocation == null) {
                        Log.d(TAG, "No saved location. Daily weather check skipped.");
                        finishPendingResult(pendingResult, isFinished);
                        return;
                    }

                    fetchForecastAndNotifyIfRisky(
                            context,
                            schedules,
                            lastLocation,
                            pendingResult,
                            isFinished
                    );
                } catch (Exception exception) {
                    Log.w(TAG, "Daily weather check failed after schedule query.", exception);
                    finishPendingResult(pendingResult, isFinished);
                }
            }

            @Override
            public void onError(Exception exception) {
                scheduleRepository.shutdown();
                Log.w(TAG, "Failed to query outdoor schedules.", exception);
                finishPendingResult(pendingResult, isFinished);
            }
        });
    }

    private void fetchForecastAndNotifyIfRisky(
            Context context,
            List<ScheduleEntity> schedules,
            LastLocation lastLocation,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        WeatherRepository weatherRepository = new WeatherRepository();

        // 위치 권한 요청은 Receiver에서 하지 않습니다.
        // MainActivity가 마지막으로 저장한 위도/경도를 사용해 Forecast API만 호출합니다.
        weatherRepository.getForecast(lastLocation.latitude, lastLocation.longitude, new WeatherRepository.ForecastCallback() {
            @Override
            public void onSuccess(ForecastResponse forecastResponse) {
                Log.d(
                        TAG,
                        "Forecast API 응답 list 개수: "
                                + (forecastResponse == null ? 0 : forecastResponse.getForecastItems().size())
                );

                if (DEBUG_USE_FAKE_RAIN_FORECAST) {
                    applyFakeRainForScheduleDates(schedules, forecastResponse);
                }

                handleForecastResult(
                        context,
                        schedules,
                        forecastResponse,
                        pendingResult,
                        isFinished
                );
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Forecast request failed: " + errorMessage);
                finishPendingResult(pendingResult, isFinished);
            }
        });
    }

    private void handleForecastResult(
            Context context,
            List<ScheduleEntity> schedules,
            ForecastResponse forecastResponse,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        try {
            ForecastScheduleMatcher matcher = new ForecastScheduleMatcher();
            List<ForecastScheduleRiskResult> riskySchedules =
                    matcher.findRiskyOutdoorSchedules(schedules, forecastResponse);

            Log.d(TAG, "ForecastScheduleMatcher 결과 개수: " + riskySchedules.size());
            NotificationHelper notificationHelper = new NotificationHelper(context);
            if (!riskySchedules.isEmpty()) {
                // 위험 일정이 여러 개여도 사용자가 과하게 방해받지 않도록 알림은 한 번만 표시합니다.
                boolean notificationShown = notificationHelper.showNotification(
                        DAILY_WEATHER_NOTIFICATION_ID,
                        NOTIFICATION_TITLE,
                        NOTIFICATION_CONTENT
                );
                Log.d(TAG, "알림 표시 여부: " + notificationShown);
            } else {
                Log.d(TAG, "알림 표시 여부: false");
                Log.d(TAG, "No risky outdoor schedules. Notification skipped.");
            }
        } catch (Exception exception) {
            Log.w(TAG, "Failed to match forecast with schedules.", exception);
        } finally {
            finishPendingResult(pendingResult, isFinished);
        }
    }

    private void applyFakeRainForScheduleDates(
            List<ScheduleEntity> schedules,
            ForecastResponse forecastResponse
    ) {
        if (forecastResponse == null || schedules == null) {
            Log.d(TAG, "Rain 테스트 수정 건너뜀: Forecast 응답 또는 일정 목록이 비어 있습니다.");
            return;
        }

        Set<String> addedDates = new LinkedHashSet<>();

        // 발표/개발 검증용이며 최종 제출 전 false 권장입니다.
        // 실제 Forecast API 응답 list를 받은 뒤, 야외 일정 날짜와 같은 예보 항목만 Rain으로 바꿉니다.
        // 같은 날짜 항목이 없을 때만 list에 테스트용 Rain 예보를 추가해 실제 응답 구조를 최대한 유지합니다.
        for (ScheduleEntity schedule : schedules) {
            if (schedule == null || isBlank(schedule.getDate()) || addedDates.contains(schedule.getDate())) {
                continue;
            }

            String scheduleDate = schedule.getDate();
            addedDates.add(scheduleDate);
            Log.d(TAG, "수정 대상 일정 날짜: " + scheduleDate);

            ForecastResponse.ForecastItem forecastItem =
                    forecastResponse.findFirstItemByDateForTest(scheduleDate);
            boolean addedNewItem = false;

            if (forecastItem == null) {
                forecastItem = ForecastResponse.ForecastItem.createRainItemForTest(scheduleDate);
                forecastResponse.addForecastItemForTest(forecastItem);
                addedNewItem = true;
            } else {
                forecastItem.applyFakeRainForTest();
            }

            Log.d(TAG, "Rain으로 수정한 forecast item의 dt_txt: " + forecastItem.getDtTxt());
            Log.d(TAG, "같은 날짜 항목이 없어 새로 추가했는지 여부: " + addedNewItem);
        }
    }

    private LastLocation getLastLocation(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(WEATHER_PREFS_NAME, Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean(KEY_HAS_LAST_LOCATION, false)) {
            return null;
        }

        String latitudeText = sharedPreferences.getString(KEY_LAST_LATITUDE, null);
        String longitudeText = sharedPreferences.getString(KEY_LAST_LONGITUDE, null);
        try {
            return new LastLocation(
                    Double.parseDouble(latitudeText),
                    Double.parseDouble(longitudeText)
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private void finishPendingResult(PendingResult pendingResult, AtomicBoolean isFinished) {
        if (isFinished.compareAndSet(false, true)) {
            pendingResult.finish();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class LastLocation {

        private final double latitude;
        private final double longitude;

        private LastLocation(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
