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

// 매일 정해진 시간에 날씨와 일정을 비교하여 알림을 보냄
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
    private static final String OK_NOTIFICATION_TITLE = "PlanCast 날씨 예보";
    private static final String OK_NOTIFICATION_CONTENT =
            "예정된 일정 기간 동안 날씨가 좋습니다. 즐거운 하루 되세요!";
    private static final String TEST_NOTIFICATION_TITLE = "PlanCast 테스트 알림";
    private static final String TEST_NOTIFICATION_CONTENT =
            "테스트 모드: 예정된 야외 일정 날짜에 위험 날씨가 예보된 상황을 가정했습니다.";

    private static final boolean DEBUG_FORCE_NOTIFICATION_ONLY = false;
    private static final boolean DEBUG_USE_FAKE_RAIN_FORECAST = true;

    // 브로드캐스트 수신 시 실행됨
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        AtomicBoolean isFinished = new AtomicBoolean(false);
        Context appContext = context.getApplicationContext();

        try {
            Log.d(TAG, "Receiver 실행됨");

            if (DEBUG_FORCE_NOTIFICATION_ONLY) {
                // 테스트용 강제 알림 모드임
                showDebugRiskNotification(appContext, pendingResult, isFinished);
                return;
            }
            // 야외 일정 날씨 체크 시작함
            checkOutdoorScheduleWeather(appContext, pendingResult, isFinished);
        } catch (Exception exception) {
            Log.w(TAG, "Daily weather check failed before async work started.", exception);
            finishPendingResult(pendingResult, isFinished);
        }
    }

    // 디버그용 알림 표시함
    private void showDebugRiskNotification(
            Context context,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        try {
            NotificationHelper notificationHelper = new NotificationHelper(context);
            notificationHelper.showNotification(
                    DAILY_WEATHER_NOTIFICATION_ID,
                    TEST_NOTIFICATION_TITLE,
                    TEST_NOTIFICATION_CONTENT
            );
        } catch (Exception exception) {
            Log.w(TAG, "Failed to show debug risk notification.", exception);
        } finally {
            finishPendingResult(pendingResult, isFinished);
        }
    }

    // DB에서 야외 일정을 조회함
    private void checkOutdoorScheduleWeather(
            Context context,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        ScheduleRepository scheduleRepository = new ScheduleRepository(context);

        scheduleRepository.getOutdoorSchedulesWithinFiveDays(new ScheduleRepository.RepositoryCallback<List<ScheduleEntity>>() {
            @Override
            public void onSuccess(List<ScheduleEntity> schedules) {
                scheduleRepository.shutdown();

                try {
                    if (schedules == null || schedules.isEmpty()) {
                        finishPendingResult(pendingResult, isFinished);
                        return;
                    }

                    // 마지막 저장된 위치 정보 가져옴
                    LastLocation lastLocation = getLastLocation(context);
                    if (lastLocation == null) {
                        finishPendingResult(pendingResult, isFinished);
                        return;
                    }

                    // 날씨 예보 정보 요청함
                    fetchForecastAndNotifyIfRisky(
                            context,
                            schedules,
                            lastLocation,
                            pendingResult,
                            isFinished
                    );
                } catch (Exception exception) {
                    finishPendingResult(pendingResult, isFinished);
                }
            }

            @Override
            public void onError(Exception exception) {
                scheduleRepository.shutdown();
                finishPendingResult(pendingResult, isFinished);
            }
        });
    }

    // 날씨 예보를 가져와 위험 여부 판단함
    private void fetchForecastAndNotifyIfRisky(
            Context context,
            List<ScheduleEntity> schedules,
            LastLocation lastLocation,
            PendingResult pendingResult,
            AtomicBoolean isFinished
    ) {
        WeatherRepository weatherRepository = new WeatherRepository();

        weatherRepository.getForecast(lastLocation.latitude, lastLocation.longitude, new WeatherRepository.ForecastCallback() {
            @Override
            public void onSuccess(ForecastResponse forecastResponse) {
                if (DEBUG_USE_FAKE_RAIN_FORECAST) {
                    // 테스트를 위해 비 예보를 강제로 주입함
                    applyFakeRainForScheduleDates(schedules, forecastResponse);
                }

                // 예보 분석 및 알림 처리함
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
                finishPendingResult(pendingResult, isFinished);
            }
        });
    }

    // 분석 결과에 따라 알림을 보냄
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

            NotificationHelper notificationHelper = new NotificationHelper(context);
            if (!riskySchedules.isEmpty()) {
                // 위험 일정이 있으면 경고 알림 보냄
                notificationHelper.showNotification(
                        DAILY_WEATHER_NOTIFICATION_ID,
                        NOTIFICATION_TITLE,
                        NOTIFICATION_CONTENT
                );
            } else {
                // 위험 일정이 없으면 안심 알림 보냄
                notificationHelper.showNotification(
                        DAILY_WEATHER_NOTIFICATION_ID,
                        OK_NOTIFICATION_TITLE,
                        OK_NOTIFICATION_CONTENT
                );
            }
        } catch (Exception exception) {
            Log.w(TAG, "Failed to match forecast with schedules.", exception);
        } finally {
            finishPendingResult(pendingResult, isFinished);
        }
    }

    // 테스트용 비 예보 생성함
    private void applyFakeRainForScheduleDates(
            List<ScheduleEntity> schedules,
            ForecastResponse forecastResponse
    ) {
        if (forecastResponse == null || schedules == null) {
            return;
        }

        Set<String> addedDates = new LinkedHashSet<>();

        for (ScheduleEntity schedule : schedules) {
            if (schedule == null || isBlank(schedule.getDate()) || addedDates.contains(schedule.getDate())) {
                continue;
            }

            String scheduleDate = schedule.getDate();
            addedDates.add(scheduleDate);

            ForecastResponse.ForecastItem forecastItem =
                    forecastResponse.findFirstItemByDateForTest(scheduleDate);

            if (forecastItem == null) {
                forecastItem = ForecastResponse.ForecastItem.createRainItemForTest(scheduleDate);
                forecastResponse.addForecastItemForTest(forecastItem);
            } else {
                forecastItem.applyFakeRainForTest();
            }
        }
    }

    // 마지막 저장된 위치 반환함
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
