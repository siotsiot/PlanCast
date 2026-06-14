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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DailyWeatherCheckReceiver extends BroadcastReceiver {

    public static final String ACTION_DAILY_WEATHER_CHECK = "com.sch.plancast.action.DAILY_WEATHER_CHECK";
    public static final String WEATHER_PREFS_NAME = "plancast_weather_preferences";
    public static final String KEY_LAST_LATITUDE = "last_latitude";
    public static final String KEY_LAST_LONGITUDE = "last_longitude";
    public static final String KEY_HAS_LAST_LOCATION = "has_last_location";

    private static final String TAG = "DailyWeatherCheck";
    private static final int DAILY_WEATHER_NOTIFICATION_ID = 8001;
    private static final String NOTIFICATION_TITLE = "PlanCast 날씨 변경 알림";
    private static final String NOTIFICATION_CONTENT =
            "며칠 뒤 예정된 야외 일정의 날씨가 변경되었습니다! 우산이나 준비물을 확인하세요.";
    private static final String OK_NOTIFICATION_TITLE = "PlanCast 날씨 예보";
    private static final String OK_NOTIFICATION_CONTENT =
            "예정된 일정 기간 동안 날씨가 좋습니다. 즐거운 하루 되세요!";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        AtomicBoolean isFinished = new AtomicBoolean(false);
        Context appContext = context.getApplicationContext();

        try {
            checkOutdoorScheduleWeather(appContext, pendingResult, isFinished);
        } catch (Exception exception) {
            Log.w(TAG, "Daily weather check failed before async work started.", exception);
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
                try {
                    ForecastScheduleMatcher matcher = new ForecastScheduleMatcher();
                    List<ForecastScheduleRiskResult> riskySchedules =
                            matcher.findRiskyOutdoorSchedules(schedules, forecastResponse);

                    Log.d(TAG, "Risky outdoor schedules: " + riskySchedules.size());
                    NotificationHelper notificationHelper = new NotificationHelper(context);
                    if (!riskySchedules.isEmpty()) {
                        notificationHelper.showNotification(
                                DAILY_WEATHER_NOTIFICATION_ID,
                                NOTIFICATION_TITLE,
                                NOTIFICATION_CONTENT
                        );
                    } else {
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

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Forecast request failed: " + errorMessage);
                finishPendingResult(pendingResult, isFinished);
            }
        });
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

    private static class LastLocation {

        private final double latitude;
        private final double longitude;

        private LastLocation(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
